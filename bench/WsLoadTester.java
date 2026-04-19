import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class WsLoadTester {

    private static final class Config {
        String url;
        int connections = 1000;
        int rampSec = 30;
        int durationSec = 180;
        int sendIntervalMs = 1000;
        int messageBytes = 64;
        boolean expectEcho = true;
        boolean connectOnly = false;
        int connectTimeoutSec = 10;
        int maxLatencySamples = 1_000_000;
    }

    private static final class Stats {
        final LongAdder connectAttempts = new LongAdder();
        final LongAdder connectSuccess = new LongAdder();
        final LongAdder connectFailed = new LongAdder();
        final LongAdder messagesSent = new LongAdder();
        final LongAdder messagesSendFailed = new LongAdder();
        final LongAdder messagesReceivedText = new LongAdder();
        final LongAdder messagesReceivedBinary = new LongAdder();
        final LongAdder closes = new LongAdder();
        final LongAdder errors = new LongAdder();
        final AtomicInteger activeConnections = new AtomicInteger(0);
        final AtomicLong minLatencyNs = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong maxLatencyNs = new AtomicLong(0);
        final ConcurrentLinkedQueue<Long> latencySamplesNs = new ConcurrentLinkedQueue<Long>();
        final AtomicInteger sampledCount = new AtomicInteger(0);

        void recordLatency(long ns, int maxSamples) {
            if (ns < 0) {
                return;
            }

            minLatencyNs.accumulateAndGet(ns, Math::min);
            maxLatencyNs.accumulateAndGet(ns, Math::max);

            int idx = sampledCount.incrementAndGet();
            if (idx <= maxSamples) {
                latencySamplesNs.add(ns);
            }
        }
    }

    private static final class WsSession implements WebSocket.Listener {
        private final int id;
        private final Config cfg;
        private final Stats stats;
        private final ScheduledExecutorService scheduler;

        private volatile WebSocket ws;
        private volatile boolean closed;
        private int seq;

        WsSession(int id, Config cfg, Stats stats, ScheduledExecutorService scheduler) {
            this.id = id;
            this.cfg = cfg;
            this.stats = stats;
            this.scheduler = scheduler;
            this.closed = false;
            this.seq = 0;
        }

        CompletionStage<?> start(HttpClient client, URI uri) {
            stats.connectAttempts.increment();
            CompletableFuture<WebSocket> future = client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(cfg.connectTimeoutSec))
                    .buildAsync(uri, this);

            return future.whenComplete((socket, err) -> {
                if (err != null) {
                    stats.connectFailed.increment();
                } else {
                    ws = socket;
                }
            });
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            stats.connectSuccess.increment();
            stats.activeConnections.incrementAndGet();
            webSocket.request(1);

            if (!cfg.connectOnly) {
                scheduler.scheduleAtFixedRate(this::sendOne, 0, cfg.sendIntervalMs, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            stats.messagesReceivedText.increment();
            if (cfg.expectEcho) {
                long sentNs = parseSentNano(data.toString());
                if (sentNs > 0) {
                    stats.recordLatency(System.nanoTime() - sentNs, cfg.maxLatencySamples);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            stats.messagesReceivedBinary.increment();
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!closed) {
                closed = true;
                stats.activeConnections.decrementAndGet();
                stats.closes.increment();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            stats.errors.increment();
        }

        void close() {
            WebSocket socket = ws;
            if (socket != null && !closed) {
                try {
                    socket.sendClose(WebSocket.NORMAL_CLOSURE, "load-test-end");
                } catch (Exception ignored) {
                }
            }
        }

        private void sendOne() {
            if (closed || ws == null) {
                return;
            }

            String payload = createPayload();
            try {
                ws.sendText(payload, true)
                        .orTimeout(5, TimeUnit.SECONDS)
                        .whenComplete((ignore, err) -> {
                            if (err != null) {
                                stats.messagesSendFailed.increment();
                            } else {
                                stats.messagesSent.increment();
                            }
                        });
            } catch (Exception e) {
                stats.messagesSendFailed.increment();
            }
        }

        private String createPayload() {
            long nowNs = System.nanoTime();
            String prefix = "T:" + nowNs + ":" + id + ":" + (seq++) + "|";

            if (cfg.messageBytes <= prefix.length()) {
                return prefix;
            }

            int fill = cfg.messageBytes - prefix.length();
            char[] chars = new char[fill];
            Arrays.fill(chars, 'x');
            return prefix + new String(chars);
        }

        private static long parseSentNano(String payload) {
            if (!payload.startsWith("T:")) {
                return -1L;
            }

            int p1 = payload.indexOf(':', 2);
            if (p1 < 0) {
                return -1L;
            }

            try {
                return Long.parseLong(payload.substring(2, p1));
            } catch (NumberFormatException nfe) {
                return -1L;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Config cfg = parseArgs(args);
        validate(cfg);

        URI uri = URI.create(cfg.url);
        Stats stats = new Stats();

        int schedulerThreads = Math.max(8, Math.min(64, cfg.connections / 64));
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(schedulerThreads);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(cfg.connectTimeoutSec))
                .executor(scheduler)
                .build();

        List<WsSession> sessions = new ArrayList<WsSession>(cfg.connections);
        CountDownLatch rampLatch = new CountDownLatch(cfg.connections);

        long testStartNs = System.nanoTime();
        long rampTotalMs = Math.max(1, cfg.rampSec * 1000L);
        long stepMs = Math.max(1, rampTotalMs / Math.max(1, cfg.connections));

        for (int i = 0; i < cfg.connections; i++) {
            int id = i;
            WsSession session = new WsSession(id, cfg, stats, scheduler);
            sessions.add(session);

            long delay = id * stepMs;
            scheduler.schedule(() -> {
                try {
                    session.start(client, uri).toCompletableFuture().join();
                } catch (Exception ignored) {
                } finally {
                    rampLatch.countDown();
                }
            }, delay, TimeUnit.MILLISECONDS);
        }

        long lastSent = 0L;
        long lastRecv = 0L;
        long lastErr = 0L;
        for (int sec = 1; sec <= cfg.durationSec; sec++) {
            Thread.sleep(1000L);

            long sent = stats.messagesSent.sum();
            long recv = stats.messagesReceivedText.sum() + stats.messagesReceivedBinary.sum();
            long err = stats.errors.sum() + stats.messagesSendFailed.sum() + stats.connectFailed.sum();
            int active = stats.activeConnections.get();

            double outRate = sent - lastSent;
            double inRate = recv - lastRecv;
            double errRate = err - lastErr;

            lastSent = sent;
            lastRecv = recv;
            lastErr = err;

            System.out.printf(
                    Locale.US,
                    "[%4ds] active=%d connectOK=%d connectFail=%d send/s=%.0f recv/s=%.0f err/s=%.0f%n",
                    sec,
                    active,
                    stats.connectSuccess.sum(),
                    stats.connectFailed.sum(),
                    outRate,
                    inRate,
                    errRate
            );
        }

        rampLatch.await(30, TimeUnit.SECONDS);
        for (WsSession s : sessions) {
            s.close();
        }
        Thread.sleep(2000L);
        scheduler.shutdownNow();
        scheduler.awaitTermination(10, TimeUnit.SECONDS);

        long elapsedNs = System.nanoTime() - testStartNs;
        emitSummary(cfg, stats, elapsedNs);
    }

    private static void emitSummary(Config cfg, Stats stats, long elapsedNs) {
        long connectOk = stats.connectSuccess.sum();
        long connectFail = stats.connectFailed.sum();
        long sent = stats.messagesSent.sum();
        long recv = stats.messagesReceivedText.sum() + stats.messagesReceivedBinary.sum();
        long totalErrors = stats.errors.sum() + stats.messagesSendFailed.sum() + connectFail;

        double elapsedSec = elapsedNs / 1_000_000_000.0;
        double sentPerSec = sent / Math.max(1.0, elapsedSec);
        double recvPerSec = recv / Math.max(1.0, elapsedSec);
        double connectFailRatio = (connectOk + connectFail) == 0
                ? 0.0
                : (connectFail * 100.0 / (connectOk + connectFail));

        long[] pct = latencyPercentiles(stats.latencySamplesNs);
        double p50ms = nanosToMs(pct[0]);
        double p95ms = nanosToMs(pct[1]);
        double p99ms = nanosToMs(pct[2]);
        double minMs = nanosToMs(stats.minLatencyNs.get() == Long.MAX_VALUE ? 0L : stats.minLatencyNs.get());
        double maxMs = nanosToMs(stats.maxLatencyNs.get());

        System.out.println();
        System.out.println("===== FINAL SUMMARY =====");
        System.out.printf(Locale.US, "url=%s%n", cfg.url);
        System.out.printf(Locale.US, "connections_target=%d active_end=%d connect_ok=%d connect_fail=%d fail_ratio=%.2f%%%n",
                cfg.connections, stats.activeConnections.get(), connectOk, connectFail, connectFailRatio);
        System.out.printf(Locale.US, "messages_sent=%d messages_recv=%d send_tps=%.2f recv_tps=%.2f%n",
                sent, recv, sentPerSec, recvPerSec);
        System.out.printf(Locale.US, "errors_total=%d closes=%d%n", totalErrors, stats.closes.sum());
        System.out.printf(Locale.US, "latency_ms p50=%.2f p95=%.2f p99=%.2f min=%.2f max=%.2f samples=%d%n",
                p50ms, p95ms, p99ms, minMs, maxMs, stats.latencySamplesNs.size());

        System.out.printf(
                Locale.US,
                "FINAL_JSON {\"url\":\"%s\",\"connections\":%d,\"connect_ok\":%d,\"connect_fail\":%d,\"fail_ratio_pct\":%.4f,\"messages_sent\":%d,\"messages_recv\":%d,\"send_tps\":%.4f,\"recv_tps\":%.4f,\"errors_total\":%d,\"latency_p50_ms\":%.4f,\"latency_p95_ms\":%.4f,\"latency_p99_ms\":%.4f,\"latency_min_ms\":%.4f,\"latency_max_ms\":%.4f,\"latency_samples\":%d,\"elapsed_sec\":%.4f}%n",
                escapeJson(cfg.url),
                cfg.connections,
                connectOk,
                connectFail,
                connectFailRatio,
                sent,
                recv,
                sentPerSec,
                recvPerSec,
                totalErrors,
                p50ms,
                p95ms,
                p99ms,
                minMs,
                maxMs,
                stats.latencySamplesNs.size(),
                elapsedSec
        );
    }

    private static String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static long[] latencyPercentiles(ConcurrentLinkedQueue<Long> samples) {
        if (samples.isEmpty()) {
            return new long[] {0L, 0L, 0L};
        }

        long[] arr = new long[samples.size()];
        int idx = 0;
        for (Long val : samples) {
            if (val != null) {
                arr[idx++] = val.longValue();
            }
        }

        if (idx == 0) {
            return new long[] {0L, 0L, 0L};
        }

        if (idx < arr.length) {
            arr = Arrays.copyOf(arr, idx);
        }

        Arrays.sort(arr);
        return new long[] {
                percentile(arr, 0.50),
                percentile(arr, 0.95),
                percentile(arr, 0.99)
        };
    }

    private static long percentile(long[] sorted, double p) {
        if (sorted.length == 0) {
            return 0L;
        }
        int idx = (int) Math.ceil(p * sorted.length) - 1;
        if (idx < 0) {
            idx = 0;
        } else if (idx >= sorted.length) {
            idx = sorted.length - 1;
        }
        return sorted[idx];
    }

    private static double nanosToMs(long ns) {
        return ns / 1_000_000.0;
    }

    private static void validate(Config cfg) {
        Objects.requireNonNull(cfg.url, "Missing --url");
        if (!cfg.url.startsWith("ws://") && !cfg.url.startsWith("wss://")) {
            throw new IllegalArgumentException("--url must start with ws:// or wss://");
        }
        if (cfg.connections <= 0) {
            throw new IllegalArgumentException("--connections must be > 0");
        }
        if (cfg.durationSec <= 0) {
            throw new IllegalArgumentException("--durationSec must be > 0");
        }
        if (!cfg.connectOnly && cfg.sendIntervalMs <= 0) {
            throw new IllegalArgumentException("--sendIntervalMs must be > 0");
        }
    }

    private static Config parseArgs(String[] args) {
        Config cfg = new Config();

        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }

            int eq = arg.indexOf('=');
            if (eq <= 2 || !arg.startsWith("--")) {
                continue;
            }

            String key = arg.substring(2, eq).trim();
            String value = arg.substring(eq + 1).trim();

            switch (key) {
                case "url":
                    cfg.url = value;
                    break;
                case "connections":
                    cfg.connections = Integer.parseInt(value);
                    break;
                case "rampSec":
                    cfg.rampSec = Integer.parseInt(value);
                    break;
                case "durationSec":
                    cfg.durationSec = Integer.parseInt(value);
                    break;
                case "sendIntervalMs":
                    cfg.sendIntervalMs = Integer.parseInt(value);
                    break;
                case "messageBytes":
                    cfg.messageBytes = Integer.parseInt(value);
                    break;
                case "expectEcho":
                    cfg.expectEcho = Boolean.parseBoolean(value);
                    break;
                case "connectOnly":
                    cfg.connectOnly = Boolean.parseBoolean(value);
                    break;
                case "connectTimeoutSec":
                    cfg.connectTimeoutSec = Integer.parseInt(value);
                    break;
                case "maxLatencySamples":
                    cfg.maxLatencySamples = Integer.parseInt(value);
                    break;
                default:
                    break;
            }
        }

        return cfg;
    }
}

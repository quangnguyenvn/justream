# justream

`justream` is a low-level Java library for building HTTP/WebSocket servers for real-time streaming workloads.  
![justream icon](assets/justream-icon.svg)

Unlike full-stack frameworks, `justream` gives you deep control over I/O flow, thread model, queueing, buffering, TLS handling, and endpoint routing behavior.

## What justream is good for

- Realtime gateways for dashboards, telemetry, and notifications.
- WebSocket-heavy services with many concurrent long-lived connections.
- Teams that want to build a custom-fit server tuned for a specific workload.

High-level architecture:

- `Acceptor` accepts new connections.
- `Poller` uses Java NIO selectors to read/process incoming data.
- `Worker` executes request handlers.
- Built-in HTTP -> WebSocket upgrade and TLS engine support.

## Why developers choose justream

### 1) Deep customization

You can tune critical knobs directly:

- `numPollers`, `numWorkers`, queue sizes.
- timeout behavior, recv buffer sizing, write strategy.
- session distribution across pollers/workers.

### 2) Performance tuning by workload

For lightweight realtime traffic with many connections, this model can be very cost-efficient when tuned correctly.

### 3) Practical for SMB teams with strong engineering ownership

Small and medium businesses can reduce:

- number of service instances/servers,
- CPU and RAM per connection,
- total cloud compute cost.

Important: this is not automatic. Savings come from benchmark-driven tuning, not from defaults.

## Requirements

### Runtime

- JDK 17+
- Ant (for the existing build flow)

### Dependency note

Current source imports `jucommon.*`.

You need `jucommon` available in classpath (or as local jars) to build the core server modules.

## Build

`build.xml` is already included:

```powershell
cd D:\git\justream
ant main
```

If your jar locations differ by environment, update the jar path in `build.xml`.

## Quick performance smoke test (local)

This repo includes a Java load tester and local mock WebSocket endpoints to validate your benchmark pipeline before testing real environments.

### 1) Start local mock endpoints

```bat
cd D:\git\justream\mock-endpoints
start-mocks.cmd
```

Default URLs:

- baseline: `ws://127.0.0.1:18081/ws`
- candidate: `ws://127.0.0.1:18082/ws`

### 2) Run benchmark scenarios

```powershell
cd D:\git\justream

# baseline
.\bench\run-scenarios.ps1 -Url "ws://127.0.0.1:18081/ws" -Tag "baseline"

# candidate
.\bench\run-scenarios.ps1 -Url "ws://127.0.0.1:18082/ws" -Tag "candidate"
```

### 3) Compare results

```powershell
.\bench\compare-results.ps1 `
  -BaselineDir "D:\git\justream\bench\results\baseline_YYYYMMDD_HHMMSS" `
  -CandidateDir "D:\git\justream\bench\results\candidate_YYYYMMDD_HHMMSS"
```

Detailed benchmark guidance:

- `docs/perf-validation-guide.vi.md`

## Suggested onboarding path for new developers

1. Start with one small, bounded use case (for example: an internal realtime dashboard).
2. Define baseline KPIs clearly (`recv_tps`, `p95`, `p99`, fail rate).
3. Run A/B benchmarks on the exact same workload.
4. Target realistic gains first (for example: `1.2x -> 2x`) before stretching goals.
5. Move to broader production rollout only after stability is proven.

## Repository map

- `common/` low-level channel, TLS, buffers, socket writers.
- `core/` acceptor/poller/worker pipeline and worker management.
- `http/` HTTP session and request/response parsing.
- `ws/` WebSocket session, frame processing, WS poller.
- `session/` session and handler abstractions.
- `bench/` load tester and benchmark scripts.
- `mock-endpoints/` local websocket echo endpoints for quick testing.

## Current status and production considerations

`justream` is a strong base for custom realtime services and performance-focused experimentation.

For production-grade rollout, plan to add:

- observability (metrics, tracing, structured logs),
- backpressure/rate limiting protections,
- stress/chaos/regression test suites.

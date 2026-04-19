const http = require("http");
const { WebSocketServer } = require("ws");

function parseArgs(argv) {
  const out = {};
  for (const arg of argv) {
    if (!arg.startsWith("--")) continue;
    const eq = arg.indexOf("=");
    if (eq <= 2) continue;
    const k = arg.slice(2, eq);
    const v = arg.slice(eq + 1);
    out[k] = v;
  }
  return out;
}

const args = parseArgs(process.argv.slice(2));

const name = args.name || "ws-endpoint";
const host = args.host || "127.0.0.1";
const port = Number(args.port || 18081);
const path = args.path || "/ws";
const sendWelcome = String(args.sendWelcome || "false").toLowerCase() === "true";
const artificialDelayMs = Number(args.delayMs || 0);

const server = http.createServer((req, res) => {
  if (req.url === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ ok: true, name, host, port, path }));
    return;
  }

  res.writeHead(404, { "Content-Type": "text/plain" });
  res.end("Not Found");
});

const wss = new WebSocketServer({ server, path });

wss.on("connection", (ws, req) => {
  if (sendWelcome) {
    ws.send(`WELCOME:${name}`);
  }

  ws.on("message", (data, isBinary) => {
    const send = () => ws.send(data, { binary: isBinary });
    if (artificialDelayMs > 0) {
      setTimeout(send, artificialDelayMs);
    } else {
      send();
    }
  });
});

server.listen(port, host, () => {
  console.log(`[${name}] listening at ws://${host}:${port}${path}`);
  console.log(`[${name}] health at   http://${host}:${port}/health`);
});

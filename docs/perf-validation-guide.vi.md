# Kiem Chung Kha Thi 2x/3x/5x Cho Justream

Tai lieu nay giup ban tra loi cau hoi: "Justream co giup tiet kiem server that su khong?"

## 1) Nguyen tac test de tranh sai lech

- Test A/B tren cung workload:
  - A: stack hien tai (baseline).
  - B: stack dung Justream (candidate).
- Cung cau hinh may, cung network zone, cung payload, cung thoi gian test.
- Moi case chay it nhat 3 lan, lay median.
- Luon co warmup truoc khi do ket qua chinh.

## 2) KPI can do

- `recv_tps`: thong luong message nhan duoc (cang cao cang tot).
- `latency_p95_ms` va `latency_p99_ms`: do tre cuoi duoi (cang thap cang tot).
- `fail_ratio_pct`: ti le ket noi that bai (cang thap cang tot).
- `errors_total`: tong loi runtime.

## 3) Dieu kien de duoc coi la "thang"

Ban co the dat rule tham khao:

- 2x kha thi:
  - `tps_ratio >= 2.0`
  - `p95_ratio <= 1.2` (do tre khong xau hon qua 20%)
  - `fail_pct_delta <= 0.2`
- 3x kha thi:
  - `tps_ratio >= 3.0`
  - `p95_ratio <= 1.3`
  - `fail_pct_delta <= 0.3`
- 5x kha thi:
  - `tps_ratio >= 5.0`
  - `p95_ratio <= 1.5`
  - `fail_pct_delta <= 0.5`

Trong do:
- `tps_ratio = candidate_recv_tps / baseline_recv_tps`
- `p95_ratio = candidate_p95_ms / baseline_p95_ms`
- `fail_pct_delta = candidate_fail_pct - baseline_fail_pct`

## 4) Cach chay bo benchmark da duoc scaffold

### 4.1 Chay cho baseline

```powershell
cd D:\git\justream
.\bench\run-scenarios.ps1 -Url "ws://<baseline-host>:<port>/<path>" -Tag "baseline" -MainConnections 2000
```

### 4.2 Chay cho candidate (Justream)

```powershell
cd D:\git\justream
.\bench\run-scenarios.ps1 -Url "ws://<candidate-host>:<port>/<path>" -Tag "candidate" -MainConnections 2000
```

Moi lan chay se tao thu muc ket qua trong `bench/results/...` va co 3 case:

- `01_warmup`
- `02_sustained_1msgps`
- `03_highrate_5msgps`

Moi case co:
- `.log` (raw logs)
- `.json` (chi so tong hop, lay tu dong tu dong `FINAL_JSON`)

### 4.3 So sanh A/B

```powershell
.\bench\compare-results.ps1 `
  -BaselineDir "D:\git\justream\bench\results\baseline_YYYYMMDD_HHMMSS" `
  -CandidateDir "D:\git\justream\bench\results\candidate_YYYYMMDD_HHMMSS"
```

## 5) Mau ket luan bao cao noi bo

- Muc tieu: xac nhan kha nang giam chi phi ha tang cho realtime gateway.
- Workload: 2,000 ket noi dong thoi, 1 msg/s va 5 msg/s.
- Ket qua:
  - `tps_ratio = ...`
  - `p95_ratio = ...`
  - `fail_pct_delta = ...`
- Ket luan:
  - Dat/khong dat moc 2x
  - Dat/khong dat moc 3x
  - Dat/khong dat moc 5x
- De xuat:
  - Neu dat 2x tro len: lam pilot production nho.
  - Neu chua dat: toi uu them poller/worker, TLS, payload strategy, queue sizing.

## 6) Luu y quan trong

- Tool `WsLoadTester` gia dinh server co echo message text neu `--expectEcho=true`.
- Neu endpoint khong echo, chuyen sang:
  - `--expectEcho=false` de do throughput/conection stability.
- Latency duoc tinh theo timestamp embed trong message text.
- De phan tich "tiet kiem server", nen bo sung do CPU/RAM/network o host bang telemetry he thong.

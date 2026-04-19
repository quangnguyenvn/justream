# Mock endpoints de chay benchmark nhanh

Khi chua co endpoint that (baseline/candidate), ban co the dung cap mock nay de test pipeline benchmark.

## Start

```bat
cd D:\git\justream\mock-endpoints
start-mocks.cmd
```

URL tao ra:

- baseline: `ws://127.0.0.1:18081/ws`
- candidate: `ws://127.0.0.1:18082/ws`

Ghi chu:

- baseline mock co delay nho `2ms` moi message.
- candidate mock khong delay.
- Day la mock de kiem tra quy trinh benchmark, **khong phai** ket qua nang luc that cua justream.

## Stop

```bat
cd D:\git\justream\mock-endpoints
stop-mocks.cmd
```

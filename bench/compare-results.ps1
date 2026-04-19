param(
    [Parameter(Mandatory = $true)]
    [string]$BaselineDir,

    [Parameter(Mandatory = $true)]
    [string]$CandidateDir
)

$ErrorActionPreference = "Stop"

function Read-JsonFile {
    param([string]$Path)
    if (!(Test-Path $Path)) {
        throw "Missing file: $Path"
    }
    return Get-Content $Path -Raw | ConvertFrom-Json
}

function Ratio {
    param([double]$A, [double]$B)
    if ($A -eq 0) { return [double]::NaN }
    return $B / $A
}

$cases = @(
    "01_warmup",
    "02_sustained_1msgps",
    "03_highrate_5msgps"
)

$rows = @()
foreach ($name in $cases) {
    $base = Read-JsonFile (Join-Path $BaselineDir ($name + ".json"))
    $cand = Read-JsonFile (Join-Path $CandidateDir ($name + ".json"))

    $tpsRatio = Ratio -A $base.recv_tps -B $cand.recv_tps
    $p95Ratio = Ratio -A $base.latency_p95_ms -B $cand.latency_p95_ms
    $failDelta = [double]$cand.fail_ratio_pct - [double]$base.fail_ratio_pct

    $rows += [pscustomobject]@{
        case = $name
        baseline_recv_tps = [math]::Round([double]$base.recv_tps, 2)
        candidate_recv_tps = [math]::Round([double]$cand.recv_tps, 2)
        tps_ratio = [math]::Round($tpsRatio, 3)
        baseline_p95_ms = [math]::Round([double]$base.latency_p95_ms, 2)
        candidate_p95_ms = [math]::Round([double]$cand.latency_p95_ms, 2)
        p95_ratio = [math]::Round($p95Ratio, 3)
        baseline_fail_pct = [math]::Round([double]$base.fail_ratio_pct, 3)
        candidate_fail_pct = [math]::Round([double]$cand.fail_ratio_pct, 3)
        fail_pct_delta = [math]::Round($failDelta, 3)
    }
}

$rows | Format-Table -AutoSize

Write-Host ""
Write-Host "Interpretation:"
Write-Host "- tps_ratio > 1 means candidate handles more throughput."
Write-Host "- p95_ratio < 1 means candidate has better latency."
Write-Host "- fail_pct_delta <= 0 means candidate is at least as stable."

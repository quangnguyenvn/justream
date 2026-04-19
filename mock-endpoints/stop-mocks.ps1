$ErrorActionPreference = "Continue"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$pidFiles = @(
    (Join-Path $root "baseline-mock.pid"),
    (Join-Path $root "candidate-mock.pid")
)

foreach ($pidFile in $pidFiles) {
    if (!(Test-Path $pidFile)) {
        continue
    }

    try {
        $jobId = Get-Content $pidFile -Raw
        if ($jobId) {
            $job = Get-Job -Id ([int]$jobId) -ErrorAction SilentlyContinue
            if ($null -ne $job) {
                Stop-Job -Id ([int]$jobId) -Force -ErrorAction SilentlyContinue
                Remove-Job -Id ([int]$jobId) -Force -ErrorAction SilentlyContinue
                Write-Host ("Stopped JobId={0}" -f $jobId)
            }
        }
    } catch {
    } finally {
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Done."

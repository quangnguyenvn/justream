param(
    [string]$BindHost = "127.0.0.1",
    [string]$PathName = "/ws",
    [int]$BaselinePort = 18081,
    [int]$CandidatePort = 18082
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Start-Endpoint {
    param(
        [string]$Name,
        [int]$Port,
        [int]$DelayMs
    )

    $args = @(
        "echo-server.js",
        "--name=$Name",
        "--host=$BindHost",
        "--port=$Port",
        "--path=$PathName",
        "--delayMs=$DelayMs"
    )
    $log = Join-Path $root ("$Name.out.log")
    $pidFile = Join-Path $root ("$Name.pid")

    $job = Start-Job -Name $Name -ScriptBlock {
        param($wd, $argv, $logFile)
        Set-Location $wd
        & node @argv *>> $logFile
    } -ArgumentList $root, $args, $log

    Set-Content -Path $pidFile -Value $job.Id
    Write-Host ("Started {0} on ws://{1}:{2}{3} (JobId={4})" -f $Name, $BindHost, $Port, $PathName, $job.Id)
}

# Baseline mock: add tiny delay so you can validate compare pipeline end-to-end.
Start-Endpoint -Name "baseline-mock" -Port $BaselinePort -DelayMs 2

# Candidate mock: no delay
Start-Endpoint -Name "candidate-mock" -Port $CandidatePort -DelayMs 0

Write-Host ""
Write-Host "Use these URLs:"
Write-Host ("baseline  = ws://{0}:{1}{2}" -f $BindHost, $BaselinePort, $PathName)
Write-Host ("candidate = ws://{0}:{1}{2}" -f $BindHost, $CandidatePort, $PathName)

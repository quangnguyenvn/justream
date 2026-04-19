param(
    [Parameter(Mandatory = $true)]
    [string]$Url,

    [string]$Tag = "candidate",

    [string]$OutDir = "",

    [int]$WarmupConnections = 500,
    [int]$MainConnections = 2000
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$binDir = Join-Path $PSScriptRoot "bin"

if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $OutDir = Join-Path $PSScriptRoot ("results\" + $Tag + "_" + $stamp)
}

New-Item -ItemType Directory -Force -Path $binDir | Out-Null
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

Write-Host "Compiling WsLoadTester.java ..."
javac -d $binDir (Join-Path $PSScriptRoot "WsLoadTester.java")

function Run-Case {
    param(
        [string]$Name,
        [string]$CaseArgs
    )

    $logFile = Join-Path $OutDir ($Name + ".log")
    $jsonFile = Join-Path $OutDir ($Name + ".json")
    Write-Host ("Running {0} -> {1}" -f $Name, $logFile)

    $argList = @("-cp", $binDir, "WsLoadTester") + ($CaseArgs -split "\s+")
    & java @argList 2>&1 | Tee-Object -FilePath $logFile

    $final = Select-String -Path $logFile -Pattern "^FINAL_JSON " | Select-Object -Last 1
    if ($null -eq $final) {
        throw "Missing FINAL_JSON output in $logFile"
    }

    $json = $final.Line.Substring("FINAL_JSON ".Length)
    Set-Content -Path $jsonFile -Value $json -Encoding UTF8
}

# 1) Warmup
$warmupArgs = @(
    "--url=$Url",
    "--connections=$WarmupConnections",
    "--rampSec=20",
    "--durationSec=60",
    "--connectOnly=false",
    "--expectEcho=true",
    "--sendIntervalMs=1000",
    "--messageBytes=64"
) -join " "
Run-Case -Name "01_warmup" -CaseArgs $warmupArgs

# 2) Sustained low rate
$sustainedArgs = @(
    "--url=$Url",
    "--connections=$MainConnections",
    "--rampSec=60",
    "--durationSec=300",
    "--connectOnly=false",
    "--expectEcho=true",
    "--sendIntervalMs=1000",
    "--messageBytes=64"
) -join " "
Run-Case -Name "02_sustained_1msgps" -CaseArgs $sustainedArgs

# 3) Higher message rate
$highRateArgs = @(
    "--url=$Url",
    "--connections=$MainConnections",
    "--rampSec=60",
    "--durationSec=300",
    "--connectOnly=false",
    "--expectEcho=true",
    "--sendIntervalMs=200",
    "--messageBytes=64"
) -join " "
Run-Case -Name "03_highrate_5msgps" -CaseArgs $highRateArgs

Write-Host ""
Write-Host "Done. Results folder: $OutDir"
Write-Host "Use compare-results.ps1 to compare baseline vs candidate."

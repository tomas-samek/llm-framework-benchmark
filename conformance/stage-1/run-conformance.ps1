# Grade one Stage-1 trial.
# Usage:
#   ./run-conformance.ps1 -Framework spring -Trial 01 -AppStartCmd "mvn -q -pl app spring-boot:run"
# The app start command is run from the trial working directory and must connect
# to localhost:9092. The script starts it in the background, grades, then stops it.
param(
    [Parameter(Mandatory = $true)][string]$Framework,
    [Parameter(Mandatory = $true)][string]$Trial,
    [Parameter(Mandatory = $true)][string]$AppStartCmd,
    [string]$TrialDir = ""
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot/../.."
if ($TrialDir -eq "") {
    $TrialDir = Join-Path $Root "runs/stage-1/$Framework/trial-$Trial"
}
$Scenarios = Join-Path $Root "fixtures/stage-1/scenarios.json"
$OracleJar = Join-Path $Root "conformance/oracle/target/conformance-oracle.jar"
$OutJson   = Join-Path $Root "results/stage-1-$Framework-$Trial.json"
$Compose   = Join-Path $Root "conformance/docker-compose.yml"

Write-Host "Bringing up broker..."
docker compose -f $Compose up -d
Start-Sleep -Seconds 8

Write-Host "Starting contestant app in $TrialDir ..."
$startedAt = Get-Date
$app = Start-Process -FilePath "powershell" -ArgumentList "-NoProfile","-Command",$AppStartCmd `
    -WorkingDirectory $TrialDir -PassThru
Start-Sleep -Seconds 20  # let consumers join before publishing

try {
    Write-Host "Running oracle..."
    & java -jar $OracleJar "localhost:9092" $Scenarios $OutJson
    $code = $LASTEXITCODE
} finally {
    if (-not $app.HasExited) { Stop-Process -Id $app.Id -Force }
    docker compose -f $Compose down
}

$elapsed = [int]((Get-Date) - $startedAt).TotalSeconds
$compliance = (Get-Content $OutJson | ConvertFrom-Json).compliance
$row = "{0},{1},stage-1,{2},{3},{4},,," -f (Get-Date -Format s), $Framework, $Trial, $compliance, $elapsed
Add-Content -Path (Join-Path $Root "results/metrics.csv") -Value $row
Write-Host "Compliance=$compliance elapsed=${elapsed}s (oracle exit $code)"
exit $code

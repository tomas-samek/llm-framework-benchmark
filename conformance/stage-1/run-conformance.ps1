# Grade one Stage-1 trial (portable single-trial variant of grade-batch.ps1).
# Usage:
#   $env:BENCH_MODEL = 'claude-opus-4-8'
#   ./run-conformance.ps1 -Framework spring -Trial 01 -AppStartCmd "java -jar target/notify-0.0.1-SNAPSHOT.jar"
# The app start command is run from the trial working directory and must connect
# to localhost:9092. The script brings up a fresh broker (down -v), pre-creates the
# topics, starts the app in the background, grades, then stops the app's whole
# process tree and tears the broker down. Uses java/docker from PATH.
#   -Fixtures v1 (default) grades with fixtures/stage-1/scenarios.json -> results/metrics.csv;
#   -Fixtures v2 grades with scenarios.v2.json -> results/*.v2.json + results/metrics-v2.csv
#   (see fixtures/stage-1/README.md).
param(
    [Parameter(Mandatory = $true)][string]$Framework,
    [Parameter(Mandatory = $true)][string]$Trial,
    [Parameter(Mandatory = $true)][string]$AppStartCmd,
    [string]$TrialDir = "",
    [ValidateSet('v1', 'v2')][string]$Fixtures = 'v1'
)

$ErrorActionPreference = "Continue"
$Root = (Resolve-Path "$PSScriptRoot/../..").Path
if ($TrialDir -eq "") {
    $TrialDir = Join-Path $Root "runs/stage-1/$Framework/trial-$Trial"
}
$Suffix    = if ($Fixtures -eq 'v2') { '.v2' } else { '' }
$Scenarios = Join-Path $Root "fixtures/stage-1/scenarios$Suffix.json"
$OracleJar = Join-Path $Root "conformance/oracle/target/conformance-oracle.jar"
$OutJson   = Join-Path $Root "results/stage-1-$Framework-$Trial$Suffix.json"
$Compose   = Join-Path $Root "conformance/docker-compose.yml"
$Metrics   = Join-Path $Root "results/metrics$(if ($Fixtures -eq 'v2') { '-v2' } else { '' }).csv"
$Topics    = @('product-updates', 'user-updates', 'price-updates', 'purchases', 'notifications')
$Model = $env:BENCH_MODEL
if (-not $Model) { $Model = 'unknown' }

if (-not (Test-Path $OracleJar)) { Write-Host "Oracle jar missing at $OracleJar - build it first."; exit 2 }
if (-not (Test-Path $TrialDir))  { Write-Host "Trial dir missing: $TrialDir"; exit 2 }

# Fresh broker per trial: apache/kafka keeps topic data in an anonymous volume, so -v is mandatory.
Write-Host "Bringing up a fresh broker..."
docker compose -f $Compose down -v | Out-Null
docker compose -f $Compose up -d | Out-Null
Start-Sleep -Seconds 10
foreach ($topic in $Topics) {
    docker exec bench-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic $topic --partitions 1 --replication-factor 1 | Out-Null
}

Write-Host "Starting contestant app in $TrialDir ..."
$startedAt = Get-Date
$app = Start-Process -FilePath "powershell" -ArgumentList "-NoProfile", "-Command", $AppStartCmd `
    -WorkingDirectory $TrialDir -RedirectStandardOutput (Join-Path $TrialDir 'app.log') `
    -RedirectStandardError (Join-Path $TrialDir 'app.err.log') -PassThru
Start-Sleep -Seconds 40  # let the app boot and its consumers join before publishing

$code = 1
try {
    Write-Host "Running oracle..."
    & java -jar $OracleJar "localhost:9092" $Scenarios $OutJson
    $code = $LASTEXITCODE
} finally {
    # Kill the whole tree (powershell -> mvn -> java): a surviving JVM would reconnect
    # to the next trial's broker and contaminate its topics.
    if (-not $app.HasExited) { taskkill /PID $app.Id /T /F | Out-Null }
    docker compose -f $Compose down -v | Out-Null
}

$elapsed = [int]((Get-Date) - $startedAt).TotalSeconds
$compliance = 0
if (Test-Path $OutJson) {
    try { $compliance = [math]::Round((Get-Content $OutJson -Raw | ConvertFrom-Json).compliance * 100) } catch { $compliance = 0 }
}

# metrics row: timestamp,model,framework,stage,trial,compliance,wall_clock_seconds,output_tokens,agent_turns,tool_calls,cost_usd
# Token/turn/cost columns are left blank; backfill them with conformance/token-accounting.py.
$ts = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
Add-Content -Path $Metrics -Value ("{0},{1},{2},stage-1,{3},{4},{5},,,," -f $ts, $Model, $Framework, $Trial, $compliance, $elapsed)
Write-Host "Compliance=$compliance% elapsed=${elapsed}s (oracle exit $code)"
exit $code

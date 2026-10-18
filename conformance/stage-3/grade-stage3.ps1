# Grade Stage-3 trials (docs/specs/stage-3-spec.md): for each trial, one fresh broker and
# ONE app instance, graded twice in a row by the conformance oracle:
#   1. regression: the Stage-1 v2 scenarios (fixtures/stage-1/scenarios.v2.json)
#   2. search:     the Stage-3 HTTP checks (fixtures/stage-3/scenarios.json), which run
#                  after the Stage-1 data is in the store, as the spec describes.
# Fully mechanical, no LLM involvement, safe to re-run.
#
# Usage:
#   $env:BENCH_MODEL = 'claude-sonnet-5-5'
#   .\grade-stage3.ps1 -Trials @('s3-spring-411:s55-01:jar','s3-tiko-060:s55-01:mvn')
#
# Trial entry: <cell>:<trialId>:<runType>, trial dir runs/stage-3/<cell>/trial-<trialId>.
# Results: results/stage-3-<cell>-<trialId>.regression.json and .search.json, plus one row
# in results/metrics-stage3.csv.
param(
    [Parameter(Mandatory = $true)][string[]]$Trials
)

$ErrorActionPreference = 'Continue'
$Root       = (Resolve-Path "$PSScriptRoot\..\..").Path
$Compose    = Join-Path $Root 'conformance\docker-compose.yml'
$Oracle     = Join-Path $Root 'conformance\oracle\target\conformance-oracle.jar'
$Regression = Join-Path $Root 'fixtures\stage-1\scenarios.v2.json'
$Search     = Join-Path $Root 'fixtures\stage-3\scenarios.json'
$Metrics    = Join-Path $Root 'results\metrics-stage3.csv'
$Java       = 'W:\tools\java\jdk21\bin\java.exe'
$Mvn        = 'W:\tools\apache-maven\bin\mvn.cmd'
$env:JAVA_HOME = 'W:\tools\java\jdk21'
$Model = $env:BENCH_MODEL
if (-not $Model) { $Model = 'unknown' }

if (-not (Test-Path $Oracle)) { Write-Host "Oracle jar missing at $Oracle - build it first."; exit 2 }
if (-not (Test-Path $Metrics)) {
    # No BOM, so CSV readers see a clean header.
    [IO.File]::WriteAllText($Metrics, "timestamp,model,cell,stage,trial,regression_passed,regression_total,search_passed,search_total,wall_clock_seconds,output_tokens,tool_calls,cost_usd`n")
}

$Topics = @('product-updates', 'user-updates', 'price-updates', 'purchases', 'notifications')

function Get-Passed([string]$path) {
    if (-not (Test-Path $path)) { return @(0, 0) }
    try { $r = Get-Content $path -Raw | ConvertFrom-Json; return @([int]$r.passed, [int]$r.total) } catch { return @(0, 0) }
}

foreach ($t in $Trials) {
    $parts = $t.Split(':')
    if ($parts.Count -ne 3) { Write-Host "SKIP malformed entry: $t"; continue }
    $cell = $parts[0]; $trialId = $parts[1]; $type = $parts[2]
    $trialDir = Join-Path $Root "runs\stage-3\$cell\trial-$trialId"
    $outReg   = Join-Path $Root "results\stage-3-$cell-$trialId.regression.json"
    $outSrch  = Join-Path $Root "results\stage-3-$cell-$trialId.search.json"
    Write-Host "===== grading $cell/trial-$trialId ($type) ====="
    if (-not (Test-Path $trialDir)) { Write-Host "SKIP: $trialDir missing"; continue }

    # A contestant app left alive would join this broker and corrupt both runs.
    $stray = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
             Where-Object { $_.CommandLine -match 'runs\\stage-3|bench-contestants' }
    if ($stray) { Write-Host "STRAY trial JVM found: $($stray.ProcessId -join ', ') - aborting"; exit 3 }
    $port = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
    if ($port) { Write-Host "Port 8080 already in use (PID $($port.OwningProcess)) - aborting"; exit 3 }

    docker compose -f $Compose down -v | Out-Null
    docker compose -f $Compose up -d | Out-Null
    Start-Sleep -Seconds 10
    foreach ($topic in $Topics) {
        docker exec bench-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic $topic --partitions 1 --replication-factor 1 | Out-Null
    }

    $started = Get-Date
    $appOut = Join-Path $trialDir 'app.log'
    $appErr = Join-Path $trialDir 'app.err.log'
    $app = $null
    if ($type -eq 'jar') {
        $jar = Get-ChildItem (Join-Path $trialDir 'target\*.jar') -ErrorAction SilentlyContinue |
               Where-Object { $_.Name -notlike '*original*' -and $_.Name -notlike '*sources*' } |
               Select-Object -First 1
        if ($null -eq $jar) { Write-Host "SKIP: no jar in $trialDir\target"; docker compose -f $Compose down -v | Out-Null; continue }
        $app = Start-Process -FilePath $Java -ArgumentList '-jar', $jar.FullName `
                 -WorkingDirectory $trialDir -RedirectStandardOutput $appOut -RedirectStandardError $appErr -PassThru
    } else {
        $app = Start-Process -FilePath $Mvn -ArgumentList '-f', (Join-Path $trialDir 'pom.xml'), 'exec:java' `
                 -WorkingDirectory $trialDir -RedirectStandardOutput $appOut -RedirectStandardError $appErr -PassThru
    }
    Start-Sleep -Seconds 40

    & $Java -jar $Oracle 'localhost:9092' $Regression $outReg
    & $Java -jar $Oracle 'localhost:9092' $Search $outSrch
    $elapsed = [int]((Get-Date) - $started).TotalSeconds

    $reg = Get-Passed $outReg
    $srch = Get-Passed $outSrch
    # output_tokens / tool_calls / cost_usd are backfilled from the transcripts afterwards.
    $trialNum = $trialId -replace '^[A-Za-z0-9]+-', ''
    $ts = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    # LF line endings, like the header: CSV readers then see one consistent file.
    [IO.File]::AppendAllText($Metrics, ("{0},{1},{2},stage-3,{3},{4},{5},{6},{7},{8},,,`n" -f $ts, $Model, $cell, $trialNum, $reg[0], $reg[1], $srch[0], $srch[1], $elapsed))

    if ($null -ne $app) {
        try { taskkill /PID $app.Id /T /F | Out-Null } catch {}
    }
    docker compose -f $Compose down -v | Out-Null
    Write-Host ("===== {0}/trial-{1} -> regression {2}/{3}, search {4}/{5} =====" -f $cell, $trialId, $reg[0], $reg[1], $srch[0], $srch[1])
}
Write-Host 'BATCH COMPLETE'

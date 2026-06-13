# Grade a batch of Stage-1 trials with the conformance oracle, sequentially,
# with a fresh broker per trial (down -v). No LLM involvement — fully mechanical,
# safe to re-run; each trial's result lands in results/ as soon as it finishes.
#
# Usage:
#   $env:BENCH_MODEL = 'claude-fable-5'
#   .\grade-batch.ps1 -Trials @('spring:f5-01:jar','tiko:f5-01:mvn',...)
#
# Trial entry format:  <framework>:<trialId>:<runType>
#   framework = subdir under runs/stage-1/   (spring | spring3 | tiko | tiko-mcp)
#   trialId   = trial dir suffix             (trial-<trialId>)
#   runType   = jar (java -jar target/*.jar) | mvn (mvn exec:java)
#
# Prereqs: docker daemon up; oracle jar built (mvn -f conformance/oracle/pom.xml package).
param(
    [Parameter(Mandatory = $true)][string[]]$Trials
)

$ErrorActionPreference = 'Continue'
$Root      = (Resolve-Path "$PSScriptRoot\..\..").Path
$Compose   = Join-Path $Root 'conformance\docker-compose.yml'
$Oracle    = Join-Path $Root 'conformance\oracle\target\conformance-oracle.jar'
$Scenarios = Join-Path $Root 'fixtures\stage-1\scenarios.json'
$Metrics   = Join-Path $Root 'results\metrics.csv'
$Java      = 'W:\tools\java\jdk21\bin\java.exe'
$Mvn       = 'W:\tools\apache-maven\bin\mvn.cmd'
$env:JAVA_HOME = 'W:\tools\java\jdk21'
$Model = $env:BENCH_MODEL
if (-not $Model) { $Model = 'unknown' }

if (-not (Test-Path $Oracle)) {
    Write-Host "Oracle jar missing at $Oracle - build it first."
    exit 2
}

$Topics = @('product-updates', 'user-updates', 'price-updates', 'purchases', 'notifications')

foreach ($t in $Trials) {
    $parts = $t.Split(':')
    if ($parts.Count -ne 3) { Write-Host "SKIP malformed entry: $t"; continue }
    $fw = $parts[0]; $trialId = $parts[1]; $type = $parts[2]
    $trialDir = Join-Path $Root "runs\stage-1\$fw\trial-$trialId"
    $outJson  = Join-Path $Root "results\stage-1-$fw-$trialId.json"
    Write-Host "===== grading $fw/trial-$trialId ($type) ====="

    if (-not (Test-Path $trialDir)) { Write-Host "SKIP: $trialDir missing"; continue }

    docker compose -f $Compose down -v | Out-Null
    docker compose -f $Compose up -d | Out-Null
    Start-Sleep -Seconds 10
    foreach ($topic in $Topics) {
        docker exec bench-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic $topic --partitions 1 --replication-factor 1 | Out-Null
    }

    # Guard against cross-trial contamination: a contestant app left alive by an
    # earlier build smoke-run will reconnect to this broker and pollute the topics
    # (extra consumers on purchases / extra producers on notifications), corrupting
    # the oracle's reads. On a dedicated bench box, kill stray JVMs before starting
    # this trial's app. Opt-in (set BENCH_KILL_STRAY_JAVA=1) since it kills ALL java.
    if ($env:BENCH_KILL_STRAY_JAVA -eq '1') {
        Get-Process java, mvn -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 1
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

    & $Java -jar $Oracle 'localhost:9092' $Scenarios $outJson
    $elapsed = [int]((Get-Date) - $started).TotalSeconds

    $compliance = 0
    if (Test-Path $outJson) {
        try { $compliance = [math]::Round((Get-Content $outJson -Raw | ConvertFrom-Json).compliance * 100) } catch { $compliance = 0 }
    }

    # metrics row: timestamp,model,framework,stage,trial,compliance,wall_clock_seconds,output_tokens,agent_turns,tool_calls
    $trialNum = $trialId -replace '^[A-Za-z0-9]+-', ''
    $ts = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    Add-Content -Path $Metrics -Value ("{0},{1},{2},stage-1,{3},{4},{5},,," -f $ts, $Model, $fw, $trialNum, $compliance, $elapsed)

    if ($null -ne $app) {
        try { taskkill /PID $app.Id /T /F | Out-Null } catch {}
    }
    docker compose -f $Compose down -v | Out-Null
    Write-Host "===== $fw/trial-$trialId -> $compliance% ====="
}
Write-Host 'BATCH COMPLETE'

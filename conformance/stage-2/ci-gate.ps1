# Stage-2 CI gate --- the ONLY feedback a "loop until complies" contestant receives.
#
# Runs the hidden acceptance oracle against the contestant's app and reports, per
# scenario, PASS/FAIL plus a coarse failure CATEGORY --- and NOTHING ELSE. It never
# prints expected or actual values (the oracle's own stdout does, on a value
# mismatch: "expected X but got Y" --- so that stdout is suppressed and only the
# JSON's boolean `passed` field is read; the `detail` field is deliberately ignored
# except to derive the leak-free category "no output produced" / "output incorrect").
#
# This mirrors a provided integration-test suite: the developer learns WHICH cases
# fail, not the answer key. Self-check only --- writes no metrics, grades nothing.
#
# Usage:
#   .\ci-gate.ps1 -TrialDir <abs path> -RunType <jar|mvn> [-SkipBuild]
param(
    [Parameter(Mandatory=$true)][string]$TrialDir,
    [Parameter(Mandatory=$true)][ValidateSet('jar','mvn')][string]$RunType,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Continue'
$Root      = (Resolve-Path "$PSScriptRoot\..\..").Path
$Compose   = Join-Path $Root 'conformance\docker-compose.yml'
$Oracle    = Join-Path $Root 'conformance\oracle\target\conformance-oracle.jar'
$Scenarios = Join-Path $Root 'fixtures\stage-1\scenarios.json'
$Java      = 'W:\tools\java\jdk21\bin\java.exe'
$Mvn       = 'W:\tools\apache-maven\bin\mvn.cmd'
$env:JAVA_HOME = 'W:\tools\java\jdk21'
$Topics = @('product-updates','user-updates','price-updates','purchases','notifications')

if (-not (Test-Path $TrialDir)) { Write-Host "GATE ERROR: trial dir not found"; exit 2 }

# 1. Rebuild (so the gate always tests current source).
if (-not $SkipBuild) {
    Write-Host "GATE: building..."
    & $Mvn -q -f (Join-Path $TrialDir 'pom.xml') -DskipTests package *> (Join-Path $env:TEMP 'ci-gate-build.log')
    if ($LASTEXITCODE -ne 0) {
        Write-Host "GATE: BUILD FAILED --- fix compilation before the acceptance gate can run."
        Get-Content (Join-Path $env:TEMP 'ci-gate-build.log') -Tail 15 | Where-Object { $_ -match 'ERROR|error:' } | Select-Object -First 8
        exit 1
    }
}

# 2. Fresh broker + topics.
Get-Process java,mvn -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
docker compose -f $Compose down -v *> $null
docker compose -f $Compose up -d *> $null
Start-Sleep -Seconds 10
foreach ($t in $Topics) {
    docker exec bench-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic $t --partitions 1 --replication-factor 1 *> $null
}

# 3. Start the contestant app.
$appOut = Join-Path $TrialDir 'app.log'; $appErr = Join-Path $TrialDir 'app.err.log'
if ($RunType -eq 'jar') {
    $jar = Get-ChildItem (Join-Path $TrialDir 'target\*.jar') -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -notlike '*original*' -and $_.Name -notlike '*sources*' } | Select-Object -First 1
    if (-not $jar) { Write-Host "GATE: no runnable jar in target/ --- did the build produce one?"; docker compose -f $Compose down -v *> $null; exit 1 }
    $app = Start-Process -FilePath $Java -ArgumentList '-jar', $jar.FullName -WorkingDirectory $TrialDir -RedirectStandardOutput $appOut -RedirectStandardError $appErr -PassThru
} else {
    $app = Start-Process -FilePath $Mvn -ArgumentList '-f', (Join-Path $TrialDir 'pom.xml'), 'exec:java' -WorkingDirectory $TrialDir -RedirectStandardOutput $appOut -RedirectStandardError $appErr -PassThru
}
Start-Sleep -Seconds 40

# 4. Run the hidden oracle. SUPPRESS its stdout (it prints expected/actual on
#    mismatch). Read ONLY the JSON's boolean pass/fail; ignore its detail values.
$tmp = Join-Path $env:TEMP ("ci-gate-" + [System.IO.Path]::GetRandomFileName() + ".json")
& $Java -jar $Oracle 'localhost:9092' $Scenarios $tmp *> $null

if ($null -ne $app) { try { taskkill /PID $app.Id /T /F *> $null } catch {} }
docker compose -f $Compose down -v *> $null

if (-not (Test-Path $tmp)) { Write-Host "GATE: the app produced no gradable output (it may not have started or consumed). Check app.log."; exit 1 }
$report = Get-Content $tmp -Raw | ConvertFrom-Json
Remove-Item $tmp -ErrorAction SilentlyContinue

# 5. Emit pass/fail + leak-free category ONLY.
$pass = 0; $total = 0
Write-Host "GATE RESULT (acceptance scenarios --- pass/fail only):"
foreach ($r in $report.results) {
    $total++
    if ($r.passed) {
        $pass++
        Write-Host ("  {0,-4} {1,-6} PASS" -f $r.scenario, $r.key)
    } else {
        $cat = if ($r.detail -like 'no notification*') { 'no output produced for this case' } else { 'output produced but incorrect' }
        Write-Host ("  {0,-4} {1,-6} FAIL  ({2})" -f $r.scenario, $r.key, $cat)
    }
}
Write-Host ("GATE: {0}/{1} scenarios passing." -f $pass, $total)
if ($pass -eq $total) { Write-Host "GATE: ALL PASS --- complies." } else { Write-Host "GATE: not yet complying." }

param(
    [string]$JavaHome = 'I:\DSH\work\tools\jdk21\jdk-21.0.2',
    [string]$JarPath,
    [ValidatePattern('^[a-zA-Z0-9-]+$')]
    [string]$RunName = ('companion-' + (Get-Date -Format 'yyyyMMdd-HHmmss')),
    [int]$Port = 25641
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$seedRoot = Join-Path $projectRoot '.codex-temp\rc32-envA'
if (-not $JarPath) {
    $version = (Get-Content -LiteralPath (Join-Path $projectRoot 'gradle.properties') |
        Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', ''
    $JarPath = Join-Path $projectRoot "build\libs\naturalrelationship-$version+1.21.11.jar"
}
$probeJar = Join-Path $projectRoot 'build\companionTest\companion-probe.jar'
foreach ($required in @($JarPath, $probeJar, (Join-Path $seedRoot 'fabric-server-launch.jar'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing prerequisite: $required" }
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $Port is already in use"
}
$probeRoot = Join-Path $projectRoot ".codex-temp\$RunName"
if (Test-Path -LiteralPath $probeRoot) { throw "Refusing to reuse test world: $probeRoot" }
New-Item -ItemType Directory -Path $probeRoot,(Join-Path $probeRoot 'mods'),(Join-Path $probeRoot '.fabric') -Force | Out-Null
foreach ($folder in @('libraries','versions')) {
    Copy-Item -LiteralPath (Join-Path $seedRoot $folder) -Destination $probeRoot -Recurse
}
Copy-Item -LiteralPath (Join-Path $seedRoot '.fabric\server') -Destination (Join-Path $probeRoot '.fabric') -Recurse
Copy-Item -LiteralPath (Join-Path $seedRoot 'fabric-server-launch.jar') -Destination $probeRoot
Copy-Item -LiteralPath (Join-Path $seedRoot 'mods\fabric-api-0.141.6+1.21.11.jar'),$JarPath,$probeJar -Destination (Join-Path $probeRoot 'mods')
$testedJar = Join-Path (Join-Path $probeRoot 'mods') (Split-Path -Leaf $JarPath)
$testedHash = (Get-FileHash -LiteralPath $testedJar -Algorithm SHA256).Hash
[System.IO.File]::WriteAllText((Join-Path $probeRoot 'artifact.sha256'), "$testedHash  $(Split-Path -Leaf $JarPath)")
$settings = @"
server-ip=127.0.0.1
server-port=$Port
online-mode=false
difficulty=hard
enable-rcon=false
level-name=companion-test
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","features":false,"lakes":false}
view-distance=3
simulation-distance=3
max-players=2
spawn-protection=0
sync-chunk-writes=false
max-tick-time=120000
"@
[System.IO.File]::WriteAllText((Join-Path $probeRoot 'server.properties'), $settings)
# This reuses the project's existing, already-accepted test-server EULA.
Copy-Item -LiteralPath (Join-Path $seedRoot 'eula.txt') -Destination $probeRoot
$stdout = Join-Path $probeRoot 'console.log'
$stderr = Join-Path $probeRoot 'console.err'
$serverProcess = Start-Process -FilePath (Join-Path $JavaHome 'bin\java.exe') -ArgumentList @(
    '-Xms512M','-Xmx2G','-Dnonaffinity.companionProbe=true','-jar','fabric-server-launch.jar','nogui'
) -WorkingDirectory $probeRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
[System.IO.File]::WriteAllText((Join-Path $probeRoot 'process.id'), $serverProcess.Id.ToString())
Write-Output "Test server PID $($serverProcess.Id); output: $probeRoot"
$timer = [System.Diagnostics.Stopwatch]::StartNew()
while (-not $serverProcess.WaitForExit(1000)) {
    if ($timer.Elapsed.TotalSeconds -gt 180) {
        Stop-Process -Id $serverProcess.Id
        throw "Test timed out; inspect $stdout"
    }
}
$result = Select-String -LiteralPath $stdout -Pattern 'COMPANION_PROBE_RESULT passed=\d+ failed=0$'
if ($serverProcess.ExitCode -ne 0 -or -not $result -or (Select-String -LiteralPath $stdout -Pattern 'COMPANION_PROBE_FATAL')) {
    Get-Content -LiteralPath $stdout -Tail 45
    throw "Companion tests failed; inspect $stdout and $stderr"
}
$result.Line
Write-Output "Verified artifact SHA256: $testedHash"

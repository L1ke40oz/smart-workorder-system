param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $projectDirectory 'backend'
$pomPath = Join-Path $backendDirectory 'pom.xml'

if (-not (Test-Path -LiteralPath $pomPath -PathType Leaf)) {
    throw "Backend POM not found: $pomPath"
}

$mavenCommand = Get-Command mvn.cmd -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $mavenCommand) {
    throw 'Maven was not found in PATH. Install Maven and reopen this terminal.'
}

[xml]$pom = Get-Content -LiteralPath $pomPath -Raw -Encoding UTF8
if ($pom.project.parent.groupId -ne 'org.springframework.boot' -or
    $pom.project.parent.artifactId -ne 'spring-boot-starter-parent') {
    throw 'Expected a Spring Boot parent in backend/pom.xml.'
}
$bootVersion = [string]$pom.project.parent.version
$runGoal = "org.springframework.boot:spring-boot-maven-plugin:${bootVersion}:run"

# Parse literal settings only; never execute a local configuration file as code.
$envPath = Join-Path $backendDirectory '.env'
$localSettings = @{}
if (Test-Path -LiteralPath $envPath -PathType Leaf) {
    $lineNumber = 0
    foreach ($line in (Get-Content -LiteralPath $envPath -Encoding UTF8)) {
        $lineNumber++
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        if ($trimmed -notmatch '^([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            throw "Invalid .env format at line $lineNumber. Use KEY=value."
        }
        $settingName = $Matches[1]
        $settingValue = $Matches[2].Trim()
        if ($settingValue.StartsWith('"') -or $settingValue.StartsWith("'")) {
            if ($settingValue.Length -lt 2 -or $settingValue[-1] -ne $settingValue[0]) {
                throw "Unclosed .env quote at line $lineNumber."
            }
            $settingValue = $settingValue.Substring(1, $settingValue.Length - 2)
        }
        if ($settingValue.Length -gt 0) { $localSettings[$settingName] = $settingValue }
    }
}

Write-Host "Backend POM: $pomPath"
Write-Host "Starting backend on port $Port. Keep this terminal open; press Ctrl+C to stop."
Write-Host 'The default dev profile starts its embedded H2 database automatically.'

# A fixed working directory also keeps the H2 file and vector index in backend/data.
Push-Location -LiteralPath $backendDirectory
$appliedSettings = @()
try {
    foreach ($settingName in $localSettings.Keys) {
        if ($null -eq [Environment]::GetEnvironmentVariable($settingName, 'Process')) {
            [Environment]::SetEnvironmentVariable($settingName, $localSettings[$settingName], 'Process')
            $appliedSettings += $settingName
        }
    }
    if (Test-Path -LiteralPath $envPath -PathType Leaf) {
        Write-Host 'Local backend/.env found. Settings loaded without displaying values.'
    }
    & $mavenCommand.Source --file $pomPath $runGoal "-Dspring-boot.run.arguments=--server.port=$Port"
    $mavenExitCode = $LASTEXITCODE
}
finally {
    foreach ($settingName in $appliedSettings) {
        [Environment]::SetEnvironmentVariable($settingName, [NullString]::Value, 'Process')
    }
    Pop-Location
}

if ($mavenExitCode -ne 0) {
    throw "Backend exited with code $mavenExitCode. See the Maven output above for details."
}

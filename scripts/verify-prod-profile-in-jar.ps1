param(
    [string]$JarPath = "build/libs/app-with-docs.jar"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $JarPath)) {
    Write-Error "JAR not found: $JarPath"
}

$entries = & jar tf $JarPath
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to read jar entries: $JarPath"
}

$required = @(
    "BOOT-INF/classes/application.yml",
    "BOOT-INF/classes/application-local.yml",
    "BOOT-INF/classes/application-prod.yml"
)

$missing = @()
foreach ($item in $required) {
    if (-not ($entries -contains $item)) {
        $missing += $item
    }
}

if ($missing.Count -gt 0) {
    Write-Host "Missing profile resources in JAR:"
    $missing | ForEach-Object { Write-Host " - $_" }
    exit 1
}

Write-Host "OK: profile resources are packaged in $JarPath"

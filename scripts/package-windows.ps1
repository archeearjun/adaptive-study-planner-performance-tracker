param(
    [string]$OutputDir = "dist",
    [string]$PackageName = "AdaptiveStudyPlanner",
    [string]$AppVersion = "1.0.0"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$packageInput = Join-Path $projectRoot "target/package-input"
$outputPath = Join-Path $projectRoot $OutputDir
$appImagePath = Join-Path $outputPath $PackageName
$zipPath = Join-Path $outputPath "$PackageName-windows-x64.zip"

Push-Location $projectRoot

try {
    if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
        throw "jpackage is required and was not found on PATH."
    }

    New-Item -ItemType Directory -Force $packageInput | Out-Null
    Remove-Item -Recurse -Force (Join-Path $packageInput "*") -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $outputPath | Out-Null
    Remove-Item -Recurse -Force $appImagePath -ErrorAction SilentlyContinue
    Remove-Item $zipPath -Force -ErrorAction SilentlyContinue

    & .\mvnw.cmd -q -DskipTests package dependency:copy-dependencies -DincludeScope=runtime "-DoutputDirectory=target/package-input"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to build the application jar and copy runtime dependencies."
    }
    Copy-Item "target\adaptive-study-planner-$AppVersion.jar" $packageInput -Force

    & jpackage `
        --type app-image `
        --dest $outputPath `
        --name $PackageName `
        --input $packageInput `
        --main-jar "adaptive-study-planner-$AppVersion.jar" `
        --main-class com.studyplanner.ui.MainApplication `
        --vendor "Archee Arjun" `
        --app-version $AppVersion `
        --description "Adaptive Study Planner & Performance Tracker"
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed to create the Windows app image."
    }

    Compress-Archive -Path $appImagePath -DestinationPath $zipPath

    Write-Output "App image: $appImagePath"
    Write-Output "Zip archive: $zipPath"
} finally {
    Pop-Location
}

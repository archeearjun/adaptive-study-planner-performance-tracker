param(
    [string]$OutputDir = "docs/screenshots",
    [string]$DatabasePath = "build-check/screenshot-export/studyplanner.db"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot

try {
    New-Item -ItemType Directory -Force (Split-Path -Parent $OutputDir) | Out-Null
    New-Item -ItemType Directory -Force (Split-Path -Parent $DatabasePath) | Out-Null

    & .\mvnw.cmd -q -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target/classpath.txt"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to compile the project and build the runtime classpath."
    }

    $classpathEntries = (Get-Content target\classpath.txt) -split ";"
    $javafxEntries = $classpathEntries | Where-Object { $_ -match "\\org\\openjfx\\" }
    $runtimeEntries = $classpathEntries | Where-Object {
        $_ -notmatch "\\org\\openjfx\\" -and
        $_ -notmatch "junit" -and
        $_ -notmatch "opentest4j" -and
        $_ -notmatch "apiguardian" -and
        $_ -notmatch "platform-engine"
    }

    if (-not $javafxEntries) {
        throw "JavaFX runtime jars were not found on the Maven classpath."
    }

    $modulePath = $javafxEntries -join ";"
    $classPath = (@("target\classes") + $runtimeEntries) -join ";"

    Remove-Item $DatabasePath -Force -ErrorAction SilentlyContinue

    & java `
        "-Dstudyplanner.exportScreenshots=true" `
        "-Dstudyplanner.screenshots.dir=$OutputDir" `
        "-Dstudyplanner.db.path=$DatabasePath" `
        --module-path $modulePath `
        --add-modules javafx.controls,javafx.fxml,javafx.swing `
        -cp $classPath `
        com.studyplanner.ui.MainApplication
    if ($LASTEXITCODE -ne 0) {
        throw "Screenshot export failed."
    }
} finally {
    Pop-Location
}

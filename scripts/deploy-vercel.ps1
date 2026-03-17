param(
    [switch]$Production
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot

try {
    if (-not (Get-Command npx -ErrorAction SilentlyContinue)) {
        throw "npx is required to run the Vercel CLI."
    }

    $arguments = @("vercel@latest", "deploy", "--yes")
    if ($Production) {
        $arguments += "--prod"
    }

    & npx @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Vercel deployment failed. Ensure you are logged in with 'npx vercel@latest login' or provide a valid VERCEL_TOKEN."
    }
} finally {
    Pop-Location
}

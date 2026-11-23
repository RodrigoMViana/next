# Build completo do Next (Windows/PowerShell): front-end (testes + build) e back-end (testes + jars).
# Uso:  powershell -ExecutionPolicy Bypass -File scripts/build.ps1 [-SemTestes]
param([switch]$SemTestes)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot

. "$PSScriptRoot/java25.ps1"

Push-Location "$raiz/frontend/next-app"
try {
    if (-not (Test-Path node_modules)) { npm ci }
    if (-not $SemTestes) { npx vitest run; if ($LASTEXITCODE) { throw 'Testes do front-end falharam' } }
    npx vue-tsc --build; if ($LASTEXITCODE) { throw 'Checagem de tipos falhou' }
    npx vite build; if ($LASTEXITCODE) { throw 'Build do front-end falhou' }
} finally { Pop-Location }

Push-Location "$raiz/backend"
try {
    $alvo = if ($SemTestes) { @('-DskipTests', 'package') } else { @('verify') }
    & ./mvnw.cmd -B @alvo; if ($LASTEXITCODE) { throw 'Build do back-end falhou' }
} finally { Pop-Location }

Write-Host "`nArtefatos:" -ForegroundColor Green
Write-Host "  backend/next-configuracao/target/next-configuracao.jar"
Write-Host "  backend/next-bff/target/next-bff.jar (inclui o next-app em /static)"

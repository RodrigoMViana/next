# Sobe o Next localmente (Windows): módulo Configuração em 8081 (H2 em modo Oracle + dados fictícios)
# e BFF em 8080 servindo o front-end. Abra http://localhost:8080
# Pré-requisito: scripts/build.ps1 executado ao menos uma vez.
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/java25.ps1"
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$modulo = "$raiz/backend/next-configuracao/target/next-configuracao.jar"
$bff = "$raiz/backend/next-bff/target/next-bff.jar"
if (-not (Test-Path $modulo) -or -not (Test-Path $bff)) { throw 'Jars não encontrados. Execute scripts/build.ps1 antes.' }

New-Item -ItemType Directory -Force "$raiz/logs" | Out-Null
Write-Host 'Iniciando next-configuracao na porta 8081...'
$proc = Start-Process -FilePath $java -ArgumentList @('-jar', $modulo) -RedirectStandardOutput "$raiz/logs/next-configuracao.log" `
    -RedirectStandardError "$raiz/logs/next-configuracao.err.log" -PassThru -WindowStyle Hidden
try {
    for ($i = 0; $i -lt 60; $i++) {
        try { Invoke-WebRequest -UseBasicParsing http://localhost:8081/actuator/health -TimeoutSec 2 | Out-Null; break } catch { Start-Sleep -Seconds 2 }
    }
    Write-Host 'Iniciando next-bff na porta 8080 (Ctrl+C para encerrar)...'
    & $java -jar $bff
} finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
}

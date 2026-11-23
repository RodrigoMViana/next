# Localiza um JDK 25+ (LTS) e define JAVA_HOME para a sessão atual. Usado pelos demais scripts.
function Test-Jdk25([string]$dir) {
    if (-not $dir -or -not (Test-Path "$dir/release")) { return $false }
    $linha = Select-String -Path "$dir/release" -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
    return $linha -and [int]$linha.Matches[0].Groups[1].Value -ge 25
}
if (-not (Test-Jdk25 $env:JAVA_HOME)) {
    $candidatos = @('C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft') |
        Where-Object { Test-Path $_ } | ForEach-Object { Get-ChildItem $_ -Directory } |
        Where-Object { Test-Jdk25 $_.FullName } | Sort-Object Name -Descending
    if ($candidatos) { $env:JAVA_HOME = $candidatos[0].FullName }
    else { throw 'JDK 25 não encontrado. Instale com: winget install EclipseAdoptium.Temurin.25.JDK' }
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Write-Host "JAVA_HOME = $env:JAVA_HOME"

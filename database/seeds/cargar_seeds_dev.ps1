<#
.SYNOPSIS
    Script de utilidad para cargar semillas ficticias de desarrollo en MediTriaje 2.0.

.DESCRIPTION
    Ejecuta el script SQL database/seeds/dev_seeds_m3.sql contra la base de datos de desarrollo.
    Requiere las variables de entorno DB_URL, DB_USER y DB_PASSWORD.
    ADVERTENCIA: EXCLUSIVO PARA DESARROLLO (DEV). NUNCA EJECUTAR EN PRODUCCION.
#>

param (
    [string]$SeedFile = "$PSScriptRoot/dev_seeds_m3.sql"
)

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "MediTriaje 2.0 — Carga de Semillas de Desarrollo (Fase M3)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "ADVERTENCIA: Este script solo debe ejecutarse en entornos DEV." -ForegroundColor Yellow

if (-not (Test-Path $SeedFile)) {
    Write-Error "El archivo de semillas no existe: $SeedFile"
    exit 1
}

$dbUser = $env:DB_USER
$dbPass = $env:DB_PASSWORD
$dbUrl  = $env:DB_URL

if (-not $dbUser -or -not $dbPass) {
    Write-Host "AVISO: Variables DB_USER y DB_PASSWORD no configuradas en el entorno." -ForegroundColor DarkYellow
    Write-Host "Configura tu entorno o ejecuta el script SQL directamente en SQLcl / DBeaver:" -ForegroundColor DarkYellow
    Write-Host "  sql `$DB_USER/`$DB_PASSWORD@`$DB_ALIAS @$SeedFile" -ForegroundColor Gray
    exit 0
}

Write-Host "Cargando archivo de semillas: $SeedFile ..." -ForegroundColor Green
# Si SQLcl o sqlplus esta disponible, ejecutar
if (Get-Command sql -ErrorAction SilentlyContinue) {
    & sql "$dbUser/$dbPass@$dbUrl" "@$SeedFile"
} elseif (Get-Command sqlplus -ErrorAction SilentlyContinue) {
    & sqlplus "$dbUser/$dbPass@$dbUrl" "@$SeedFile"
} else {
    Write-Host "Cliente CLI Oracle (sql o sqlplus) no encontrado en PATH." -ForegroundColor Yellow
    Write-Host "Puedes ejecutar el contenido de $SeedFile desde tu herramienta preferida (DBeaver, VS Code Oracle Developer Tools, etc.)." -ForegroundColor White
}

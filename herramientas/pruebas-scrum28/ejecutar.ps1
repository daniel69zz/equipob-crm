param([switch]$SinCompilar, [switch]$ConservarEntorno)
$ErrorActionPreference = 'Stop'
$repoPruebas = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$logsPruebas = Join-Path $repoPruebas 'logs/scrum28'
New-Item -ItemType Directory -Force -Path $logsPruebas | Out-Null
$contenedorPruebas = 'crm-scrum28-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
$procesosPrueba = @()
$variablesPrueba = @('DB_URL','DB_USUARIO','DB_PASSWORD','JWT_SECRETO','ADMIN_USUARIO','ADMIN_PASSWORD','SERVICIO_SEGMENTACION_URL','SERVICIO_COMPORTAMIENTO_URL','SERVICIO_PERFIL_URL','E2E_USUARIO','E2E_PASSWORD','E2E_URL')
$anteriores = @{}
foreach ($nombre in $variablesPrueba) { $anteriores[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process') }
$terminado = $false
function Comprobar([string]$paso) { if ($LASTEXITCODE -ne 0) { throw "Falló: $paso" } }
function EsperarHttp([string]$url) {
    $limite = (Get-Date).AddSeconds(120)
    do {
        try { if ((Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 3).StatusCode -eq 200) { return } } catch {}
        Start-Sleep -Milliseconds 700
    } while ((Get-Date) -lt $limite)
    throw "No respondió $url. Revise $logsPruebas"
}
$javaInfo = & java -XshowSettings:properties -version 2>&1 | Out-String
$runtimeJava = [regex]::Match($javaInfo, '(?m)^\s*java.home\s*=\s*(.+)$').Groups[1].Value.Trim()
$javaEjecutable = Join-Path $runtimeJava 'bin/java.exe'
try {
    foreach ($puerto in @(18080,18081,18082,18083,14200)) {
        if (Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue) { throw "Puerto $puerto ocupado" }
    }
    if (-not $SinCompilar) {
        foreach ($servicio in @('api-gateway','servicio-perfil','servicio-comportamiento','servicio-segmentacion')) {
            & mvn.cmd -B -q -f (Join-Path $repoPruebas "backend/$servicio/pom.xml") -DskipTests package
            Comprobar "compilar $servicio"
        }
        Push-Location (Join-Path $repoPruebas 'frontend/crm-web')
        try {
            & npm.cmd ci --no-audit --no-fund; Comprobar 'dependencias frontend'
            & npm.cmd run build; Comprobar 'compilar frontend'
            & npx.cmd playwright install chromium; Comprobar 'Chromium'
        } finally { Pop-Location }
    }
    $env:DB_USUARIO = 'crm_pruebas'
    $env:DB_PASSWORD = [guid]::NewGuid().ToString('N')
    $env:JWT_SECRETO = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $env:ADMIN_USUARIO = 'admin-scrum28'
    $env:ADMIN_PASSWORD = 'Test-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
    $env:E2E_USUARIO = $env:ADMIN_USUARIO
    $env:E2E_PASSWORD = $env:ADMIN_PASSWORD
    $env:E2E_URL = 'http://127.0.0.1:14200'
    $env:SERVICIO_SEGMENTACION_URL = 'http://127.0.0.1:18083'
    $env:SERVICIO_COMPORTAMIENTO_URL = 'http://127.0.0.1:18082'
    $env:SERVICIO_PERFIL_URL = 'http://127.0.0.1:18081'
    & docker run -d --rm --name $contenedorPruebas -p '127.0.0.1::5432' -e POSTGRES_DB=crm_scrum28 -e "POSTGRES_USER=$env:DB_USUARIO" -e "POSTGRES_PASSWORD=$env:DB_PASSWORD" postgres:16-alpine | Out-Null
    Comprobar 'PostgreSQL aislado'
    $puertoBD = ((& docker port $contenedorPruebas 5432) -split ':')[-1].Trim()
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$puertoBD/crm_scrum28"
    foreach ($servicio in @(@{Nombre='api-gateway';Puerto=18080},@{Nombre='servicio-perfil';Puerto=18081},
            @{Nombre='servicio-comportamiento';Puerto=18082},@{Nombre='servicio-segmentacion';Puerto=18083})) {
        $jar = Join-Path $repoPruebas "backend/$($servicio.Nombre)/target/$($servicio.Nombre)-0.1.0-SNAPSHOT.jar"
        $opciones = @{
            FilePath=$javaEjecutable
            ArgumentList=@('-jar', ('"' + $jar + '"'), "--server.port=$($servicio.Puerto)", '--spring.rabbitmq.listener.simple.auto-startup=false','--management.health.rabbit.enabled=false')
            WindowStyle='Hidden';PassThru=$true
            RedirectStandardOutput=(Join-Path $logsPruebas "$($servicio.Nombre).log")
            RedirectStandardError=(Join-Path $logsPruebas "$($servicio.Nombre)-error.log")
        }
        $procesosPrueba += Start-Process @opciones
        EsperarHttp "http://127.0.0.1:$($servicio.Puerto)/actuator/health"
    }
    Get-Content -Raw (Join-Path $repoPruebas 'backend/servicio-segmentacion/src/test/resources/scrum28-datos.sql') |
        & docker exec -i $contenedorPruebas psql -v ON_ERROR_STOP=1 -U crm_pruebas -d crm_scrum28 | Out-Null
    Comprobar 'datos de prueba'
    $frontend = Join-Path $repoPruebas 'frontend/crm-web'
    $ng = Join-Path $frontend 'node_modules/@angular/cli/bin/ng.js'
    $opciones = @{
        FilePath='node';WorkingDirectory=$frontend
        ArgumentList=@(('"' + $ng + '"'),'serve','--host=127.0.0.1','--port=14200','--proxy-config=proxy.e2e.json')
        WindowStyle='Hidden';PassThru=$true
        RedirectStandardOutput=(Join-Path $logsPruebas 'frontend.log')
        RedirectStandardError=(Join-Path $logsPruebas 'frontend-error.log')
    }
    $procesosPrueba += Start-Process @opciones
    EsperarHttp $env:E2E_URL
    Push-Location $frontend
    try { & npx.cmd playwright test scrum28.spec.ts; Comprobar 'pruebas reales de navegador' } finally { Pop-Location }
    $terminado = $true
} finally {
    if ($ConservarEntorno -and $terminado) {
        @{url=$env:E2E_URL;usuario=$env:E2E_USUARIO;password=$env:E2E_PASSWORD;procesos=@($procesosPrueba | ForEach-Object {$_.Id});contenedor=$contenedorPruebas} |
            ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $logsPruebas 'entorno-local.json')
        Write-Output 'Entorno de pruebas conservado. Acceso y procesos en logs/scrum28/entorno-local.json (ignorado por Git).'
    } else {
        foreach ($p in $procesosPrueba) { if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force } }
        & docker rm -f -v $contenedorPruebas 2>$null | Out-Null
    }
    foreach ($nombre in $variablesPrueba) { [Environment]::SetEnvironmentVariable($nombre, $anteriores[$nombre], 'Process') }
}

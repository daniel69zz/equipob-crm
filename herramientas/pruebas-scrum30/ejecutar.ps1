param([switch]$ReutilizarCompilacion)
$ErrorActionPreference = 'Stop'
$repoPruebas = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$directorioLogs = Join-Path $repoPruebas 'logs/scrum30'
New-Item -ItemType Directory -Force -Path $directorioLogs | Out-Null
$sufijoPruebas = [guid]::NewGuid().ToString('N').Substring(0, 10)
$postgresPruebas = 'crm-scrum30-pg-' + $sufijoPruebas
$rabbitPruebas = 'crm-scrum30-rabbit-' + $sufijoPruebas
$contenedoresCreados = @()
$procesosPrueba = @()
$variablesPrueba = @('DB_URL', 'DB_USUARIO', 'DB_PASSWORD', 'JWT_SECRETO', 'ADMIN_USUARIO', 'ADMIN_PASSWORD', 'SERVICIO_SEGMENTACION_URL', 'E2E_USUARIO', 'E2E_PASSWORD', 'E2E_RABBIT_URL', 'RABBIT_HOST', 'RABBIT_PUERTO', 'RABBIT_USUARIO', 'RABBIT_PASSWORD', 'SPRING_PROFILES_ACTIVE', 'E2E_URL', 'NG_CLI_ANALYTICS')
$valoresAnteriores = @{}
foreach ($nombre in $variablesPrueba) { $valoresAnteriores[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process') }

function ComprobarSalida([string]$paso) {
    if ($LASTEXITCODE -ne 0) { throw "Falló: $paso" }
}
function EsperarHttp([string]$url, [hashtable]$cabeceras = @{}) {
    $limite = (Get-Date).AddSeconds(90)
    do {
        try { if ((Invoke-WebRequest -UseBasicParsing -Uri $url -Headers $cabeceras -TimeoutSec 3).StatusCode -eq 200) { return } } catch { }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $limite)
    throw "No respondió $url. Revise $directorioLogs"
}
function IniciarServicio([string]$nombre, [int]$puerto, [string]$javaEjecutable) {
    $jar = Join-Path $repoPruebas "backend/$nombre/target/$nombre-0.1.0-SNAPSHOT.jar"
    if (-not (Test-Path -LiteralPath $jar)) { throw "Falta compilar $nombre" }
    $opcionesProceso = @{
        FilePath = $javaEjecutable
        ArgumentList = @('-jar', ('"' + $jar + '"'), "--server.port=$puerto")
        WindowStyle = 'Hidden'
        PassThru = $true
        RedirectStandardOutput = (Join-Path $directorioLogs "$nombre.log")
        RedirectStandardError = (Join-Path $directorioLogs "$nombre-error.log")
    }
    $proceso = Start-Process @opcionesProceso
    $script:procesosPrueba += $proceso
    EsperarHttp "http://127.0.0.1:$puerto/actuator/health"
}

try {
    $env:NG_CLI_ANALYTICS = 'false'
    foreach ($puerto in @(18080, 18083, 14200)) {
        if (Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue) {
            throw "El puerto $puerto está ocupado; no se interrumpen servicios existentes."
        }
    }
    $consultaJava = New-Object System.Diagnostics.Process
    $consultaJava.StartInfo.FileName = 'java'
    $consultaJava.StartInfo.Arguments = '-XshowSettings:properties -version'
    $consultaJava.StartInfo.UseShellExecute = $false
    $consultaJava.StartInfo.CreateNoWindow = $true
    $consultaJava.StartInfo.RedirectStandardError = $true
    $consultaJava.Start() | Out-Null
    $propiedadesJava = $consultaJava.StandardError.ReadToEnd()
    $consultaJava.WaitForExit()
    $javaRuntime = [regex]::Match($propiedadesJava, '(?m)^\s*java.home\s*=\s*(.+)$').Groups[1].Value.Trim()
    if (-not $javaRuntime) { throw 'No se pudo localizar java.home' }
    $javaEjecutable = Join-Path $javaRuntime 'bin/java.exe'
    $consultaJava.Dispose()

    if (-not $ReutilizarCompilacion) {
        foreach ($servicio in @('api-gateway', 'servicio-segmentacion')) {
            & mvn.cmd -B -q -f (Join-Path $repoPruebas "backend/$servicio/pom.xml") package
            ComprobarSalida "pruebas y compilación de $servicio"
        }
    }
    $frontendPruebas = Join-Path $repoPruebas 'frontend/crm-web'
    Push-Location $frontendPruebas
    try {
        if (-not (Test-Path node_modules)) { & npm.cmd ci --no-audit --no-fund; ComprobarSalida 'dependencias frontend' }
        & npm.cmd run build; ComprobarSalida 'compilación frontend'
        & npx.cmd playwright install chromium; ComprobarSalida 'navegador Chromium'
    } finally { Pop-Location }

    $env:SPRING_PROFILES_ACTIVE = 'default'
    $env:DB_USUARIO = 'crm_pruebas'
    $env:DB_PASSWORD = [guid]::NewGuid().ToString('N')
    $env:JWT_SECRETO = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $env:ADMIN_USUARIO = 'admin-scrum30'
    $env:ADMIN_PASSWORD = 'Test-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
    $env:E2E_USUARIO = $env:ADMIN_USUARIO
    $env:E2E_PASSWORD = $env:ADMIN_PASSWORD
    $env:E2E_URL = 'http://127.0.0.1:14200'
    $env:SERVICIO_SEGMENTACION_URL = 'http://127.0.0.1:18083'
    $env:RABBIT_HOST = '127.0.0.1'
    $env:RABBIT_USUARIO = 'crm_pruebas'
    $env:RABBIT_PASSWORD = [guid]::NewGuid().ToString('N')

    & docker run -d --rm --name $postgresPruebas -p '127.0.0.1::5432' -e POSTGRES_DB=crm_scrum30 -e "POSTGRES_USER=$env:DB_USUARIO" -e "POSTGRES_PASSWORD=$env:DB_PASSWORD" postgres:16-alpine | Out-Null
    ComprobarSalida 'PostgreSQL de pruebas'
    $contenedoresCreados += $postgresPruebas
    $puertoBD = ((& docker port $postgresPruebas 5432) -split ':')[-1].Trim()
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$puertoBD/crm_scrum30"
    & docker run -d --rm --name $rabbitPruebas -p '127.0.0.1::5672' -p '127.0.0.1::15672' -e "RABBITMQ_DEFAULT_USER=$env:RABBIT_USUARIO" -e "RABBITMQ_DEFAULT_PASS=$env:RABBIT_PASSWORD" rabbitmq:3.13-management | Out-Null
    ComprobarSalida 'RabbitMQ de pruebas'
    $contenedoresCreados += $rabbitPruebas
    $env:RABBIT_PUERTO = ((& docker port $rabbitPruebas 5672) -split ':')[-1].Trim()
    $puertoRabbitHttp = ((& docker port $rabbitPruebas 15672) -split ':')[-1].Trim()
    $env:E2E_RABBIT_URL = "http://127.0.0.1:$puertoRabbitHttp"
    $credencialRabbit = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("$($env:RABBIT_USUARIO):$($env:RABBIT_PASSWORD)"))
    EsperarHttp "$env:E2E_RABBIT_URL/api/overview" @{ Authorization = "Basic $credencialRabbit" }

    IniciarServicio 'api-gateway' 18080 $javaEjecutable
    # Registro anterior a la migración: el servicio debe importarlo sin inventar versión.
    $sqlLegado = @'
INSERT INTO auditoria.evento(usuario,operacion,entidad,entidad_id,detalle)
VALUES ('sistema','SEGMENTO_ASIGNADO','SEGMENTO_CLIENTE','30003',
'{"anterior":null,"nuevo":{"segmento":"Legado","criterioId":999,"indicador":"FRECUENCIA"},"metricas":{"frecuencia":2,"valor":100,"categorias":[]},"idEvento":"evento-legado-e2e","tipoEventoOrigen":"COMPRA_CONFIRMADA"}');
'@
    $sqlLegado | & docker exec -i $postgresPruebas psql -v ON_ERROR_STOP=1 -U $env:DB_USUARIO -d crm_scrum30 | Out-Null
    ComprobarSalida 'datos anteriores a SCRUM-30'
    IniciarServicio 'servicio-segmentacion' 18083 $javaEjecutable

    $ng = Join-Path $frontendPruebas 'node_modules/@angular/cli/bin/ng.js'
    $opcionesFrontend = @{
        FilePath = 'node'
        WorkingDirectory = $frontendPruebas
        ArgumentList = @(('"' + $ng + '"'), 'serve', '--host=127.0.0.1', '--port=14200', '--proxy-config=proxy.e2e.json')
        WindowStyle = 'Hidden'
        PassThru = $true
        RedirectStandardOutput = (Join-Path $directorioLogs 'frontend.log')
        RedirectStandardError = (Join-Path $directorioLogs 'frontend-error.log')
    }
    $procesosPrueba += Start-Process @opcionesFrontend
    EsperarHttp 'http://127.0.0.1:14200/login'
    Push-Location $frontendPruebas
    try {
        & npx.cmd playwright test e2e/scrum30.spec.ts
        ComprobarSalida 'pruebas de navegador SCRUM-30'
    } finally { Pop-Location }
} finally {
    foreach ($proceso in $procesosPrueba) {
        if (-not $proceso.HasExited) { Stop-Process -Id $proceso.Id -Force }
    }
    foreach ($contenedor in $contenedoresCreados) { & docker rm -f -v $contenedor 2>$null | Out-Null }
    foreach ($nombre in $variablesPrueba) {
        [Environment]::SetEnvironmentVariable($nombre, $valoresAnteriores[$nombre], 'Process')
    }
}

param(
    [switch]$ReutilizarDependencias,
    [ValidateSet('scrum29', 'scrum25')][string]$Escenario = 'scrum29'
)
$ErrorActionPreference = 'Stop'
$repoPruebas = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$directorioLogs = Join-Path $repoPruebas 'logs/scrum29'
New-Item -ItemType Directory -Force -Path $directorioLogs | Out-Null
$contenedorPruebas = 'crm-scrum29-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
$procesosPrueba = @()
# Oracle javapath puede iniciar otro proceso y terminar: usar el binario real para poder detenerlo.
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

$variablesPrueba = @('DB_URL', 'DB_USUARIO', 'DB_PASSWORD', 'JWT_SECRETO', 'ADMIN_USUARIO', 'ADMIN_PASSWORD', 'SERVICIO_SEGMENTACION_URL', 'E2E_USUARIO', 'E2E_PASSWORD', 'E2E_URL')
$valoresAnteriores = @{}
foreach ($nombre in $variablesPrueba) { $valoresAnteriores[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process') }

function ComprobarSalida([string]$paso) { if ($LASTEXITCODE -ne 0) { throw "Falló: $paso" } }
function EsperarHttp([string]$url) {
    $limite = (Get-Date).AddSeconds(90)
    do {
        try { if ((Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 3).StatusCode -eq 200) { return } } catch { }
        Start-Sleep -Milliseconds 700
    } while ((Get-Date) -lt $limite)
    throw "No respondió $url. Revise $directorioLogs"
}

try {
    foreach ($puerto in @(18080, 18083, 14200)) {
        if (Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue) {
            throw "El puerto $puerto está ocupado. Detenga la ejecución de pruebas anterior antes de iniciar otra."
        }
    }
    foreach ($servicio in @('api-gateway', 'servicio-segmentacion')) {
        & mvn.cmd -B -q -f (Join-Path $repoPruebas "backend/$servicio/pom.xml") -DskipTests package
        ComprobarSalida "compilación de $servicio"
    }
    Push-Location (Join-Path $repoPruebas 'frontend/crm-web')
    try {
        if (-not $ReutilizarDependencias) { & npm.cmd ci --no-audit --no-fund; ComprobarSalida 'dependencias frontend' }
        & npm.cmd run build; ComprobarSalida 'compilación frontend'
        & npx.cmd playwright install chromium; ComprobarSalida 'navegador Chromium'
    } finally { Pop-Location }

    $env:DB_USUARIO = 'crm_pruebas'
    $env:DB_PASSWORD = [guid]::NewGuid().ToString('N')
    $env:JWT_SECRETO = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $env:ADMIN_USUARIO = 'admin-scrum29'
    $env:ADMIN_PASSWORD = 'Test-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
    $env:E2E_USUARIO = $env:ADMIN_USUARIO; $env:E2E_PASSWORD = $env:ADMIN_PASSWORD
    $env:E2E_URL = 'http://127.0.0.1:14200'
    $env:SERVICIO_SEGMENTACION_URL = 'http://127.0.0.1:18083'
    & docker run -d --rm --name $contenedorPruebas -p '127.0.0.1::5432' -e POSTGRES_DB=crm_scrum29 -e "POSTGRES_USER=$env:DB_USUARIO" -e "POSTGRES_PASSWORD=$env:DB_PASSWORD" postgres:16-alpine | Out-Null
    ComprobarSalida 'PostgreSQL de pruebas'
    $puertoBD = ((& docker port $contenedorPruebas 5432) -split ':')[-1].Trim()
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$puertoBD/crm_scrum29"

    foreach ($servicio in @(@{Nombre='api-gateway'; Puerto=18080}, @{Nombre='servicio-segmentacion'; Puerto=18083})) {
        $jar = Join-Path $repoPruebas "backend/$($servicio.Nombre)/target/$($servicio.Nombre)-0.1.0-SNAPSHOT.jar"
        $proceso = Start-Process -FilePath $javaEjecutable -ArgumentList @('-jar', "`"$jar`"", "--server.port=$($servicio.Puerto)", '--spring.rabbitmq.listener.simple.auto-startup=false', '--management.health.rabbit.enabled=false') -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $directorioLogs "$($servicio.Nombre).log") -RedirectStandardError (Join-Path $directorioLogs "$($servicio.Nombre)-error.log")
        $procesosPrueba += $proceso
        EsperarHttp "http://127.0.0.1:$($servicio.Puerto)/actuator/health"
    }
    if ($Escenario -eq 'scrum29') {
        Get-Content -Raw -Encoding utf8 (Join-Path $PSScriptRoot 'datos.sql') | & docker exec -i $contenedorPruebas psql -U $env:DB_USUARIO -d crm_scrum29 -v ON_ERROR_STOP=1
        ComprobarSalida 'datos de prueba'
    }
    $frontendPruebas = Join-Path $repoPruebas 'frontend/crm-web'
    $ng = Join-Path $frontendPruebas 'node_modules/@angular/cli/bin/ng.js'
    $proceso = Start-Process -FilePath 'node' -WorkingDirectory $frontendPruebas -ArgumentList @("`"$ng`"", 'serve', '--host=127.0.0.1', '--port=14200', '--proxy-config=proxy.e2e.json') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $directorioLogs 'frontend.log') -RedirectStandardError (Join-Path $directorioLogs 'frontend-error.log')
    $procesosPrueba += $proceso
    EsperarHttp 'http://127.0.0.1:14200/login'
    Push-Location $frontendPruebas
    try { & npx.cmd playwright test "e2e/$Escenario.spec.ts"; ComprobarSalida 'pruebas de navegador' } finally { Pop-Location }

} finally {
    foreach ($proceso in $procesosPrueba) {
        if (-not $proceso.HasExited) { Stop-Process -Id $proceso.Id -Force }
    }
    & docker rm -f -v $contenedorPruebas 2>$null | Out-Null
    foreach ($nombre in $variablesPrueba) {
        [Environment]::SetEnvironmentVariable($nombre, $valoresAnteriores[$nombre], 'Process')
    }
}


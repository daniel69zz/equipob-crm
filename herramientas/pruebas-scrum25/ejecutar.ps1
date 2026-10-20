param([switch]$ReutilizarDependencias)
$ErrorActionPreference = 'Stop'
$repoPruebas = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$directorioLogs = Join-Path $repoPruebas 'logs/scrum25'
New-Item -ItemType Directory -Force -Path $directorioLogs | Out-Null
$contenedorPruebas = 'crm-scrum25-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
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

$variablesPrueba = @('DB_URL', 'DB_USUARIO', 'DB_PASSWORD', 'JWT_SECRETO', 'ADMIN_USUARIO', 'ADMIN_PASSWORD', 'SERVICIO_SEGMENTACION_URL', 'SERVICIO_COMPORTAMIENTO_URL', 'E2E_USUARIO', 'E2E_PASSWORD')
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
    foreach ($puerto in @(18080, 18082, 18083, 14200)) {
        if (Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue) {
            throw "El puerto $puerto está ocupado. Detenga la ejecución de pruebas anterior antes de iniciar otra."
        }
    }
    foreach ($servicio in @('api-gateway', 'servicio-segmentacion', 'servicio-comportamiento')) {
        & mvn.cmd -B -q -f (Join-Path $repoPruebas "backend/$servicio/pom.xml") package
        ComprobarSalida "pruebas y compilación de $servicio"
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
    $env:ADMIN_USUARIO = 'admin-scrum25'
    $env:ADMIN_PASSWORD = 'Test-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
    $env:E2E_USUARIO = $env:ADMIN_USUARIO; $env:E2E_PASSWORD = $env:ADMIN_PASSWORD
    $env:SERVICIO_SEGMENTACION_URL = 'http://127.0.0.1:18083'
    $env:SERVICIO_COMPORTAMIENTO_URL = 'http://127.0.0.1:18082'
    & docker run -d --rm --name $contenedorPruebas -p '127.0.0.1::5432' -e POSTGRES_DB=crm_scrum25 -e "POSTGRES_USER=$env:DB_USUARIO" -e "POSTGRES_PASSWORD=$env:DB_PASSWORD" postgres:16-alpine | Out-Null
    ComprobarSalida 'PostgreSQL de pruebas'
    $puertoBD = ((& docker port $contenedorPruebas 5432) -split ':')[-1].Trim()
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$puertoBD/crm_scrum25"

    foreach ($servicio in @(@{Nombre='api-gateway'; Puerto=18080}, @{Nombre='servicio-segmentacion'; Puerto=18083}, @{Nombre='servicio-comportamiento'; Puerto=18082})) {
        $jar = Join-Path $repoPruebas "backend/$($servicio.Nombre)/target/$($servicio.Nombre)-0.1.0-SNAPSHOT.jar"
        $proceso = Start-Process -FilePath $javaEjecutable -ArgumentList @('-jar', "`"$jar`"", "--server.port=$($servicio.Puerto)", '--spring.rabbitmq.listener.simple.auto-startup=false', '--spring.rabbitmq.listener.direct.auto-startup=false', '--management.health.rabbit.enabled=false') -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $directorioLogs "$($servicio.Nombre).log") -RedirectStandardError (Join-Path $directorioLogs "$($servicio.Nombre)-error.log")
        $procesosPrueba += $proceso
        EsperarHttp "http://127.0.0.1:$($servicio.Puerto)/actuator/health"
    }
    # Categorías reales en la base temporal, para seleccionar y comprobar desde el combobox.
    @'
WITH evento AS (
    INSERT INTO comportamiento.evento_recibido (estado, contenido, tipo_evento)
    VALUES ('PROCESADO', '{"tipoEvento":"COMPRA_CONFIRMADA","compra":{"idCompra":"CATALOGO-SCRUM25","idCliente":"CLI-CATALOGO","montoTotal":2,"items":[{"categoria":"Electrónica","cantidad":1,"monto":1},{"categoria":"Libros","cantidad":1,"monto":1}]}}', 'COMPRA_CONFIRMADA') RETURNING id
), compra AS (
    INSERT INTO comportamiento.compra (id_compra_origen, id_cliente_origen, fecha, monto_total, id_evento)
    SELECT 'CATALOGO-SCRUM25', 'CLI-CATALOGO', now(), 2, id FROM evento RETURNING id
)
INSERT INTO comportamiento.compra_item (id_compra, categoria, cantidad, monto)
SELECT compra.id, categoria, 1, 1 FROM compra CROSS JOIN (VALUES ('Electrónica'), ('Libros')) AS catalogo(categoria);
'@ | & docker exec -i $contenedorPruebas psql -U $env:DB_USUARIO -d crm_scrum25 -v ON_ERROR_STOP=1
    ComprobarSalida 'categorías del catálogo de pruebas'
    $frontendPruebas = Join-Path $repoPruebas 'frontend/crm-web'
    $ng = Join-Path $frontendPruebas 'node_modules/@angular/cli/bin/ng.js'
    $proceso = Start-Process -FilePath 'node' -WorkingDirectory $frontendPruebas -ArgumentList @("`"$ng`"", 'serve', '--host=127.0.0.1', '--port=14200', '--proxy-config=proxy.e2e.json') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $directorioLogs 'frontend.log') -RedirectStandardError (Join-Path $directorioLogs 'frontend-error.log')
    $procesosPrueba += $proceso
    EsperarHttp 'http://127.0.0.1:14200/login'
    Push-Location $frontendPruebas
    try { & npx.cmd playwright test e2e/scrum25.spec.ts e2e/criterios-ayuda.spec.ts; ComprobarSalida 'pruebas de navegador' } finally { Pop-Location }

} finally {
    foreach ($proceso in $procesosPrueba) {
        if (-not $proceso.HasExited) { Stop-Process -Id $proceso.Id -Force }
    }
    & docker rm -f -v $contenedorPruebas 2>$null | Out-Null
    foreach ($nombre in $variablesPrueba) {
        [Environment]::SetEnvironmentVariable($nombre, $valoresAnteriores[$nombre], 'Process')
    }
}

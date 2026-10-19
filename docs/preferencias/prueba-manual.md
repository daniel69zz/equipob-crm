# Comprobar SCRUM-41

## Pruebas automatizadas

Requiere Java 17+, Maven y Docker Desktop en ejecución.

```powershell
cd C:\Users\frede\Desktop\Repositorios\equipob-crm\backend\servicio-comportamiento
mvn test
cd ..\api-gateway
mvn test
```

Testcontainers crea PostgreSQL/RabbitMQ temporales para las pruebas de integración y los elimina
al terminar. No utiliza la base de desarrollo `crm_maxiconecta`. Una ejecución con pruebas omitidas
por ausencia de Docker no acredita la persistencia.

Para ejecutar solo los casos nuevos de comportamiento:

```powershell
mvn "-Dtest=ConstructorPreferenciasTest,IdentificadoresPreferenciasTest,PreferenciasIntegracionTest" test
```

## Prueba sobre tu aplicación

Arrancar la base y servicios existentes según sus README. Reiniciar Comportamiento y Gateway
con el código actualizado; Comportamiento aplica V11 mediante Flyway. Perfil debe estar disponible
porque se utiliza para verificar la identidad. SCRUM-41 se comprueba por API; todavía no agrega
una pantalla de preferencias (SCRUM-43).

En PowerShell, iniciar sesión con tu cuenta de administrador habitual. No se crea otra cuenta
ni se cambia su contraseña:

```powershell
$credencialCrm = Get-Credential -UserName admin -Message 'Credenciales habituales del CRM'
$cuerpoLogin = @{
    usuario = $credencialCrm.UserName
    password = $credencialCrm.GetNetworkCredential().Password
} | ConvertTo-Json
$sesionCrm = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/login' -ContentType 'application/json' -Body $cuerpoLogin
$cabecerasCrm = @{ Authorization = "Bearer $($sesionCrm.token)" }
$cuerpoLogin = $null

# Cambiar por el ID de un cliente real que aparezca en tu CRM.
$clienteCrm = 100
$rutaPreferencias = "http://localhost:8080/api/comportamiento/clientes/$clienteCrm/preferencias"

# Antes de la primera construcción: construido=false, version=0.
Invoke-RestMethod -Uri $rutaPreferencias -Headers $cabecerasCrm | ConvertTo-Json -Depth 6

# Construir y guardar sus preferencias.
$perfilPreferencias = Invoke-RestMethod -Method Post -Uri "$rutaPreferencias/construir" -Headers $cabecerasCrm
$perfilPreferencias.categorias | Format-Table posicion, categoria, compras, unidades, monto

# Confirmar que la consulta devuelve la misma versión y categorías persistidas.
Invoke-RestMethod -Uri $rutaPreferencias -Headers $cabecerasCrm | ConvertTo-Json -Depth 6
```

El POST escribe solamente las preferencias de ese cliente. No altera sus compras. Al repetirlo
sube `version`; si las compras siguen iguales, las categorías y sus montos siguen iguales.

## Resultados esperados

| Caso | Qué debe pasar |
|---|---|
| Varias categorías | Orden por monto, después compras y nombre; posiciones consecutivas |
| Una categoría | Una única fila, posición 1 |
| Sin compras vigentes | HTTP 200, `construido=true`, `sinPreferencias=true`, categorías vacías |
| Compra anulada | No participa al reconstruir |
| Devolución parcial | Descuenta solo las categorías devueltas; quita una categoría si se devolvió por completo |
| Categoría sin asignar | No aparece como preferencia; las categorías válidas se conservan |
| Nueva compra después de construir | GET conserva la instantánea; POST refleja la compra nueva (automatización en SCRUM-42) |
| Otro cliente | Sus compras no cambian el resultado del cliente consultado |
| Sin JWT / rol incorrecto | 401 / 403 en el Gateway |
| Perfil no disponible | 503, sin borrar preferencias anteriores |

La ingesta y los constraints actuales rechazan categorías nulas o en blanco. Ese caso de datos
legados se cubre con pruebas automatizadas en la base temporal; no hay que deshabilitar controles
en la base de desarrollo para comprobarlo.

# servicio-interacciones — Interacciones con el Cliente

Registra la atención al cliente: consultas, reclamos y notas, con su canal, la compra relacionada y quién las
atendió. Así se arma el historial unificado de la relación del cliente con la empresa (SCRUM-52).

- **No consume eventos:** todo entra desde la Aplicación Web, a través del API Gateway.
- **Consulta a servicio-perfil** (`SERVICIO_PERFIL_URL`, por defecto `http://localhost:8081`) para comprobar que el
  cliente exista antes de guardar una interacción. Si Perfil no responde, devuelve `503` y no guarda nada.
- **Consulta a servicio-comportamiento** (`SERVICIO_COMPORTAMIENTO_URL`, por defecto `http://localhost:8082`) para
  mostrar las compras junto con las interacciones (SCRUM-53). Si no responde, el historial se muestra sin compras y
  con un aviso.
- **Esquema en la base:** `interacciones`
- **Puerto:** `8085` (`PUERTO`)

## Registro de interacciones (SCRUM-52)

El modelo de datos, las reglas y los permisos están en `docs/interacciones/registro-interacciones.md`.

| Método | Ruta | Quién |
|---|---|---|
Todas bajo `/api/interacciones/clientes/{clienteId}/interacciones` (`docs/seguridad/convencion-rutas-clientes.md`):

| Método | Ruta | Quién |
|---|---|---|
| POST | `…/interacciones` | Agente de Atención o Administrador de CRM (`INTERACCIONES_REGISTRAR`) |
| PUT | `…/interacciones/{id}` | El agente que la registró o el Administrador de CRM |
| PATCH | `…/interacciones/{id}` | Estado de seguimiento o tipo: cualquier Agente de Atención o el Administrador (SCRUM-55) |
| GET | `…/interacciones?tipo=&estado=&texto=&desde=&hasta=&pagina=&tamanio=` | `INTERACCIONES_CONSULTAR` |
| GET | `…/interacciones/{id}` | `INTERACCIONES_CONSULTAR` |
| GET | `…/interacciones/{id}/auditoria` | Administrador de CRM con `AUDITORIA_CONSULTAR` |

- Una edición lleva la `version` que el usuario tenía en pantalla. Si otra persona la cambió antes, responde `409`.
- Una interacción no se borra.
- La creación y cada cambio quedan en `interacciones.interaccion_auditoria` (solo inserción) y en
  `auditoria.evento` como `INTERACCION_REGISTRADA` o `INTERACCION_MODIFICADA` (SCRUM-436).
- Los errores de campo vienen en `campos`.

## Historial del cliente (SCRUM-53)

`GET /api/interacciones/clientes/{clienteId}/historial?limite=50` une las interacciones y las compras del cliente
en una sola línea de tiempo, de la más reciente a la más antigua. El diseño está en
`docs/interacciones/historial-cliente.md`.

## Compra vinculada a una interacción (SCRUM-54)

El agente vincula una interacción con una compra del historial del cliente, por ejemplo el reclamo con la compra que
lo originó. Reglas en `docs/interacciones/vinculacion-compras.md`.

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/interacciones/clientes/{clienteId}/compras?texto=&limite=` | Compras del cliente para elegir (mismos permisos que el historial) |
| PUT | `…/interacciones/{id}/compra` | Vincula la compra `{referencia, version}` o reemplaza la vinculada |
| DELETE | `…/interacciones/{id}/compra?version=` | Quita la compra vinculada |

- Vincular exige el rol de Agente o Administrador con `INTERACCIONES_REGISTRAR` e `INDICADORES_CONSULTAR`.
- La compra debe ser del cliente: se comprueba en servicio-comportamiento. Si no responde, `503`.
- Una interacción cerrada no se vincula (`409`).
- Cada cambio queda en la auditoría de la interacción (`COMPRA_VINCULADA` o `COMPRA_DESVINCULADA`) y en
  `auditoria.evento` (`INTERACCION_COMPRA_VINCULADA` o `INTERACCION_COMPRA_DESVINCULADA`).

## Ejecutar

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn spring-boot:run
```

Variables:

- Base de datos: `DB_URL`, `DB_USUARIO` y `DB_PASSWORD`.
- Perfil: `SERVICIO_PERFIL_URL` y `PERFIL_TIEMPO_ESPERA`.
- Comportamiento: `SERVICIO_COMPORTAMIENTO_URL` y `COMPORTAMIENTO_TIEMPO_ESPERA`.

Los dos tiempos de espera son de `2s` por defecto.

## Pruebas

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn -o clean test
```

Las pruebas de integración usan PostgreSQL con Testcontainers (necesitan Docker) y las migraciones del API Gateway
para la auditoría compartida.

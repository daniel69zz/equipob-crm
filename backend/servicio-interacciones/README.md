# servicio-interacciones — Interacciones con el Cliente

Registra la atención al cliente: consultas, reclamos y notas, con su canal, la compra relacionada y quién las
atendió. Así se arma el historial unificado de la relación del cliente con la empresa (SCRUM-52).

- **No consume eventos:** todo entra desde la Aplicación Web, a través del API Gateway.
- **Consulta a servicio-perfil** (`SERVICIO_PERFIL_URL`, por defecto `http://localhost:8081`) para comprobar que el
  cliente exista antes de guardar una interacción. Si Perfil no responde, devuelve `503` y no guarda nada.
- **Esquema en la base:** `interacciones`
- **Puerto:** `8085` (`PUERTO`)

## Registro de interacciones (SCRUM-52)

El modelo de datos, las reglas y los permisos están en `docs/interacciones/registro-interacciones.md`.

| Método | Ruta | Quién |
|---|---|---|
| POST | `/api/interacciones` | Agente de Atención o Administrador de CRM (`INTERACCIONES_REGISTRAR`) |
| PUT | `/api/interacciones/{id}` | El agente que la registró o el Administrador de CRM |
| GET | `/api/interacciones/{id}` | `INTERACCIONES_CONSULTAR` |
| GET | `/api/interacciones/clientes/{clienteId}?tipo=&texto=&desde=&hasta=&pagina=&tamanio=` | `INTERACCIONES_CONSULTAR` |
| GET | `/api/interacciones/{id}/auditoria` | Administrador de CRM con `AUDITORIA_CONSULTAR` |

- Una edición lleva la `version` que el usuario tenía en pantalla. Si otra persona la cambió antes, responde `409`.
- Una interacción no se borra.
- La creación y cada cambio quedan en `interacciones.interaccion_auditoria` (solo inserción) y en
  `auditoria.evento` como `INTERACCION_REGISTRADA` o `INTERACCION_MODIFICADA` (SCRUM-436).
- Los errores de campo vienen en `campos`.

## Ejecutar

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn spring-boot:run
```

Variables: `DB_URL`, `DB_USUARIO`, `DB_PASSWORD`, `SERVICIO_PERFIL_URL`, `PERFIL_TIEMPO_ESPERA` (por defecto `2s`).

## Pruebas

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn -o clean test
```

Las pruebas de integración usan PostgreSQL con Testcontainers (necesitan Docker) y las migraciones del API Gateway
para la auditoría compartida.

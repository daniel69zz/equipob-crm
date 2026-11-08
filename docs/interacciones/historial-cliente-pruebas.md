# Pruebas funcionales del historial del cliente (SCRUM-442)

Casos de prueba de SCRUM-53 (`docs/interacciones/historial-cliente.md`). Cada caso dice qué criterio de aceptación
cubre y qué prueba automatizada lo verifica. Los que se revisan a mano se ejecutan en la Aplicación Web, con el
API Gateway, `servicio-perfil`, `servicio-comportamiento` y `servicio-interacciones` levantados.

## Datos de prueba

| Cliente | Situación |
|---|---|
| 42 (CLI-5521 en Ventas) | 2 compras (V-100234 del 05/11, V-100300 del 07/11) y un reclamo abierto del 06/11 |
| 43 | Una compra y ninguna interacción |
| 44 | Sin identificadores de Ventas ni interacciones |
| 999 | No existe en el CRM |

Usuarios: `agente.ana` (Agente de Atención), `admin` (Administrador de CRM) y `luis` (Gerente Comercial).

## Casos

| N.º | Criterio | Caso | Pasos | Resultado esperado | Prueba automatizada |
|---|---|---|---|---|---|
| P1 | 1 | Interacciones y compras en orden cronológico | `agente.ana` abre **Historial de atención** del cliente 42 | Se ven, en este orden: V-100300 (07/11), el reclamo (06/11) y V-100234 (05/11). Cada interacción muestra su tipo y su estado | `HistorialClienteIntegracionTest.lasComprasYLasInteraccionesVanEnUnaSolaLineaDeTiempo` |
| P2 | 1 | Misma fecha y hora | Una compra y una interacción registradas en el mismo instante | La compra aparece antes que la interacción | `aIgualFechaLaCompraVaAntesQueLaInteraccion` |
| P3 | 1 | Filtrar la vista | En el cliente 42, pulsar **Interacciones** y luego **Compras** | Solo se ven las interacciones, o solo las compras, sin volver a consultar | Revisión manual |
| P4 | 1 | Más eventos que el límite | Cliente con más de 50 eventos | Se muestran los 50 más recientes, con enlaces a Interacciones y a Compras e indicadores | `elLimiteCortaLaLineaDeTiempoUnida` y `soloTraeLasDelClienteYRespetaElLimite` |
| P5 | 2 | Cliente sin interacciones | Abrir el historial del cliente 43 | Sin error. Aviso "El cliente no tiene interacciones registradas" y su compra en la línea de tiempo | `unClienteSinInteraccionesVeSusComprasYElAviso` y `unClienteSinInteraccionesSeIndicaSinError` |
| P6 | 2 | Cliente sin interacciones ni compras | Abrir el historial del cliente 44 | Sin error. El aviso y "El cliente no tiene interacciones ni compras registradas". No se consulta Comportamiento | `unClienteSinIdentificadoresDeVentasNoConsultaCompras` |
| P7 | 3 | Gerente Comercial | `luis` entra a `/clientes/42/historial-atencion` o llama a la API | La pestaña no aparece. La ruta lleva a **Acceso denegado** y la API responde `403`, registrado como `ACCESO_DENEGADO` | `AccesoPorRolTest.soloElAdministradorYElAgenteConsultanElHistorialDelClienteConSusCompras` y `unUsuarioSinPermisoNoConsultaElHistorial` |
| P8 | 3 | Agente sin `INDICADORES_CONSULTAR` | Quitar el permiso al rol y consultar | `403` | Las mismas dos pruebas de P7 |
| P9 | 3 | Sin sesión | Llamar a la API sin token | `401` | Las mismas dos pruebas de P7 |
| P10 | 3 | Administrador | `admin` abre el historial del cliente 42 | Lo ve igual que el Agente | `elAdministradorTambienConsultaElHistorial` |
| P11 | — | Comportamiento caído | Detener `servicio-comportamiento` y abrir el cliente 42 | Se ven las interacciones, el aviso de que las compras no están disponibles y "—" en compras | `siComportamientoNoRespondeSeMuestranLasInteraccionesConUnAviso` y `ComprasClienteTest` |
| P12 | — | Cliente que no existe | Abrir `/clientes/999/historial-atencion` | Mensaje "No existe el cliente 999 en el CRM" (`404`) | `unClienteQueNoExisteResponde404` |
| P13 | — | Auditoría de la consulta | Consultar como `agente.ana` y revisar **Auditoría** como `admin` | `CLIENTE_CONSULTADO` del cliente 42 por `agente.ana` | Filtro de auditoría del Gateway (`AuditoriaAccesoFilterTest`) |

Las pruebas automatizadas están en:

- `servicio-interacciones`: `HistorialClienteIntegracionTest` y `ComprasClienteTest`. Usan PostgreSQL con
  Testcontainers, y Perfil y Comportamiento simulados.
- `api-gateway`: `AccesoPorRolTest`.

## Ejecutar

```bash
cd backend/servicio-interacciones && JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn -o clean test
cd backend/api-gateway && JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn -o clean test
```

Para los casos manuales, con un token de `agente.ana`:

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/interacciones/clientes/42/historial | jq
```

# Base de datos — crm_maxiconecta

Base **PostgreSQL** centralizada, con **un esquema por dominio**. Cada microservicio es dueño de su esquema y no escribe en los de otros.

| Esquema | Dueño |
|---|---|
| `perfil` | servicio-perfil |
| `comportamiento` | servicio-comportamiento |
| `segmentacion` | servicio-segmentacion |
| `fidelizacion` | servicio-fidelizacion |
| `interacciones` | servicio-interacciones |
| `auditoria` | registro de accesos y consultas (compartido, solo inserción) |

Las migraciones se versionan con **Flyway** dentro de cada microservicio.

## Datos de prueba

[100 clientes para SCRUM-25](semillas/README.md): carga SQL manual con compras, categorías,
consentimientos e indicadores para probar segmentación sobre el esquema existente.

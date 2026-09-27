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

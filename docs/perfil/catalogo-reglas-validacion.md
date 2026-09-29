# Catálogo de reglas de validación de perfiles de cliente

Reglas que usa `servicio-perfil` para determinar si el perfil de un cliente está completo, incompleto o inconsistente (SCRUM-12, RF-04). Es el insumo para el motor de detección (SCRUM-166) y para etiquetar el estado del perfil (SCRUM-165).

## Perfil incompleto

Un perfil queda **incompleto** cuando un dato obligatorio falta o no cumple su formato. Ya implementado por `ValidadorPerfil` sobre los datos que llegan en el evento RIO-CRM-01 (ver `docs/contratos-eventos/RIO-CRM-01-datos-cliente.md`).

| Campo | Regla | Motivo registrado |
|---|---|---|
| `nombres` / `apellidos` | Obligatorio, máx. 100 caracteres, solo letras, espacios, `.`, `'`, `-` | `nombres: vacío` / `nombres: formato inválido` / `nombres: excede 100 caracteres` |
| `tipoDocumento` | Obligatorio, uno de `CI`, `NIT`, `PASAPORTE`, `CE` | `tipoDocumento: vacío` / `tipoDocumento: valor no válido (...)` |
| `numeroDocumento` | Obligatorio, 4 a 20 letras, números o guiones | `numeroDocumento: vacío` / `numeroDocumento: formato inválido` |
| `email` | Formato de correo, máx. 150 caracteres | `email: formato inválido` / `email: excede 150 caracteres` |
| `telefono` | 7 a 20 dígitos, `+`, espacios, guiones o paréntesis; al menos 7 dígitos | `telefono: formato inválido` |
| contacto | Al menos un correo o un teléfono válido | `contacto: se necesita al menos un correo o un teléfono válido` |
| `direcciones[].idDireccion` | Obligatorio, máx. 64 caracteres, único por cliente | `direcciones[N].idDireccion: vacío` / `repetido` / `excede 64 caracteres` |
| `direcciones[].calle` | Obligatorio, máx. 200 caracteres | `direcciones[N].calle: vacío` / `excede 200 caracteres` |
| `direcciones[].ciudad` | Obligatorio, máx. 100 caracteres | `direcciones[N].ciudad: vacío` / `excede 100 caracteres` |

Un dato inválido no se guarda: el campo queda vacío y su motivo se agrega a `motivos_incompleto` (ver `docs/perfil/modelo-datos-perfil.md`).

## Perfil inconsistente

Un perfil queda **inconsistente** cuando sus datos están presentes y cumplen su formato individual, pero no son coherentes entre sí. A diferencia de lo incompleto, esto no impide guardar el dato: el motor de detección (SCRUM-166) lo marca para revisión sin descartar el valor.

| Regla | Motivo registrado |
|---|---|
| El formato de `numeroDocumento` no corresponde al `tipoDocumento` declarado (p. ej. letras en un `NIT`, que es solo numérico) | `numeroDocumento: no coincide con el formato de {tipoDocumento}` |
| El cliente tiene direcciones activas pero ninguna está marcada como `principal` | `direcciones: ninguna dirección principal` |

Esta lista se amplía cuando el motor de detección (SCRUM-166) lo requiera. No incluye la detección de perfiles duplicados entre `origen` (mismo `tipoDocumento` y `numeroDocumento` en más de un perfil): esa comparación es de la unificación de duplicados (SCRUM-13).

## Evaluación

- **Al sincronizar** (SCRUM-9): `ValidadorPerfil` aplica las reglas de completitud sobre cada evento entrante, antes de guardar.
- **Detección independiente** (SCRUM-166): vuelve a evaluar el perfil ya guardado contra ambos catálogos, sin esperar un nuevo evento, y lo etiqueta con la regla incumplida (criterio de aceptación 1 de SCRUM-12). Un perfil que no incumple ninguna regla queda **válido**, es decir `COMPLETO` (criterio de aceptación 2).
- `perfil.cliente.estado` distingue `COMPLETO`, `INCOMPLETO` e `INCONSISTENTE`; los motivos se conservan en `motivos_incompleto` y `motivos_inconsistencia`. La consulta expone también `motivosIncidencia`, que combina ambos grupos para revisión (SCRUM-165). Si concurren ambos tipos, prevalece `INCOMPLETO` sin perder las inconsistencias.

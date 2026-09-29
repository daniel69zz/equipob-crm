# Consentimiento de tratamiento de datos (RF-06)

Registro del consentimiento que cada cliente da para el tratamiento de sus datos personales: **qué autorizó** (alcance), **por qué canal**, **desde cuándo y hasta cuándo** (vigencia) y si lo **revocó**. Los demás módulos del CRM consultan este registro para usar la información del cliente solo dentro de lo autorizado.

Historia: SCRUM-14 · Épica: EPIC-01 Gestión y Perfil del Cliente.

## Modelo

```
cliente 1 ──── 0..1 consentimiento 1 ──── N consentimiento_alcance
   │                (estado actual)            (finalidades autorizadas)
   │
   └──── N consentimiento_historial
            (un registro por cada cambio, de solo lectura)
```

### `perfil.consentimiento` — estado actual, una fila por cliente

| Columna | Contenido |
|---|---|
| `id_cliente` | Perfil del cliente (clave primaria) |
| `estado` | `OTORGADO` o `REVOCADO` |
| `canal` | Por dónde lo dio el cliente: `MARKETPLACE`, `VENTAS`, `PRESENCIAL`, `TELEFONICO` o `CORREO` |
| `fecha_otorgamiento` | Momento en que el cliente otorgó el consentimiento |
| `vigencia_desde` | Primer día de vigencia |
| `vigencia_hasta` | Último día de vigencia; vacío si no vence |
| `fecha_revocacion` | Momento de la revocación; solo tiene valor si el estado es `REVOCADO` |
| `motivo_revocacion` | Motivo informado por el cliente al revocar (opcional) |
| `actualizado_en` / `actualizado_por` | Último cambio y el usuario del CRM que lo hizo |

La base impide guardar una vigencia cuyo fin sea anterior a su inicio y exige que `fecha_revocacion` exista **solo** cuando el estado es `REVOCADO`.

### `perfil.consentimiento_alcance` — finalidades autorizadas

Una fila por finalidad. Un consentimiento otorgado tiene al menos una.

| Alcance | Autoriza a usar los datos para |
|---|---|
| `GESTION_CLIENTE` | Mantener el perfil y atender al cliente |
| `ANALISIS_COMPORTAMIENTO` | Calcular indicadores de compra (ticket, frecuencia, recencia, valor) |
| `SEGMENTACION` | Asignar al cliente a segmentos |
| `FIDELIZACION` | Acumular y canjear puntos |
| `COMUNICACIONES_COMERCIALES` | Enviarle ofertas y campañas |

### `perfil.consentimiento_historial` — un registro por cambio

Cada otorgamiento, actualización o revocación guarda una **foto completa** del consentimiento tal como quedó, con la fecha y el responsable. Así se puede ver el estado del consentimiento en cualquier momento del pasado.

| Columna | Contenido |
|---|---|
| `id` | Identificador del registro |
| `id_cliente` | Perfil del cliente |
| `fecha` | Momento en que el CRM guardó el cambio |
| `operacion` | `OTORGAMIENTO`, `ACTUALIZACION` o `REVOCACION` |
| `estado`, `canal`, `fecha_otorgamiento`, `vigencia_desde`, `vigencia_hasta`, `fecha_revocacion`, `motivo_revocacion` | Estado del consentimiento después del cambio |
| `alcances` | Finalidades autorizadas después del cambio, separadas por comas y en orden alfabético |
| `responsable` | Usuario del CRM que hizo el cambio |

**Es de solo lectura:** triggers de la base rechazan `UPDATE`, `DELETE` y `TRUNCATE`, la entidad JPA es `@Immutable` y el API no ofrece ninguna forma de editarlo o borrarlo. Cubre la DoD *"Historial de cambios verificado como no editable"*.

## Operaciones

| Operación | Cuándo | Resultado |
|---|---|---|
| `OTORGAMIENTO` | El cliente no tenía consentimiento o lo había revocado | Estado `OTORGADO` |
| `ACTUALIZACION` | Cambia el canal, el alcance o la vigencia de un consentimiento otorgado | Sigue `OTORGADO` |
| `REVOCACION` | El cliente retira su consentimiento | Estado `REVOCADO` y se guarda la fecha de revocación |

Si una actualización llega con los mismos datos que ya tiene el consentimiento, **no** genera registro en el historial.

### Vigencia

Un consentimiento **está vigente** si su estado es `OTORGADO` y la fecha de hoy está entre `vigencia_desde` y `vigencia_hasta` (ambas incluidas). Uno otorgado cuya vigencia ya terminó o todavía no empieza no autoriza nada, aunque su estado siga siendo `OTORGADO`.

Para saber si puede usar los datos de un cliente, un módulo llama a `GestionConsentimiento.autoriza(idCliente, alcance)`: devuelve `true` solo si el consentimiento está vigente e incluye esa finalidad.

## Reglas de validación

| Regla | Mensaje |
|---|---|
| El cliente debe existir y no haber sido unificado en otro | `No existe el cliente 42` / `El cliente 42 ya fue unificado en el cliente 7` |
| Canal obligatorio | `Debe indicar el canal del consentimiento` (o el error de validación del campo `canal`) |
| Al menos un alcance | `Debe autorizar al menos un alcance` |
| La fecha final de la vigencia no puede ser anterior a la inicial | `La fecha final de la vigencia (2026-09-01) no puede ser anterior a la inicial (2026-10-01)` |
| La vigencia no puede haber terminado ya | `La vigencia terminó el 2026-09-01: registre una vigencia que incluya la fecha de hoy o una futura` |
| La fecha de otorgamiento no puede ser futura | `La fecha de otorgamiento no puede ser futura` |
| La vigencia no puede empezar antes del otorgamiento | `La vigencia no puede empezar (2026-09-01) antes de la fecha de otorgamiento (2026-09-10)` |
| Un nuevo otorgamiento debe ser posterior a la última revocación | `El nuevo otorgamiento no puede ser anterior a la revocación del 2026-09-20` |
| Solo se revoca un consentimiento otorgado | `El cliente 42 no tiene un consentimiento otorgado que revocar` (409) |

Las violaciones de regla responden **400 Bad Request** con el formato de error común (`ApiError`), salvo la revocación sin consentimiento otorgado, que responde **409 Conflict**.

## API

Todas las rutas siguen `docs/seguridad/convencion-rutas-clientes.md`, así que el API Gateway audita cada acceso. Las consultas exigen `CLIENTE_CONSULTAR` y los cambios `CLIENTE_EDITAR`. El responsable del cambio es el usuario que envía el Gateway en la cabecera `X-Usuario`.

| Método y ruta | Uso |
|---|---|
| `GET /api/perfil/clientes/{clienteId}/consentimiento` | Estado actual. **404** si el cliente nunca registró un consentimiento |
| `PUT /api/perfil/clientes/{clienteId}/consentimiento` | Otorgar o actualizar |
| `POST /api/perfil/clientes/{clienteId}/consentimiento/revocacion` | Revocar |
| `GET /api/perfil/clientes/{clienteId}/consentimiento/historial` | Todos los estados, del más reciente al más antiguo |

### Otorgar o actualizar

```http
PUT /api/perfil/clientes/42/consentimiento
X-Usuario: admin

{
  "canal": "PRESENCIAL",
  "alcances": ["GESTION_CLIENTE", "ANALISIS_COMPORTAMIENTO"],
  "vigenciaDesde": "2026-09-29",
  "vigenciaHasta": "2027-09-28",
  "fechaOtorgamiento": "2026-09-29T10:15:00-04:00"
}
```

`vigenciaHasta` vacío significa que no vence. Si no se envían, `vigenciaDesde` es hoy y `fechaOtorgamiento` es el momento del registro. Al actualizar un consentimiento ya otorgado, los que no se envían conservan su valor actual.

### Respuesta del estado actual

```json
{
  "idCliente": 42,
  "estado": "OTORGADO",
  "vigente": true,
  "canal": "PRESENCIAL",
  "alcances": ["ANALISIS_COMPORTAMIENTO", "GESTION_CLIENTE"],
  "fechaOtorgamiento": "2026-09-29T10:15:00-04:00",
  "vigenciaDesde": "2026-09-29",
  "vigenciaHasta": "2027-09-28",
  "fechaRevocacion": null,
  "motivoRevocacion": null,
  "actualizadoEn": "2026-09-29T10:15:03-04:00",
  "actualizadoPor": "admin"
}
```

### Revocar

```http
POST /api/perfil/clientes/42/consentimiento/revocacion
X-Usuario: admin

{ "motivo": "El cliente lo pidió por correo" }
```

El motivo es opcional (hasta 300 caracteres).

## Interfaz

La pantalla **Consentimiento** (`/clientes/{id}/consentimiento`, enlazada desde la búsqueda de clientes) muestra el estado actual y su historial. Los usuarios con `CLIENTE_EDITAR` ven además el formulario para otorgar o actualizar y el botón para revocar.

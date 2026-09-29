# Identificadores de origen del cliente (RF-62)

El módulo Marketplace y Ventas identifica a cada cliente con su `idCliente`. Una misma persona puede terminar con más de un identificador (por ejemplo, si abrió dos cuentas: `CLI-5521` y `mp-user-9001`). El CRM mantiene **un solo perfil** y le asocia todos esos identificadores, para que cada evento se aplique al cliente correcto sin crear duplicados.

## Tablas

| Tabla | Para qué |
|---|---|
| `perfil.cliente_origen` | Identificadores vinculados: `id_cliente_origen` (PK) → `id_cliente`. Varios pueden apuntar al mismo perfil |
| `perfil.vinculacion_pendiente` | Identificadores desconocidos que coinciden con un perfil existente y esperan que un administrador decida |
| `perfil.cliente.id_cliente_consolidado` | En un perfil absorbido por una unificación, el perfil que se conserva |

`cliente_origen` guarda además cómo se vinculó cada identificador:

| `motivo_vinculacion` | Cuándo |
|---|---|
| `ALTA_AUTOMATICA` | El identificador llegó en un evento y se creó un perfil nuevo para él |
| `VINCULACION_MANUAL` | Un administrador lo asoció a un perfil existente (por ejemplo, una vinculación pendiente) |
| `UNIFICACION` | Vino de un perfil absorbido al unificar duplicados |

y `vinculado_por` (usuario del CRM o `sincronizacion-automatica`).

## Cómo se resuelve el perfil de cada evento

```
evento (idCliente)
  │
  ├─ ¿está en cliente_origen? ──sí──► se aplica a ese perfil
  │                                  (si ese perfil fue absorbido, al que lo absorbió)
  no
  │
  ├─ ¿tiene una vinculación pendiente? ──PENDIENTE──► el evento queda PENDIENTE
  │                                   └─NUEVO_PERFIL─► se crea un perfil nuevo
  no
  │
  ├─ ¿su documento (tipo + número) coincide con un perfil existente? ──sí──► se registra la
  │                                        vinculación pendiente y el evento queda PENDIENTE
  no
  │
  └─► se crea un perfil nuevo (ALTA_AUTOMATICA)
```

La coincidencia por documento **no vincula sola**: un error de tipeo en el documento uniría a dos personas distintas. Un administrador confirma la vinculación o decide que es otra persona.

## Qué datos quedan cuando varios identificadores informan al mismo perfil

- Cada evento se aplica al perfil igual que si viniera del identificador principal: identificación, contacto y direcciones se actualizan con lo que trae.
- El orden (descartar eventos obsoletos) es **por identificador**: cada uno guarda la fecha del último cambio aplicado.

## Vinculación pendiente

| Acción del administrador | Resultado |
|---|---|
| Vincular el identificador al perfil sugerido (u otro) | Se crea el vínculo `VINCULACION_MANUAL` y los eventos pendientes de ese identificador se aplican a ese perfil, en orden |
| Declarar que es otra persona | La vinculación queda `NUEVO_PERFIL` y los eventos pendientes crean un perfil nuevo |

Mientras la vinculación está pendiente, los nuevos eventos del mismo identificador también quedan pendientes, para aplicarse todos en orden.

## Unificación de duplicados

Al unificar dos perfiles (la fusión de datos es SCRUM-13), **todos los identificadores del perfil absorbido pasan al que se conserva** con motivo `UNIFICACION`, conservando la fecha del último cambio aplicado de cada uno. El perfil absorbido queda con `id_cliente_consolidado` apuntando al conservado y sin identificadores.

Así, un evento posterior con un identificador del perfil absorbido se aplica al perfil conservado y **no vuelve a crear el duplicado**.

## Registro de cambios

Cada vinculación y cada unificación queda en `perfil.cambio_perfil` con origen `CRM`, el usuario que la hizo y el campo `identificadoresOrigen` (por ejemplo `null → mp-user-3307`).

## API

| Método | Ruta | Permiso (Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/perfil/vinculaciones` | `CLIENTE_CONSULTAR` | Vinculaciones pendientes (o `?estado=VINCULADO`, `NUEVO_PERFIL`) con el perfil sugerido |
| POST | `/api/perfil/clientes/{clienteId}/identificadores` | `CLIENTE_EDITAR` | Vincula un identificador (`{"idCliente": "mp-user-3307"}`) al perfil y aplica sus eventos pendientes |
| POST | `/api/perfil/vinculaciones/nuevo-perfil` | `CLIENTE_EDITAR` | Declara que el identificador (`{"idCliente"}`) es otra persona: sus eventos pendientes crean un perfil nuevo |

La unificación se expone con la historia SCRUM-13, que usa `ConsolidacionIdentificadores` para mover los identificadores.

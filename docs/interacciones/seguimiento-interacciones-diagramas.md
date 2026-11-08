# Diagramas de la clasificación y el seguimiento de interacciones (SCRUM-605)

Diagrama de clases y de secuencia de SCRUM-55. El catálogo de tipos y estados, las transiciones y las reglas
están en `docs/interacciones/seguimiento-interacciones.md`. Están escritos en Mermaid: GitHub los dibuja al abrir
este archivo, y para adjuntarlos a la historia en Jira se exportan como imagen desde <https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class InteraccionesClienteComponent {
        <<Angular, SCRUM-450>>
        +borrador FiltroInteracciones
        +buscar()
        +seguimientoActualizado(interaccion)
    }
    class SeguimientoInteraccionComponent {
        <<Angular, SCRUM-450>>
        +interaccion
        +estado
        +tipo
        +comentario
        +guardar()
    }
    class InteraccionesService {
        <<Angular>>
        +historial(clienteId, filtro, pagina, tamanio)
        +seguimiento(clienteId, id, cambio, version)
    }
    class SecurityConfig {
        <<API Gateway>>
        PATCH: INTERACCIONES_REGISTRAR
    }
    class InteraccionesController {
        <<SCRUM-452>>
        +seguimiento(clienteId, id, solicitud, req) Interaccion
        +historial(clienteId, tipo, estado, ...) PaginaInteracciones
    }
    class SolicitudSeguimiento {
        <<record, SCRUM-452>>
        +String estado
        +String tipo
        +String comentario
        +Long version
    }
    class ServicioInteracciones {
        <<SCRUM-452 y SCRUM-453>>
        +seguimiento(clienteId, id, solicitud, identidad) Interaccion
        +modificar(clienteId, id, solicitud, identidad) Interaccion
        -exigirNoCerrada(interaccion)
        -verificarVersion(interaccion, version)
    }
    class RegistroInteracciones {
        <<repository>>
        +bloquear(clienteId, id) Optional~Interaccion~
        +actualizarSeguimiento(id, tipo, estado, cambiaEstado, usuario) Interaccion
        +historial(clienteId, filtro) PaginaInteracciones
    }
    class AuditoriaInteracciones {
        <<repository>>
        +seguimiento(interaccion, usuario, rol, cambios, comentario)
    }
    class EstadoInteraccion {
        <<enumeration, SCRUM-451>>
        ABIERTA
        EN_PROCESO
        CERRADA
    }
    class TipoInteraccion {
        <<enumeration>>
        CONSULTA
        RECLAMO
        NOTA
    }
    class Interaccion {
        <<record, SCRUM-449>>
        +String tipo
        +String estado
        +String estadoActualizadoPor
        +OffsetDateTime estadoActualizadoEn
        +long version
        +cerrada() boolean
    }

    InteraccionesClienteComponent --> SeguimientoInteraccionComponent : una por interacción
    InteraccionesClienteComponent --> InteraccionesService
    SeguimientoInteraccionComponent --> InteraccionesService
    InteraccionesService ..> SecurityConfig : HTTP con el token
    SecurityConfig ..> InteraccionesController : X-Usuario, X-Usuario-Rol, X-Usuario-Permisos
    InteraccionesController ..> SolicitudSeguimiento
    InteraccionesController --> ServicioInteracciones
    ServicioInteracciones --> RegistroInteracciones
    ServicioInteracciones --> AuditoriaInteracciones : SEGUIMIENTO e INTERACCION_SEGUIMIENTO_ACTUALIZADO
    Interaccion ..> EstadoInteraccion
    Interaccion ..> TipoInteraccion
    RegistroInteracciones ..> Interaccion
```

## Diagrama de secuencia: cambiar el estado o reabrir una interacción

```mermaid
sequenceDiagram
    autonumber
    actor U as Agente o Administrador
    participant W as crm-web (SeguimientoInteraccionComponent)
    participant G as API Gateway
    participant I as servicio-interacciones
    participant DB as PostgreSQL

    U->>W: elige el estado "En proceso" y escribe un comentario
    W->>G: PATCH /api/interacciones/clientes/42/interacciones/7 {estado: EN_PROCESO, comentario, version: 1}
    alt Gerente o sin sesión
        G->>DB: auditoria.evento ACCESO_DENEGADO
        G-->>W: 401 o 403
    else Agente o Administrador
        G->>DB: auditoria.evento CLIENTE_MODIFICADO
        G->>I: PATCH con la identidad del usuario
        I->>I: rol y permiso, y validación del cuerpo
        I->>DB: BEGIN
        I->>DB: SELECT interaccion 7 del cliente 42 FOR UPDATE
        alt cerrada y el usuario no es Administrador (criterio 2)
            I->>DB: ROLLBACK
            I-->>W: 403 Solo el Administrador de CRM puede reabrir una interacción cerrada
        else otra versión
            I-->>W: 409 con campos.version
        else reapertura sin comentario
            I-->>W: 400 con campos.comentario
        else cerrada y solo cambia el tipo
            I-->>W: 409 La interacción está cerrada: reábrala para modificarla
        else mismo estado y mismo tipo
            I-->>W: 400 La interacción ya tiene ese estado y ese tipo
        else válido
            I->>DB: UPDATE estado, estado_actualizado_por, estado_actualizado_en, version + 1
            I->>DB: INSERT interaccion_auditoria SEGUIMIENTO con ABIERTA → EN_PROCESO y el comentario
            I->>DB: INSERT auditoria.evento INTERACCION_SEGUIMIENTO_ACTUALIZADO (criterio 1)
            I->>DB: COMMIT
            I-->>W: 200 interacción en proceso, version 2
            W-->>U: "La interacción n.º 7 quedó en proceso como reclamo"
        end
    end
```

## Diagrama de secuencia: filtrar por tipo y estado

```mermaid
sequenceDiagram
    autonumber
    actor U as Agente, Administrador o Gerente
    participant W as crm-web (InteraccionesClienteComponent)
    participant G as API Gateway
    participant I as servicio-interacciones
    participant DB as PostgreSQL

    U->>W: elige tipo Reclamo y estado Abierta, y pulsa Buscar
    W->>G: GET /api/interacciones/clientes/42/interacciones?tipo=RECLAMO&estado=ABIERTA
    G->>DB: auditoria.evento CLIENTE_CONSULTADO
    G->>I: GET con INTERACCIONES_CONSULTAR
    I->>I: valida tipo y estado (400 con campos si no existen)
    I->>DB: reclamos abiertos del cliente 42, de la más reciente a la más antigua
    I-->>W: {interacciones, total, pagina, tamanio}
    W-->>U: solo las que coinciden (criterio 3)
```

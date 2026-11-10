# Diagramas de la asignación de interacciones (SCRUM-608)

Diagrama de clases y de secuencia de SCRUM-56. El flujo, las reglas, la API y las notificaciones están en
`docs/interacciones/asignacion-interacciones.md`. Están escritos en Mermaid: GitHub los dibuja al abrir este
archivo, y para adjuntarlos a la historia en Jira se exportan como imagen desde <https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class InteraccionesClienteComponent {
        <<Angular>>
        +soloMias boolean
        +asignada(interaccion)
    }
    class AsignacionInteraccionComponent {
        <<Angular, SCRUM-455>>
        +interaccion
        +agente
        +comentario
        +abrir()
        +guardar()
    }
    class NotificacionesComponent {
        <<Angular, SCRUM-455>>
        +cargar()
        +marcar(notificacion)
    }
    class LayoutComponent {
        <<Angular>>
        campana con las no leídas
    }
    class InteraccionesService {
        <<Angular>>
        +agentes()
        +asignar(clienteId, id, responsable, comentario, version)
        +notificaciones(soloNoLeidas)
        +marcarLeida(id)
    }
    class SecurityConfig {
        <<API Gateway>>
        GET: INTERACCIONES_CONSULTAR
        PUT y POST: INTERACCIONES_REGISTRAR
    }
    class AgentesController {
        <<SCRUM-457>>
        +activos(req) List~Agente~
    }
    class InteraccionesController {
        +asignar(clienteId, id, solicitud, req) Interaccion
        +historial(clienteId, ..., responsable, req) PaginaInteracciones
    }
    class NotificacionesController {
        <<SCRUM-459>>
        +consultar(soloNoLeidas, req) Bandeja
        +marcarLeida(id, req) Notificacion
    }
    class IdentidadGateway {
        <<SCRUM-456>>
        +asignacion(req) Identidad
        +consulta(req) Identidad
    }
    class SolicitudAsignacion {
        <<record>>
        +String responsable
        +String comentario
        +Long version
    }
    class ServicioInteracciones {
        <<SCRUM-457>>
        +asignar(clienteId, id, solicitud, identidad) Interaccion
    }
    class AgentesAtencion {
        <<repository, lee seguridad.usuario>>
        +activos() List~Agente~
        +activo(usuario) Optional~Agente~
    }
    class RegistroInteracciones {
        <<repository>>
        +bloquear(clienteId, id) Optional~Interaccion~
        +asignar(id, responsable, usuario) Interaccion
    }
    class AuditoriaInteracciones {
        <<repository, SCRUM-458>>
        +asignada(interaccion, usuario, rol, anterior, nuevo, comentario)
    }
    class NotificacionesUsuario {
        <<repository, SCRUM-459>>
        +crear(destinatario, tipo, interaccionId, clienteId, mensaje, comentario, creadaPor) Notificacion
        +de(destinatario, soloNoLeidas, limite) List~Notificacion~
        +marcarLeida(id, destinatario) Optional~Notificacion~
    }
    class Interaccion {
        <<record>>
        +String estado
        +long version
        +String responsable
        +String responsableAsignadoPor
        +OffsetDateTime responsableAsignadoEn
    }
    class Notificacion {
        <<record>>
        +String tipo
        +long interaccionId
        +long clienteId
        +String mensaje
        +String comentario
        +OffsetDateTime leidaEn
    }

    InteraccionesClienteComponent --> AsignacionInteraccionComponent : una por interacción
    AsignacionInteraccionComponent --> InteraccionesService
    NotificacionesComponent --> InteraccionesService
    LayoutComponent --> InteraccionesService
    InteraccionesService ..> SecurityConfig : HTTP con el token
    SecurityConfig ..> AgentesController : X-Usuario, X-Usuario-Rol, X-Usuario-Permisos
    SecurityConfig ..> InteraccionesController
    SecurityConfig ..> NotificacionesController
    AgentesController ..> IdentidadGateway
    InteraccionesController ..> IdentidadGateway
    NotificacionesController ..> IdentidadGateway
    AgentesController --> AgentesAtencion
    InteraccionesController ..> SolicitudAsignacion
    InteraccionesController --> ServicioInteracciones
    NotificacionesController --> NotificacionesUsuario
    ServicioInteracciones --> AgentesAtencion
    ServicioInteracciones --> RegistroInteracciones
    ServicioInteracciones --> AuditoriaInteracciones : ASIGNADA e INTERACCION_ASIGNADA
    ServicioInteracciones --> NotificacionesUsuario : INTERACCION_ASIGNADA
    RegistroInteracciones ..> Interaccion
    NotificacionesUsuario ..> Notificacion
```

## Diagrama de secuencia: asignar una interacción y notificar al agente

```mermaid
sequenceDiagram
    autonumber
    actor A as Administrador de CRM
    participant W as crm-web
    participant G as API Gateway
    participant I as servicio-interacciones
    participant DB as PostgreSQL
    actor Ag as Agente de Atención

    A->>W: "Asignar" en el reclamo n.º 7
    W->>G: GET /api/interacciones/agentes
    G->>I: GET con la identidad del usuario
    I->>I: rol ADMINISTRADOR_CRM e INTERACCIONES_REGISTRAR (SCRUM-456)
    I->>DB: SELECT seguridad.usuario activos con rol AGENTE_ATENCION
    I-->>W: 200 agentes
    A->>W: elige a agente.ana y escribe un comentario
    W->>G: PUT /api/interacciones/clientes/42/interacciones/7/responsable {responsable, comentario, version: 2}
    alt Gerente o sin sesión
        G-->>W: 401 o 403
    else con INTERACCIONES_REGISTRAR
        G->>DB: auditoria.evento CLIENTE_MODIFICADO
        G->>I: PUT con la identidad del usuario
        alt no es Administrador (criterio 3)
            I-->>W: 403 Solo el Administrador de CRM puede asignar interacciones
        else Administrador
            I->>I: validación del cuerpo
            I->>DB: ¿agente.ana es un Agente de Atención activo?
            alt no lo es
                I-->>W: 400 con campos.responsable
            else lo es
                I->>DB: BEGIN
                I->>DB: SELECT interaccion 7 del cliente 42 FOR UPDATE
                alt otra versión, cerrada o el mismo responsable
                    I->>DB: ROLLBACK
                    I-->>W: 409 o 400
                else válido
                    I->>DB: UPDATE responsable, responsable_asignado_por, responsable_asignado_en, version + 1
                    I->>DB: INSERT interaccion_auditoria ASIGNADA con el anterior y el nuevo (criterio 2)
                    I->>DB: INSERT auditoria.evento INTERACCION_ASIGNADA
                    I->>DB: INSERT notificacion para agente.ana (criterio 1)
                    I->>DB: COMMIT
                    I-->>W: 200 interacción con su responsable, version 3
                    W-->>A: "La interacción n.º 7 quedó asignada a agente.ana"
                end
            end
        end
    end
    Ag->>W: navega por el CRM
    W->>G: GET /api/interacciones/notificaciones?soloNoLeidas=true
    G->>I: GET con la identidad del agente
    I->>DB: SELECT notificaciones de agente.ana
    I-->>W: 1 sin leer
    W-->>Ag: campana con 1, y en Notificaciones "Se le asignó el reclamo n.º 7 del cliente 42"
    Ag->>W: "Marcar como leída"
    W->>G: POST /api/interacciones/notificaciones/15/leida
    G->>I: POST
    I->>DB: UPDATE leida_en WHERE id = 15 AND destinatario = agente.ana
    I-->>W: 200
```

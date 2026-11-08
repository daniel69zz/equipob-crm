# Diagramas del registro de interacciones (SCRUM-604)

Diagrama de clases y de secuencia de SCRUM-52. El modelo de datos, los permisos y las reglas están en
`docs/interacciones/registro-interacciones.md`. Están escritos en Mermaid: GitHub los dibuja al abrir este archivo,
y para adjuntarlos a la historia en Jira se exportan como imagen desde <https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class InteraccionesClienteComponent {
        <<Angular, SCRUM-430 y SCRUM-431>>
        +borrador FiltroInteracciones
        +cargar(pagina)
        +buscar()
        +limpiar()
        +recargar(id)
    }
    class FormularioInteraccionComponent {
        <<Angular, SCRUM-434>>
        +clienteId
        +interaccion
        +guardar()
    }
    class InteraccionesService {
        <<Angular>>
        +historial(clienteId, filtro, pagina, tamanio)
        +detalle(clienteId, id)
        +registrar(clienteId, datos)
        +modificar(clienteId, id, datos, version)
    }
    class SecurityConfig {
        <<API Gateway>>
        GET: INTERACCIONES_CONSULTAR
        POST y PUT: INTERACCIONES_REGISTRAR
    }
    class AuditoriaAccesoFilter {
        <<API Gateway>>
        CLIENTE_CONSULTADO, CLIENTE_MODIFICADO, ACCESO_DENEGADO
    }
    class InteraccionesController {
        <<SCRUM-432>>
        +registrar(clienteId, solicitud, req) Interaccion
        +modificar(clienteId, id, solicitud, req) Interaccion
        +detalle(clienteId, id, req) Interaccion
        +historial(clienteId, filtros, req) PaginaInteracciones
        +auditoria(clienteId, id, req) List~RegistroAuditoria~
    }
    class IdentidadGateway {
        <<SCRUM-435>>
        +consulta(req) Identidad
        +registro(req) Identidad
        +auditoria(req) Identidad
    }
    class SolicitudInteraccion {
        <<record>>
        +String tipo
        +String canal
        +String asunto
        +String descripcion
        +String referenciaCompra
    }
    class SolicitudCambio {
        <<record>>
        +String tipo
        +String canal
        +String asunto
        +String descripcion
        +String referenciaCompra
        +Long version
    }
    class ServicioInteracciones {
        <<SCRUM-432>>
        +registrar(clienteId, solicitud, identidad) Interaccion
        +modificar(clienteId, id, solicitud, identidad) Interaccion
        +detalle(clienteId, id) Interaccion
        +historial(clienteId, filtros) PaginaInteracciones
        +auditoria(clienteId, id) List~RegistroAuditoria~
    }
    class ClientesPerfil {
        <<cliente HTTP>>
        +existe(clienteId) boolean
    }
    class RegistroInteracciones {
        <<repository>>
        +registrar(clienteId, campos, usuario, rol) Interaccion
        +buscar(clienteId, id) Optional~Interaccion~
        +bloquear(clienteId, id) Optional~Interaccion~
        +modificar(id, campos, usuario) Interaccion
        +historial(clienteId, filtro) PaginaInteracciones
    }
    class AuditoriaInteracciones {
        <<repository, SCRUM-436>>
        +creada(interaccion)
        +modificada(interaccion, rol, cambios)
        +de(interaccionId) List~RegistroAuditoria~
    }
    class Interaccion {
        <<record, SCRUM-433>>
        +long id
        +long clienteId
        +String tipo
        +String canal
        +String asunto
        +String descripcion
        +String referenciaCompra
        +String registradoPor
        +OffsetDateTime registradoEn
        +String modificadoPor
        +long version
    }

    InteraccionesClienteComponent --> FormularioInteraccionComponent : registrar y editar
    InteraccionesClienteComponent --> InteraccionesService
    FormularioInteraccionComponent --> InteraccionesService
    InteraccionesService ..> SecurityConfig : HTTP con el token
    SecurityConfig ..> AuditoriaAccesoFilter
    SecurityConfig ..> InteraccionesController : X-Usuario, X-Usuario-Rol, X-Usuario-Permisos
    InteraccionesController --> IdentidadGateway : acceso antes del cuerpo
    InteraccionesController ..> SolicitudInteraccion
    InteraccionesController ..> SolicitudCambio
    InteraccionesController --> ServicioInteracciones
    ServicioInteracciones --> ClientesPerfil : el cliente existe
    ServicioInteracciones --> RegistroInteracciones : interacciones.interaccion
    ServicioInteracciones --> AuditoriaInteracciones : interaccion_auditoria y auditoria.evento
    RegistroInteracciones ..> Interaccion
```

## Diagrama de secuencia: registrar una interacción

```mermaid
sequenceDiagram
    autonumber
    actor A as Agente de Atención
    participant W as crm-web
    participant G as API Gateway
    participant I as servicio-interacciones
    participant P as servicio-perfil
    participant DB as PostgreSQL

    A->>W: abre Interacciones del cliente 42 y pulsa "Registrar interacción"
    A->>W: tipo Reclamo, canal Teléfono, descripción y compra V-100234
    W->>W: valida tipo, descripción y largos (las reglas de SCRUM-432)
    W->>G: POST /api/interacciones/clientes/42/interacciones
    alt sin sesión o sin INTERACCIONES_REGISTRAR (Gerente)
        G->>DB: auditoria.evento ACCESO_DENEGADO
        G-->>W: 401 o 403
    else Agente o Administrador
        G->>DB: auditoria.evento CLIENTE_MODIFICADO (cliente 42)
        G->>I: POST con X-Usuario agente.ana, X-Usuario-Rol AGENTE_ATENCION
        I->>I: rol y permiso otra vez (SCRUM-435), y validación del cuerpo
        alt cuerpo inválido
            I-->>W: 400 con campos (por ejemplo campos.descripcion)
        else válido
            I->>P: GET /api/perfil/clientes/42
            alt Perfil no responde
                I-->>W: 503, no se guarda nada
            else 404
                I-->>W: 404 No existe el cliente 42 en el CRM
            else existe
                I->>DB: BEGIN
                I->>DB: INSERT interaccion (version 1, registrado_por agente.ana)
                I->>DB: INSERT interaccion_auditoria CREADA con los valores iniciales
                I->>DB: INSERT auditoria.evento INTERACCION_REGISTRADA (sin la descripción)
                I->>DB: COMMIT
                I-->>W: 201 interacción n.º 7
                W-->>A: "Se registró la interacción n.º 7" y el historial desde la primera página
            end
        end
    end
```

## Diagrama de secuencia: corregir una interacción

```mermaid
sequenceDiagram
    autonumber
    actor A as Agente de Atención
    participant W as crm-web
    participant G as API Gateway
    participant I as servicio-interacciones
    participant DB as PostgreSQL

    A->>W: pulsa "Editar" en la interacción 7 (version 1)
    A->>W: cambia el canal y guarda
    W->>G: PUT /api/interacciones/clientes/42/interacciones/7 {..., version: 1}
    G->>DB: auditoria.evento CLIENTE_MODIFICADO
    G->>I: PUT con la identidad del usuario
    I->>DB: BEGIN
    I->>DB: SELECT interaccion 7 del cliente 42 FOR UPDATE
    alt no existe o es de otro cliente
        I-->>W: 404
    else la registró otro agente
        I-->>W: 403 Solo puede modificar las interacciones que usted registró
    else la versión ya es 2
        I-->>W: 409 con campos.version
        W-->>A: "Otra persona modificó esta interacción" y Recargar
        A->>W: Recargar la interacción
        W->>G: GET /api/interacciones/clientes/42/interacciones/7
        G-->>W: la versión vigente, con el formulario abierto otra vez
    else sin cambios
        I-->>W: 400 La interacción no tiene cambios para guardar
    else válida
        I->>DB: UPDATE interaccion (version 2, modificado_por, modificado_en)
        I->>DB: INSERT interaccion_auditoria MODIFICADA con el canal anterior y el nuevo
        I->>DB: INSERT auditoria.evento INTERACCION_MODIFICADA con los campos cambiados
        I->>DB: COMMIT
        I-->>W: 200 interacción con version 2
    end
```

## Diagrama de secuencia: consultar y filtrar el historial

```mermaid
sequenceDiagram
    autonumber
    actor U as Agente, Administrador o Gerente
    participant W as crm-web
    participant G as API Gateway
    participant I as servicio-interacciones
    participant DB as PostgreSQL

    U->>W: elige Reclamo, escribe "envío" y las fechas, y pulsa Buscar
    W->>W: desde no puede ser posterior a hasta
    W->>G: GET /api/interacciones/clientes/42/interacciones?tipo=RECLAMO&texto=envío&desde=2026-10-01&hasta=2026-10-31
    G->>DB: auditoria.evento CLIENTE_CONSULTADO
    G->>I: GET con INTERACCIONES_CONSULTAR
    I->>I: valida los filtros (400 con campos si no son válidos)
    I->>DB: count y página de interacciones del cliente 42, de la más reciente a la más antigua
    I-->>W: {interacciones, total, pagina, tamanio}
    W-->>U: "3 interacciones coinciden con los filtros"
```

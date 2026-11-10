# Diagramas de la vinculación de interacciones con compras (SCRUM-607)

Diagrama de clases y de secuencia de SCRUM-54. La regla de asociación, la API y la auditoría están en
`docs/interacciones/vinculacion-compras.md`. Están escritos en Mermaid: GitHub los dibuja al abrir este archivo, y
para adjuntarlos a la historia en Jira se exportan como imagen desde <https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class InteraccionesClienteComponent {
        <<Angular>>
        +compraActualizada(interaccion)
    }
    class CompraInteraccionComponent {
        <<Angular, SCRUM-444>>
        +interaccion
        +texto
        +abrir()
        +buscar()
        +vincular(compra)
        +quitar()
    }
    class InteraccionesService {
        <<Angular>>
        +comprasElegibles(clienteId, texto, limite)
        +vincularCompra(clienteId, id, referencia, version)
        +desvincularCompra(clienteId, id, version)
    }
    class SecurityConfig {
        <<API Gateway>>
        GET: INTERACCIONES_CONSULTAR
        PUT y DELETE: INTERACCIONES_REGISTRAR
    }
    class ComprasElegiblesController {
        <<SCRUM-446>>
        +consultar(clienteId, texto, limite, req) ComprasElegibles
    }
    class ServicioComprasElegibles {
        <<SCRUM-446>>
        +consultar(clienteId, texto, limite) ComprasElegibles
    }
    class InteraccionesController {
        +vincularCompra(clienteId, id, solicitud, req) Interaccion
        +desvincularCompra(clienteId, id, version, req) Interaccion
    }
    class IdentidadGateway {
        <<SCRUM-447>>
        +historialCliente(req) Identidad
        +vinculacionCompra(req) Identidad
    }
    class SolicitudVinculacion {
        <<record>>
        +String referencia
        +Long version
    }
    class ServicioInteracciones {
        <<SCRUM-446 y SCRUM-447>>
        +vincularCompra(clienteId, id, solicitud, identidad) Interaccion
        +desvincularCompra(clienteId, id, version, identidad) Interaccion
        -validarVinculacion(interaccion, version, referencia)
    }
    class ClientesPerfil {
        <<HTTP a servicio-perfil>>
        +identificadores(clienteId) Optional~List~
    }
    class ComprasCliente {
        <<HTTP a servicio-comportamiento>>
        +elegibles(clienteId, ids, texto, limite) ComprasRecientes
        +buscar(clienteId, ids, referencia) Optional~Compra~
    }
    class RegistroInteracciones {
        <<repository, SCRUM-445>>
        +bloquear(clienteId, id) Optional~Interaccion~
        +vincularCompra(id, referencia, fecha, monto, estado, usuario) Interaccion
        +desvincularCompra(id) Interaccion
    }
    class AuditoriaInteracciones {
        <<repository, SCRUM-448>>
        +compra(interaccion, usuario, rol, anterior, nueva)
    }
    class Interaccion {
        <<record>>
        +long id
        +String estado
        +long version
        +CompraVinculada compra
    }
    class CompraVinculada {
        <<record, SCRUM-445>>
        +String referencia
        +OffsetDateTime fecha
        +BigDecimal montoTotal
        +String estado
        +String vinculadaPor
        +OffsetDateTime vinculadaEn
    }

    InteraccionesClienteComponent --> CompraInteraccionComponent : una por interacción
    CompraInteraccionComponent --> InteraccionesService
    InteraccionesService ..> SecurityConfig : HTTP con el token
    SecurityConfig ..> ComprasElegiblesController : X-Usuario, X-Usuario-Rol, X-Usuario-Permisos
    SecurityConfig ..> InteraccionesController
    ComprasElegiblesController --> ServicioComprasElegibles
    ComprasElegiblesController ..> IdentidadGateway
    InteraccionesController ..> IdentidadGateway
    InteraccionesController ..> SolicitudVinculacion
    InteraccionesController --> ServicioInteracciones
    ServicioComprasElegibles --> ClientesPerfil
    ServicioComprasElegibles --> ComprasCliente
    ServicioInteracciones --> ClientesPerfil
    ServicioInteracciones --> ComprasCliente
    ServicioInteracciones --> RegistroInteracciones
    ServicioInteracciones --> AuditoriaInteracciones : COMPRA_VINCULADA e INTERACCION_COMPRA_VINCULADA
    RegistroInteracciones ..> Interaccion
    Interaccion *-- CompraVinculada : 0..1
```

## Diagrama de secuencia: vincular una compra a un reclamo

```mermaid
sequenceDiagram
    autonumber
    actor U as Agente de Atención
    participant W as crm-web (CompraInteraccionComponent)
    participant G as API Gateway
    participant I as servicio-interacciones
    participant P as servicio-perfil
    participant C as servicio-comportamiento
    participant DB as PostgreSQL

    U->>W: "Vincular compra" en el reclamo n.º 7
    W->>G: GET /api/interacciones/clientes/42/compras?limite=20
    G->>I: GET con la identidad del usuario
    I->>P: GET /api/perfil/clientes/42
    P-->>I: identificadores en Marketplace y Ventas
    I->>C: GET /api/comportamiento/clientes/42/compras?identificador=CLI-5521
    C-->>I: compras del cliente
    I-->>W: 200 compras para elegir
    U->>W: elige la compra V-1001
    W->>G: PUT /api/interacciones/clientes/42/interacciones/7/compra {referencia: V-1001, version: 2}
    alt Gerente o sin sesión
        G-->>W: 401 o 403
    else Agente o Administrador
        G->>DB: auditoria.evento CLIENTE_MODIFICADO
        G->>I: PUT con la identidad del usuario
        I->>I: rol, INTERACCIONES_REGISTRAR e INDICADORES_CONSULTAR (SCRUM-447)
        I->>DB: SELECT interaccion 7 del cliente 42
        alt otra versión, cerrada o la misma compra
            I-->>W: 409 o 400, sin consultar Comportamiento
        else
            I->>P: identificadores del cliente
            I->>C: busca V-1001 entre las compras del cliente
            alt Comportamiento no responde
                I-->>W: 503
            else la compra no es del cliente (criterio 2)
                I-->>W: 400 con campos.referencia "no es una compra del cliente"
            else es del cliente
                I->>DB: BEGIN
                I->>DB: SELECT interaccion 7 FOR UPDATE y vuelve a validar
                I->>DB: UPDATE compra_referencia, compra_fecha, compra_monto_total, compra_estado, compra_vinculada_por, compra_vinculada_en, version + 1
                I->>DB: INSERT interaccion_auditoria COMPRA_VINCULADA con null → V-1001 (criterio 3)
                I->>DB: INSERT auditoria.evento INTERACCION_COMPRA_VINCULADA
                I->>DB: COMMIT
                I-->>W: 200 interacción con la compra vinculada (criterio 1)
                W-->>U: "La interacción n.º 7 quedó vinculada a la compra V-1001"
            end
        end
    end
```

# Diagramas de la consulta del historial del cliente (SCRUM-606)

Diagrama de clases y de secuencia de SCRUM-53. El diseño está en `docs/interacciones/historial-cliente.md` y las
pruebas en `docs/interacciones/historial-cliente-pruebas.md`. Están escritos en Mermaid: GitHub los dibuja al abrir
este archivo, y para adjuntarlos a la historia en Jira se exportan como imagen desde <https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class HistorialClienteComponent {
        <<Angular, SCRUM-439>>
        +vista Todo, Interacciones o Compras
        +cargar()
    }
    class historialClienteGuard {
        <<Angular>>
        Administrador o Agente con los dos permisos
    }
    class InteraccionesService {
        <<Angular>>
        +historialCliente(clienteId, limite)
    }
    class SecurityConfig {
        <<API Gateway, SCRUM-438>>
        GET historial: ADMINISTRADOR_CRM o AGENTE_ATENCION
        INTERACCIONES_CONSULTAR e INDICADORES_CONSULTAR
    }
    class HistorialClienteController {
        <<SCRUM-440>>
        +consultar(clienteId, limite, req) HistorialCliente
    }
    class IdentidadGateway {
        <<SCRUM-438>>
        +historialCliente(req) Identidad
    }
    class ServicioHistorialCliente {
        <<SCRUM-440 y SCRUM-441>>
        +consultar(clienteId, limite) HistorialCliente
    }
    class ClientesPerfil {
        <<cliente HTTP>>
        +identificadores(clienteId) Optional~List~String~~
    }
    class ComprasCliente {
        <<cliente HTTP, SCRUM-441>>
        +recientes(clienteId, identificadores, limite) ComprasRecientes
    }
    class RegistroInteracciones {
        <<repository>>
        +historial(clienteId, filtro) PaginaInteracciones
    }
    class HistorialCliente {
        <<record>>
        +List~EventoHistorial~ eventos
        +long totalInteracciones
        +long totalCompras
        +boolean sinInteracciones
        +boolean comprasDisponibles
        +boolean hayMas
        +List~String~ avisos
    }
    class EventoHistorial {
        <<record>>
        +Tipo tipo
        +OffsetDateTime fecha
        +Interaccion interaccion
        +Compra compra
        +CRONOLOGICO Comparator
    }
    class Compra {
        <<record>>
        +OffsetDateTime fecha
        +String referencia
        +BigDecimal montoTotal
        +String estado
    }

    HistorialClienteComponent ..> historialClienteGuard : ruta clientes/:id/historial-atencion
    HistorialClienteComponent --> InteraccionesService
    InteraccionesService ..> SecurityConfig : HTTP con el token
    SecurityConfig ..> HistorialClienteController : X-Usuario, X-Usuario-Rol, X-Usuario-Permisos
    HistorialClienteController --> IdentidadGateway : acceso antes que todo
    HistorialClienteController --> ServicioHistorialCliente
    ServicioHistorialCliente --> ClientesPerfil : servicio-perfil
    ServicioHistorialCliente --> RegistroInteracciones : interacciones.interaccion
    ServicioHistorialCliente --> ComprasCliente : servicio-comportamiento
    ServicioHistorialCliente ..> HistorialCliente
    HistorialCliente *-- EventoHistorial
    EventoHistorial ..> Compra
```

## Diagrama de secuencia: abrir el historial de atención

```mermaid
sequenceDiagram
    autonumber
    actor A as Agente de Atención
    participant W as crm-web (HistorialClienteComponent)
    participant G as API Gateway
    participant I as servicio-interacciones
    participant P as servicio-perfil
    participant C as servicio-comportamiento
    participant DB as PostgreSQL

    A->>W: abre la pestaña "Historial de atención" del cliente 42
    W->>G: GET /api/interacciones/clientes/42/historial?limite=50
    alt sin sesión, Gerente o sin INDICADORES_CONSULTAR (criterio 3)
        G->>DB: auditoria.evento ACCESO_DENEGADO
        G-->>W: 401 o 403
        W-->>A: "No tiene permiso para consultar el historial de atención"
    else Administrador o Agente
        G->>DB: auditoria.evento CLIENTE_CONSULTADO
        G->>I: GET con la identidad del usuario
        I->>I: rol y permisos otra vez, y límite de 1 a 100
        I->>P: GET /api/perfil/clientes/42
        alt no existe
            I-->>W: 404 No existe el cliente 42 en el CRM
        else Perfil no responde
            I-->>W: 503
        else existe (CLI-5521)
            I->>DB: las 50 interacciones más recientes del cliente 42 y el total
            opt tiene identificadores en Ventas
                I->>C: GET /api/comportamiento/clientes/42/compras?identificador=CLI-5521&tamanio=50
                alt Comportamiento no responde
                    C--xI: error o tiempo agotado
                    I->>I: comprasDisponibles false y aviso
                else
                    C-->>I: compras y total
                end
            end
            I->>I: une y ordena del más reciente al más antiguo (a igual fecha, la compra primero)
            I->>I: corta en 50; sin interacciones agrega el aviso (criterio 2)
            I-->>W: 200 {eventos, totales, avisos}
            W-->>A: línea de tiempo con interacciones y compras (criterio 1)
        end
    end
```

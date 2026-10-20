# Diagramas de la consulta de clientes por segmento (SCRUM-576)

Diagrama de clases y diagramas de secuencia de SCRUM-28. El criterio de consulta está en
`docs/segmentacion/criterio-consulta-clientes.md` y el uso, en `docs/segmentacion/clientes-por-segmento.md`.
Están escritos en Mermaid: GitHub los dibuja al abrir este archivo, y para adjuntarlos a la historia
en Jira se exportan como imagen desde <https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    namespace frontend {
        class ClientesSegmentoComponent {
            +filtro FiltroClientesSegmento
            +consultado Signal~FiltroClientesSegmento~
            +orden Signal~OrdenClientes~
            +direccion Signal~Direccion~
            +consultar()
            +ordenarPor(OrdenClientes columna)
            +buscar(number pagina)
        }
        class ClientesSegmentoService {
            +segmentos() Observable~List~SegmentoDisponible~~
            +buscar(filtro, orden, direccion, pagina, tamanio) Observable~PaginaClientesSegmento~
        }
        class PaginacionComponent {
            +pagina number
            +tamanio number
            +total number
            +cambiar EventEmitter~number~
        }
    }

    namespace servicio_segmentacion {
        class PermisoConsultaClientes {
            <<WebMvcConfigurer>>
            +addInterceptors(InterceptorRegistry)
        }
        class ClientesSegmentoController {
            +buscar(segmento, nivelConsumo, desde, hasta, orden, direccion, pagina, tamanio, usuario) ListadoClientes
            +segmentos() List~SegmentoDisponible~
        }
        class ConsultaClientesSegmento {
            +buscar(FiltroClientes, OrdenClientes, Direccion, int pagina, int tamanio, String usuario) ListadoClientes
            +segmentos() List~SegmentoDisponible~
        }
        class FiltroClientes {
            <<record>>
            +String segmento
            +NivelConsumo nivelConsumo
            +LocalDate ultimaCompraDesde
            +LocalDate ultimaCompraHasta
            +de(String, String, LocalDate, LocalDate)$ FiltroClientes
        }
        class OrdenClientes {
            <<enumeration>>
            VALOR
            NIVEL_CONSUMO
            ULTIMA_COMPRA
            FRECUENCIA
            SEGMENTO
            ASIGNADO_EN
            +de(String)$ OrdenClientes
        }
        class ListadoClientesRepository {
            <<repository>>
            +buscar(FiltroClientes, OrdenClientes, Direccion, int, int) PaginaFilas
            +segmentos() List~SegmentoDisponible~
        }
        class PerfilesClientes {
            +buscar(Collection~Long~ clienteIds) NombresClientes
        }
        class AuditoriaConsultaClientes {
            <<repository>>
            +registrar(usuario, FiltroClientes, OrdenClientes, Direccion, pagina, tamanio, total, clientes)
        }
        class ListadoClientes {
            <<record>>
            +List~Cliente~ content
            +int pagina
            +int tamanio
            +long total
            +String orden
            +String direccion
            +boolean nombresDisponibles
        }
        class RegistroSegmentos {
            <<repository>>
            +guardarMetricas(MetricasCliente)
        }
        class MetricasCliente {
            <<record>>
            +Long idCliente
            +Long frecuencia
            +BigDecimal valor
            +String nivelConsumo
            +OffsetDateTime ultimaCompra
        }
        class NivelConsumo {
            <<enumeration>>
            BAJO
            MEDIO
            ALTO
        }
    }

    namespace servicio_comportamiento {
        class CalculoMetricasCliente {
            +calcular(String idEvento, String tipo, long idCliente) MetricasCliente
        }
        class CriterioNivelConsumo {
            +clasificar(BigDecimal valor) Optional~NivelConsumo~
        }
    }

    namespace servicio_perfil {
        class PerfilController {
            +porIdentificadores(List~Long~ ids) List~ResumenPerfil~
        }
        class ConsultaPerfil {
            +resumenes(List~Long~ ids) List~ResumenPerfil~
        }
    }

    ClientesSegmentoComponent --> ClientesSegmentoService
    ClientesSegmentoComponent --> PaginacionComponent
    ClientesSegmentoService ..> ClientesSegmentoController : HTTP vía API Gateway
    PermisoConsultaClientes ..> ClientesSegmentoController : SEGMENTOS_CONSULTAR
    ClientesSegmentoController --> ConsultaClientesSegmento
    ClientesSegmentoController ..> FiltroClientes
    ClientesSegmentoController ..> OrdenClientes
    ConsultaClientesSegmento --> ListadoClientesRepository
    ConsultaClientesSegmento --> PerfilesClientes
    ConsultaClientesSegmento --> AuditoriaConsultaClientes
    ConsultaClientesSegmento ..> ListadoClientes
    FiltroClientes --> NivelConsumo
    PerfilesClientes ..> PerfilController : HTTP interno
    PerfilController --> ConsultaPerfil
    CalculoMetricasCliente --> CriterioNivelConsumo
    CalculoMetricasCliente ..> MetricasCliente : publica por RabbitMQ
    RegistroSegmentos ..> MetricasCliente
    ListadoClientesRepository ..> RegistroSegmentos : lee metricas_cliente y segmento_cliente
```

## Diagrama de secuencia: consulta de un segmento

```mermaid
sequenceDiagram
    autonumber
    actor Gerente as Gerente Comercial
    participant Web as ClientesSegmentoComponent
    participant GW as API Gateway
    participant Ctrl as ClientesSegmentoController
    participant Svc as ConsultaClientesSegmento
    participant Repo as ListadoClientesRepository
    participant Perfil as servicio-perfil
    participant Aud as auditoria.evento

    Gerente->>Web: Elige segmento y filtros, pulsa Consultar
    Web->>GW: GET /api/segmentacion/clientes?segmento=..&orden=valor&pagina=0
    alt Rol sin SEGMENTOS_CONSULTAR
        GW->>Aud: ACCESO_DENEGADO (sin cliente)
        GW-->>Web: 403
        Web-->>Gerente: «Su rol no tiene permiso…»
    else Rol autorizado
        GW->>Ctrl: Reenvía con X-Usuario y X-Usuario-Permisos
        Note over Ctrl: PermisoConsultaClientes vuelve a comprobar el permiso (401/403)
        Ctrl->>Ctrl: FiltroClientes.de(...), OrdenClientes.de(...)  [400 si no son válidos]
        Ctrl->>Svc: buscar(filtro, orden, direccion, pagina, tamanio, usuario)
        Svc->>Repo: buscar(...)
        Repo-->>Svc: Página de filas y total (segmento_cliente FULL JOIN metricas_cliente)
        Svc->>Perfil: GET /api/perfil/clientes?id=..&id=.. (una llamada por página)
        alt perfil responde
            Perfil-->>Svc: Nombres y apellidos
        else perfil no responde
            Svc->>Svc: nombresDisponibles = false
        end
        Svc->>Aud: CLIENTES_SEGMENTO_CONSULTADOS (grupo, filtros, clientes mostrados)
        Svc-->>Ctrl: ListadoClientes
        Ctrl-->>GW: 200
        GW->>Aud: CLIENTE_CONSULTADO (sin cliente)
        GW-->>Web: 200 con la página
        Web-->>Gerente: Tabla paginada o «El segmento no tiene clientes asignados»
    end

    Gerente->>Web: Ordena por «Última compra» o pide la página siguiente
    Note over Web: Reutiliza el grupo consultado: no pierde el segmento elegido
    Web->>GW: GET /api/segmentacion/clientes?segmento=..&orden=ultimaCompra&direccion=desc&pagina=0
```

## Diagrama de secuencia: de dónde salen el nivel y la última compra

```mermaid
sequenceDiagram
    autonumber
    participant MV as Marketplace y Ventas
    participant Comp as servicio-comportamiento
    participant MQ as RabbitMQ
    participant Seg as servicio-segmentacion
    participant DB as segmentacion.metricas_cliente

    MV->>Comp: compra.confirmada / compra.anulada
    Comp->>Comp: CalculoMetricasCliente.calcular: frecuencia, valor, categorías,<br/>nivelConsumo (CriterioNivelConsumo) y ultimaCompra
    Comp->>MQ: metricas.actualizadas (después del commit)
    MQ->>Seg: crm.segmentacion.metricas
    Seg->>Seg: AsignacionSegmento.aplicar (segmento por prioridad, SCRUM-26)
    Seg->>DB: RegistroSegmentos.guardarMetricas con nivel_consumo y ultima_compra
```

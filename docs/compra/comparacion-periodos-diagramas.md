# Diagramas de la comparación entre periodos (SCRUM-587)

Diagrama de clases y diagramas de secuencia de la comparación de la frecuencia y el valor de compra
entre dos periodos, para un cliente o para un segmento (SCRUM-40). La lógica que implementan está en
`docs/compra/comparacion-periodos.md`. Están escritos en Mermaid: GitHub los dibuja al abrir este
archivo, y para adjuntarlos a la historia en Jira se exportan como imagen desde <https://mermaid.live>.

## Diagrama de clases

Paquete `comparacion` de `servicio-comportamiento`, la agregación que reutiliza del paquete `periodo`
(SCRUM-39), la consulta de segmentos en `servicio-segmentacion` y los componentes del frontend.

```mermaid
classDiagram
    direction LR

    namespace frontend {
        class ComparacionPeriodosComponent {
            +alcance: CLIENTE | SEGMENTO
            +segmento: string
            +desdeA, hastaA, desdeB, hastaB: string
            +comparar()
            +porcentaje(variacion) string
        }
        class ComparacionPeriodosService {
            +compararCliente(idCliente, identificadores, periodos) Observable
            +compararSegmento(segmento, periodos) Observable
        }
    }

    namespace comparacion {
        class ComparacionPeriodosController {
            +compararCliente(clienteId, identificadores, desdeA, hastaA, desdeB, hastaB) ResultadoComparacion
            +compararSegmento(segmento, desdeA, hastaA, desdeB, hastaB) ResultadoComparacion
        }
        class ConsultaComparacionPeriodos {
            -ZoneId zona
            +compararCliente(List~Identificador~ ids, PeriodosAComparar periodos) ResultadoComparacion
            +compararSegmento(String segmento, PeriodosAComparar periodos) ResultadoComparacion
        }
        class PeriodosAComparar {
            <<record>>
            +RangoPeriodo a
            +RangoPeriodo b
            +de(desdeA, hastaA, desdeB, hastaB)$ PeriodosAComparar
        }
        class ClientesDelSegmento {
            +idsDe(String segmento) List~Long~
        }
        class ResultadoComparacion {
            <<record>>
            +AlcanceComparacion alcance
            +RangoPeriodo periodoA
            +RangoPeriodo periodoB
            +VariacionConsumo frecuencia
            +VariacionConsumo valor
            +boolean sinDatos
            +String mensaje
        }
        class VariacionConsumo {
            <<record>>
            +BigDecimal periodoA
            +BigDecimal periodoB
            +BigDecimal variacionAbsoluta
            +BigDecimal variacionPorcentual
            +TendenciaConsumo tendencia
            +calcular(a, b)$ VariacionConsumo
        }
        class TendenciaConsumo {
            <<enumeration>>
            CRECE
            DECAE
            ESTABLE
            NUEVO
        }
    }

    namespace periodo {
        class RangoPeriodo {
            <<record>>
            +LocalDate desde
            +LocalDate hasta
            +inicio(ZoneId) OffsetDateTime
            +fin(ZoneId) OffsetDateTime
        }
        class AgregadoConsumoPeriodo {
            +resumir(List~Identificador~ ids, inicio, fin) ResumenCompras
            +resumirPerfiles(List~Long~ idsPerfil, inicio, fin) ResumenCompras
        }
    }

    namespace segmentacion {
        class ClientesDeSegmentoController {
            +buscar(String segmento, int pagina, int tamanio) ClientesDeSegmento
        }
        class SegmentoClienteRepository {
            <<interface>>
            +findBySegmentoOrderByClienteId(String segmento, Pageable) Page
        }
    }

    class ManejadorDeErrores {
        +reglaNegocio() 400
        +servicioNoDisponible() 503
    }

    ComparacionPeriodosComponent --> ComparacionPeriodosService
    ComparacionPeriodosService ..> ComparacionPeriodosController : GET
    ComparacionPeriodosController --> ConsultaComparacionPeriodos
    ComparacionPeriodosController --> PeriodosAComparar : valida los periodos
    ComparacionPeriodosController ..> ManejadorDeErrores
    PeriodosAComparar --> RangoPeriodo
    ConsultaComparacionPeriodos --> AgregadoConsumoPeriodo : una agregación por periodo
    ConsultaComparacionPeriodos --> ClientesDelSegmento : alcance de segmento
    ConsultaComparacionPeriodos --> ResultadoComparacion
    ResultadoComparacion --> VariacionConsumo
    VariacionConsumo --> TendenciaConsumo
    ClientesDelSegmento ..> ClientesDeSegmentoController : GET /api/segmentacion/segmentos/miembros?segmento=
    ClientesDeSegmentoController --> SegmentoClienteRepository
```

## Diagrama de secuencia: comparar un cliente

Criterios de aceptación 1 y 3.

```mermaid
sequenceDiagram
    autonumber
    actor U as Analista Comercial
    participant W as ComparacionPeriodosComponent
    participant G as API Gateway
    participant C as ComparacionPeriodosController
    participant S as ConsultaComparacionPeriodos
    participant A as AgregadoConsumoPeriodo
    participant DB as PostgreSQL

    U->>W: elige el periodo A, el periodo B y compara
    W->>W: valida fechas completas, en orden y sin superponerse
    W->>G: GET /api/comportamiento/clientes/{id}/comparacion-periodos?desdeA&hastaA&desdeB&hastaB&identificador
    G->>G: exige INDICADORES_CONSULTAR y audita el acceso
    G->>C: reenvía la consulta
    C->>C: PeriodosAComparar valida los dos periodos
    C->>S: compararCliente(identificadores, periodos)
    S->>A: resumir(ids, inicio y fin del periodo A)
    A->>DB: count y sum de lo vigente del periodo A
    A-->>S: compras y valor del periodo A
    S->>A: resumir(ids, inicio y fin del periodo B)
    A->>DB: count y sum de lo vigente del periodo B
    A-->>S: compras y valor del periodo B
    S->>S: VariacionConsumo.calcular para frecuencia y valor
    S-->>C: ResultadoComparacion
    C-->>W: 200 con las variaciones y la tendencia
    W-->>U: tabla con A, B, variación, % y tendencia
```

## Diagrama de secuencia: comparar un segmento

Criterio de aceptación 2.

```mermaid
sequenceDiagram
    autonumber
    actor U as Gerente Comercial
    participant G as API Gateway
    participant C as ComparacionPeriodosController
    participant S as ConsultaComparacionPeriodos
    participant K as ClientesDelSegmento
    participant SG as servicio-segmentacion
    participant A as AgregadoConsumoPeriodo

    U->>G: GET /api/comportamiento/comparacion-periodos?segmento=FRECUENTE&...
    G->>G: exige INDICADORES_CONSULTAR y SEGMENTOS_CONSULTAR
    G->>C: reenvía la consulta
    C->>S: compararSegmento(segmento, periodos)
    S->>K: idsDe("FRECUENTE")
    loop mientras falten clientes
        K->>SG: GET /api/segmentacion/segmentos/miembros?segmento=FRECUENTE&pagina&tamanio=1000
        SG-->>K: clienteId de la página y total
    end
    alt servicio-segmentacion no responde
        K-->>S: ServicioNoDisponibleException
        S-->>U: 503 sin resultado en cero
    else el segmento no tiene clientes
        K-->>S: lista vacía
        S-->>U: 200 en cero con un mensaje
    else el segmento tiene clientes
        K-->>S: identificadores de perfil
        S->>A: resumirPerfiles(clientes, periodo A)
        A-->>S: compras y valor de esos clientes en A
        S->>A: resumirPerfiles(clientes, periodo B)
        A-->>S: compras y valor de esos clientes en B
        S-->>U: 200 con las variaciones del segmento
    end
```

## Diagrama de secuencia: usuario sin permiso

```mermaid
sequenceDiagram
    autonumber
    actor U as Agente de Atención (sin SEGMENTOS_CONSULTAR)
    participant G as API Gateway
    participant C as ComparacionPeriodosController

    U->>G: GET /api/comportamiento/comparacion-periodos?segmento=FRECUENTE&...
    G->>G: valida el token y los dos permisos requeridos
    G-->>U: 403 Acceso denegado (401 si no hay sesión)
    Note over G,C: la petición nunca llega al microservicio
```

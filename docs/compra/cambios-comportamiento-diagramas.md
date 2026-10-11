# Diagramas de la detección de cambios de comportamiento (SCRUM-580)

Diagrama de clases y diagramas de secuencia de la detección de variaciones en la frecuencia o el
monto de compra (SCRUM-34). La lógica que implementan está en `docs/compra/cambios-comportamiento.md`.
Están escritos en Mermaid: GitHub los dibuja al abrir este archivo, y para adjuntarlos a la historia en
Jira se exportan como imagen desde <https://mermaid.live>.

## Diagrama de clases

Paquete `cambio` de `servicio-comportamiento`, la agregación de compras que lo alimenta, y los bloques
que muestran las alertas en `servicio-perfil` y en el frontend.

```mermaid
classDiagram
    direction LR

    class TipoVariacion {
        <<enumeration>>
        FRECUENCIA
        MONTO
    }
    class DireccionVariacion {
        <<enumeration>>
        AUMENTO
        DISMINUCION
    }
    class EstadoComportamiento {
        <<enumeration>>
        SIN_LINEA_BASE
        ESTABLE
        CON_VARIACION
    }
    class Ventanas {
        <<record>>
        +OffsetDateTime inicioBase
        +OffsetDateTime inicioActual
        +OffsetDateTime fin
    }
    class Variacion {
        <<record>>
        +TipoVariacion tipo
        +DireccionVariacion direccion
        +BigDecimal actual
        +BigDecimal habitual
        +BigDecimal relativa
    }
    class Evaluacion {
        <<record>>
        +EstadoComportamiento estado
        +int comprasLineaBase
        +List~Variacion~ variaciones
    }
    class CriterioCambioComportamiento {
        -int ventanaDias
        -int periodosBase
        -int minimoComprasBase
        -BigDecimal umbral
        -Clock reloj
        +ventanas() Ventanas
        +evaluar(ComprasPorPeriodoCliente compras, Ventanas ventanas) Evaluacion
    }
    class DetectorCambiosComportamiento {
        +detectarTodos() ResultadoDeteccion
    }
    class DeteccionCambiosScheduler {
        +ejecutar()
    }
    class DeteccionCambioComportamiento {
        <<entity>>
        -Long id
        -String idClienteOrigen
        -TipoVariacion tipo
        -DireccionVariacion direccion
        -BigDecimal valorActual
        -BigDecimal valorHabitual
        -BigDecimal variacion
        -BigDecimal umbral
        -OffsetDateTime periodoDesde
        -OffsetDateTime periodoHasta
        -OffsetDateTime detectadoEn
    }
    class EstadoComportamientoCliente {
        <<entity>>
        -String idClienteOrigen
        -EstadoComportamiento estado
        -int comprasLineaBase
        -OffsetDateTime evaluadoEn
        +actualizar(estado, comprasLineaBase, evaluadoEn)
    }
    class DeteccionCambioComportamientoRepository {
        <<interface>>
        +existsBy...DetectadoEnAfter(cliente, tipo, direccion, desde) boolean
        +findByIdClienteOrigenIn...(identificadores, pageable) List
    }
    class EstadoComportamientoClienteRepository {
        <<interface>>
    }
    class ConsultaCambiosComportamiento {
        +buscar(List~Identificador~ identificadores) CambiosComportamiento
    }
    class CambiosComportamientoController {
        +buscar(String clienteId, List~String~ identificador) CambiosComportamiento
    }

    class AgregadoComprasCliente {
        +comprasPorPeriodo(inicioBase, inicioActual, fin) List~ComprasPorPeriodoCliente~
    }
    class ComprasPorPeriodoCliente {
        <<record>>
        +String idClienteOrigen
        +OffsetDateTime primeraCompra
        +Long comprasBase
        +BigDecimal montoBase
        +Long comprasActual
        +BigDecimal montoActual
    }

    class CambiosComportamientoDelCliente {
        <<servicio-perfil>>
        +buscar(Long clienteId, List~ClienteOrigen~ origenes) CambiosComportamientoResponse
    }
    class ConsultaFichaIntegral {
        <<servicio-perfil>>
        +obtener(Long clienteId) FichaIntegralResponse
    }
    class CambiosComportamientoComponent {
        <<frontend>>
        +cambios CambiosComportamientoFicha
    }

    DeteccionCambiosScheduler --> DetectorCambiosComportamiento
    DetectorCambiosComportamiento --> AgregadoComprasCliente
    DetectorCambiosComportamiento --> CriterioCambioComportamiento
    DetectorCambiosComportamiento --> DeteccionCambioComportamientoRepository
    DetectorCambiosComportamiento --> EstadoComportamientoClienteRepository
    AgregadoComprasCliente ..> ComprasPorPeriodoCliente : agrega
    CriterioCambioComportamiento ..> Ventanas : calcula
    CriterioCambioComportamiento ..> Evaluacion : produce
    Evaluacion o-- Variacion
    Evaluacion --> EstadoComportamiento
    Variacion --> TipoVariacion
    Variacion --> DireccionVariacion
    DeteccionCambioComportamientoRepository ..> DeteccionCambioComportamiento
    EstadoComportamientoClienteRepository ..> EstadoComportamientoCliente
    CambiosComportamientoController --> ConsultaCambiosComportamiento
    ConsultaCambiosComportamiento --> DeteccionCambioComportamientoRepository
    ConsultaCambiosComportamiento --> EstadoComportamientoClienteRepository
    ConsultaFichaIntegral --> CambiosComportamientoDelCliente
    CambiosComportamientoDelCliente ..> CambiosComportamientoController : GET HTTP
    CambiosComportamientoComponent ..> ConsultaFichaIntegral : ficha integral vía Gateway
```

## Diagrama de secuencia: evaluación periódica de un cliente contra su línea base

```mermaid
sequenceDiagram
    autonumber
    participant S as DeteccionCambiosScheduler
    participant D as DetectorCambiosComportamiento
    participant C as CriterioCambioComportamiento
    participant A as AgregadoComprasCliente
    participant E as EstadoComportamientoClienteRepository
    participant R as DeteccionCambioComportamientoRepository
    participant DB as PostgreSQL (comportamiento)

    S->>D: detectarTodos() (cron, 04:00)
    D->>C: ventanas()
    C-->>D: [inicioBase, inicioActual, fin)
    D->>A: comprasPorPeriodo(inicioBase, inicioActual, fin)
    A->>DB: compras vigentes con fecha < fin, agrupadas por cliente
    DB-->>A: filas por idClienteOrigen
    A-->>D: List<ComprasPorPeriodoCliente>
    loop cada cliente
        D->>C: evaluar(compras, ventanas)
        alt historial insuficiente
            C-->>D: SIN_LINEA_BASE, sin variaciones
        else con línea base
            C->>C: habitual = total base × ventana / días observados
            C->>C: variación = (actual − habitual) / habitual, para frecuencia y monto
            C-->>D: ESTABLE o CON_VARIACION + variaciones
        end
        D->>E: guardar estado (estado, compras base, evaluadoEn)
        loop cada variación relevante
            D->>R: existe la misma (cliente, tipo, dirección) después de inicioActual?
            alt ya detectada en el periodo
                R-->>D: sí, no se duplica
            else nueva
                D->>R: save(DeteccionCambioComportamiento)
                D->>D: log de auditoría
            end
        end
    end
    D->>E: quitar estados de clientes sin compras vigentes
    D-->>S: ResultadoDeteccion(evaluados, sinLineaBase, nuevas)
```

## Diagrama de secuencia: el usuario ve la alerta en la ficha integral

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario (FICHA_INTEGRAL_CONSULTAR)
    participant W as Frontend (FichaIntegralComponent)
    participant G as API Gateway
    participant P as servicio-perfil (ConsultaFichaIntegral)
    participant K as CambiosComportamientoDelCliente
    participant B as servicio-comportamiento (CambiosComportamientoController)
    participant Q as ConsultaCambiosComportamiento

    U->>W: abre la ficha del cliente 42
    W->>G: GET /api/perfil/clientes/42/ficha-integral (JWT)
    G->>P: reenvía (permiso verificado y acceso auditado)
    P->>P: datos, historial, segmento, puntos, nivel
    P->>K: buscar(42, identificadores de origen)
    K->>B: GET /api/comportamiento/clientes/42/cambios-comportamiento?identificador=...
    B->>Q: buscar(identificadores)
    Q-->>B: estado + alertas (más recientes primero)
    B-->>K: 200 JSON
    alt comportamiento no responde
        K-->>P: sinDatos = true (la ficha carga igual)
    else
        K-->>P: CambiosComportamientoResponse
    end
    P-->>G: FichaIntegralResponse con cambiosComportamiento
    G-->>W: 200
    W->>U: CambiosComportamientoComponent: fecha, tipo, dirección y valores comparados
```

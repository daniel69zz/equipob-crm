# Diagramas del riesgo de abandono (SCRUM-581)

Diagrama de clases y diagramas de secuencia de SCRUM-35. La fórmula, los niveles y las consultas están
en `docs/compra/riesgo-abandono.md`. Están escritos en Mermaid: GitHub los dibuja al abrir este
archivo, y para adjuntarlos a la historia en Jira se exportan como imagen desde
<https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    namespace servicio_comportamiento {
        class CriterioRiesgoAbandono {
            +evaluar(DatosRiesgo datos, OffsetDateTime ahora) EvaluacionRiesgo
            ~nivel(BigDecimal puntaje, boolean inactivo) NivelRiesgo
            +pesoRecencia() BigDecimal
            +umbralMedio() BigDecimal
            +umbralAlto() BigDecimal
        }
        class DatosRiesgo {
            <<record>>
            +long compras
            +BigDecimal valorAcumulado
            +OffsetDateTime ultimaCompra
            +boolean disminucionDetectada
        }
        class EvaluacionRiesgo {
            <<record>>
            +BigDecimal puntaje
            +NivelRiesgo nivel
            +long diasSinComprar
            +ClasificacionFrecuencia clasificacionFrecuencia
            +NivelConsumo nivelConsumo
            +boolean inactivo
            +boolean cambioDetectado
            +BigDecimal puntosRecencia
            +BigDecimal puntosFrecuencia
            +BigDecimal puntosValor
            +BigDecimal puntosCambio
        }
        class NivelRiesgo {
            <<enumeration>>
            BAJO
            MEDIO
            ALTO
        }
        class FactorRiesgo {
            <<enumeration>>
            RECENCIA
            FRECUENCIA
            VALOR
            CAMBIO_COMPORTAMIENTO
        }
        class CalculadoraRiesgoAbandono {
            +recalcularTodos() ResultadoRecalculo
            +recalcular(long clienteId) Optional~EvaluacionRiesgo~
        }
        class AgregadoRiesgoClientes {
            <<repository>>
            +porCliente() List~ComprasCliente~
            +deCliente(long clienteId) Optional~ComprasCliente~
            +conDisminucionDesde(OffsetDateTime desde) Set~Long~
        }
        class RiesgoAbandonoCliente {
            <<entity>>
            +Long clienteId
            +BigDecimal puntaje
            +NivelRiesgo nivel
            +OffsetDateTime enSeguimientoDesde
            +aplicar(EvaluacionRiesgo, OffsetDateTime) boolean
        }
        class RiesgoAbandonoClienteRepository {
            <<repository>>
            +findByNivelOrderByPuntajeDescClienteIdAsc(NivelRiesgo, Pageable) Page
        }
        class RiesgoAbandonoScheduler {
            +ejecutar()
        }
        class RecalculoRiesgoPorCompra {
            +recalcular(MetricasCliente metricas)
        }
        class ConsultaRiesgoAbandono {
            +buscar(long clienteId) RiesgoAbandono
        }
        class RiesgoAbandonoController {
            +buscar(long clienteId) RiesgoAbandono
        }
        class ConsultaClientesEnRiesgo {
            +buscar(NivelRiesgo, int pagina, int tamanio) Page
        }
        class ClientesEnRiesgoController {
            +buscar(String riesgoAbandono, int pagina, int tamanio) ClientesEnRiesgo
        }
        class CriterioInactividad
        class CriterioClasificacionFrecuencia
        class CriterioNivelConsumo
        class CriterioCambioComportamiento
        class PublicadorMetricas {
            +programar(String idEvento, String tipo, Compra compra)
        }
    }

    namespace servicio_perfil {
        class ConsultaFichaIntegral {
            +obtener(Long clienteId) FichaIntegralResponse
        }
        class RiesgoAbandonoDelCliente {
            +buscar(Long clienteId, List~ClienteOrigen~) RiesgoAbandonoResponse
        }
        class RiesgoAbandonoResponse {
            <<record>>
            +String nivel
            +BigDecimal puntaje
            +List~Factor~ factores
            +boolean sinDatos
        }
    }

    namespace frontend {
        class FichaIntegralComponent
        class RiesgoAbandonoComponent {
            +riesgo RiesgoAbandonoFicha
            +proporcion(FactorRiesgoAbandono) number
        }
    }

    CriterioRiesgoAbandono --> CriterioInactividad
    CriterioRiesgoAbandono --> CriterioClasificacionFrecuencia
    CriterioRiesgoAbandono --> CriterioNivelConsumo
    CriterioRiesgoAbandono ..> DatosRiesgo
    CriterioRiesgoAbandono ..> EvaluacionRiesgo
    EvaluacionRiesgo --> NivelRiesgo
    CalculadoraRiesgoAbandono --> AgregadoRiesgoClientes
    CalculadoraRiesgoAbandono --> CriterioRiesgoAbandono
    CalculadoraRiesgoAbandono --> CriterioCambioComportamiento : periodo actual
    CalculadoraRiesgoAbandono --> RiesgoAbandonoClienteRepository
    RiesgoAbandonoClienteRepository ..> RiesgoAbandonoCliente
    RiesgoAbandonoScheduler --> CalculadoraRiesgoAbandono
    PublicadorMetricas ..> RecalculoRiesgoPorCompra : evento MetricasCliente (después del commit)
    RecalculoRiesgoPorCompra --> CalculadoraRiesgoAbandono
    RiesgoAbandonoController --> ConsultaRiesgoAbandono
    ConsultaRiesgoAbandono --> RiesgoAbandonoClienteRepository
    ConsultaRiesgoAbandono ..> FactorRiesgo
    ClientesEnRiesgoController --> ConsultaClientesEnRiesgo
    ConsultaClientesEnRiesgo --> RiesgoAbandonoClienteRepository
    ConsultaFichaIntegral --> RiesgoAbandonoDelCliente
    RiesgoAbandonoDelCliente ..> RiesgoAbandonoController : HTTP interno
    RiesgoAbandonoDelCliente ..> RiesgoAbandonoResponse
    FichaIntegralComponent --> RiesgoAbandonoComponent
```

## Diagrama de secuencia: recálculo con cada compra y cada día

```mermaid
sequenceDiagram
    autonumber
    participant MV as Marketplace y Ventas
    participant Proc as ProcesadorCompras / ProcesadorAnulaciones
    participant Pub as PublicadorMetricas
    participant Rec as RecalculoRiesgoPorCompra
    participant Calc as CalculadoraRiesgoAbandono
    participant Agr as AgregadoRiesgoClientes
    participant Crit as CriterioRiesgoAbandono
    participant DB as riesgo_abandono_cliente
    participant Sch as RiesgoAbandonoScheduler
    participant Det as DetectorCambiosComportamiento

    MV->>Proc: compra.confirmada / compra.anulada
    Proc->>Pub: programar(idEvento, tipo, compra) [compra con perfil]
    Pub-->>Rec: MetricasCliente (después del commit)
    Rec->>Calc: recalcular(clienteId) en una transacción nueva
    Calc->>Agr: deCliente(clienteId)
    alt sin compras vigentes
        Calc->>DB: elimina su riesgo
    else con compras vigentes
        Calc->>Agr: conDisminucionDesde(inicio del periodo actual)
        Calc->>Crit: evaluar(compras, valor, última compra, caída)
        Crit-->>Calc: puntaje, nivel y puntos por factor
        Calc->>DB: guarda y, si entra en ALTO, fija enSeguimientoDesde
    end
    Note over Rec: Si falla, se registra en el log: la compra ya quedó guardada

    Det->>Det: 04:00 detecta cambios de comportamiento (SCRUM-34)
    Sch->>Calc: 04:30 recalcularTodos()
    Calc->>Agr: porCliente() y conDisminucionDesde(...)
    loop cada cliente con compras vigentes
        Calc->>Crit: evaluar(...)
        Calc->>DB: guarda (la recencia avanzó y una caída nueva suma puntos)
    end
    Calc->>DB: quita a quienes ya no tienen compras vigentes
```

## Diagrama de secuencia: consulta en la ficha integral

```mermaid
sequenceDiagram
    autonumber
    actor Usuario as Agente o Administrador
    participant Web as FichaIntegralComponent
    participant GW as API Gateway
    participant Perfil as ConsultaFichaIntegral
    participant Cliente as RiesgoAbandonoDelCliente
    participant Ctrl as RiesgoAbandonoController
    participant Cons as ConsultaRiesgoAbandono
    participant DB as riesgo_abandono_cliente

    Usuario->>Web: Abre la ficha del cliente
    Web->>GW: GET /api/perfil/clientes/{id}/ficha-integral
    GW->>Perfil: FICHA_INTEGRAL_CONSULTAR y auditoría
    Perfil->>Cliente: buscar(clienteId, identificadores de origen)
    alt sin identificadores de origen
        Cliente-->>Perfil: sinDatos
    else
        Cliente->>Ctrl: GET /api/comportamiento/clientes/{id}/riesgo-abandono
        Ctrl->>Cons: buscar(clienteId)
        Cons->>DB: riesgo del cliente
        Cons-->>Ctrl: nivel, puntaje, seguimiento y factores con su explicación
        Ctrl-->>Cliente: 200
        Cliente-->>Perfil: bloque riesgoAbandono (sinDatos si comportamiento no responde)
    end
    Perfil-->>Web: ficha integral con el bloque riesgoAbandono
    Web-->>Usuario: Nivel, puntaje sobre 100, «En seguimiento desde…» y una barra por factor
```

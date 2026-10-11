# Diagramas del nivel de consumo del cliente (SCRUM-575)

Diagrama de clases y diagrama de secuencia de la clasificación por nivel de consumo (SCRUM-27). La
lógica que implementan está en `docs/compra/nivel-consumo.md`. Están escritos en Mermaid: GitHub los
dibuja al abrir este archivo, y para adjuntarlos a la historia en Jira se exportan como imagen desde
<https://mermaid.live>.

## Diagrama de clases

Paquete `consumo` de `servicio-comportamiento`, sus puntos de enganche con la ingesta y los
indicadores, y los bloques que consumen el nivel en `servicio-perfil` y en el frontend.

```mermaid
classDiagram
    direction LR

    class NivelConsumo {
        <<enumeration>>
        BAJO
        MEDIO
        ALTO
    }
    class CriterioNivelConsumo {
        -BigDecimal umbralMedio
        -BigDecimal umbralAlto
        +clasificar(BigDecimal valor) Optional~NivelConsumo~
    }
    class ClasificadorConsumo {
        +reclasificar(String idClienteOrigen) Optional~NivelConsumo~
        +clasificarTodos() int
    }
    class ClasificacionConsumoScheduler {
        +ejecutar()
    }
    class ClienteNivelConsumo {
        -String idClienteOrigen
        -NivelConsumo nivel
        -BigDecimal valorAcumulado
        -OffsetDateTime actualizadoEn
        +actualizar(NivelConsumo nivel, BigDecimal valor)
    }
    class ClienteNivelConsumoRepository {
        <<interface>>
        +findByNivelOrderByValorAcumuladoDesc...(nivel, pageable) Page
    }
    class ConsultaClientesPorNivel {
        +buscar(NivelConsumo nivel, int pagina, int tamanio) Page
    }
    class ClientesPorNivelController {
        +buscar(String nivelConsumo, int pagina, int tamanio) ClientesPorNivel
    }
    class NivelConsumoCliente {
        <<record>>
        +NivelConsumo nivel
        +BigDecimal valorAcumulado
        +boolean sinNivel
        +de(ValorAcumulado valor, CriterioNivelConsumo criterio)$ NivelConsumoCliente
    }

    class AgregadoComprasCliente {
        +resumir(List~Identificador~) ResumenCompras
        +valorVigentePorCliente() List~ValorCliente~
    }
    class ValorAcumulado {
        <<record>>
        +de(ResumenCompras resumen)$ ValorAcumulado
    }
    class ConsultaIndicadores {
        +valorAcumulado(List~Identificador~) ValorAcumulado
        +nivelConsumo(List~Identificador~) NivelConsumoCliente
    }
    class IndicadoresController {
        +indicadores(String clienteId, List~String~ identificadores)
    }
    class ProcesadorCompras {
        +procesar(Long idEvento) Compra
    }
    class ProcesadorAnulaciones {
        +procesar(Long idEvento) Anulacion
    }

    class ConsultaFichaIntegral {
        +obtener(Long clienteId) FichaIntegralResponse
    }
    class NivelConsumoDelCliente {
        +buscar(Long clienteId, List~ClienteOrigen~ origenes) NivelConsumoResponse
    }
    class NivelConsumoComponent {
        +nivel: NivelConsumoFicha
        +nombreNivel(nivel) string
    }

    ClasificadorConsumo --> AgregadoComprasCliente : reutiliza el valor vigente
    ClasificadorConsumo --> CriterioNivelConsumo : aplica los umbrales
    ClasificadorConsumo --> ClienteNivelConsumoRepository : guarda o quita la fila
    ClasificacionConsumoScheduler --> ClasificadorConsumo : clasificarTodos()
    ProcesadorCompras --> ClasificadorConsumo : reclasifica al comprar
    ProcesadorAnulaciones --> ClasificadorConsumo : reclasifica al anular o devolver
    ClienteNivelConsumoRepository --> ClienteNivelConsumo
    ClienteNivelConsumo --> NivelConsumo
    CriterioNivelConsumo --> NivelConsumo
    ConsultaClientesPorNivel --> ClienteNivelConsumoRepository
    ClientesPorNivelController --> ConsultaClientesPorNivel
    ClientesPorNivelController --> CriterioNivelConsumo : expone los umbrales
    ConsultaIndicadores --> AgregadoComprasCliente
    ConsultaIndicadores --> ValorAcumulado
    ConsultaIndicadores --> NivelConsumoCliente
    NivelConsumoCliente --> CriterioNivelConsumo
    IndicadoresController --> ConsultaIndicadores
    NivelConsumoDelCliente ..> IndicadoresController : GET .../indicadores
    ConsultaFichaIntegral --> NivelConsumoDelCliente
    NivelConsumoComponent ..> ConsultaFichaIntegral : bloque nivelConsumo
```

## Diagrama de secuencia: una compra o una anulación cambia el nivel

Cubre los criterios de aceptación 1, 2 y 3. Todo ocurre en la transacción de la compra o de la
anulación: si esa transacción se revierte, la clasificación también.

```mermaid
sequenceDiagram
    autonumber
    participant MV as Marketplace y Ventas
    participant P as ProcesadorCompras / ProcesadorAnulaciones
    participant C as ClasificadorConsumo
    participant A as AgregadoComprasCliente
    participant K as CriterioNivelConsumo
    participant R as ClienteNivelConsumoRepository

    MV->>P: evento COMPRA_CONFIRMADA o COMPRA_ANULADA (RabbitMQ)
    P->>P: guarda la compra o descuenta lo revertido
    P->>C: reclasificar(idClienteOrigen)
    C->>A: resumir(idClienteOrigen)
    A-->>C: compras vigentes y monto vigente (valor acumulado)
    C->>K: clasificar(valor acumulado)
    alt valor mayor que cero
        K-->>C: BAJO, MEDIO o ALTO (en el umbral, el nivel superior)
        C->>R: guarda o actualiza la fila del cliente
    else sin compras vigentes
        K-->>C: sin nivel
        C->>R: quita la fila del cliente
    end
    C-->>P: nivel resultante
    P->>P: marca el evento como PROCESADO
```

## Diagrama de secuencia: el usuario ve el nivel en la ficha integral

Criterio de aceptación 4. Si `servicio-comportamiento` no responde, el bloque llega como "sin datos"
y el resto de la ficha carga igual.

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario autorizado
    participant W as FichaIntegralComponent
    participant G as API Gateway
    participant F as ConsultaFichaIntegral
    participant N as NivelConsumoDelCliente
    participant I as IndicadoresController
    participant X as ConsultaIndicadores

    U->>W: abre la ficha integral del cliente
    W->>G: GET /api/perfil/clientes/{id}/ficha-integral
    G->>G: exige FICHA_INTEGRAL_CONSULTAR y audita el acceso
    G->>F: reenvía la consulta
    F->>N: buscar(clienteId, identificadores de origen)
    N->>I: GET /api/comportamiento/clientes/{id}/indicadores?identificador=...
    I->>X: nivelConsumo(identificadores)
    X-->>I: nivel y valor acumulado del conjunto de identificadores
    I-->>N: indicadores (campo nivelConsumo)
    N-->>F: NivelConsumoResponse
    F-->>G: ficha con el bloque nivelConsumo
    G-->>W: respuesta JSON
    W-->>U: muestra Bajo, Medio, Alto o "Sin nivel"
```

## Diagrama de secuencia: clasificación programada y listado por nivel

```mermaid
sequenceDiagram
    autonumber
    participant S as ClasificacionConsumoScheduler
    participant C as ClasificadorConsumo
    participant A as AgregadoComprasCliente
    participant R as ClienteNivelConsumoRepository
    actor U as Usuario con INDICADORES_CONSULTAR
    participant L as ClientesPorNivelController

    S->>C: clasificarTodos() (cron diario)
    C->>A: valorVigentePorCliente()
    A-->>C: valor vigente de cada cliente con compras
    loop cada cliente
        C->>R: guarda o actualiza su nivel
    end
    C->>R: quita a los clientes que ya no tienen compras vigentes
    U->>L: GET /api/comportamiento/clientes?nivelConsumo=ALTO
    L->>R: clientes del nivel, del que más aporta al que menos
    R-->>L: página de clientes
    L-->>U: umbrales, clientes y total
```

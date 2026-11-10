# Diagramas del reporte comparativo entre periodos (SCRUM-612)

Diagrama de clases y de secuencia de SCRUM-62. Los indicadores, las reglas de comparación y la exportación están
en [reporte-comparativo.md](reporte-comparativo.md). Están escritos en Mermaid: GitHub los dibuja al abrir este
archivo, y para adjuntarlos a la historia en Jira se exportan como imagen.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class ReporteComparativoComponent {
        <<Angular, SCRUM-495>>
        +comparar()
        +exportar()
    }
    class validarPeriodos {
        <<funcion, SCRUM-494>>
        cada periodo con validarPeriodo
        y sin superponerse
    }
    class SecurityConfig {
        <<API Gateway>>
        GET comportamiento/reportes: REPORTES_CONSULTAR
    }
    class ReporteComparativoController {
        <<SCRUM-494>>
        +comparar(desdeA, hastaA, desdeB, hastaB, segmento)
        +exportarCsv(mismos parametros)
    }
    class PermisoReportes {
        <<interceptor, SCRUM-500>>
        comportamiento, comparativo y comparativo/csv
    }
    class PeriodosAComparar {
        <<SCRUM-40, compartido>>
        +de(desdeA, hastaA, desdeB, hastaB)
        sin superponerse
    }
    class ConsultaReporteComparativo {
        <<SCRUM-496>>
        +generar(periodos, segmento) ReporteComparativo
    }
    class ConsultaReporteComportamiento {
        <<SCRUM-58, SCRUM-497>>
        +generar(rango, segmento) ReporteComportamiento
    }
    class VariacionConsumo {
        <<SCRUM-40, compartido>>
        +calcular(a, b)
    }
    class ReporteComparativo {
        <<record>>
        +periodoA
        +periodoB
        +alcance
        +indicadores
    }
    class Indicador {
        <<record, SCRUM-498>>
        +codigo
        +periodoA
        +periodoB
        +variacionAbsoluta
        +variacionPorcentual
        +tendencia
    }
    class ExportacionComparativoCsv {
        <<SCRUM-499>>
        +exportar(reporte) String
    }

    ReporteComparativoComponent ..> validarPeriodos : antes de consultar
    ReporteComparativoComponent ..> SecurityConfig : GET
    SecurityConfig ..> ReporteComparativoController
    PermisoReportes ..> ReporteComparativoController : intercepta
    ReporteComparativoController --> PeriodosAComparar
    ReporteComparativoController --> ConsultaReporteComparativo
    ReporteComparativoController --> ExportacionComparativoCsv
    ConsultaReporteComparativo --> ConsultaReporteComportamiento : una vez por periodo
    ConsultaReporteComparativo --> ReporteComparativo : arma
    ReporteComparativo *-- Indicador
    Indicador ..> VariacionConsumo
    ExportacionComparativoCsv ..> ReporteComparativo : el mismo objeto
```

## Diagrama de secuencia

Comparar y exportar.

```mermaid
sequenceDiagram
    autonumber
    actor Analista as Analista Comercial
    participant UI as ReporteComparativoComponent
    participant GW as API Gateway
    participant CT as ReporteComparativoController
    participant SV as ConsultaReporteComparativo
    participant R58 as ConsultaReporteComportamiento
    participant CSV as ExportacionComparativoCsv

    Analista->>UI: periodos A y B, Comparar
    UI->>UI: validarPeriodos
    UI->>GW: GET reportes/comparativo
    GW->>CT: con X-Usuario y permisos
    CT->>CT: PeriodosAComparar.de(...)
    CT->>SV: generar(periodos, segmento)
    SV->>R58: generar(periodo A, segmento)
    R58-->>SV: indicadores de A
    SV->>R58: generar(periodo B, segmento)
    R58-->>SV: indicadores de B
    SV->>SV: VariacionConsumo de cada indicador
    SV-->>UI: ReporteComparativo
    UI-->>Analista: tabla A, B, variacion y tendencia

    Analista->>UI: Exportar CSV
    UI->>GW: GET reportes/comparativo/csv
    GW->>CT: exportarCsv
    CT->>SV: generar(periodos, segmento)
    SV-->>CT: ReporteComparativo
    CT->>CSV: exportar(reporte)
    CSV-->>CT: texto CSV con los mismos valores
    CT-->>UI: 200 text/csv, archivo adjunto
    UI-->>Analista: descarga
```

Los casos que no son el normal.

```mermaid
sequenceDiagram
    autonumber
    actor Analista as Analista Comercial
    actor Agente as Agente de Atencion
    participant UI as ReporteComparativoComponent
    participant GW as API Gateway
    participant CT as ReporteComparativoController

    rect rgb(255, 248, 235)
    note over Analista, CT: Criterio 2: periodos superpuestos
    Analista->>UI: A 01/08 a 10/09, B 01/09 a 30/09
    UI->>UI: validarPeriodos: se superponen
    UI-->>Analista: aviso, ninguna peticion
    end

    rect rgb(235, 245, 255)
    note over Analista, CT: Si la peticion llega igual al servidor
    Analista->>GW: GET comparativo con periodos superpuestos
    GW->>CT: comparar
    CT->>CT: PeriodosAComparar rechaza
    CT-->>Analista: 400 con el motivo
    end

    rect rgb(245, 240, 245)
    note over Agente, GW: El Agente no abre los reportes
    Agente->>GW: GET comparativo o comparativo/csv
    GW-->>Agente: 403
    end
```

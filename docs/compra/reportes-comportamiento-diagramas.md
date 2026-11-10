# Diagramas del reporte de comportamiento de compra (SCRUM-610)

Diagrama de clases y de secuencia de SCRUM-58. La consulta base, los cortes y los permisos están en
[reportes-comportamiento.md](reportes-comportamiento.md). Están escritos en Mermaid: GitHub los dibuja al
abrir este archivo, y para adjuntarlos a la historia en Jira se exportan como imagen.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class ReporteComportamientoComponent {
        <<Angular, SCRUM-58>>
        +desde
        +hasta
        +segmento
        +generar()
    }
    class validarPeriodo {
        <<funcion, criterio 2>>
        periodo incompleto, invertido o de mas de 5 anios
    }
    class SecurityConfig {
        <<API Gateway>>
        GET comportamiento/reportes: REPORTES_CONSULTAR
    }
    class ReporteComportamientoController {
        <<SCRUM-58>>
        +comportamiento(desde, hasta, segmento) ReporteComportamiento
    }
    class PermisoReportes {
        <<interceptor>>
        401 sin identidad, 403 sin REPORTES_CONSULTAR
    }
    class RangoPeriodo {
        <<record, SCRUM-469>>
        +desde
        +hasta
        +inicio(zona)
        +fin(zona)
    }
    class ConsultaReporteComportamiento {
        <<SCRUM-468>>
        +generar(rango, segmento) ReporteComportamiento
    }
    class ClientesDelSegmento {
        <<cliente HTTP, SCRUM-357>>
        +idsDe(segmento) List~Long~
    }
    class AgregadoConsumoPeriodo {
        <<SCRUM-466 y SCRUM-467>>
        +resumirPerfiles(ids, inicio, fin) ResumenCompras
        +resumirVinculadas(inicio, fin) ResumenCompras
        +clientesConCompras(ids, inicio, fin) long
        +clientesConComprasVinculadas(inicio, fin) long
    }
    class AgregadoConsumoCategorias {
        <<SCRUM-465>>
        +consumirPerfiles(ids, desde, hasta) ConsumoEnRango
        +consumirVinculadas(desde, hasta) ConsumoEnRango
    }
    class RankingCategorias {
        <<SCRUM-18, compartido>>
        +calcular(compradas, devueltas) List~CategoriaConsumida~
    }
    class ValorAcumulado {
        <<indicador, compartido>>
        +de(resumen)
    }
    class TicketPromedio {
        <<indicador, compartido>>
        +de(resumen)
    }
    class ReporteComportamiento {
        <<record>>
        +alcance
        +sinDatos
        +mensaje
        +frecuencia
        +valor
        +categorias
    }

    ReporteComportamientoComponent ..> validarPeriodo : antes de consultar
    ReporteComportamientoComponent ..> SecurityConfig : GET
    SecurityConfig ..> ReporteComportamientoController
    PermisoReportes ..> ReporteComportamientoController : intercepta
    ReporteComportamientoController --> RangoPeriodo
    ReporteComportamientoController --> ConsultaReporteComportamiento
    ConsultaReporteComportamiento --> ClientesDelSegmento
    ConsultaReporteComportamiento --> AgregadoConsumoPeriodo
    ConsultaReporteComportamiento --> AgregadoConsumoCategorias
    ConsultaReporteComportamiento ..> RankingCategorias
    ConsultaReporteComportamiento ..> ValorAcumulado
    ConsultaReporteComportamiento ..> TicketPromedio
    ConsultaReporteComportamiento --> ReporteComportamiento : arma
```

## Diagrama de secuencia

Reporte de un segmento en un periodo.

```mermaid
sequenceDiagram
    autonumber
    actor Analista as Analista Comercial
    participant UI as ReporteComportamientoComponent
    participant GW as API Gateway
    participant CT as ReporteComportamientoController
    participant SV as ConsultaReporteComportamiento
    participant SG as servicio-segmentacion
    participant AP as AgregadoConsumoPeriodo
    participant AC as AgregadoConsumoCategorias

    Analista->>UI: periodo y segmento, Generar
    UI->>UI: validarPeriodo(desde, hasta)
    UI->>GW: GET /api/comportamiento/reportes/comportamiento
    GW->>GW: REPORTES_CONSULTAR (403 si no lo tiene)
    GW->>CT: GET con X-Usuario y X-Usuario-Permisos
    CT->>CT: RangoPeriodo(desde, hasta)
    CT->>SV: generar(rango, "Alto valor")
    SV->>SG: GET segmentos/miembros?segmento=Alto valor
    SG-->>SV: perfiles 101, 102
    SV->>AP: resumirPerfiles(perfiles, inicio, fin)
    AP-->>SV: 3 compras, 1100.00
    SV->>AP: clientesConCompras(perfiles, inicio, fin)
    AP-->>SV: 2
    SV->>AC: consumirPerfiles(perfiles, inicio, fin)
    AC-->>SV: lineas compradas y devueltas
    SV->>SV: RankingCategorias, ValorAcumulado, TicketPromedio
    SV-->>CT: ReporteComportamiento
    CT-->>UI: 200
    UI-->>Analista: frecuencia, valor y categorias
```

Los casos que no son el normal.

```mermaid
sequenceDiagram
    autonumber
    actor Analista as Analista Comercial
    actor Agente as Agente de Atencion
    participant UI as ReporteComportamientoComponent
    participant GW as API Gateway
    participant SV as ConsultaReporteComportamiento
    participant SG as servicio-segmentacion

    rect rgb(255, 248, 235)
    note over Analista, SG: Criterio 2: periodo invertido, sin consultar al servidor
    Analista->>UI: desde 30/09, hasta 01/09, Generar
    UI->>UI: validarPeriodo: desde posterior a hasta
    UI-->>Analista: aviso, ninguna peticion
    end

    rect rgb(235, 245, 255)
    note over Analista, SG: Segmento sin clientes o periodo sin compras
    Analista->>UI: periodo valido, segmento Vacio
    UI->>GW: GET reportes/comportamiento
    GW->>SV: generar(rango, "Vacio")
    SV->>SG: miembros de Vacio
    SG-->>SV: ninguno
    SV-->>UI: 200, sinDatos true y el motivo
    UI-->>Analista: Sin datos para este filtro
    end

    rect rgb(245, 240, 245)
    note over Agente, GW: El Agente no abre los reportes
    Agente->>GW: GET reportes/comportamiento
    GW-->>Agente: 403 (sin REPORTES_CONSULTAR)
    end
```

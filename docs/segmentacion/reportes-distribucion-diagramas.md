# Diagramas del reporte de distribución de clientes (SCRUM-609)

Diagrama de clases y de secuencia de SCRUM-57. Los criterios, las fuentes de datos y los permisos
están en [reportes-distribucion.md](reportes-distribucion.md). Están escritos en Mermaid: GitHub los
dibuja al abrir este archivo, y para adjuntarlos a la historia en Jira se exportan como imagen desde
<https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class SecurityConfig {
        <<API Gateway, SCRUM-462>>
        GET reportes: REPORTES_CONSULTAR
        escritura y fidelizacion/reportes: denegado
    }
    class ReportesController {
        <<SCRUM-57>>
        +distribucion(segmento, nivelConsumo, usuario) ReporteDistribucion
    }
    class PermisoReportes {
        <<SCRUM-462>>
        REPORTES_CONSULTAR
        401 sin identidad, 403 sin permiso
    }
    class FiltroReporte {
        <<record>>
        +String segmento
        +NivelConsumo nivelConsumo
        +de(segmento, nivelConsumo) FiltroReporte
        +cubreTodos() boolean
    }
    class ServicioReporteDistribucion {
        <<SCRUM-57>>
        +generar(filtro, usuario) ReporteDistribucion
    }
    class DistribucionRepository {
        <<SCRUM-463 y SCRUM-460>>
        +total(filtro) long
        +porSegmento(filtro) List~Conteo~
        +porNivelConsumo(filtro) List~Conteo~
        +clientes(filtro) List~Long~
    }
    class NivelesFidelizacion {
        <<cliente HTTP, SCRUM-464>>
        +todos() ConteoNiveles
        +deClientes(clientes) ConteoNiveles
    }
    class AuditoriaReportes {
        <<auditoria compartida>>
        +registrar(usuario, filtro, total, disponible)
    }
    class ReporteDistribucion {
        <<record>>
        +OffsetDateTime generadoEn
        +long total
        +boolean sinDatos
        +List~Grupo~ segmentos
        +List~Grupo~ nivelesConsumo
        +List~Grupo~ nivelesFidelizacion
        +boolean fidelizacionDisponible
    }
    class Grupo {
        <<record>>
        +String valor
        +long clientes
        +BigDecimal porcentaje
        +de(valor, clientes, total) Grupo
    }
    class DistribucionNivelesController {
        <<servicio-fidelizacion, SCRUM-464>>
        +distribucion(solicitud) DistribucionNiveles
    }
    class SaldoPuntosRepository {
        <<servicio-fidelizacion>>
        +contarPorNivel() List~ConteoNivel~
        +contarPorNivel(clientes) List~ConteoNivel~
    }

    SecurityConfig ..> ReportesController : reenvia con X-Usuario
    PermisoReportes ..> ReportesController : intercepta
    ReportesController --> FiltroReporte : de()
    ReportesController --> ServicioReporteDistribucion
    ServicioReporteDistribucion --> DistribucionRepository
    ServicioReporteDistribucion --> NivelesFidelizacion
    ServicioReporteDistribucion --> AuditoriaReportes
    ServicioReporteDistribucion --> ReporteDistribucion : arma
    ReporteDistribucion *-- Grupo
    NivelesFidelizacion ..> DistribucionNivelesController : POST distribucion-niveles
    DistribucionNivelesController --> SaldoPuntosRepository
```

## Diagrama de secuencia

Reporte sin filtro: los tres cortes sobre toda la población.

```mermaid
sequenceDiagram
    autonumber
    actor Gerente as Gerente Comercial
    participant GW as API Gateway
    participant CT as ReportesController
    participant PR as PermisoReportes
    participant SV as ServicioReporteDistribucion
    participant RP as DistribucionRepository
    participant NF as NivelesFidelizacion
    participant FD as servicio-fidelizacion
    participant AU as AuditoriaReportes

    Gerente->>GW: GET /api/segmentacion/reportes/distribucion
    GW->>GW: REPORTES_CONSULTAR (403 si no lo tiene)
    GW->>CT: GET con X-Usuario y X-Usuario-Permisos
    CT->>PR: preHandle
    PR-->>CT: permiso correcto
    CT->>SV: generar(filtro vacio, "luis")
    SV->>RP: total(filtro)
    RP-->>SV: 8 clientes
    SV->>RP: porSegmento(filtro)
    RP-->>SV: Alto valor 5, Frecuente 2, sin segmento 1
    SV->>RP: porNivelConsumo(filtro)
    RP-->>SV: BAJO 1, MEDIO 3, ALTO 4
    SV->>NF: todos()
    NF->>FD: POST /api/fidelizacion/reportes/distribucion-niveles
    FD-->>NF: BRONCE 4, PLATA 2, ORO 1
    NF-->>SV: conteos disponibles
    SV->>SV: porcentajes y grupo sin nivel (8 - 7 = 1)
    SV->>AU: registrar(usuario, filtro, 8, true)
    AU-->>SV: registrado
    SV-->>CT: ReporteDistribucion
    CT-->>Gerente: 200 con los tres cortes
```

Reporte filtrado y los dos casos que no son el normal: sin datos y fidelización caída.

```mermaid
sequenceDiagram
    autonumber
    actor Gerente as Gerente Comercial
    actor Agente as Agente de Atencion
    participant GW as API Gateway
    participant SV as ServicioReporteDistribucion
    participant RP as DistribucionRepository
    participant NF as NivelesFidelizacion
    participant FD as servicio-fidelizacion
    participant AU as AuditoriaReportes

    rect rgb(235, 245, 255)
    note over Gerente, AU: Filtrado por segmento: los niveles se piden solo para esos clientes
    Gerente->>GW: GET ...?segmento=Alto valor
    GW->>SV: generar(filtro con segmento)
    SV->>RP: total(filtro)
    RP-->>SV: 2 clientes
    SV->>RP: clientes(filtro)
    RP-->>SV: [501, 502]
    SV->>NF: deClientes([501, 502])
    NF->>FD: POST con el grupo (lotes de 1000)
    FD-->>NF: ORO 2
    NF-->>SV: conteos disponibles
    SV->>AU: registrar(usuario, filtro, 2, true)
    SV-->>Gerente: 200 con ORO al 100 %
    end

    rect rgb(255, 248, 235)
    note over Gerente, AU: Criterio 2: el filtro no deja clientes
    Gerente->>GW: GET ...?segmento=Sin clientes
    GW->>SV: generar(filtro)
    SV->>RP: total(filtro)
    RP-->>SV: 0 clientes
    SV->>AU: registrar(usuario, filtro, 0, true)
    SV-->>Gerente: 200 con sinDatos true, sin error
    end

    rect rgb(255, 240, 240)
    note over Gerente, AU: servicio-fidelizacion no responde
    Gerente->>GW: GET /api/segmentacion/reportes/distribucion
    GW->>SV: generar(filtro vacio)
    SV->>NF: todos()
    NF->>FD: POST distribucion-niveles
    FD--xNF: sin respuesta
    NF-->>SV: no disponibles
    SV->>AU: registrar(usuario, filtro, 8, false)
    SV-->>Gerente: 200 con los otros dos cortes y fidelizacionDisponible false
    end

    rect rgb(245, 240, 245)
    note over Agente, GW: Criterio 3: el Agente de Atencion no abre el reporte
    Agente->>GW: GET /api/segmentacion/reportes/distribucion
    GW-->>Agente: 403 Acceso denegado (sin REPORTES_CONSULTAR)
    end
```

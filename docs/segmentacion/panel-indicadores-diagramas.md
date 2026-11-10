# Diagramas del panel de indicadores de clientes (SCRUM-611)

Diagrama de clases y de secuencia de SCRUM-59. Las métricas, la consolidación y los permisos están en
[panel-indicadores.md](panel-indicadores.md). Están escritos en Mermaid: GitHub los dibuja al abrir este
archivo, y para adjuntarlos a la historia en Jira se exportan como imagen.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class PanelIndicadoresComponent {
        <<Angular, SCRUM-472>>
        +panel
        +cargar()
    }
    class permisoGuard {
        <<Angular, SCRUM-477>>
        REPORTES_CONSULTAR
    }
    class SecurityConfig {
        <<API Gateway>>
        GET segmentacion/reportes: REPORTES_CONSULTAR
        consultas internas: denegadas
    }
    class ReportesController {
        <<servicio-segmentacion>>
        +panel(usuario) PanelIndicadores
    }
    class PermisoReportes {
        <<interceptor, SCRUM-462>>
        401 sin identidad, 403 sin permiso
    }
    class ServicioPanelIndicadores {
        <<SCRUM-471>>
        +consultar(usuario) PanelIndicadores
    }
    class DistribucionRepository {
        <<SCRUM-57, SCRUM-473>>
        +total(filtro) long
        +porSegmento(filtro) List~Conteo~
    }
    class IndicadoresExternos {
        <<clientes HTTP>>
        +clientesActivos() Optional
        +puntosAcumulados() Optional
    }
    class AuditoriaReportes {
        <<auditoria compartida>>
        +registrarPanel(usuario, clientes, activos, puntos)
    }
    class PanelIndicadores {
        <<record>>
        +clientes
        +sinDatos
        +activos
        +segmentos
        +puntos
    }
    class ClientesActivosController {
        <<servicio-comportamiento, SCRUM-475>>
        +clientesActivos() ClientesActivos
    }
    class CriterioInactividad {
        <<SCRUM-295, compartido>>
        +umbralDias()
        +fechaCorte()
    }
    class PuntosAcumuladosController {
        <<servicio-fidelizacion, SCRUM-476>>
        +puntos() PuntosAcumulados
    }

    PanelIndicadoresComponent ..> permisoGuard
    PanelIndicadoresComponent ..> SecurityConfig : GET panel
    SecurityConfig ..> ReportesController
    PermisoReportes ..> ReportesController : intercepta
    ReportesController --> ServicioPanelIndicadores
    ServicioPanelIndicadores --> DistribucionRepository
    ServicioPanelIndicadores --> IndicadoresExternos
    ServicioPanelIndicadores --> AuditoriaReportes
    ServicioPanelIndicadores --> PanelIndicadores : arma
    IndicadoresExternos ..> ClientesActivosController : GET clientes-activos
    IndicadoresExternos ..> PuntosAcumuladosController : GET puntos
    ClientesActivosController --> CriterioInactividad
```

## Diagrama de secuencia

Apertura del panel y actualización.

```mermaid
sequenceDiagram
    autonumber
    actor Gerente as Gerente Comercial
    participant UI as PanelIndicadoresComponent
    participant GW as API Gateway
    participant SV as ServicioPanelIndicadores
    participant RP as DistribucionRepository
    participant CO as servicio-comportamiento
    participant FD as servicio-fidelizacion
    participant AU as AuditoriaReportes

    Gerente->>UI: Panel de indicadores
    UI->>GW: GET /api/segmentacion/reportes/panel
    GW->>GW: REPORTES_CONSULTAR (403 si no lo tiene)
    GW->>SV: consultar("luis")
    SV->>RP: total y porSegmento de toda la cartera
    RP-->>SV: 3 clientes, Alto valor 2, sin segmento 1
    SV->>CO: GET reportes/clientes-activos
    CO-->>SV: 3 activos, umbral 90 dias
    SV->>FD: GET reportes/puntos
    FD-->>SV: 110 puntos, 2 clientes
    SV->>AU: registrarPanel(usuario, 3, true, true)
    SV-->>UI: PanelIndicadores
    UI-->>Gerente: tres tarjetas

    note over Gerente, FD: Criterio 2: llegan puntos nuevos
    Gerente->>UI: Actualizar
    UI->>GW: GET /api/segmentacion/reportes/panel
    GW->>SV: consultar("luis")
    SV->>FD: GET reportes/puntos
    FD-->>SV: 118 puntos, 3 clientes
    SV-->>UI: PanelIndicadores actualizado
    UI-->>Gerente: 118 puntos
```

Los casos que no son el normal.

```mermaid
sequenceDiagram
    autonumber
    actor Gerente as Gerente Comercial
    actor Agente as Agente de Atencion
    participant GW as API Gateway
    participant SV as ServicioPanelIndicadores
    participant CO as servicio-comportamiento
    participant FD as servicio-fidelizacion

    rect rgb(255, 240, 240)
    note over Gerente, FD: Un servicio no responde
    Gerente->>GW: GET panel
    GW->>SV: consultar
    SV->>CO: GET clientes-activos
    CO--xSV: sin respuesta
    SV->>FD: GET puntos
    FD-->>SV: 110 puntos
    SV-->>Gerente: 200, activos no disponible, lo demas igual
    end

    rect rgb(245, 240, 245)
    note over Agente, GW: Criterio 3: rol sin permiso
    Agente->>GW: GET panel
    GW-->>Agente: 403 Acceso denegado
    Agente->>GW: GET comportamiento/reportes/clientes-activos
    GW-->>Agente: 403, consulta interna
    end
```

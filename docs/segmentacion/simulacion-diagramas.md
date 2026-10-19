# SCRUM-577 · Diagramas de SCRUM-29

Corresponden a la [implementación de simulación](simulacion.md). Versiones exportadas: [clases SVG](simulacion-clases.svg) y [secuencia SVG](simulacion-secuencia.svg). Fuentes editables: [clases Mermaid](simulacion-clases.mmd) y [secuencia Mermaid](simulacion-secuencia.mmd).

## Clases

```mermaid
classDiagram
    class CriteriosComponent {
        formulario
        simulacion
        simulando
        simular()
        invalidarSimulacion()
        guardar()
    }
    class CriteriosService {
        simular(regla, criterioId)
        crear(regla)
        editar(id, regla)
    }
    class CriterioController {
        simular(solicitud, identidad)
        crear(regla, identidad)
        editar(id, regla, identidad)
    }
    class SolicitudSimulacion {
        Long criterioId
        SolicitudCriterio regla
    }
    class SimulacionSegmentacion {
        int maxClientes
        int maxSegundos
        simular(solicitud) ResultadoSimulacion
    }
    class ResultadoSimulacion {
        long clientesEvaluados
        long asignacionesSinMetricas
        long clientesQueCambiarian
        boolean reglaActiva
        calculadoEn
        metricasMasAntiguas
        metricasMasRecientes
        long duracionMs
    }
    class Distribucion {
        String clave
        String segmento
        boolean propuesta
        long actual
        long simulado
        long diferencia
    }
    class CriterioService {
        prepararSimulacion(id, regla) Criterio
        crear(regla, usuario)
        editar(id, regla, usuario)
    }
    class ReglaPrioridadSegmento {
        elegir(criterios, frecuencia, valor, categorias)
    }
    CriteriosComponent --> CriteriosService
    CriteriosService ..> CriterioController : HTTP via Gateway
    CriterioController --> SimulacionSegmentacion
    CriterioController --> CriterioService : guardado explicito
    CriterioController ..> SolicitudSimulacion
    SimulacionSegmentacion --> CriterioService : preparar sin escribir
    SimulacionSegmentacion --> CriterioRepository : criterios activos
    SimulacionSegmentacion --> JdbcTemplate : SELECT metricas y segmentos
    SimulacionSegmentacion --> ReglaPrioridadSegmento
    SimulacionSegmentacion ..> ResultadoSimulacion
    ResultadoSimulacion "1" *-- "0..*" Distribucion
    CriterioService --> CriterioRepository
    CriterioService --> ReglasCriterio : normalizacion y conflictos
    CriterioService --> AuditoriaCriterios : solo crear o editar
    ReglaPrioridadSegmento --> ReglasCriterio : cumple
    AsignacionSegmento --> ReglaPrioridadSegmento : misma prioridad
```

## Secuencia: simular, guardar y aplicar posteriormente

```mermaid
sequenceDiagram
    actor Admin as Administrador CRM
    participant UI as CriteriosComponent
    participant GW as Gateway
    participant API as CriterioController
    participant SIM as SimulacionSegmentacion
    participant CR as CriterioService
    participant DB as PostgreSQL
    participant PRI as ReglaPrioridadSegmento
    participant EVT as Eventos de metricas
    participant ASIG as AsignacionSegmento

    Admin->>UI: Crear o editar borrador, Simular impacto
    UI->>GW: POST /api/segmentacion/criterios/simular
    GW->>GW: Validar JWT, rol y permiso
    GW->>API: Solicitud con identidad vigente
    API->>SIM: simular(solicitud)
    Note over SIM,DB: Transaccion de solo lectura, REPEATABLE_READ
    SIM->>CR: prepararSimulacion(id, regla)
    CR->>DB: SELECT criterio y configuracion
    CR->>CR: Normalizar, validar version y conflictos
    alt Regla invalida o conflicto
        CR-->>API: 400 / 404 / 409
        API-->>UI: Mensaje del problema, sin resultado
    else Regla valida
        CR-->>SIM: Criterio hipotetico (sin INSERT ni UPDATE)
        SIM->>DB: SELECT criterios activos, metricas y asignaciones
        loop Cada cliente con metricas, dentro de limites
            SIM->>PRI: elegir(escenario, metricas)
            PRI-->>SIM: Un segmento o sin segmento
            SIM->>SIM: Acumular actual, simulado y diferencia
        end
        SIM->>DB: SELECT cantidad de asignaciones sin metricas
        alt Datos invalidos o limite excedido
            SIM-->>API: 409 / 503, sin resultado parcial
        else Calculo completo
            SIM-->>API: ResultadoSimulacion
            API-->>UI: 200 con distribucion y fechas
            UI-->>Admin: Tabla y resumen, opcion Guardar regla simulada
        end
    end
    Note over SIM,DB: Ninguna escritura de reglas, clientes, eventos o historial
    opt El administrador decide guardar
        Admin->>UI: Guardar regla simulada
        UI->>GW: POST criterios o PUT criterios/id
        GW->>API: Peticion autorizada
        API->>CR: crear o editar
        CR->>DB: Bloquear configuracion y revalidar
        alt Cambio concurrente incompatible
            CR-->>UI: 409, conservar formulario y mostrar problema
        else Regla valida al guardar
            CR->>DB: INSERT/UPDATE criterio y auditoria del criterio
            CR-->>UI: Criterio guardado y nueva version
        end
    end
    Note over EVT,DB: Aplicacion posterior por el flujo existente de SCRUM-26
    EVT->>ASIG: Nuevo evento de metricas del cliente
    ASIG->>DB: Verificar evento, leer reglas y guardar metricas
    ASIG->>PRI: elegir(criterios activos, metricas)
    PRI-->>ASIG: Segmento resultante
    ASIG->>DB: Guardar segmento si cambia, evento y auditoria
```

La simulación no llama a `AsignacionSegmento`. Ese participante figura únicamente para explicar la aplicación posterior del criterio guardado. Los errores de autenticación terminan en el Gateway antes de entrar al simulador. Un cambio de formulario cancela la suscripción HTTP y elimina la previsualización; una nueva simulación obtiene otra instantánea.

# Diagramas de los niveles de fidelización (SCRUM-597)

Diagrama de clases y diagramas de secuencia de SCRUM-49. Las reglas y umbrales están en
`docs/fidelizacion/niveles-fidelizacion.md`. Están escritos en Mermaid: GitHub los dibuja al abrir este
archivo, y para adjuntarlos a la historia en Jira se exportan como imagen desde <https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class NivelesController {
        +vigente() ConfiguracionNiveles
        +actualizar(SolicitudNiveles, HttpServletRequest) Resultado
        +historial(HttpServletRequest) List~ConfiguracionNiveles~
        +recalcular(HttpServletRequest) Reclasificacion
    }
    class SolicitudNiveles {
        <<record>>
        +Integer periodoMeses
        +List~UmbralNivel~ niveles
        +String motivo
        +Long version
    }
    class ServicioNiveles {
        +vigente() ConfiguracionNiveles
        +actualizar(SolicitudNiveles, String usuario) Resultado
        +historial() List~ConfiguracionNiveles~
        +recalcularTodos() int
    }
    class ReglasNiveles {
        <<utility>>
        +validar(List~UmbralNivel~) List~UmbralNivel~
    }
    class ConfiguracionesNiveles {
        <<repository>>
        +vigente() ConfiguracionNiveles
        +guardar(version, periodoMeses, umbrales, motivo, usuario)
        +historial() List~ConfiguracionNiveles~
    }
    class AsignacionNiveles {
        <<repository>>
        +recalcular(long clienteId) Nivel
        +recalcularTodos() int
    }
    class ConfiguracionNiveles {
        <<record>>
        +long version
        +int periodoMeses
        +List~UmbralNivel~ umbrales
        +String modificadoPor
        +OffsetDateTime vigenteDesde
        +nivelPara(long puntosPeriodo) Optional~Nivel~
    }
    class UmbralNivel {
        <<record>>
        +Nivel nivel
        +Long puntosMinimos
        +Long puntosMaximos
        +incluye(long puntos) boolean
    }
    class Nivel {
        <<enum>>
        BRONCE
        PLATA
        ORO
    }
    class RecalculoProgramadoNiveles {
        +recalcular()
    }
    class ProcesadorMovimientos {
        +procesar(long idEvento, String contenido) long
    }
    class SaldoPuntos {
        <<entity>>
        +Long clienteId
        +long saldo
        +String nivel
        +long puntosPeriodo
        +OffsetDateTime nivelActualizadoEn
    }
    class FidelizacionController {
        +obtener(Long clienteId) PuntosResponse
    }

    NivelesController ..> SolicitudNiveles
    NivelesController --> ServicioNiveles
    ServicioNiveles --> ReglasNiveles : superposiciones y huecos
    ServicioNiveles --> ConfiguracionesNiveles : versión nueva
    ServicioNiveles --> AsignacionNiveles : reclasifica a todos
    ConfiguracionesNiveles ..> ConfiguracionNiveles
    ConfiguracionNiveles *-- UmbralNivel
    UmbralNivel --> Nivel
    RecalculoProgramadoNiveles --> ServicioNiveles : cada noche
    ProcesadorMovimientos --> AsignacionNiveles : al cambiar el saldo
    AsignacionNiveles ..> SaldoPuntos : nivel y puntos del periodo
    FidelizacionController ..> SaldoPuntos : muestra el nivel
```

## Diagrama de secuencia: el nivel sigue al saldo

```mermaid
sequenceDiagram
    autonumber
    participant MQ as RabbitMQ (puntos.movimiento)
    participant P as ProcesadorMovimientos
    participant A as AsignacionNiveles
    participant DB as PostgreSQL (fidelizacion)
    participant W as Ficha integral / consulta de puntos

    MQ->>P: movimiento ACUMULACION
    P->>DB: BEGIN
    P->>DB: INSERT movimiento_puntos (puntos según la regla vigente)
    P->>DB: saldo = saldo + puntos
    P->>A: recalcular(clienteId)
    A->>DB: puntosPeriodo = Σ acumulaciones − reversas − redenciones de los últimos N meses
    A->>DB: nivel = umbral vigente que contiene puntosPeriodo
    A->>DB: UPDATE saldo_puntos (nivel, puntos_periodo, nivel_actualizado_en si cambió)
    A->>DB: si cambió, INSERT cambio_nivel con su origen (SCRUM-541)
    P->>DB: COMMIT
    W->>DB: GET /api/fidelizacion/clientes/{id}/puntos
    DB-->>W: saldo, nivel actual y puntos del periodo
```

## Diagrama de secuencia: el administrador cambia los umbrales

```mermaid
sequenceDiagram
    autonumber
    actor A as Administrador de CRM
    participant W as Aplicación Web
    participant G as API Gateway
    participant C as NivelesController
    participant S as ServicioNiveles
    participant R as ReglasNiveles
    participant DB as PostgreSQL (fidelizacion)

    A->>W: edita periodo y umbrales
    W->>W: marca superposiciones y huecos antes de enviar
    W->>G: PUT /api/fidelizacion/niveles
    G->>C: PUT + identidad (FIDELIZACION_CONFIGURAR)
    alt rol distinto de ADMINISTRADOR_CRM
        C-->>W: 403
    else administrador
        C->>S: actualizar(solicitud, usuario)
        S->>R: validar(umbrales)
        alt umbrales que se superponen o dejan huecos
            R-->>W: 400 indicando el conflicto y el mínimo correcto
        else válidos
            S->>DB: SELECT versión vigente
            alt la versión editada ya no es la vigente
                S-->>W: 409
            else
                S->>DB: INSERT configuracion_niveles + umbral_nivel (versión + 1)
                S->>DB: reclasificar a todos los clientes con saldo
                S-->>W: 200 configuración nueva y clientes reclasificados
            end
        end
    end
```

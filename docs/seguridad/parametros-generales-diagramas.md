# Diagramas de los parámetros generales del CRM · SCRUM-65 / SCRUM-615

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class ParametrosGeneralesComponent {
        +guardar(grupo)
        +descartar(servicio)
        -validar(lista) Map
    }
    class ParametrosGeneralesService {
        +listar(servicio) ParametroGeneral[]
        +guardar(servicio, cambios) ParametroGeneral[]
        +historial(servicio) CambioRegistrado[]
    }
    class ParametrosController {
        +listar(solicitud) List~Parametro~
        +actualizar(SolicitudParametros, solicitud) List~Parametro~
        +historial(solicitud) List~CambioRegistrado~
    }
    class AccesoParametros {
        <<utility>>
        +administrador(solicitud) String
    }
    class ParametrosGenerales {
        +inicializar()
        +valor(codigo) BigDecimal
        +entero(codigo) int
        +listar() List~Parametro~
        +actualizar(cambios, usuario) List~Modificacion~
    }
    class DefinicionParametro {
        <<record>>
        +String codigo
        +BigDecimal minimo
        +BigDecimal maximo
        +int decimales
        +BigDecimal valorInicial
    }
    class CambioParametro {
        <<record>>
        +String codigo
        +BigDecimal valor
        +Long version
    }
    class AuditoriaParametros {
        <<repository>>
        +registrar(Modificacion)
        +historial(codigos, limite) List~CambioRegistrado~
    }
    class ParametrosModificados {
        <<event>>
        +Set~String~ codigos
    }
    class CriterioInactividad {
        +umbralDias() int
        +fechaCorte() OffsetDateTime
    }
    class CriterioNivelConsumo {
        +umbralMedio() BigDecimal
        +umbralAlto() BigDecimal
        +clasificar(valor) Optional~NivelConsumo~
    }
    class VigenciaPuntos {
        +meses() int
        +configuracion() Configuracion
    }
    class RecalculoPorParametros {
        +alModificar(ParametrosModificados)
    }
    class ReclasificacionPorParametros {
        +alModificar(ParametrosModificados)
    }

    ParametrosGeneralesComponent --> ParametrosGeneralesService
    ParametrosGeneralesService ..> ParametrosController : HTTP por el API Gateway
    ParametrosController --> AccesoParametros : rol y permiso
    ParametrosController --> ParametrosGenerales
    ParametrosController --> AuditoriaParametros : historial
    ParametrosGenerales --> DefinicionParametro : rango y valor inicial
    ParametrosGenerales ..> CambioParametro
    ParametrosGenerales --> AuditoriaParametros : registra cada cambio
    ParametrosGenerales ..> ParametrosModificados : publica
    CriterioInactividad --> ParametrosGenerales : INACTIVIDAD_DIAS
    CriterioNivelConsumo --> ParametrosGenerales : umbrales
    VigenciaPuntos --> ParametrosGenerales : PUNTOS_VIGENCIA_MESES
    RecalculoPorParametros ..> ParametrosModificados : escucha
    ReclasificacionPorParametros ..> ParametrosModificados : escucha
```

`ParametrosGenerales`, `ParametrosController`, `AccesoParametros` y `AuditoriaParametros` existen en
servicio-comportamiento (inactividad y umbrales) y en servicio-fidelizacion (vigencia de puntos), cada uno con su
tabla `parametro_general`.

## Diagrama de secuencia: cambiar el periodo de inactividad

```mermaid
sequenceDiagram
    autonumber
    actor A as Administrador de CRM
    participant UI as Aplicación Web
    participant GW as API Gateway
    participant C as ParametrosController
    participant P as ParametrosGenerales
    participant DB as PostgreSQL
    participant R as RecalculoPorParametros

    A->>UI: Periodo de inactividad 120, Guardar cambios
    UI->>UI: validar rango 7 a 730 y decimales
    alt fuera de rango
        UI-->>A: Debe estar entre 7 y 730 días (no se envía)
    else válido
        UI->>GW: PUT /api/comportamiento/parametros {INACTIVIDAD_DIAS, 120, versión 3}
        alt sin ADMINISTRADOR_CRM o sin PARAMETROS_CONFIGURAR
            GW-->>UI: 403
        else con permiso
            GW->>C: PUT + X-Usuario, X-Usuario-Rol, X-Usuario-Permisos
            C->>C: AccesoParametros.administrador
            C->>P: actualizar(cambios, usuario)
            P->>P: validar rango, decimales y regla entre parámetros
            alt inválido
                P-->>UI: 400 con el motivo por parámetro
            else válido
                P->>DB: BEGIN y SELECT parametro_general FOR UPDATE
                alt la versión no coincide
                    P->>DB: ROLLBACK
                    P-->>UI: 409 y la pantalla recarga los valores
                else misma versión
                    P->>DB: UPDATE valor 120, versión 4, usuario, fecha
                    P->>DB: INSERT auditoria.evento PARAMETRO_GENERAL_MODIFICADO (90 a 120)
                    P->>DB: COMMIT
                    P->>R: ParametrosModificados(INACTIVIDAD_DIAS)
                    R->>DB: identificar clientes inactivos con 120 días
                    R->>DB: recalcular el riesgo de abandono
                    C-->>UI: 200 con los parámetros y sus versiones
                    UI-->>A: Se guardó, los cálculos siguientes usan el valor nuevo
                    UI->>GW: GET historial de comportamiento y fidelización
                    GW-->>UI: anterior, nuevo, usuario y fecha
                end
            end
        end
    end
```

## Diagrama de secuencia: la vigencia de los puntos rige en la consulta siguiente

```mermaid
sequenceDiagram
    autonumber
    actor A as Administrador de CRM
    participant UI as Aplicación Web
    participant GW as API Gateway
    participant C as ParametrosController (fidelización)
    participant P as ParametrosGenerales (fidelización)
    participant V as VigenciaPuntos
    participant S as SaldoVigente
    participant DB as PostgreSQL

    A->>UI: Vigencia de los puntos 24 meses, Guardar cambios
    UI->>GW: PUT /api/fidelizacion/parametros {PUNTOS_VIGENCIA_MESES, 24, versión}
    GW->>C: PUT con la identidad del Administrador
    C->>P: actualizar(cambios, usuario)
    P->>DB: UPDATE parametro_general e INSERT auditoria.evento en la misma transacción
    P->>P: descarta la copia en memoria
    C-->>UI: 200
    Note over S,DB: Una consulta de saldo posterior
    S->>V: meses()
    V->>P: entero(PUNTOS_VIGENCIA_MESES)
    P-->>V: 24
    S->>DB: última acumulación del cliente
    S-->>S: fecha de caducidad = última acumulación + 24 meses
    Note over V,DB: La caducidad diaria siguiente vence los saldos con la vigencia nueva
```

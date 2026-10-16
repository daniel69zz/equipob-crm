# Diagramas de la actualización del perfil de preferencias por eventos (SCRUM-589)

Diagrama de clases y diagrama de secuencia de SCRUM-42. La lógica está en
`docs/preferencias/actualizacion-por-eventos.md`. Están escritos en Mermaid: GitHub los dibuja al
abrir este archivo, y para adjuntarlos a la historia en Jira se exportan como imagen desde
<https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class ProcesadorCompras {
        +procesar(Long idEvento) Compra
    }
    class ProcesadorAnulaciones {
        +procesar(Long idEvento) Anulacion
    }
    class PublicadorMetricas {
        +programar(String idEvento, String tipo, Compra compra)
        +publicar(MetricasCliente metricas)
    }
    class MetricasCliente {
        <<record>>
        +String idEvento
        +String tipoEventoOrigen
        +long idCliente
    }
    class ReceptorPreferencias {
        +recibir(Message mensaje)
    }
    class PerfilesPreferencias {
        +construir(long clienteId) PerfilPreferencias
        +actualizarPorEvento(long clienteId, String idEvento, String tipo) Actualizacion
        +actualizaciones(long clienteId) List~Actualizacion~
    }
    class IdentificadoresPreferencias {
        +obtener(long clienteId) List~Identificador~
    }
    class AgregadoConsumoCategorias {
        +consumirTodo(List~Identificador~) ConsumoEnRango
    }
    class ConstructorPreferencias {
        <<utility>>
        +construir(ConsumoEnRango)$ List~CategoriaPreferida~
    }
    class PerfilPreferenciasRepository {
        <<repository>>
        +bloquear(long clienteId)
        +eventoAplicado(String idEvento) boolean
        +obtener(long clienteId) PerfilPreferencias
        +guardar(clienteId, fecha, categorias, origen, idEvento) PerfilPreferencias
        +registrarActualizacion(idEvento, tipo, anterior, nuevo)
        +actualizaciones(long clienteId) List~Actualizacion~
    }
    class PerfilPreferencias {
        <<record>>
        +long version
        +OffsetDateTime construidoEn
        +String origen
        +String idEventoOrigen
        +List~CategoriaPreferida~ categorias
    }
    class PreferenciasController {
        +construir(long clienteId)
        +consultar(long clienteId)
        +actualizaciones(long clienteId)
    }

    ProcesadorCompras --> PublicadorMetricas
    ProcesadorAnulaciones --> PublicadorMetricas
    PublicadorMetricas ..> MetricasCliente : publica
    PublicadorMetricas ..> ReceptorPreferencias : RabbitMQ metricas.actualizadas
    ReceptorPreferencias --> PerfilesPreferencias
    PreferenciasController --> PerfilesPreferencias
    PerfilesPreferencias --> IdentificadoresPreferencias
    PerfilesPreferencias --> AgregadoConsumoCategorias
    PerfilesPreferencias ..> ConstructorPreferencias : recalcula
    PerfilesPreferencias --> PerfilPreferenciasRepository
    PerfilPreferenciasRepository ..> PerfilPreferencias : guarda
```

## Diagrama de secuencia: compra confirmada, reentrega y anulación

```mermaid
sequenceDiagram
    autonumber
    participant MV as Marketplace y Ventas
    participant MQ as RabbitMQ
    participant I as Ingesta (servicio-comportamiento)
    participant R as ReceptorPreferencias
    participant P as PerfilesPreferencias
    participant PF as servicio-perfil
    participant DB as PostgreSQL (comportamiento)

    MV->>MQ: compra.confirmada o compra.anulada en ventas.eventos
    MQ->>I: entrega el evento
    I->>DB: bitácora, idempotencia, compra o anulación
    alt evento reentregado
        I->>DB: DESCARTADO, no publica métricas
    else evento nuevo con cliente vinculado
        I->>DB: COMMIT
        I->>MQ: metricas.actualizadas (idEvento, tipo, idCliente)
        MQ->>R: entrega en crm.comportamiento.preferencias
        alt mensaje inválido
            R-->>MQ: rechazo, va a respaldo
        else mensaje válido
            R->>P: actualizarPorEvento(idCliente, idEvento, tipo)
            P->>PF: GET /api/perfil/clientes/{id} (identificadores)
            alt Perfil no responde
                P-->>R: 503, se reintenta y luego va a respaldo
            else
                P->>DB: BEGIN REPEATABLE READ y bloqueo por cliente
                alt idEvento ya aplicado
                    P-->>R: DUPLICADO, el perfil no cambia
                else
                    P->>DB: historial vigente (compras, líneas, devoluciones)
                    P->>P: validar y recalcular el ranking
                    P->>DB: perfil con nueva versión, fecha, origen e idEvento
                    P->>DB: INSERT actualizacion_preferencias (versiones y categorías antes/después)
                    P->>DB: COMMIT
                    P-->>R: ACTUALIZADO
                end
            end
        end
    end
```

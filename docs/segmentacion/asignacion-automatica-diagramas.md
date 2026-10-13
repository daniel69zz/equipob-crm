# Diagramas de la asignación automática de segmento (SCRUM-574)

Diagrama de clases y diagrama de secuencia de la integración asíncrona de SCRUM-26. La lógica está en
`docs/segmentacion/asignacion-automatica.md`. Están escritos en Mermaid: GitHub los dibuja al abrir
este archivo, y para adjuntarlos a la historia en Jira se exportan como imagen desde
<https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    namespace servicio_comportamiento {
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
        class CalculoMetricasCliente {
            +calcular(String idEvento, String tipo, long idCliente) MetricasCliente
        }
        class MetricasCliente {
            <<record>>
            +String idEvento
            +String tipoEventoOrigen
            +long idCliente
            +List~String~ identificadoresOrigen
            +long frecuencia
            +BigDecimal valor
            +List~String~ categorias
            +OffsetDateTime calculadasEn
        }
    }

    namespace servicio_segmentacion {
        class ReceptorMetricas {
            +recibir(Message mensaje)
        }
        class AsignacionSegmento {
            +aplicar(MetricasCliente metricas) Resultado
        }
        class ReglaPrioridadSegmento {
            <<utility>>
            +elegir(List~Criterio~, long frecuencia, BigDecimal valor, List~String~ categorias)$ Optional~Criterio~
        }
        class RegistroSegmentos {
            <<repository>>
            +bloquearCliente(long clienteId)
            +eventoYaProcesado(String idEvento) boolean
            +registrarEvento(String idEvento, long clienteId, String resultado)
            +metricasAplicadasEn(long clienteId) Optional~OffsetDateTime~
            +guardarMetricas(MetricasCliente)
            +segmento(long clienteId) Optional~SegmentoActual~
            +asignar(long clienteId, Criterio, String idEvento)
            +retirar(long clienteId)
        }
        class AuditoriaSegmentos {
            <<repository>>
            +registrar(Operacion, long clienteId, SegmentoActual anterior, Criterio nuevo, MetricasCliente)
        }
        class CriterioRepository {
            <<repository>>
            +activos() List~Criterio~
        }
        class ReinyectorRespaldo {
            +reinyectarUno(String usuario) Optional~Reinyeccion~
        }
        class RespaldoController {
            +reinyectar() Reinyeccion
        }
    }

    ProcesadorCompras --> PublicadorMetricas
    ProcesadorAnulaciones --> PublicadorMetricas
    PublicadorMetricas --> CalculoMetricasCliente
    CalculoMetricasCliente ..> MetricasCliente : produce
    PublicadorMetricas ..> ReceptorMetricas : RabbitMQ crm.comportamiento.eventos / metricas.actualizadas
    ReceptorMetricas --> AsignacionSegmento
    AsignacionSegmento --> RegistroSegmentos
    AsignacionSegmento --> CriterioRepository
    AsignacionSegmento ..> ReglaPrioridadSegmento : elige
    AsignacionSegmento --> AuditoriaSegmentos
    RespaldoController --> ReinyectorRespaldo
```

## Diagrama de secuencia: compra, reentrega y anulación

```mermaid
sequenceDiagram
    autonumber
    participant MV as Marketplace y Ventas
    participant MQ as RabbitMQ
    participant C as servicio-comportamiento
    participant DBC as PostgreSQL (comportamiento)
    participant S as servicio-segmentacion
    participant DBS as PostgreSQL (segmentacion, auditoria)

    MV->>MQ: publica compra.confirmada o compra.anulada en ventas.eventos
    MQ->>C: entrega el evento (cola de compras o de anulaciones)
    C->>DBC: bitácora, clave de idempotencia, compra o anulación
    alt evento reentregado (clave ya registrada)
        C->>DBC: evento DESCARTADO
        Note over C: no publica métricas
    else evento nuevo con perfil vinculado
        C->>DBC: frecuencia, valor y categorías vigentes del perfil
        C->>DBC: COMMIT
        C->>MQ: metricas.actualizadas en crm.comportamiento.eventos (idEvento, métricas, calculadasEn)
        MQ->>S: entrega en crm.segmentacion.metricas
        alt mensaje inválido
            S-->>MQ: rechazo sin reintento
            MQ->>MQ: pasa a crm.segmentacion.metricas.respaldo
        else mensaje válido
            S->>DBS: bloqueo por cliente
            alt idEvento ya aplicado
                Note over S: se descarta sin recalcular
            else calculado antes que la foto vigente
                S->>DBS: evento DESACTUALIZADO, segmento sin cambios
            else
                S->>DBS: guarda la foto de métricas
                S->>DBS: criterios activos
                S->>S: ReglaPrioridadSegmento: valor, frecuencia, categoría
                alt el segmento cambia
                    S->>DBS: asigna, cambia o retira segmento_cliente
                    S->>DBS: INSERT auditoria.evento (SEGMENTO_ASIGNADO / CAMBIADO / RETIRADO)
                end
                S->>DBS: evento APLICADO
                S->>DBS: COMMIT
            end
        end
    end
```

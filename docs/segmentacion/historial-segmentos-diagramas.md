# Diagramas del historial de segmentos — SCRUM-578

## Clases

```mermaid
classDiagram
  class AsignacionSegmento {
    +aplicar(metricas) Resultado
  }
  class RegistroSegmentos {
    +bloquearCliente(clienteId)
    +eventoYaProcesado(idEvento) boolean
    +asignar(clienteId, criterio, idEvento)
    +retirar(clienteId)
  }
  class Criterio {
    +Long id
    +long version
    +String nombre
    +Indicador indicador
    +Condicion condicion
    +BigDecimal minimo
    +BigDecimal maximo
    +String categoria
  }
  class AuditoriaSegmentos {
    +registrar(operacion, cliente, anterior, nuevo, metricas, evaluados)
  }
  class HistorialSegmentos {
    +registrar(auditoriaId)
    +consultar(cliente, pagina, tamanio) Pagina
  }
  class Cambio {
    +long clienteId
    +OffsetDateTime fecha
    +String segmentoAnterior
    +String segmentoNuevo
    +JsonNode regla
    +JsonNode criteriosEvaluados
    +JsonNode metricas
    +String idEvento
    +boolean reglaCompleta
  }
  class HistorialSegmentosController {
    +consultar(clienteId, pagina, tamanio) Pagina
  }
  class HistorialSegmentosComponent {
    +buscar(pagina)
    +reintentar()
  }
  class HistorialSegmentosService {
    +consultar(clienteId, pagina)
  }
  AsignacionSegmento --> RegistroSegmentos
  AsignacionSegmento --> Criterio : evalúa instantánea
  AsignacionSegmento --> AuditoriaSegmentos : cambio efectivo
  AuditoriaSegmentos --> HistorialSegmentos : misma transacción
  HistorialSegmentos --> Cambio : consulta inmutable
  HistorialSegmentosController --> HistorialSegmentos
  HistorialSegmentosComponent --> HistorialSegmentosService
  HistorialSegmentosService --> HistorialSegmentosController : GET mediante Gateway
```

## Registro y consulta

```mermaid
sequenceDiagram
  autonumber
  participant B as RabbitMQ / PubSub
  participant A as AsignacionSegmento
  participant D as PostgreSQL
  participant U as AuditoriaSegmentos
  participant H as HistorialSegmentos
  participant F as Angular
  participant G as API Gateway
  participant C as HistorialSegmentosController
  B->>A: Métricas del cliente
  A->>D: BEGIN y bloqueo por cliente
  A->>D: Verificar evento y fecha de métricas
  alt Evento duplicado o desactualizado
    A-->>B: Sin cambio ni historial nuevo
  else Evento vigente
    A->>D: Guardar métricas y evaluar criterios activos
    alt Cambia criterio o nombre, se asigna o se retira
      A->>D: Guardar / retirar segmento
      A->>U: Registrar antes/después, regla y métricas
      U->>D: INSERT auditoria.evento RETURNING id
      U->>H: registrar(auditoriaId)
      H->>D: INSERT historial_segmento
    else Recálculo sin cambio
      Note over A,D: No insertar auditoría de segmento ni historial
    end
    A->>D: Marcar evento procesado y COMMIT
    Note over A,D: Cualquier fallo revierte todas las escrituras
    A-->>B: Evento procesado
  end
  F->>G: GET historial con JWT y página
  G->>G: Validar sesión y SEGMENTOS_CONSULTAR
  G->>C: Identidad interna validada
  C->>H: consultar(cliente, pagina, tamanio)
  H->>D: COUNT + SELECT en misma instantánea
  D-->>H: Filas por fecha e id descendentes
  H-->>C: Página con instantáneas originales
  C-->>G: 200 con contenido y totales
  G->>D: Auditoría de acceso al cliente
  G-->>F: Página de historial
  F->>F: Mostrar cambios en modo solo lectura
```

La tabla tiene restricciones únicas y triggers que rechazan UPDATE, DELETE y TRUNCATE. El endpoint solo implementa GET; el gateway deniega los métodos de escritura.

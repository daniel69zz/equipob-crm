# Diagramas de la sugerencia de categorías afines (SCRUM-591)

Diagrama de clases y diagrama de secuencia de SCRUM-44. El criterio está en
`docs/preferencias/sugerencia-categorias.md`. Están escritos en Mermaid: GitHub los dibuja al abrir
este archivo, y para adjuntarlos a la historia en Jira se exportan como imagen desde
<https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class SugerenciasController {
        +consultar(long clienteId) SugerenciasCliente
    }
    class SugerenciasCategorias {
        +sugerir(long clienteId) SugerenciasCliente
    }
    class IdentificadoresPreferencias {
        +obtener(long clienteId) List~Identificador~
    }
    class MiembrosSegmento {
        +obtener(long clienteId) Segmento
    }
    class Segmento {
        <<record>>
        +String nombre
        +List~Long~ companeros
        +asignado() boolean
    }
    class PerfilPreferenciasRepository {
        <<repository>>
        +obtener(long clienteId) PerfilPreferencias
    }
    class PerfilPreferencias {
        <<record>>
        +boolean construido
        +List~CategoriaPreferida~ categorias
    }
    class AgregadoSegmento {
        <<repository>>
        +consumir(List~Long~ clientes) ConsumoSegmento
    }
    class ConsumoSegmento {
        <<record>>
        +int clientesConPreferencias
        +List~CategoriaSegmento~ categorias
    }
    class MotorSugerencias {
        <<utility>>
        +sugerir(categoriasDelCliente, ConsumoSegmento, ReglaSugerencias)$ List~Sugerencia~
    }
    class ReglaSugerencias {
        <<configuración>>
        +int minimoClientes
        +BigDecimal afinidadMinima
        +int maximo
    }
    class SugerenciasCliente {
        <<record>>
        +String segmento
        +int clientesDelSegmento
        +List~Sugerencia~ sugerencias
        +boolean sinSugerencias
        +Motivo motivo
    }
    class Sugerencia {
        <<record>>
        +int posicion
        +String categoria
        +BigDecimal afinidad
        +int clientesQueCompran
        +BigDecimal montoSegmento
    }
    class SegmentacionController {
        <<servicio-segmentacion>>
        +miembros(Long clienteId) MiembrosSegmentoResponse
    }

    SugerenciasController --> SugerenciasCategorias
    SugerenciasCategorias --> IdentificadoresPreferencias
    SugerenciasCategorias --> MiembrosSegmento
    SugerenciasCategorias --> PerfilPreferenciasRepository
    SugerenciasCategorias --> AgregadoSegmento
    SugerenciasCategorias ..> MotorSugerencias : aplica
    MotorSugerencias ..> ReglaSugerencias : umbrales
    MiembrosSegmento ..> SegmentacionController : HTTP
    MiembrosSegmento ..> Segmento : devuelve
    PerfilPreferenciasRepository ..> PerfilPreferencias
    AgregadoSegmento ..> ConsumoSegmento
    SugerenciasCategorias ..> SugerenciasCliente : responde
    SugerenciasCliente *-- Sugerencia
```

## Diagrama de secuencia: generación y consulta de sugerencias

```mermaid
sequenceDiagram
    autonumber
    actor G as Gerente Comercial
    participant W as Frontend (pestaña Sugerencias)
    participant GW as API Gateway
    participant S as SugerenciasCategorias (servicio-comportamiento)
    participant PF as servicio-perfil
    participant SG as servicio-segmentacion
    participant DB as PostgreSQL (comportamiento)

    G->>W: Abre «Sugerencias» del cliente
    W->>GW: GET /api/comportamiento/clientes/{id}/sugerencias-categorias
    alt sin SUGERENCIAS_CONSULTAR
        GW-->>W: 403 Acceso denegado
    else permiso válido
        GW->>S: reenvía con la identidad del usuario
        S->>PF: GET /api/perfil/clientes/{id}
        alt cliente inexistente, consolidado o Perfil caído
            S-->>W: 404, 409 o 503
        else cliente válido
            S->>SG: GET /api/segmentacion/clientes/{id}/segmento/miembros
            alt Segmentación caída o respuesta incompleta
                S-->>W: 503
            else
                S->>DB: BEGIN REPEATABLE READ (solo lectura)
                S->>DB: perfil de preferencias del cliente
                alt sin perfil, sin categorías o sin segmento
                    S-->>W: sinSugerencias + motivo
                else
                    S->>DB: consumo por categoría de los compañeros de segmento
                    alt ningún compañero con preferencias
                        S-->>W: sinSugerencias, SIN_DATOS_SEGMENTO
                    else
                        S->>S: excluir lo que ya compra, afinidad, umbrales, orden y máximo
                        S-->>W: categorías sugeridas (o SIN_CATEGORIAS_NUEVAS)
                    end
                end
            end
        end
        W-->>G: categorías sugeridas con su afinidad, o el motivo
    end
```

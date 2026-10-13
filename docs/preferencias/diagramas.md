# Diagramas de SCRUM-41

Modelo y reglas: [perfil de preferencias](perfil-preferencias.md).

## Clases

Fuente editable: [clases.mmd](clases.mmd).

```mermaid
classDiagram
    class PreferenciasController {
        +construir(clienteId) PerfilPreferencias
        +consultar(clienteId) PerfilPreferencias
    }
    class PerfilesPreferencias {
        +construir(clienteId) PerfilPreferencias
        +consultar(clienteId) PerfilPreferencias
    }
    class IdentificadoresPreferencias {
        +obtener(clienteId) List~Identificador~
    }
    class AgregadoConsumoCategorias {
        +consumirTodo(identificadores) ConsumoEnRango
    }
    class ConstructorPreferencias {
        +construir(consumo) List~CategoriaPreferida~
    }
    class RankingCategorias {
        +calcular(compradas, devueltas) List~CategoriaConsumida~
    }
    class PerfilPreferenciasRepository {
        +bloquear(clienteId)
        +guardar(clienteId, fecha, categorias) PerfilPreferencias
        +obtener(clienteId) PerfilPreferencias
    }
    class PerfilPreferencias {
        +long clienteId
        +boolean construido
        +boolean sinPreferencias
        +OffsetDateTime construidoEn
        +long version
        +String criterio
    }
    class CategoriaPreferida {
        +int posicion
        +String categoria
        +long compras
        +long unidades
        +BigDecimal monto
    }
    PreferenciasController --> PerfilesPreferencias
    PerfilesPreferencias --> IdentificadoresPreferencias
    PerfilesPreferencias --> AgregadoConsumoCategorias
    PerfilesPreferencias --> ConstructorPreferencias
    ConstructorPreferencias --> RankingCategorias : reutiliza SCRUM-18
    PerfilesPreferencias --> PerfilPreferenciasRepository
    PerfilPreferenciasRepository --> PerfilPreferencias
    PerfilPreferencias "1" *-- "0..*" CategoriaPreferida
```

## Secuencia de construcción

Fuente editable: [secuencia.mmd](secuencia.mmd).

```mermaid
sequenceDiagram
    actor Admin as Administrador
    participant G as API Gateway
    participant C as PreferenciasController
    participant S as PerfilesPreferencias
    participant P as Servicio Perfil
    participant A as AgregadoConsumoCategorias
    participant R as ConstructorPreferencias / RankingCategorias
    participant D as PostgreSQL
    Admin->>G: POST /clientes/{id}/preferencias/construir
    G->>G: Verificar JWT, rol y CLIENTE_EDITAR
    G->>C: Petición autorizada
    C->>S: construir(id)
    S->>P: Obtener cliente e identificadores oficiales
    alt Perfil inexistente, consolidado o no disponible
        P-->>S: Error o identidad inválida
        S-->>G: 404 / 409 / 503, sin escribir preferencias
    else Identidad válida
        P-->>S: Identificadores del cliente
        loop Máximo 3 intentos ante conflicto de concurrencia
            S->>D: BEGIN REPEATABLE READ y bloqueo por cliente
            S->>A: consumirTodo(identificadores)
            A->>D: Leer compras vigentes, categorías y devoluciones
            D-->>A: Filas de la misma instantánea
            A-->>S: ConsumoEnRango
            S->>R: Validar consumo, ordenar y excluir sin categoría
            alt Datos inconsistentes
                R-->>S: Conflicto de datos
                S->>D: ROLLBACK
            else Datos válidos
                R-->>S: Categorías ordenadas o lista vacía
                S->>D: Guardar cabecera y reemplazar categorías
                alt Escritura correcta
                    S->>D: COMMIT
                else Error de escritura
                    S->>D: ROLLBACK, conservar versión anterior
                end
            end
        end
        S-->>C: Perfil guardado o error
        C-->>G: Respuesta HTTP
    end
    G->>G: Auditar resultado del acceso
    G-->>Admin: Resultado
```

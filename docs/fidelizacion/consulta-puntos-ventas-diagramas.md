# Diagramas de la consulta de puntos por Marketplace y Ventas (SCRUM-601)

Diagrama de clases y diagrama de secuencia de SCRUM-525 (RIO-CRM-04). El contrato, la regla de caducidad y los
errores están en `docs/contratos-eventos/RIO-CRM-04-consulta-puntos.md`, y la especificación OpenAPI en
`docs/contratos-eventos/RIO-CRM-04-consulta-puntos.openapi.yaml`. Están escritos en Mermaid: GitHub los dibuja
al abrir este archivo, y para adjuntarlos a la historia en Jira se exportan como imagen desde
<https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class AuthController {
        <<API Gateway>>
        +tokenDeServicio(TokenServicioRequest) TokenServicioResponse
    }
    class ClientesServicio {
        <<API Gateway · SCRUM-545>>
        +ROL = SISTEMA
        +autenticar(cliente, secreto) Optional~SesionToken~
        +sesionVigente(cliente) Optional~SesionToken~
    }
    class JwtService {
        <<API Gateway>>
        +emitirServicio(SesionToken) TokenEmitido
        +verificar(token) Optional~TokenVerificado~
    }
    class TokenVerificado {
        <<record>>
        +SesionToken sesion
        +boolean servicio
    }
    class JwtAuthenticationFilter {
        <<API Gateway>>
        servicio → ClientesServicio
        usuario → UsuarioService
    }
    class SecurityConfig {
        <<API Gateway>>
        GET /api/fidelizacion/ventas/clientes/*/puntos
        rol SISTEMA y PUNTOS_CONSULTAR_VENTAS
    }
    class AuditoriaAccesoFilter {
        <<API Gateway · SCRUM-546>>
        CLIENTE_CONSULTADO / ACCESO_DENEGADO
    }
    class ConsultaPuntosVentasController {
        +puntos(idClienteVentas, solicitud) PuntosParaVentas
    }
    class IdentidadGateway {
        +sistemaVentas(solicitud) String
    }
    class ConsultaPuntosVentas {
        <<service · SCRUM-544>>
        -int vigenciaMeses
        +consultar(idClienteVentas) PuntosParaVentas
    }
    class PuntosParaVentas {
        <<record>>
        +String idClienteVentas
        +Long clienteId
        +boolean tieneCuenta
        +long saldoDisponible
        +String nivel
        +OffsetDateTime fechaCaducidad
        +long puntosCaducados
        +int vigenciaMeses
        +OffsetDateTime consultadoEn
    }
    class ClientesPerfil {
        +buscar(idClienteOrigen) Optional~Long~
    }
    class SaldoPuntosRepository {
        <<repository>>
        +findById(clienteId) Optional~SaldoPuntos~
    }
    class RegistroPuntos {
        <<repository>>
        +ultimaAcumulacion(clienteId) Optional~OffsetDateTime~
    }
    class AuditoriaConsultaSaldo {
        <<repository · SCRUM-546>>
        +registrarConsultaVentas(cliente, puntos)
    }

    AuthController --> ClientesServicio : cliente y secreto
    AuthController --> JwtService : token tipo=SERVICIO
    JwtAuthenticationFilter --> JwtService
    JwtAuthenticationFilter --> ClientesServicio : sigue habilitado
    JwtService ..> TokenVerificado
    SecurityConfig ..> ConsultaPuntosVentasController : X-Usuario, X-Usuario-Rol, X-Usuario-Permisos
    AuditoriaAccesoFilter ..> SecurityConfig : registra el resultado
    ConsultaPuntosVentasController --> IdentidadGateway : 401 / 403
    ConsultaPuntosVentasController --> ConsultaPuntosVentas
    ConsultaPuntosVentasController --> AuditoriaConsultaSaldo : PUNTOS_CONSULTADOS_POR_VENTAS
    ConsultaPuntosVentas --> ClientesPerfil : idClienteVentas → cliente del CRM
    ConsultaPuntosVentas --> SaldoPuntosRepository : saldo y nivel
    ConsultaPuntosVentas --> RegistroPuntos : vigencia desde la última acumulación
    ConsultaPuntosVentas ..> PuntosParaVentas
```

## Diagrama de secuencia: Ventas consulta los puntos antes de una redención

```mermaid
sequenceDiagram
    autonumber
    participant V as Marketplace y Ventas
    participant G as API Gateway
    participant F as servicio-fidelizacion
    participant P as servicio-perfil
    participant DB as PostgreSQL

    V->>G: POST /api/auth/servicio/token (marketplace-ventas, secreto)
    alt cliente o secreto incorrectos, o cliente deshabilitado
        G-->>V: 401
    else correctos
        G-->>V: 200 token JWT de servicio (tipo=SERVICIO, rol SISTEMA, PUNTOS_CONSULTAR_VENTAS)
    end

    V->>G: GET /api/fidelizacion/ventas/clientes/CLI-5521/puntos (Bearer token de servicio)
    G->>G: verifica el JWT y que el cliente de servicio siga habilitado
    alt sin token, inválido o vencido
        G->>DB: auditoria.evento ACCESO_DENEGADO (anonimo, CLI-5521)
        G-->>V: 401 (la consulta no llega a fidelización)
    else token de un usuario interno
        G->>DB: auditoria.evento ACCESO_DENEGADO
        G-->>V: 403
    else token de servicio con el permiso
        G->>F: GET .../CLI-5521/puntos con X-Usuario marketplace-ventas y X-Usuario-Rol SISTEMA
        F->>F: IdentidadGateway.sistemaVentas (si no, 401 / 403)
        F->>P: GET /api/perfil/clientes?idClienteOrigen=CLI-5521
        alt Perfil no responde
            P--xF: error
            F-->>G: 503 (no aplicar la redención)
        else respuesta de Perfil
            P-->>F: cliente 42, o ninguno
            alt sin perfil o sin cuenta de puntos
                F->>F: saldo 0, tieneCuenta false
            else con cuenta
                F->>DB: BEGIN READ ONLY
                F->>DB: saldo y nivel (saldo_puntos)
                F->>DB: última acumulación con puntos (movimiento_puntos)
                F->>DB: COMMIT
                F->>F: fechaCaducidad = última acumulación + vigenciaMeses
                opt la vigencia ya venció
                    F->>F: saldoDisponible 0 y puntosCaducados = saldo
                end
            end
            F->>DB: auditoria.evento PUNTOS_CONSULTADOS_POR_VENTAS (sin el saldo)
            F-->>G: 200 PuntosParaVentas
        end
        G->>DB: auditoria.evento CLIENTE_CONSULTADO (marketplace-ventas, CLI-5521)
        G-->>V: respuesta
    end
    V->>V: aplica la redención solo si saldoDisponible alcanza
    Note over V,F: confirmada la venta, Ventas notifica la redención por RIO-CRM-03 (SCRUM-48)
```

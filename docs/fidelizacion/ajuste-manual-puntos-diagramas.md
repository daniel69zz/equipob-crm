# Diagramas del ajuste manual de puntos (SCRUM-603)

Diagrama de clases y de secuencia de SCRUM-51. El modelo de auditoría y las reglas están en
`docs/fidelizacion/ajuste-manual-puntos.md`. Están escritos en Mermaid: GitHub los dibuja al abrir este archivo, y
para adjuntarlos a la historia en Jira se exportan como imagen desde <https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class AjustePuntosComponent {
        <<Angular, SCRUM-426>>
        +revisar()
        +confirmar()
    }
    class HistorialAjustesComponent {
        <<Angular, SCRUM-427>>
        +clienteId
    }
    class AjustesPuntosService {
        <<Angular>>
        +ajustar(clienteId, solicitud)
        +historial(clienteId)
    }
    class SecurityConfig {
        <<API Gateway, SCRUM-424>>
        POST ajustes: ADMINISTRADOR_CRM y FIDELIZACION_CONFIGURAR
    }
    class AjustesController {
        <<SCRUM-423>>
        +ajustar(clienteId, solicitud, req) Ajuste
        +historial(clienteId, req) List~Ajuste~
    }
    class SolicitudAjuste {
        <<record, SCRUM-429>>
        +UUID idSolicitud
        +Long puntos
        +String motivo
        +String referencia
        +Long saldoActual
    }
    class ServicioAjustes {
        <<SCRUM-422>>
        +ajustar(clienteId, solicitud, usuario, rol) Ajuste
        +historial(clienteId) List~Ajuste~
    }
    class RegistroAjustes {
        <<repository>>
        +yaAplicado(idSolicitud) boolean
        +bloquearSaldo(clienteId) long
        +registrarMovimiento(id, clienteId, puntos, saldo)
        +registrar(...) Ajuste
        +actualizarSaldo(clienteId, saldo)
        +deCliente(clienteId) List~Ajuste~
    }
    class AuditoriaAjustes {
        <<repository, SCRUM-425>>
        +registrar(ajuste)
    }
    class Ajuste {
        <<record, SCRUM-421>>
        +long id
        +String idTransaccion
        +long puntos
        +long saldoAnterior
        +long saldoResultante
        +String motivo
        +String usuario
        +OffsetDateTime registradoEn
    }
    class ProcesoCaducidad {
        <<SCRUM-50>>
        +caducarSiVencio(clienteId, momento)
    }
    class AsignacionNiveles {
        <<repository>>
        +recalcular(cliente, idTransaccion, tipo) Nivel
    }

    AjustePuntosComponent --> AjustesPuntosService
    HistorialAjustesComponent --> AjustesPuntosService
    AjustesPuntosService ..> SecurityConfig : HTTP con el token
    SecurityConfig ..> AjustesController : X-Usuario, X-Usuario-Rol, X-Usuario-Permisos
    AjustesController ..> SolicitudAjuste
    AjustesController --> ServicioAjustes
    ServicioAjustes --> ProcesoCaducidad : primero la caducidad pendiente
    ServicioAjustes --> RegistroAjustes : saldo, movimiento AJUSTE y ajuste_puntos
    ServicioAjustes --> AsignacionNiveles : nivel con el ajuste como origen
    ServicioAjustes --> AuditoriaAjustes : PUNTOS_AJUSTADOS
    RegistroAjustes ..> Ajuste
```

## Diagrama de secuencia: revisar y confirmar un ajuste

```mermaid
sequenceDiagram
    autonumber
    actor A as Administrador de CRM
    participant W as crm-web (AjustePuntosComponent)
    participant G as API Gateway
    participant F as servicio-fidelizacion
    participant DB as PostgreSQL

    A->>W: abre Puntos del cliente 42 y pulsa "Ajustar saldo"
    W->>G: GET /api/fidelizacion/clientes/42/saldo
    G->>F: con la identidad del usuario
    F-->>W: saldo disponible 35
    A->>W: Sumar 20 puntos, motivo y referencia
    W->>W: valida las reglas (SCRUM-429) y muestra 35 → 55
    A->>W: Confirmar ajuste
    W->>G: POST /api/fidelizacion/clientes/42/ajustes {idSolicitud, puntos: 20, motivo, saldoActual: 35}
    alt sin sesión, otro rol o sin FIDELIZACION_CONFIGURAR
        G->>DB: auditoria.evento ACCESO_DENEGADO
        G-->>W: 401 o 403 (criterio 3)
    else Administrador de CRM
        G->>DB: auditoria.evento CLIENTE_MODIFICADO
        G->>F: POST con X-Usuario admin, X-Usuario-Rol ADMINISTRADOR_CRM
        F->>F: rol y permiso otra vez, y validación del cuerpo
        F->>DB: BEGIN
        F->>DB: ¿idSolicitud ya aplicado?
        alt ya aplicado
            F-->>W: 409 Este ajuste ya se aplicó
        else nuevo
            F->>DB: caducarSiVencio(42, ahora) (SCRUM-50)
            F->>DB: SELECT saldo FOR UPDATE (35)
            alt el saldo no es el revisado
                F->>DB: ROLLBACK
                F-->>W: 409 el saldo cambió: vuelva a revisar
            else saldo + puntos < 0
                F->>DB: ROLLBACK
                F-->>W: 400 El ajuste dejaría el saldo en ... (criterio 2)
            else válido
                F->>DB: INSERT movimiento AJUSTE-{idSolicitud} (+20, saldo 55)
                F->>DB: INSERT ajuste_puntos con motivo, responsable y fecha (criterio 1)
                F->>DB: UPDATE saldo = 55
                F->>DB: AsignacionNiveles.recalcular con el ajuste como origen
                F->>DB: auditoria.evento PUNTOS_AJUSTADOS con el saldo antes y después
                F->>DB: COMMIT
                F-->>W: 201 ajuste aplicado
                W-->>A: "El saldo pasó de 35 a 55 pts"
            end
        end
    end

    A->>W: abre la ficha integral del cliente
    W->>G: GET /api/fidelizacion/clientes/42/ajustes
    G->>F: con PUNTOS_CONSULTAR
    F->>DB: ajustes del cliente, del más reciente al más antiguo
    F-->>W: historial de ajustes (criterio 4)
```

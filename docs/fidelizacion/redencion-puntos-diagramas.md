# Diagramas de la redención de puntos (SCRUM-598)

Diagrama de clases y diagrama de secuencia de la integración asíncrona de SCRUM-48. La lógica está en
`docs/fidelizacion/redencion-puntos.md` y el contrato en
`docs/contratos-eventos/RIO-CRM-03-movimiento-puntos.md`. Están escritos en Mermaid: GitHub los dibuja
al abrir este archivo, y para adjuntarlos a la historia en Jira se exportan como imagen desde
<https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class ReceptorMovimientos {
        +recibir(Message mensaje)
    }
    class IngestaMovimientos {
        +recibir(String contenido) Estado
        +reprocesar(long id) EventoMovimiento
    }
    class LectorMovimientos {
        +leer(String contenido) EventoMovimientoPuntos
        -puntosRedimidos(BigDecimal puntos)
    }
    class Movimiento {
        <<record>>
        +String idTransaccion
        +String idCliente
        +String tipoMovimiento
        +BigDecimal valorConsumido
        +BigDecimal puntos
    }
    class ProcesadorMovimientos {
        +procesar(long idEvento, String contenido) long
        -redimir(cliente, movimiento, idEvento) long
        -reevaluarNivel(cliente, saldo)
    }
    class ClientesPerfil {
        +buscar(String idClienteOrigen) Optional~Long~
    }
    class RegistroPuntos {
        <<repository>>
        +reservar(idTransaccion, tipo, cliente, ...)
        +debitar(cliente, idTransaccion, puntos) long
        +asignarNivel(cliente, nivel)
        +movimientos(cliente) List~DetalleMovimiento~
    }
    class ReevaluacionNivel {
        <<interface>>
        +nivelPara(long saldo) Optional~String~
    }
    class SaldoInsuficienteException {
        +solicitados() long
        +disponibles() long
    }
    class BitacoraMovimientos {
        <<repository>>
        +registrar(contenido, cabecera) long
        +marcar(long id, Estado, String causa)
        +listar(estado, tipo, limite)
    }
    class ConfirmacionRedenciones {
        <<repository>>
        +confirmar(String idTransaccion) Optional~Confirmacion~
    }
    class MovimientosController {
        +movimientos(clienteId)
        +redencion(idTransaccion)
        +eventos(estado, tipo)
        +reprocesar(id)
    }

    ReceptorMovimientos --> IngestaMovimientos
    IngestaMovimientos --> BitacoraMovimientos
    IngestaMovimientos --> ProcesadorMovimientos
    ProcesadorMovimientos --> LectorMovimientos
    LectorMovimientos ..> Movimiento : produce
    ProcesadorMovimientos --> ClientesPerfil : vincula el cliente
    ProcesadorMovimientos --> RegistroPuntos : reserva, descuenta y anota
    ProcesadorMovimientos --> ReevaluacionNivel : nivel del nuevo saldo (SCRUM-49)
    RegistroPuntos ..> SaldoInsuficienteException : lanza
    IngestaMovimientos ..> SaldoInsuficienteException : FALLIDO con la causa
    MovimientosController --> RegistroPuntos
    MovimientosController --> ConfirmacionRedenciones
    MovimientosController --> BitacoraMovimientos
    MovimientosController --> IngestaMovimientos
```

## Diagrama de secuencia: redención, saldo insuficiente, reentrega y confirmación

```mermaid
sequenceDiagram
    autonumber
    participant MV as Marketplace y Ventas
    participant MQ as RabbitMQ (ventas.eventos)
    participant F as servicio-fidelizacion
    participant P as servicio-perfil
    participant DB as PostgreSQL (fidelizacion)

    MV->>MQ: RIO-CRM-03 puntos.movimiento (REDENCION, idTransaccion, puntos)
    MQ->>F: entrega en crm.fidelizacion.movimientos
    F->>DB: bitácora: RECIBIDO con el contenido original
    F->>DB: BEGIN
    F->>F: leer y validar (puntos entero > 0)
    alt evento inválido
        F->>DB: ROLLBACK
        F->>DB: bitácora: FALLIDO con el campo y la regla
    else válido
        F->>P: GET /api/perfil/clientes?idClienteOrigen=...
        P-->>F: cliente del CRM
        F->>DB: INSERT movimiento_puntos (idTransaccion + REDENCION)
        alt transacción ya descontada (evento reentregado)
            F->>DB: ROLLBACK
            F->>DB: bitácora: DESCARTADO (no se descuenta dos veces)
        else nueva
            F->>DB: UPDATE saldo = saldo - puntos WHERE saldo >= puntos
            alt saldo insuficiente (no se actualizó ninguna fila)
                F->>DB: ROLLBACK
                F->>DB: bitácora: FALLIDO con solicitados y disponibles (saldo sin cambios)
            else saldo suficiente
                F->>DB: saldo resultante en el movimiento
                F->>F: ReevaluacionNivel.nivelPara(saldo)
                F->>DB: nivel del cliente y bitácora PROCESADO
                F->>DB: COMMIT
            end
        end
    end

    MV->>F: GET /api/fidelizacion/redenciones/{idTransaccion} (por el API Gateway)
    F->>DB: movimiento REDENCION o último intento en la bitácora
    F-->>MV: APLICADA (saldo y nivel) · FALLIDA (causa) · EN_PROCESO
```

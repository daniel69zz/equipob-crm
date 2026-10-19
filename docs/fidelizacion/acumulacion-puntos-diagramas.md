# Diagramas de la acumulación de puntos (SCRUM-593)

Diagrama de clases y diagrama de secuencia de la integración asíncrona de SCRUM-45. La lógica está en
`docs/fidelizacion/acumulacion-puntos.md` y el contrato en
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
        +cabecera(String contenido) Cabecera
        +leer(String contenido) EventoMovimientoPuntos
    }
    class EventoMovimientoPuntos {
        <<record>>
        +String idEvento
        +String tipoEvento
        +Movimiento movimiento
    }
    class ProcesadorMovimientos {
        +procesar(long idEvento, String contenido) long
    }
    class ClientesPerfil {
        +buscar(String idClienteOrigen) Optional~Long~
    }
    class ReglaCalculoPuntos {
        -BigDecimal bolivianosPorPunto = 10
        +puntos(BigDecimal valorConsumido) long
    }
    class RegistroPuntos {
        <<repository>>
        +reservar(idTransaccion, tipo, cliente, ...)
        +acreditar(long clienteId, long puntos) long
        +movimientos(long clienteId) List~DetalleMovimiento~
    }
    class BitacoraMovimientos {
        <<repository>>
        +registrar(contenido, cabecera) long
        +marcarProcesado(long id)
        +marcar(long id, Estado, String causa)
    }
    class MovimientosController {
        +movimientos(clienteId)
        +eventos(estado)
        +reprocesar(id)
    }
    class TipoMovimiento {
        <<enum>>
        ACUMULACION
        REDENCION
        REVERSA
    }

    ReceptorMovimientos --> IngestaMovimientos
    IngestaMovimientos --> BitacoraMovimientos
    IngestaMovimientos --> ProcesadorMovimientos
    ProcesadorMovimientos --> LectorMovimientos
    LectorMovimientos ..> EventoMovimientoPuntos : produce
    ProcesadorMovimientos --> ClientesPerfil : vincula el cliente
    ProcesadorMovimientos --> ReglaCalculoPuntos : calcula
    ProcesadorMovimientos --> RegistroPuntos : detalle y saldo
    ProcesadorMovimientos ..> TipoMovimiento
    MovimientosController --> RegistroPuntos
    MovimientosController --> IngestaMovimientos
```

## Diagrama de secuencia: acumulación, reentrega y evento inválido

```mermaid
sequenceDiagram
    autonumber
    participant MV as Marketplace y Ventas
    participant MQ as RabbitMQ (ventas.eventos)
    participant F as servicio-fidelizacion
    participant P as servicio-perfil
    participant DB as PostgreSQL (fidelizacion)

    MV->>MQ: RIO-CRM-03 puntos.movimiento (ACUMULACION, idTransaccion, valorConsumido)
    MQ->>F: entrega en crm.fidelizacion.movimientos
    F->>DB: bitácora: RECIBIDO con el contenido original
    F->>DB: BEGIN
    F->>F: leer y validar el evento
    alt evento inválido o tipo aún no soportado
        F->>DB: ROLLBACK
        F->>DB: bitácora: FALLIDO con la causa (saldo sin cambios)
    else válido
        F->>P: GET /api/perfil/clientes?idClienteOrigen=...
        alt sin perfil o Perfil no responde
            F->>DB: ROLLBACK
            F->>DB: bitácora: FALLIDO, disponible para reproceso
        else cliente del CRM
            F->>F: puntos = parte entera(valorConsumido / 10)
            F->>DB: INSERT movimiento_puntos (idTransaccion + tipo)
            alt transacción ya acreditada (evento reentregado)
                F->>DB: ROLLBACK
                F->>DB: bitácora: DESCARTADO (no se acredita dos veces)
            else nueva
                F->>DB: saldo = saldo + puntos (atómico)
                F->>DB: detalle con el saldo resultante y bitácora PROCESADO
                F->>DB: COMMIT
            end
        end
    end
```

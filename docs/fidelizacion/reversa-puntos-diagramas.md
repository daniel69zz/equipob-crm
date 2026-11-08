# Diagramas de la reversa de puntos (SCRUM-599)

Diagrama de clases y diagrama de secuencia de la integración asíncrona de SCRUM-524. La regla y los
casos están en `docs/fidelizacion/reversa-puntos.md`, y los contratos en
`docs/contratos-eventos/RIO-CRM-03-movimiento-puntos.md` y `docs/contratos-eventos/RIO-CRM-05-anulacion-compra.md`.
Están escritos en Mermaid: GitHub los dibuja al abrir este archivo, y para adjuntarlos a la historia en
Jira se exportan como imagen desde <https://mermaid.live>.

## Diagrama de clases

```mermaid
classDiagram
    direction LR

    class ReceptorMovimientos {
        +recibir(Message mensaje)
        +recibirAnulacion(Message mensaje)
    }
    class IngestaMovimientos {
        +recibir(String contenido) Estado
        +reprocesar(long id) EventoMovimiento
    }
    class LectorMovimientos {
        +cabecera(String contenido) Cabecera
        +leer(String contenido) EventoMovimientoPuntos
        -leerAnulacion(String contenido) EventoMovimientoPuntos
    }
    class EventoAnulacionCompra {
        <<record>>
        +String idAnulacion
        +String idCompra
        +String tipo
        +BigDecimal montoRevertido
    }
    class Movimiento {
        <<record>>
        +String idTransaccion
        +String tipoMovimiento
        +BigDecimal valorConsumido
        +String idTransaccionOriginal
    }
    class ProcesadorMovimientos {
        +procesar(long idEvento, String contenido) long
        -revertir(cliente, movimiento, idEvento) long
    }
    class ReglaReversa {
        <<SCRUM-538>>
        +puntosARevertir(original, montoAntes, puntosAntes, id, monto) long
    }
    class Original {
        <<record>>
        +String idTransaccion
        +BigDecimal valorConsumido
        +long puntos
        +BigDecimal bolivianosPorPunto
        +BigDecimal montoMinimo
    }
    class RegistroPuntos {
        <<repository>>
        +acumulacionParaRevertir(idTransaccion) Optional~Original~
        +reservarReversa(idTransaccion, cliente, monto, original, ...)
        +revertidoAntes(idOriginal, idReversa) Revertido
        +descontarHastaCero(cliente, puntos) Descuento
        +completarReversa(idTransaccion, puntos, noRecuperados)
        +movimientos(cliente) List~DetalleMovimiento~
    }
    class AsignacionNiveles {
        <<repository>>
        +recalcular(cliente, idTransaccion, tipo) Nivel
        +cambios(cliente) List~CambioNivel~
    }
    class FidelizacionController {
        +obtener(clienteId)
        +cambiosDeNivel(clienteId)
    }
    class MovimientosController {
        +movimientos(clienteId)
        +eventos(estado, tipo)
        +reprocesar(id)
    }

    ReceptorMovimientos --> IngestaMovimientos : RIO-CRM-03 y RIO-CRM-05
    IngestaMovimientos --> ProcesadorMovimientos
    ProcesadorMovimientos --> LectorMovimientos
    LectorMovimientos ..> EventoAnulacionCompra : lee RIO-CRM-05
    LectorMovimientos ..> Movimiento : REVERSA
    ProcesadorMovimientos --> RegistroPuntos : bloquea la compra, reserva y descuenta
    ProcesadorMovimientos --> ReglaReversa : puntos a revertir
    ReglaReversa ..> Original
    RegistroPuntos ..> Original : regla de la compra
    ProcesadorMovimientos --> AsignacionNiveles : reevalúa y registra el cambio (SCRUM-541)
    FidelizacionController --> AsignacionNiveles
    MovimientosController --> RegistroPuntos
    MovimientosController --> IngestaMovimientos
```

## Diagrama de secuencia: devolución parcial, reentrega por el otro evento, saldo insuficiente y nivel

```mermaid
sequenceDiagram
    autonumber
    participant MV as Marketplace y Ventas
    participant MQ as RabbitMQ (ventas.eventos)
    participant F as servicio-fidelizacion
    participant P as servicio-perfil
    participant DB as PostgreSQL (fidelizacion)

    MV->>MQ: RIO-CRM-05 compra.anulada (V-100234-D1, compra V-100234, Bs 50.50)
    MQ->>F: entrega en crm.fidelizacion.anulaciones (Comportamiento recibe su copia aparte)
    F->>DB: bitácora: RECIBIDO con el contenido original
    F->>DB: BEGIN
    F->>F: leer como REVERSA y validar
    alt evento inválido
        F->>DB: ROLLBACK y bitácora FALLIDO con el campo y la regla
    else válido
        F->>P: GET /api/perfil/clientes?idClienteOrigen=CLI-5521
        P-->>F: cliente del CRM
        F->>DB: SELECT acumulación V-100234 FOR UPDATE
        alt sin acumulación o de otro cliente
            F->>DB: ROLLBACK y bitácora FALLIDO (reprocesable)
        else acumulación encontrada
            F->>DB: INSERT reversa V-100234-D1 con referencia a V-100234 y su regla
            alt reversa ya aplicada (reentrega o el mismo evento por RIO-CRM-03)
                F->>DB: ROLLBACK y bitácora DESCARTADO (no se descuenta otra vez)
            else nueva
                F->>DB: montos y puntos ya revertidos de V-100234
                F->>F: ReglaReversa: vigente 300.00 → 30 puntos, revertir 35 − 30 = 5
                alt supera lo que sigue vigente
                    F->>DB: ROLLBACK y bitácora FALLIDO con lo vigente
                else dentro de lo vigente
                    F->>DB: SELECT saldo FOR UPDATE y UPDATE saldo − min(saldo, 5)
                    Note over F,DB: si ya redimió los puntos, el saldo queda en 0 y la diferencia en puntosNoRecuperados
                    F->>DB: puntos, no recuperados y saldo resultante de la reversa
                    F->>DB: AsignacionNiveles: puntos del periodo (reversa con la fecha de la compra) y nivel
                    F->>DB: si cambió el nivel, cambio_nivel con la reversa como origen
                    F->>DB: bitácora PROCESADO y COMMIT
                end
            end
        end
    end

    MV->>MQ: RIO-CRM-03 REVERSA V-100234-D1 (la misma devolución)
    MQ->>F: entrega en crm.fidelizacion.movimientos
    F->>DB: INSERT reversa V-100234-D1 choca con la clave
    F->>DB: bitácora DESCARTADO
```

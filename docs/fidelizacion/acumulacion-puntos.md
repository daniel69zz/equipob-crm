# Acumulación de puntos de fidelización (SCRUM-45)

Con cada transacción que Marketplace y Ventas notifica como movimiento de **acumulación** (RIO-CRM-03,
`docs/contratos-eventos/RIO-CRM-03-movimiento-puntos.md`), el CRM calcula los puntos del cliente a partir
del valor consumido y los suma a su saldo, para premiar su recurrencia (RF-37, EPIC-08). Es la segunda
historia de integración asíncrona del Sprint 2. Diagramas en
`docs/fidelizacion/acumulacion-puntos-diagramas.md`.

## Regla de cálculo (SCRUM-386)

```
puntos = parte entera de (valorConsumido / bolivianosPorPunto)
```

- **Tasa:** 1 punto por cada **Bs 10** consumidos (`FIDELIZACION_BOLIVIANOS_POR_PUNTO`, por defecto 10;
  debe ser mayor que cero). La configuración de las reglas por el administrador es SCRUM-46.
- **Redondeo:** hacia abajo; solo cuentan los bolivianos completos de cada tramo. Bs 350,50 → 35 puntos;
  Bs 9,99 → 0 puntos.
- **Mínimo:** una transacción de menos de un tramo genera **0 puntos**: queda registrada en el detalle
  (con 0 puntos) y no cambia el saldo. **Máximo:** sin tope por transacción.
- **Valor consumido:** debe ser mayor que cero y con hasta 2 decimales; si no, el evento es inválido.
- Los puntos son enteros; el saldo es la suma de los puntos acreditados.

| Valor consumido | Puntos |
|---:|---:|
| 9,99 | 0 |
| 10,00 | 1 |
| 129,90 | 12 |
| 350,50 | 35 |
| 1.000,00 | 100 |

## Procesamiento (SCRUM-388, SCRUM-387)

1. `servicio-fidelizacion` consume la cola `crm.fidelizacion.movimientos`, enlazada a `ventas.eventos`
   con la routing key `puntos.movimiento`.
2. Anota el mensaje en la **bitácora de movimientos** (`fidelizacion.evento_movimiento`) con su contenido
   original, en una transacción propia.
3. En otra transacción: valida el evento, comprueba que sea una acumulación, vincula el cliente de
   Marketplace y Ventas con su perfil del CRM (servicio de Perfil, igual que las compras), calcula los
   puntos, suma al saldo y guarda el detalle. Todo se guarda junto o no se guarda nada.
4. Marca el evento `PROCESADO`, `DESCARTADO` o `FALLIDO` con la causa.

## Saldo (SCRUM-389)

`fidelizacion.saldo_puntos` guarda un saldo por cliente del CRM. La suma es atómica en la base
(`INSERT … ON CONFLICT DO UPDATE SET saldo = saldo + puntos`), así que dos acumulaciones simultáneas del
mismo cliente no se pisan. Un cliente que acumula por primera vez queda con saldo y **sin nivel** (los
niveles son SCRUM-49); la ficha integral lo muestra sin nivel.

## Idempotencia (SCRUM-243, criterio de aceptación 2)

Cada movimiento se identifica por `idTransaccion` + `tipoMovimiento`, clave primaria de
`fidelizacion.movimiento_puntos`. Un evento reentregado (o el mismo movimiento con otro `idEvento`) choca
con esa clave: queda `DESCARTADO` con la causa y los puntos no se acreditan dos veces, aunque las dos
copias se procesen a la vez.

## Eventos inválidos y reproceso (criterio de aceptación 3)

Un evento ilegible, incompleto o inválido, de un tipo aún no soportado (redención o reversa), de un
cliente que todavía no tiene perfil en el CRM, o que no se pudo procesar porque Perfil no respondió,
queda `FALLIDO` con la causa y su contenido original, **sin cambiar el saldo**.

| Método | Ruta | Permiso (Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/fidelizacion/eventos?estado=FALLIDO` | `PUNTOS_CONSULTAR` | Bitácora de movimientos, filtrable por estado |
| POST | `/api/fidelizacion/eventos/{id}/reprocesar` | `FIDELIZACION_CONFIGURAR` | Vuelve a procesar un evento `FALLIDO` |

Solo si ni siquiera se puede escribir en la bitácora (la base no responde), el mensaje se reintenta y
después pasa a la cola de respaldo `crm.fidelizacion.movimientos.respaldo`.

## Detalle por transacción (SCRUM-390, criterio de aceptación 4)

`fidelizacion.movimiento_puntos` guarda, por transacción: cliente del CRM e identificador de origen,
tipo, valor consumido, puntos, tasa aplicada, saldo resultante, fecha de la transacción, evento de la
bitácora y fecha de registro.

```
GET /api/fidelizacion/clientes/{clienteId}/movimientos      (PUNTOS_CONSULTAR)
```

```json
[
  {
    "idTransaccion": "V-100234",
    "tipoMovimiento": "ACUMULACION",
    "idClienteOrigen": "CLI-5521",
    "valorConsumido": 350.50,
    "puntos": 35,
    "bolivianosPorPunto": 10.00,
    "saldoResultante": 35,
    "fechaTransaccion": "2026-10-18T14:14:50Z",
    "registradoEn": "2026-10-18T14:15:06Z"
  }
]
```

## Prueba con eventos simulados (SCRUM-592)

```bash
python3 herramientas/simulador-eventos/publicar.py herramientas/simulador-eventos/eventos/movimiento-acumulacion-ana.json
python3 herramientas/simulador-eventos/publicar.py herramientas/simulador-eventos/eventos/movimiento-acumulacion-ana.json   # reentrega: DESCARTADO
python3 herramientas/simulador-eventos/publicar.py herramientas/simulador-eventos/eventos/movimiento-invalido.json          # FALLIDO
```

`AcumulacionPuntosIntegracionTest` (PostgreSQL y RabbitMQ reales) reproduce acumulación, reentrega,
eventos inválidos, tipos no soportados, cliente sin perfil, reproceso y acumulaciones simultáneas.

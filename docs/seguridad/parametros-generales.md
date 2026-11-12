# Parámetros generales del CRM (SCRUM-65 · RF-57)

En **Administración → Parámetros generales**, el Administrador de CRM cambia, sin tocar el código ni reiniciar los
servicios, los valores con los que el CRM identifica a los clientes inactivos, clasifica su nivel de consumo y hace
caducar los puntos.

| Parámetro | Código | Servicio | Rango | Inicial | Quién lo usa |
|---|---|---|---|---|---|
| Periodo de inactividad | `INACTIVIDAD_DIAS` | servicio-comportamiento | 7 a 730 días, entero | `INACTIVIDAD_UMBRAL_DIAS` (90) | Clientes inactivos (SCRUM-295), clientes activos del panel (SCRUM-59) y recencia del riesgo de abandono (SCRUM-35) |
| Umbral de consumo medio | `NIVEL_CONSUMO_UMBRAL_MEDIO` | servicio-comportamiento | 1,00 a 1 000 000,00 Bs | `NIVEL_CONSUMO_UMBRAL_MEDIO` (500) | Nivel de consumo (SCRUM-27) y las métricas que recibe Segmentación |
| Umbral de consumo alto | `NIVEL_CONSUMO_UMBRAL_ALTO` | servicio-comportamiento | 2,00 a 10 000 000,00 Bs y mayor que el medio | `NIVEL_CONSUMO_UMBRAL_ALTO` (5000) | Igual que el anterior |
| Vigencia de los puntos | `PUNTOS_VIGENCIA_MESES` | servicio-fidelizacion | 1 a 120 meses, entero | `FIDELIZACION_VIGENCIA_MESES` (12) | Caducidad diaria (SCRUM-50), consultas de saldo (SCRUM-47) y consulta de Marketplace y Ventas (RIO-CRM-04) |

## Dónde se guardan

Cada servicio es dueño de sus parámetros, como de su esquema: `comportamiento.parametro_general` (V15) y
`fidelizacion.parametro_general` (V12), con el valor, una versión, quién y cuándo lo cambió (SCRUM-519). Al arrancar,
el servicio crea los que faltan con el valor de su configuración (la variable de entorno de la tabla) y no toca los
que ya existen: un despliegue existente conserva sus valores y, desde ahí, rige la base.

Los cálculos leen el valor de una copia en memoria que dura 30 segundos. Un cambio guardado en una instancia rige
de inmediato en ella y, en las demás, a más tardar al vencer su copia.

## Criterio 1: los cálculos siguientes usan el valor nuevo

- **Periodo de inactividad:** al guardarlo se vuelve a identificar a los clientes inactivos y se recalcula el riesgo
  de abandono, sin esperar a los procesos de la madrugada.
- **Umbrales de consumo:** al guardarlos se reclasifica a todos los clientes y se vuelven a publicar sus métricas,
  para que Segmentación agrupe con los umbrales nuevos.
- **Vigencia de los puntos:** las consultas de saldo y la caducidad diaria siguiente usan la vigencia nueva. Reducirla
  no descuenta puntos en el momento: eso lo hace la caducidad diaria (o su ejecución manual), con su movimiento.

## Criterio 2: valores fuera de rango

La pantalla valida el rango, los decimales y que el umbral alto supere al medio antes de enviar. El servicio lo vuelve
a validar y responde **400** con el motivo por parámetro en `campos`; no guarda nada de la solicitud. Cada grupo se
guarda junto (todo o nada). Si otro usuario cambió un parámetro desde que se cargó la pantalla, la versión no coincide
y responde **409**: la pantalla recarga los valores vigentes.

## Criterio 3: auditoría

Cada parámetro que cambia deja `PARAMETRO_GENERAL_MODIFICADO` en `auditoria.evento`, en la misma transacción, con
entidad `PARAMETRO_GENERAL`, el código y en el detalle el nombre, la unidad, el valor anterior, el nuevo y el servicio;
el usuario y la fecha van en el propio evento (SCRUM-521). Si el registro falla, el parámetro no cambia. Un cambio
rechazado no deja registro. El historial de la pantalla y la consulta de auditoría del Administrador lo muestran.

## Acceso (SCRUM-520)

Solo el rol `ADMINISTRADOR_CRM` con el permiso `PARAMETROS_CONFIGURAR` (migración V11 del Gateway). El Gateway lo exige
en `/api/comportamiento/parametros/**` y `/api/fidelizacion/parametros/**`, y cada servicio lo vuelve a comprobar antes
de validar el contenido: sin la identidad del Gateway, 401; otro rol o sin el permiso, 403.

## API

Base `/api/comportamiento/parametros` y `/api/fidelizacion/parametros`:

- `GET /`: parámetros del servicio con valor, rango, decimales, unidad, versión, quién y cuándo.
- `PUT /`: `{ "cambios": [{ "codigo": "INACTIVIDAD_DIAS", "valor": 120, "version": 3 }] }`; devuelve los parámetros
  como quedaron.
- `GET /historial`: los últimos 100 cambios, del más reciente al más antiguo.

## Verificación

- `ParametrosGeneralesIntegracionTest`: valor inicial, rango, decimales, regla entre parámetros, versión vencida y copia
  en memoria.
- `ParametrosControllerIntegracionTest`: acceso, 400, 409 y auditoría. Ambas pruebas existen en los dos servicios.
- `ClientesInactivosIntegracionTest`, `NivelConsumoIntegracionTest` y `CaducidadPuntosIntegracionTest`: el criterio 1
  con datos reales.
- `AccesoPorRolTest`: las reglas del Gateway.
- `e2e/scrum65.spec.ts`: los tres criterios en el navegador, contra el entorno de `herramientas/pruebas-reportes/`.

Diagramas: [clases y secuencia](parametros-generales-diagramas.md).

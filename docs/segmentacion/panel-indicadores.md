# Panel de indicadores de clientes (SCRUM-59)

El Gerente Comercial ve en un panel los principales indicadores de clientes para conocer el estado de la
cartera de un vistazo (RF-51, EPIC-10). La API de consolidación es SCRUM-471; las métricas, SCRUM-474; los
clientes activos, SCRUM-475; los puntos acumulados, SCRUM-476; la distribución por segmento, SCRUM-473; la
interfaz, SCRUM-472; los permisos por rol, SCRUM-477; la validación con datos reales, SCRUM-478. Diagramas
en [panel-indicadores-diagramas.md](panel-indicadores-diagramas.md) (SCRUM-611).

## Métricas (SCRUM-474)

| Indicador | Definición | De dónde sale |
|---|---|---|
| **Clientes con compras** | Población de la segmentación: clientes con métricas de compra o con segmento | servicio-segmentacion, la misma del reporte de distribución (SCRUM-57) |
| **Clientes activos** (SCRUM-475) | Clientes con al menos una compra vigente vinculada en los últimos 90 días, y su porcentaje sobre los clientes con compras | servicio-comportamiento, con el mismo `CriterioInactividad` que la lista de inactivos (SCRUM-295, `INACTIVIDAD_UMBRAL_DIAS`) |
| **Distribución por segmento** (SCRUM-473) | Clientes por segmento vigente, con su porcentaje; los que no tienen segmento van al final como «Sin segmento» | servicio-segmentacion, las mismas consultas del reporte de distribución (SCRUM-57) |
| **Puntos acumulados** (SCRUM-476) | Suma de los saldos de puntos vigentes y cuántos clientes tienen saldo mayor que cero | servicio-fidelizacion (`saldo_puntos`) |

Decisiones:

- **Activos es el complemento exacto de inactivos.** Usa el mismo criterio y el mismo umbral, así que un
  cliente es activo en el panel si y solo si no figura en la lista de clientes inactivos. Si se cambia
  `INACTIVIDAD_UMBRAL_DIAS`, cambian los dos.
- **La distribución es la del reporte de distribución.** El panel y `GET /api/segmentacion/reportes/distribucion`
  dan el mismo corte por segmento; las pruebas lo comparan.
- **Los puntos son el saldo vigente**, no lo acumulado en la historia: lo canjeado y lo caducado ya no está.
  Un cliente con saldo en cero no cuenta como «cliente con puntos».

## Pantalla

**Análisis → Panel de indicadores** (`/analisis/panel`). Solo aparece para quien tiene `REPORTES_CONSULTAR`
(Administrador de CRM y Gerente Comercial).

Muestra tres tarjetas: clientes activos (con el total y el porcentaje), puntos acumulados (con los clientes
con puntos) y la distribución por segmento en barras. **Actualizar** lo vuelve a calcular (criterio 2) y la
hora de la última actualización queda arriba.

| Situación | Qué se ve |
|---|---|
| Datos nuevos (compras, segmentos, puntos) | Aparecen al pulsar **Actualizar** o al recargar la página: el panel no se cachea |
| Sin clientes con compras | «Todavía no hay clientes con compras»; las tarjetas en cero, sin error |
| Un servicio no responde | Su tarjeta dice «No disponible en este momento»; las demás se muestran igual |
| Rol sin permiso | Sin enlace en el menú; la ruta lleva a «Acceso denegado» y el API responde 403 (criterio 3) |

## API (SCRUM-471)

```
GET /api/segmentacion/reportes/panel
```

```json
{
  "generadoEn": "2026-11-10T12:00:00Z",
  "clientes": 3,
  "sinDatos": false,
  "activos": { "disponible": true, "activos": 3, "porcentaje": 100.00, "umbralDias": 90 },
  "segmentos": [
    { "valor": "Alto valor", "clientes": 2, "porcentaje": 66.67 },
    { "valor": null, "clientes": 1, "porcentaje": 33.33 }
  ],
  "puntos": { "disponible": true, "puntos": 110, "clientesConPuntos": 2 }
}
```

Si servicio-comportamiento o servicio-fidelizacion no responde, su bloque llega con `disponible: false` y los
demás campos en `null`; el panel responde **200** igual.

La consolidación vive en servicio-segmentacion, junto al reporte de distribución: allí están la población y
los segmentos, y ya tiene el cliente HTTP de fidelización. Los otros dos indicadores son **consultas
internas** entre servicios, como la distribución de niveles de SCRUM-464:

| Consulta interna | Servicio | Devuelve |
|---|---|---|
| `GET /api/comportamiento/reportes/clientes-activos` | servicio-comportamiento | `activos`, `umbralDias`, `desde` |
| `GET /api/fidelizacion/reportes/puntos` | servicio-fidelizacion | `puntos`, `clientesConPuntos` |

El Gateway **deniega** las dos a todos los usuarios, incluso al Administrador: son piezas del panel, no
reportes por sí mismas.

## Permisos (SCRUM-477)

El panel usa el permiso de los reportes, `REPORTES_CONSULTAR`, de Administrador de CRM y Gerente Comercial.

- El API Gateway lo exige con la regla de `GET /api/segmentacion/reportes/**` (SCRUM-462), y servicio-segmentacion
  lo vuelve a comprobar con el mismo `reporte.PermisoReportes`: sin identidad **401**, sin el permiso **403**.
- La pantalla tiene un guard por permiso y el enlace del menú solo aparece si el rol lo tiene.
- El Agente de Atención no lo tiene: no ve el enlace, la ruta lo lleva a «Acceso denegado» y el API le
  responde 403 (criterio 3).

## Auditoría

Cada consulta del panel queda en la auditoría compartida con la operación `PANEL_INDICADORES_CONSULTADO`,
entidad `REPORTE` e id `PANEL_INDICADORES`. El detalle no lleva datos personales: cuántos clientes había y
qué servicios respondieron. Se registra antes de responder, como los demás reportes.

## Configuración

| Variable | Para qué | Por defecto |
|---|---|---|
| `SERVICIO_COMPORTAMIENTO_URL` | servicio-comportamiento, para los clientes activos | `http://localhost:8082` |
| `COMPORTAMIENTO_TIEMPO_ESPERA` | Espera máxima de esa consulta | `2s` |
| `SERVICIO_FIDELIZACION_URL` / `FIDELIZACION_TIEMPO_ESPERA` | Los puntos acumulados (ya existían para SCRUM-57) | `http://localhost:8084` / `2s` |

## Pruebas (SCRUM-478)

| Prueba | Qué cubre |
|---|---|
| `ServicioPanelIndicadoresTest` | Los tres indicadores juntos, un servicio caído, la cartera vacía |
| `PanelIndicadoresIntegracionTest` | Con PostgreSQL real: criterio 1, igual a la distribución del reporte, criterio 2 al recargar, sin datos, 403 sin registro, auditoría |
| `PermisoReportesTest` | El panel con el permiso de los reportes: gerente 200, agente 403, sin identidad 401 |
| `ReporteComportamientoIntegracionTest` (comportamiento) | Clientes activos: compra reciente, antigua, anulada y sin vincular |
| `PuntosAcumuladosIntegracionTest` (fidelización) | Suma de saldos y clientes con saldo; sin saldos |
| `AccesoPorRolTest` (Gateway) | El panel exige `REPORTES_CONSULTAR`; las consultas internas, denegadas incluso al Administrador |
| `e2e/scrum59.spec.ts` (navegador) | Con datos reales sembrados por eventos: criterio 1, igual al reporte, **criterio 2 publicando un movimiento de puntos a mitad de la prueba**, criterio 3, Administrador y sin sesión |

```
herramientas/pruebas-reportes/ejecutar.sh e2e/scrum59.spec.ts
```

Es el mismo entorno aislado de SCRUM-58 (`docs/compra/reportes-comportamiento.md`); además de las altas y las
compras, siembra por eventos los movimientos de puntos de esas compras (110 puntos con la regla inicial de un
punto por cada Bs 10).

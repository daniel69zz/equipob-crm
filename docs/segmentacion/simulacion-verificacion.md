# SCRUM-288 · Verificación de SCRUM-29

Ejecución local del 18 de octubre de 2026 sobre la rama `feature/SCRUM-29-simulacion-segmentacion`, basada en `ac66cdc` de `main` (incluye SCRUM-43). Entorno: Windows, Java 17, Maven, Angular 18, Chromium y PostgreSQL 16 en Docker. Los datos de prueba se crean en bases temporales; no se usa la base de trabajo del usuario.

## Resultados

| Conjunto | Resultado |
| --- | --- |
| Servicio de segmentación completo | 56 pruebas, 0 fallos, 0 errores, 0 omitidas. Incluye criterios, asignación y simulación. |
| API Gateway completo | 57 pruebas, 0 fallos, 0 errores, 0 omitidas. Incluye la autorización de la ruta de simulación. |
| Chromium, SCRUM-29 | 8 pruebas de navegador aprobadas contra Gateway, servicio y PostgreSQL reales; los escenarios de error de red y respuesta tardía usan interceptación HTTP. |
| Chromium, regresión SCRUM-25 | 5 pruebas aprobadas del formulario existente. |
| Compilación Angular | `npm run build` correcto. |

Total: **126 pruebas distintas aprobadas**. Después del ajuste de logs para datos inconsistentes y la comprobación temprana de tiempo, se repitieron las 16 pruebas específicas del simulador: todas aprobadas.

Capturas conservadas de la ejecución: [escritorio](evidencias/scrum29-desktop.png) y [móvil](evidencias/scrum29-mobile.png).

## Evidencias importantes

- **Sin escrituras:** `SimulacionIntegracionTest` compara el contenido completo de `segmentacion.criterio`, `segmentacion.segmento_cliente`, `segmentacion.metricas_cliente`, `segmentacion.evento_procesado` y `auditoria.evento`, además del estado de `criterio_id_seq`, antes y después de dos simulaciones. Coinciden. También verifica que los conflictos no escriben.
- **Cálculo:** para tres clientes, Frecuentes pasa de 2 a 1, Alto valor de 0 a 1 y Sin segmento se mantiene en 1. El total de clientes que cambiarían es 1. Se verifica edición sin duplicar el criterio, prioridad entre indicadores, categoría normalizada, criterios inactivos, segmentos legados y población sin métricas.
- **Volumen:** 10.000 clientes, 5.000 coincidencias, cálculo en 114 ms en la suite completa y 91 ms en la repetición específica. El umbral del test es 5 segundos. Son mediciones locales con una regla, no un SLA ni un ensayo de carga concurrente de producción. Con 10.001 clientes y el límite del test en 10.000, se rechaza la simulación sin resultado parcial.
- **Errores:** reglas incompletas, frecuencia decimal, límites invertidos, superposición, nombre duplicado, edición obsoleta, criterio inexistente, categorías inconsistentes, fallo de base y tiempo excedido. Se comprueba que los detalles privados del error de conexión no llegan al usuario.
- **Acceso:** solo Administrador de CRM con permiso de configuración. Se rechazan solicitudes sin JWT, sin permiso y con roles no autorizados, incluso con cabeceras de identidad suministradas por el cliente.
- **Frontend:** simulación de alta y edición, totales y diferencias, guardado explícito, invalidación al modificar el borrador, cancelación de respuesta tardía, reintento y conflicto creado entre simulación y guardado. Vista móvil de 390 px sin desbordamiento de página; la tabla mantiene desplazamiento horizontal propio.

## Reproducir desde la raíz del repositorio

Requisitos: Java 17, Maven, Node/npm y Docker Desktop iniciado. Para las pruebas de navegador deben estar libres los puertos 18080, 18083 y 14200.

```powershell
mvn.cmd -B -f backend/servicio-segmentacion/pom.xml test
mvn.cmd -B -f backend/api-gateway/pom.xml test
./herramientas/pruebas-scrum29/ejecutar.ps1
./herramientas/pruebas-scrum29/ejecutar.ps1 -ReutilizarDependencias -Escenario scrum25
```

El primer escenario instala dependencias con `npm ci`, compila ambos servicios y el frontend, prepara Chromium y levanta un PostgreSQL temporal con credenciales aleatorias. Arranca los servicios y carga únicamente los datos de `herramientas/pruebas-scrum29/datos.sql`. Al terminar detiene sus procesos, elimina su contenedor y restaura las variables de entorno del proceso. `-ReutilizarDependencias` omite solamente `npm ci`.

Los logs están en `logs/scrum29/`; los informes Maven en `backend/*/target/surefire-reports/` y los de Playwright en `frontend/crm-web/playwright-report/`. Cada ejecución de Playwright reemplaza su informe y `test-results/`; conservar las evidencias necesarias antes de ejecutar otro escenario. Las capturas de SCRUM-29 quedan en `test-results/` con nombres `simulacion-desktop.png` y `simulacion-mobile.png`.

## Demostración manual

Con la aplicación habitual levantada y métricas recibidas desde el flujo existente:

1. Entrar como administrador y abrir **Criterios de segmentación**.
2. Elegir **Nuevo criterio**, completar un rango que no se superponga y pulsar **Simular impacto**.
3. Verificar las columnas Actual, Simulado y Diferencia, el total y las fechas. Si no existen métricas, la pantalla debe informarlo y mostrar ceros.
4. Cambiar un límite: el resultado anterior desaparece. Simular nuevamente.
5. Guardar desde **Guardar regla simulada**, comprobar el criterio en la lista y su auditoría de creación/edición.
6. Para demostrar la aplicación real posterior, procesar un evento nuevo de métricas por el flujo de SCRUM-26. Simular o guardar por sí solos no producen esa reasignación.

## Entrega

El código, pruebas y [diagramas](simulacion-diagramas.md) están preparados en la rama local. Los SVG de clases y secuencia fueron adjuntados a SCRUM-29 el 18 de octubre de 2026 con el conector de Atlassian. El cierre del DoD requiere además revisión de un compañero y merge. Los estados de Jira no sustituyen estas evidencias.

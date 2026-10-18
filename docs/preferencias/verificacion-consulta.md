# Verificación de SCRUM-43

Ejecutada el 18 de octubre de 2026 sobre `feature/SCRUM-43-consulta-preferencias-cliente`,
con base `6d366f0`. Implementación local de la consulta de preferencias y su navegación.

| Verificación | Resultado |
|---|---|
| ConstructorPreferenciasTest | 12 aprobadas |
| IdentificadoresPreferenciasTest | 14 aprobadas |
| PreferenciasIntegracionTest | 16 aprobadas |
| ActualizacionPreferenciasIntegracionTest | 8 aprobadas |
| AccesoPorRolTest | 29 aprobadas |
| AuditoriaAccesoFilterTest | 4 aprobadas |
| Playwright `scrum43.spec.ts` | 18 aprobadas, Chromium |
| Angular `npm run build` | Correcto |
| `git diff --check` | Sin errores de espacios |

**101 pruebas aprobadas, sin fallos ni omisiones.** Los cuatro grupos de preferencias incluyen
integración con PostgreSQL real mediante Testcontainers; la suite de actualización también
ejercita la mensajería del flujo existente. Las pruebas del Gateway usan MockMvc y servicios
de identidad simulados para aislar la matriz de autorización.

Las 18 pruebas de navegador se ejecutaron contra Angular, Gateway, Servicio Perfil, Servicio
Comportamiento y PostgreSQL temporal. El flujo principal usó datos sintéticos persistidos y
respuestas reales de la API. Se verificó que consultas repetidas no cambian el perfil ni su
trazabilidad. Los casos 503, 403 posterior a la carga y variantes de sesión local se simularon
explícitamente en el navegador para verificar los estados de interfaz. El cambio de cliente
incluye una respuesta retenida del cliente anterior.

Se revisaron las capturas de escritorio (1280 px) y móvil (390 px): pestañas accesibles, tabla
legible y desplazable horizontalmente en móvil, estado de preferencia principal y fecha.
El ejecutor cerró sus procesos y eliminó el contenedor temporal; no quedaron servicios de
prueba escuchando en 18080, 18081, 18082 ni 14200.

## Evidencias locales y repetición

- Informes Maven: `backend/servicio-comportamiento/target/surefire-reports/` y
  `backend/api-gateway/target/surefire-reports/`.
- Informe Playwright: `frontend/crm-web/playwright-report/index.html`.
- Capturas: `frontend/crm-web/test-results/`, archivos `preferencias-desktop.png` y
  `preferencias-mobile.png` en las carpetas de sus respectivos casos.
- Logs de servicios: `logs/scrum43/`.
- Ejecutor reproducible: `herramientas/pruebas-scrum43/ejecutar.ps1`.

Los informes y capturas de ejecución están ignorados por Git. Los comandos y el alcance están
en [consulta-preferencias.md](consulta-preferencias.md).

Esta verificación acredita la implementación y las pruebas locales. La revisión de un par,
merge y adjuntar los [diagramas](consulta-diagramas.md) a Jira siguen siendo pasos del cierre
de la historia; no se modificaron estados en Jira ni se publicó la rama.

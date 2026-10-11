# servicio-segmentacion — Segmentación de Clientes

Servicio Spring Boot del CRM MaxiConecta, propietario del esquema `segmentacion`.

- Consulta del segmento asignado para la ficha integral del cliente (SCRUM-149).
- **SCRUM-25:** alta, edición, activación y desactivación de criterios por frecuencia, valor acumulado y categoría.
- Rechazo de rangos inválidos y superposiciones, con control de concurrencia y versiones.
- Auditoría transaccional con fotos antes/después en el esquema compartido de solo inserción.
- Pantalla Angular en `/segmentacion/criterios`, disponible al Administrador de CRM.

Modelo, API, decisiones, diagramas, pruebas y puesta en marcha:
[Documentación de SCRUM-25](../../docs/segmentacion/criterios.md).

## Ejecutar

PostgreSQL disponible y migraciones del API Gateway aplicadas (crean la auditoría compartida).
Variables `DB_URL`, `DB_USUARIO`, `DB_PASSWORD`; puerto predeterminado 8083, configurable con `PUERTO`.

```powershell
mvn spring-boot:run
```

## Verificar

```powershell
mvn test
```

Las pruebas de integración requieren Docker y usan PostgreSQL real mediante Testcontainers.
No se omiten si falta Docker: el fallo indica que no pudo verificarse esa parte.
La asignación automática y el historial de cambios de segmento siguen siendo otras historias;
los criterios activos y `ReglasCriterio.cumple` quedan disponibles para esa integración.

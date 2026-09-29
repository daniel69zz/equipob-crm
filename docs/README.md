# Documentación

- `contratos-eventos/`: contratos de los eventos que intercambian el CRM y el módulo Marketplace y Ventas, su única integración.
- `seguridad/`: catálogo de roles, matriz de permisos, alcance de la auditoría y convención de rutas para datos de clientes.
- `ingesta/`: estrategia de idempotencia de los eventos de venta.
- `perfil/`: modelo de datos del perfil, identificadores de origen, mapeo de datos con su política de reintentos, histórico de cambios, y catálogo de reglas de validación (perfiles incompletos o inconsistentes).
- `compra/`: diseño de la consulta del historial de compras del cliente (SCRUM-16) y regla de cálculo del ticket promedio (SCRUM-17).

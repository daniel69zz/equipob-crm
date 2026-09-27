# api-gateway

Punto de entrada único del CRM (**Spring Cloud Gateway**).

- Enruta las peticiones de la aplicación web a cada microservicio.
- Valida el token **JWT** y aplica el control de acceso por rol (RBAC).
- Expone la consulta síncrona de saldo y nivel de puntos para Marketplace y Ventas (RIO-CRM-04), que resuelve el servicio de Fidelización.

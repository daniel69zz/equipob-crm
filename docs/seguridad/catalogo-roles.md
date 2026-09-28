# Catálogo de roles internos del CRM

Roles del personal interno que usa la Aplicación Web del CRM. Cada usuario tiene **un solo rol** y el rol define sus permisos (ver `matriz-permisos.md`).

| Código | Nombre | Quién es | Alcance funcional |
|---|---|---|---|
| `ADMINISTRADOR_CRM` | Administrador de CRM | Responsable de la operación y la calidad de los datos del CRM | Acceso completo: gestiona perfiles de clientes, consentimiento, unificación de duplicados, reproceso de eventos, configuración de segmentación y fidelización, usuarios, roles y auditoría |
| `AGENTE_ATENCION` | Agente de Atención al Cliente | Personal que atiende consultas y reclamos de los clientes | Consulta la ficha del cliente, su historial e indicadores y sus puntos. Registra y da seguimiento a interacciones. No modifica datos del perfil |
| `GERENTE_COMERCIAL` | Gerente Comercial | Responsable de las decisiones comerciales y del análisis de clientes | Consulta clientes, indicadores de comportamiento, segmentos y puntos para el análisis. No modifica datos ni configuración |

## Reglas

- Un usuario interno tiene **exactamente un rol activo**.
- Solo el **Administrador de CRM** crea usuarios, asigna roles y administra los permisos de cada rol.
- Un rol **desactivado** no se puede asignar a usuarios nuevos.
- Todo cambio de roles, permisos o asignaciones queda registrado en la **auditoría**.
- El cambio de rol de un usuario y la desactivación de un usuario o de un rol **rigen de inmediato**: el API Gateway consulta el rol y los permisos vigentes en cada petición.

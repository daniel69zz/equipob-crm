# equipob-crm

CRM MaxiConecta — módulo CRM del ERP, integrado con **Marketplace y Ventas**.
Proyecto de Ingeniería de Software (UCB) · Equipo B.

## Arquitectura

Microservicios en **Java + Spring Boot**, detrás de un **API Gateway** (REST/JSON, JWT y roles),
con una base **PostgreSQL** centralizada (`crm_maxiconecta`, un esquema por dominio) y
**RabbitMQ** para recibir los eventos de Marketplace y Ventas. El frontend es una aplicación **Angular**.

```
Marketplace y Ventas ──eventos──► RabbitMQ ──► microservicios consumidores
                                                       │
Aplicación Web (Angular) ──► API Gateway ──► microservicios ──► PostgreSQL
```

## Estructura del repositorio

| Carpeta | Contenido |
|---|---|
| `frontend/crm-web/` | Aplicación Web del CRM (Angular) |
| `backend/api-gateway/` | Punto de entrada único: enrutamiento, JWT y control por roles |
| `backend/servicio-perfil/` | Perfil del Cliente |
| `backend/servicio-comportamiento/` | Comportamiento de Compra (historial e indicadores) |
| `backend/servicio-segmentacion/` | Segmentación de clientes |
| `backend/servicio-fidelizacion/` | Fidelización y Puntos |
| `backend/servicio-interacciones/` | Interacciones con el cliente |
| `database/` | Convenciones de la base de datos y esquemas por dominio |
| `herramientas/simulador-eventos/` | Publicador de eventos simulados en RabbitMQ |
| `docs/` | Documentación y contratos de eventos |

## Integración con Marketplace y Ventas

El CRM se comunica con **un solo módulo** del ERP: **Marketplace y Ventas**. Ese módulo publica en
RabbitMQ los eventos de clientes, compras y anulaciones (contratos en `docs/contratos-eventos/`), y el
CRM los identifica por sus identificadores en ese módulo (`idCliente`, `idCompra`, `idAnulacion`).

Mientras el módulo no esté disponible, el CRM construye **su lado de la integración**
(los consumidores de eventos) y lo prueba con eventos simulados publicados en RabbitMQ
(ver `herramientas/simulador-eventos/`).

## Equipo

| Integrante | Rol |
|---|---|
| Luis Daniel Rojas | Backend + Despliegue |
| Daniel Boris Rueda | Base de Datos + Backend |
| Frederick Aguirre | Product Owner + Analista funcional + Frontend |
| Oscar Gutierrez | Auditoría + Frontend |
| Oziel Ramos | Scrum Master + Frontend/QA |

## Gestión del proyecto

Planificación en Jira: proyecto **EquipoB-CRM** (`SCRUM`).

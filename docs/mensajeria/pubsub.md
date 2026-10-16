# Google Cloud Pub/Sub como broker alternativo a RabbitMQ

Los servicios que intercambian eventos (`servicio-perfil`, `servicio-comportamiento` y
`servicio-segmentacion`) pueden consumir y publicar sus eventos con **RabbitMQ** (predeterminado) o con
**Google Cloud Pub/Sub**. El broker se elige por configuración, sin cambiar el código ni los contratos de
`docs/contratos-eventos/`: los mensajes, el procesamiento, la idempotencia y la bitácora son los mismos.

## Cómo se elige

| Broker | Cómo activarlo |
|---|---|
| RabbitMQ | Por defecto (no hace falta nada) |
| Pub/Sub | `SPRING_PROFILES_ACTIVE=pubsub` |

El perfil `pubsub` (`application-pubsub.yml` de cada servicio) activa la integración con Google Cloud,
fija `crm.mensajeria.broker=pubsub`, no inicia los receptores de RabbitMQ y cambia el chequeo de salud
de RabbitMQ por el de Pub/Sub. Sin ese perfil la integración con Google Cloud queda apagada y no pide
credenciales.

| Variable | Por defecto | Uso |
|---|---|---|
| `PUBSUB_PROYECTO` | `crm-maxiconecta` | Proyecto de Google Cloud |
| `PUBSUB_EMULADOR` | (vacío) | `host:puerto` del emulador local; vacío para Google Cloud real |
| `PUBSUB_CREAR_RECURSOS` | `true` | Crear al arrancar los tópicos y suscripciones que falten |
| `PUBSUB_MAX_INTENTOS` | `5` | Entregas fallidas antes de pasar un mensaje a respaldo |

Con Google Cloud real, las credenciales se toman de las *Application Default Credentials*
(`GOOGLE_APPLICATION_CREDENTIALS` o la cuenta de servicio del entorno).

## Equivalencia de nombres

En Pub/Sub se usan **los mismos nombres** que en RabbitMQ: cada exchange es un tópico, cada cola es una
suscripción y la routing key viaja en el atributo `ruta` del mensaje, que filtra la suscripción.

| Tópico (exchange) | Atributo `ruta` | Suscripción (cola) | Servicio |
|---|---|---|---|
| `ventas.eventos` | `cliente.*` (prefijo) | `crm.perfil.clientes` | perfil |
| `ventas.eventos` | `compra.confirmada` | `crm.comportamiento.compras` | comportamiento |
| `ventas.eventos` | `compra.anulada` | `crm.comportamiento.anulaciones` | comportamiento |
| `crm.comportamiento.eventos` | `metricas.actualizadas` | `crm.segmentacion.metricas` | segmentación (SCRUM-26) |
| `crm.comportamiento.eventos` | `metricas.actualizadas` | `crm.comportamiento.preferencias` | comportamiento (SCRUM-42) |

**Contrato para Marketplace y Ventas con Pub/Sub:** publicar en el tópico `ventas.eventos` el mismo JSON
de los contratos RIO-CRM-01, 02 y 05, con el atributo `ruta` igual a la routing key que usaría en
RabbitMQ (`cliente.registrado`, `cliente.actualizado`, `compra.confirmada` o `compra.anulada`). Un mensaje
sin `ruta` no llega a ninguna suscripción.

`servicio-comportamiento` publica las métricas en `crm.comportamiento.eventos` con
`ruta = metricas.actualizadas`, como en RabbitMQ.

## Errores, reintentos y respaldo

Cada suscripción tiene un tópico y una suscripción de respaldo con su mismo nombre y el sufijo
`.respaldo` (por ejemplo, `crm.segmentacion.metricas.respaldo`), equivalentes a las colas de respaldo de
RabbitMQ.

| Situación | RabbitMQ | Pub/Sub |
|---|---|---|
| Procesado | `ack` | `ack` |
| Rechazado a propósito (mensaje inválido) | Directo a la cola de respaldo | Se copia al tópico de respaldo con el atributo `causa` y se confirma |
| Error transitorio (la base no responde) | 3 reintentos y luego a respaldo | `nack`: Pub/Sub reintenta (2 s a 60 s) y, tras `PUBSUB_MAX_INTENTOS` entregas, lo pasa a respaldo |

El reproceso usa las mismas rutas (`POST /api/comportamiento/eventos/respaldo/reinyectar?cola=...` y
`POST /api/segmentacion/respaldo/reinyectar`): toma un mensaje de la suscripción de respaldo, lo vuelve a
publicar en su tópico con su misma ruta y recién entonces lo confirma. Como se republica en el tópico,
también lo reciben las demás suscripciones de esa ruta, que lo descartan por idempotencia si ya lo
aplicaron.

En Google Cloud real, el envío automático a respaldo requiere que la cuenta de servicio de Pub/Sub del
proyecto tenga los roles *Pub/Sub Publisher* sobre los tópicos de respaldo y *Pub/Sub Subscriber* sobre
las suscripciones.

## Probar en local con el emulador

```bash
docker run --rm -p 8085:8085 gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators \
  gcloud beta emulators pubsub start --host-port=0.0.0.0:8085

# en cada servicio
SPRING_PROFILES_ACTIVE=pubsub PUBSUB_EMULADOR=localhost:8085 mvn spring-boot:run

# eventos simulados
python3 herramientas/simulador-eventos/publicar.py herramientas/simulador-eventos/eventos/compra-ana.json --pubsub
```

Las pruebas automáticas `PubSubIntegracionTest` de cada servicio levantan el emulador oficial con
Testcontainers y verifican el consumo por suscripción filtrada, la publicación de métricas, la
actualización de preferencias y del segmento, la idempotencia, el respaldo y el reproceso.

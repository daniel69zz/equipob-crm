# Despliegue con Docker

`docker-compose.yml` levanta el CRM completo: PostgreSQL, el Pub/Sub interno, el API Gateway, los
cinco microservicios y nginx. Solo nginx publica un puerto; lo demás queda en la red interna de Docker.

| Contenedor | Función | Expuesto |
|---|---|---|
| `nginx` | Entrada por dominio: `DOMINIO_WEB` sirve el sitio (Angular) y `DOMINIO_API` la API | `80` |
| `api-gateway` | JWT, roles y permisos; consulta a los microservicios por la red interna | no |
| `servicio-perfil`, `-comportamiento`, `-segmentacion`, `-fidelizacion`, `-interacciones` | Microservicios | no |
| `pubsub-interno` | Pub/Sub entre microservicios (emulador oficial de Google Cloud) | solo `127.0.0.1:8086` |
| `postgres` | Base `crm_maxiconecta`, un esquema por dominio | solo `127.0.0.1:55432` |

## Mensajería

- **Entre microservicios** (`ventas.eventos`, `crm.comportamiento.eventos` y sus `.respaldo`): Pub/Sub en
  el contenedor `pubsub-interno`. La cuenta de servicio del curso solo puede publicar en el tópico del grupo
  y no puede crear tópicos ni suscripciones; además, ese tópico es el canal que revisa el docente y no debe
  mezclarse con el tráfico interno. Con su consentimiento, estos tópicos pueden pasar al proyecto de Google
  Cloud del curso cambiando `PUBSUB_PROYECTO`, quitando `PUBSUB_EMULADOR` y montando credenciales.
- **Hacia afuera**: `servicio-fidelizacion` publica el saldo y nivel de puntos (RIO-CRM-06) en Google Cloud
  Pub/Sub real (`FIDELIZACION_SALIDA_PROYECTO`, tópico `ucb-sis323-sub-b`), con un publicador propio que
  ignora el emulador ([publicación del saldo](../fidelizacion/publicacion-saldo-puntos.md)).

## Puesta en marcha

```bash
cp .env.example .env        # completar contraseñas, dominios, proyecto y ruta de la clave
for s in api-gateway servicio-perfil servicio-comportamiento servicio-segmentacion \
         servicio-fidelizacion servicio-interacciones; do
  (cd backend/$s && mvn -B package -DskipTests)
done
(cd frontend/crm-web && npm ci && npx ng build)
docker compose up -d --build --remove-orphans
docker compose ps
```

Los registros DNS de `DOMINIO_WEB` y `DOMINIO_API` deben apuntar al servidor. En una máquina local
sirven `crm.localhost` y `api.crm.localhost`. Un host distinto se rechaza (444).

La clave JSON de la cuenta de servicio se monta como secreto desde `GCP_CREDENCIALES_ARCHIVO`, una ruta
fuera del repositorio, y nunca se copia a la imagen. En Linux, el usuario `crm` del contenedor debe poder
leerla (por ejemplo, `chmod 644`).

## Comprobación

```bash
curl -s -H "Host: api.crm.localhost" http://localhost/actuator/health     # API Gateway
docker compose logs servicio-fidelizacion | grep -i salida                # envíos a Pub/Sub
```

Para inspeccionar el tópico del curso sin consumir los mensajes, seguir la validación de
[publicación del saldo](../fidelizacion/publicacion-saldo-puntos.md): `pull` sin `--auto-ack`.
`FIDELIZACION_SALIDA_ENABLED=false` en `.env` pausa los envíos sin perder los pendientes.

Eventos simulados de Marketplace y Ventas, desde el propio servidor:

```bash
python3 herramientas/simulador-eventos/publicar.py \
  herramientas/simulador-eventos/eventos/compra-ana.json --pubsub --pubsub-url http://localhost:8086
```

HTTPS: agregar un `server` con `listen 443 ssl` y los certificados (por ejemplo, de Let's Encrypt)
en `docker/nginx.conf.template` y publicar el puerto `443`.

Diagrama: [despliegue](diagrama-despliegue.png) (fuente PlantUML en `diagrama-despliegue.puml`).

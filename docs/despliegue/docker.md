# Despliegue con Docker

`docker-compose.yml` levanta todo el CRM en contenedores: el frontend, PostgreSQL, el Pub/Sub interno, el
API Gateway y los cinco microservicios. La única entrada pública es el **nginx del VPS** (instalado fuera de
Docker), que redirige el sitio al contenedor del frontend y la API al Gateway. El Gateway valida el JWT o el token de servicio y es el único que
consulta a los microservicios; estos no se publican, porque confían en las cabeceras de identidad que pone él.

```
Internet → nginx del VPS ─┬─ crm.tudominio.com      → crm-web (127.0.0.1:8090) y /api → Gateway
                          └─ api.crm.tudominio.com  → Gateway (127.0.0.1:8080) → microservicios (red Docker)
```

| Contenedor | Función | Expuesto |
|---|---|---|
| `crm-web` | Frontend Angular (archivos estáticos con Caddy) | solo `127.0.0.1:8090` |
| `api-gateway` | JWT, roles y permisos; consulta a los microservicios por la red interna | solo `127.0.0.1:8080` |
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
cp .env.example .env        # completar contraseñas, proyecto y ruta de la clave
for s in api-gateway servicio-perfil servicio-comportamiento servicio-segmentacion \
         servicio-fidelizacion servicio-interacciones; do
  (cd backend/$s && mvn -B package -DskipTests)
done
(cd frontend/crm-web && npm ci && npx ng build)
docker compose up -d --build --remove-orphans
docker compose ps
```

## nginx del VPS (una sola vez)

[`herramientas/despliegue/nginx-vps.conf`](../../herramientas/despliegue/nginx-vps.conf) tiene los dos
`server`: el sitio (contenedor `crm-web`, con `/api` hacia el Gateway en el mismo dominio, sin CORS) y la
API. Las cabeceras comunes están en `crm-proxy.conf`.

```bash
sudo cp herramientas/despliegue/crm-proxy.conf /etc/nginx/snippets/crm-proxy.conf
sudo cp herramientas/despliegue/nginx-vps.conf /etc/nginx/sites-available/crm-maxiconecta
sudo nano /etc/nginx/sites-available/crm-maxiconecta     # reemplazar los dos dominios
sudo ln -s /etc/nginx/sites-available/crm-maxiconecta /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d crm.tudominio.com -d api.crm.tudominio.com   # HTTPS
```

Los registros DNS de ambos dominios deben apuntar al VPS antes de ejecutar certbot. Si los puertos 8080 u
8090 ya están ocupados en el VPS, cambiar `GATEWAY_PUERTO` o `WEB_PUERTO` en `.env` y el `upstream`
correspondiente en la configuración de nginx.

La clave JSON de la cuenta de servicio se monta como secreto desde `GCP_CREDENCIALES_ARCHIVO`, una ruta
fuera del repositorio, y nunca se copia a la imagen. En Linux, el usuario `crm` del contenedor debe poder
leerla (por ejemplo, `chmod 644`).

## Pruebas de humo

`herramientas/despliegue/humo.py` (solo Python 3) verifica el despliegue y sale con código 1 si algo falla:
Gateway en `UP`, carga del sitio y de una ruta de la SPA, 401 sin token,
inicio de sesión por el sitio y por la API, listado y ficha de un cliente, y una consulta por cada
microservicio a través del Gateway. No publica eventos ni toca Pub/Sub.

```bash
CRM_USUARIO=admin CRM_PASSWORD=... python3 herramientas/despliegue/humo.py \
  --web https://crm.ejemplo.com --api https://api.crm.ejemplo.com
# en el propio VPS, sin DNS:
python3 herramientas/despliegue/humo.py --web http://localhost --host-web crm.localhost \
  --api http://localhost --host-api api.crm.localhost
```

Con `--exigir-cliente` falla si aún no hay clientes para abrir su ficha. Para revisar los envíos del saldo:
`docker compose logs servicio-fidelizacion | grep -i salida`.

Para inspeccionar el tópico del curso sin consumir los mensajes, seguir la validación de
[publicación del saldo](../fidelizacion/publicacion-saldo-puntos.md): `pull` sin `--auto-ack`.
`FIDELIZACION_SALIDA_ENABLED=false` en `.env` pausa los envíos sin perder los pendientes.

Eventos simulados de Marketplace y Ventas, desde el propio servidor:

```bash
python3 herramientas/simulador-eventos/publicar.py \
  herramientas/simulador-eventos/eventos/compra-ana.json --pubsub --pubsub-url http://localhost:8086
```

## Despliegue continuo

`.github/workflows/despliegue.yml` se ejecuta en cada push a `main` (o a mano): busca secretos con
gitleaks, compila los microservicios y el frontend, valida el compose, copia el paquete al servidor por
SSH, ejecuta `docker compose up -d --build --wait` (backend y frontend) y luego las pruebas de humo contra las
URL públicas. No hace falta clonar el
repositorio en el VPS. El servidor conserva su `.env` y la clave; el paquete no los incluye. Requisitos del
servidor: Docker con Compose v2, el nginx ya configurado, la carpeta `SSH_RUTA` con su `.env` y un usuario
con acceso a Docker.

En el entorno `produccion` del repositorio se configuran las variables `SSH_HOST`, `SSH_USUARIO`,
`SSH_RUTA`, `URL_WEB` y `URL_API`, y los secretos `SSH_CLAVE`, `SSH_KNOWN_HOSTS` (`ssh-keyscan <host>`),
`CRM_USUARIO` y `CRM_PASSWORD`. El despliegue se activa con la **variable del repositorio** (no del entorno)
`DESPLIEGUE_HABILITADO=true`, porque la condición del job se evalúa antes de cargar el entorno; sin ella,
el workflow solo compila y valida. Las pruebas de humo se identifican con el User-Agent
`crm-maxiconecta-humo/1.0`, que Cloudflare no bloquea.

Diagrama: [despliegue](diagrama-despliegue.png) (fuente PlantUML en `diagrama-despliegue.puml`).

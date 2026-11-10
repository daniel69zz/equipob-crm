#!/usr/bin/env bash
# Pruebas de navegador de los reportes de clientes (EPIC-10) contra un entorno aislado y desechable.
#
# Uso:  herramientas/pruebas-reportes/ejecutar.sh [e2e/scrumNN.spec.ts ...]
#       (sin argumentos ejecuta todas las e2e/scrum5*.spec.ts)
#
# Levanta PostgreSQL y RabbitMQ en contenedores temporales, los cinco servicios que usan los reportes y
# el frontend, y siembra los datos SOLO por eventos, como en producción: altas de clientes, compras y
# movimientos de puntos con herramientas/simulador-eventos/publicar.py, y el criterio de segmento por la
# API. Así segmentación asigna los segmentos con sus propias reglas, en vez de cargarlos por SQL.
# Al terminar, bien o mal, detiene los procesos y borra los contenedores.
#
# Requisitos: Docker en marcha, JDK 26, Maven, Node con las dependencias del frontend instaladas.
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
AQUI="$REPO/herramientas/pruebas-reportes"
LOGS="$REPO/logs/reportes"
FRONTEND="$REPO/frontend/crm-web"
mkdir -p "$LOGS"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 26)}"
SUFIJO="$(python3 -c 'import uuid; print(uuid.uuid4().hex[:10])')"
PG="crm-reportes-pg-$SUFIJO"
MQ="crm-reportes-mq-$SUFIJO"
PIDS=()

limpiar() {
    local codigo=$?
    for pid in "${PIDS[@]:-}"; do [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true; done
    # Spring se apaga en orden y tarda unos segundos: se espera para dejar los puertos libres.
    local limite=$(( SECONDS + 30 ))
    for pid in "${PIDS[@]:-}"; do
        while [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null && (( SECONDS < limite )); do sleep 0.5; done
        [[ -n "$pid" ]] && kill -9 "$pid" 2>/dev/null || true
    done
    docker rm -f -v "$PG" "$MQ" >/dev/null 2>&1 || true
    [[ $codigo -ne 0 ]] && echo "✗ Falló (código $codigo). Revise los registros en $LOGS"
    exit $codigo
}
trap limpiar EXIT INT TERM

esperar() {  # esperar <descripción> <segundos> <comando...>
    local que="$1" limite=$(( SECONDS + $2 )); shift 2
    until "$@" >/dev/null 2>&1; do
        (( SECONDS < limite )) || { echo "✗ No se cumplió: $que"; return 1; }
        sleep 1
    done
    echo "✓ $que"
}

for puerto in 18080 18081 18082 18083 18084 14200; do
    if lsof -nP -iTCP:"$puerto" -sTCP:LISTEN >/dev/null 2>&1; then
        echo "El puerto $puerto está ocupado. Detenga la ejecución anterior antes de iniciar otra."; exit 1
    fi
done

echo "== Compilando servicios (las pruebas Maven se ejecutan aparte)"
SERVICIOS=(api-gateway servicio-perfil servicio-comportamiento servicio-segmentacion servicio-fidelizacion)
for s in "${SERVICIOS[@]}"; do
    mvn -B -q -f "$REPO/backend/$s/pom.xml" -DskipTests package >"$LOGS/compilar-$s.log" 2>&1 \
        || { echo "✗ No compiló $s ($LOGS/compilar-$s.log)"; exit 1; }
done

echo "== Contenedores temporales"
export DB_USUARIO=crm_pruebas
export DB_PASSWORD="$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
MQ_USUARIO=crm_pruebas
MQ_PASSWORD="$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
docker run -d --rm --name "$PG" -p 127.0.0.1::5432 -e POSTGRES_DB=crm_reportes \
    -e POSTGRES_USER="$DB_USUARIO" -e POSTGRES_PASSWORD="$DB_PASSWORD" postgres:16-alpine >/dev/null
# El usuario guest de RabbitMQ solo acepta conexiones desde el propio contenedor: se crea uno de pruebas.
docker run -d --rm --name "$MQ" -p 127.0.0.1::5672 -p 127.0.0.1::15672 \
    -e RABBITMQ_DEFAULT_USER="$MQ_USUARIO" -e RABBITMQ_DEFAULT_PASS="$MQ_PASSWORD" rabbitmq:3.13-management >/dev/null
PUERTO_PG="$(docker port "$PG" 5432 | head -1 | awk -F: '{print $NF}')"
PUERTO_MQ="$(docker port "$MQ" 5672 | head -1 | awk -F: '{print $NF}')"
PUERTO_MQ_ADMIN="$(docker port "$MQ" 15672 | head -1 | awk -F: '{print $NF}')"
esperar "PostgreSQL listo" 60 docker exec "$PG" pg_isready -U "$DB_USUARIO" -d crm_reportes
esperar "RabbitMQ listo" 90 curl -sf -u "$MQ_USUARIO:$MQ_PASSWORD" "http://127.0.0.1:$PUERTO_MQ_ADMIN/api/overview"

export DB_URL="jdbc:postgresql://127.0.0.1:$PUERTO_PG/crm_reportes"
export RABBIT_HOST=127.0.0.1 RABBIT_PUERTO="$PUERTO_MQ" RABBIT_USUARIO="$MQ_USUARIO" RABBIT_PASSWORD="$MQ_PASSWORD"
export JWT_SECRETO="$(python3 -c 'import uuid; print(uuid.uuid4().hex + uuid.uuid4().hex)')"
export ADMIN_USUARIO=admin-reportes
export ADMIN_PASSWORD="Test-$(python3 -c 'import uuid; print(uuid.uuid4().hex[:12])')"
export SERVICIO_PERFIL_URL=http://127.0.0.1:18081 SERVICIO_COMPORTAMIENTO_URL=http://127.0.0.1:18082
export SERVICIO_SEGMENTACION_URL=http://127.0.0.1:18083 SERVICIO_FIDELIZACION_URL=http://127.0.0.1:18084
# Nunca publicar el saldo de puntos al tópico real de Pub/Sub desde una prueba.
export FIDELIZACION_SALIDA_ENABLED=false

echo "== Servicios"
iniciar() {  # iniciar <servicio> <puerto>
    java -jar "$REPO/backend/$1/target/$1-0.1.0-SNAPSHOT.jar" --server.port="$2" \
        >"$LOGS/$1.log" 2>&1 &
    PIDS+=($!)
    esperar "$1 en :$2" 120 curl -sf "http://127.0.0.1:$2/actuator/health"
}
# El Gateway primero: sus migraciones crean la auditoría compartida que usan los demás.
iniciar api-gateway 18080
iniciar servicio-perfil 18081
iniciar servicio-comportamiento 18082
iniciar servicio-segmentacion 18083
iniciar servicio-fidelizacion 18084

echo "== Semilla por eventos"
GW=http://127.0.0.1:18080
api() {  # api <método> <ruta> [json]  — como administrador
    curl -sf -X "$1" "$GW$2" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' ${3:+-d "$3"}
}
json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
TOKEN="$(curl -sf "$GW/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"usuario\":\"$ADMIN_USUARIO\",\"password\":\"$ADMIN_PASSWORD\"}" | json 'd["token"]')"
for rol in GERENTE_COMERCIAL AGENTE_ATENCION; do
    usuario="reportes-$(echo "$rol" | tr 'A-Z_' 'a-z-')"
    api POST /api/admin/usuarios "{\"nombreUsuario\":\"$usuario\",\"nombreCompleto\":\"Prueba $rol\",\"password\":\"$ADMIN_PASSWORD\",\"rol\":\"$rol\"}" >/dev/null
    echo "✓ usuario $usuario"
done
# El criterio va antes que las compras: segmentación asigna el segmento al recibir cada métrica.
api POST /api/segmentacion/criterios \
    '{"nombre":"Alto valor","descripcion":"Valor acumulado de 500 o más","indicador":"VALOR","condicion":"MAYOR_IGUAL","minimo":500}' >/dev/null
echo "✓ criterio Alto valor"

publicar() { python3 "$REPO/herramientas/simulador-eventos/publicar.py" "$@" \
    --url "http://127.0.0.1:$PUERTO_MQ_ADMIN" --usuario "$MQ_USUARIO" --password "$MQ_PASSWORD"; }
# Las altas van antes que las compras: una compra de un cliente aún desconocido queda sin vincular.
publicar "$AQUI"/eventos/1-alta-*.json
for c in R1 R2 R3; do
    esperar "perfil de CLI-$c" 60 bash -c "curl -sf '$GW/api/perfil/clientes?idClienteOrigen=CLI-$c' \
        -H 'Authorization: Bearer $TOKEN' | python3 -c 'import json,sys; sys.exit(json.load(sys.stdin)[\"total\"] != 1)'"
done
publicar "$AQUI"/eventos/2-compra-*.json
# Eventos adicionales de otras historias (3-*.json), por ejemplo movimientos de puntos.
for extra in "$AQUI"/eventos/3-*.json; do
    if [[ -e "$extra" ]]; then publicar "$extra"; fi
done
esperar "4 compras procesadas" 90 bash -c "curl -sf '$GW/api/comportamiento/reportes/comportamiento?desde=2026-09-01&hasta=2026-09-30' \
    -H 'Authorization: Bearer $TOKEN' | python3 -c 'import json,sys; sys.exit(json.load(sys.stdin)[\"frecuencia\"][\"compras\"] != 4)'"
esperar "segmento Alto valor con 2 clientes" 90 bash -c "curl -sf '$GW/api/segmentacion/segmentos' \
    -H 'Authorization: Bearer $TOKEN' | python3 -c 'import json,sys; sys.exit(not any(s[\"segmento\"] == \"Alto valor\" and s[\"clientes\"] == 2 for s in json.load(sys.stdin)))'"

echo "== Frontend"
# exec: el PID guardado es el de node, no el de la subshell, para poder detenerlo al terminar.
( cd "$FRONTEND" && exec node node_modules/@angular/cli/bin/ng.js serve --host=127.0.0.1 --port=14200 \
    --proxy-config=proxy.e2e.json >"$LOGS/frontend.log" 2>&1 ) &
PIDS+=($!)
esperar "frontend en :14200" 180 curl -sf http://127.0.0.1:14200/login

echo "== Pruebas de navegador"
export E2E_URL=http://127.0.0.1:14200 E2E_USUARIO="$ADMIN_USUARIO" E2E_PASSWORD="$ADMIN_PASSWORD"
cd "$FRONTEND"
if [[ $# -gt 0 ]]; then npx playwright test "$@"; else npx playwright test e2e/scrum5*.spec.ts; fi

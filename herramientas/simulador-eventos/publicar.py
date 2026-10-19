#!/usr/bin/env python3
"""
Publica eventos de ejemplo en RabbitMQ, como si los enviara Marketplace y Ventas.

Usa la API HTTP del plugin de administración de RabbitMQ (puerto 15672), así que no necesita
librerías adicionales: solo Python 3. Con --pubsub publica en Google Cloud Pub/Sub (el emulador local
por defecto), en el tópico ventas.eventos con la ruta en el atributo "ruta" (docs/mensajeria/pubsub.md).

Ejemplos:
    python3 publicar.py eventos/compra-ana.json
    python3 publicar.py eventos/compra-ana.json --veces 2        # el segundo queda DESCARTADO
    python3 publicar.py eventos/compra-carlos.json --nuevo     # idEvento e idCompra nuevos
    python3 publicar.py eventos/cliente-ana-alta.json --nuevo    # otro cliente con los mismos datos
    python3 publicar.py eventos/cliente-ana-alta.json --ahora    # mismo cliente, cambio con fecha actual
    python3 publicar.py eventos/*.json eventos/mensaje-ilegible.txt # todos los ejemplos
    python3 publicar.py eventos/compra-ana.json --pubsub          # en el emulador de Pub/Sub
"""
import argparse
import base64
import datetime
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

EXCHANGE = "ventas.eventos"
RUTA_POR_TIPO = {
    "COMPRA_CONFIRMADA": "compra.confirmada",
    "COMPRA_ANULADA": "compra.anulada",
    "CLIENTE_REGISTRADO": "cliente.registrado",
    "CLIENTE_ACTUALIZADO": "cliente.actualizado",
    "MOVIMIENTO_PUNTOS": "puntos.movimiento",
}
RUTA_POR_DEFECTO = "compra.confirmada"


def contenido_a_publicar(ruta_archivo, nuevo, ahora):
    with open(ruta_archivo, encoding="utf-8") as archivo:
        texto = archivo.read()
    try:
        evento = json.loads(texto)
    except json.JSONDecodeError:
        return texto, RUTA_POR_DEFECTO
    if nuevo or ahora:
        evento["idEvento"] = str(uuid.uuid4())
    if nuevo:
        if isinstance(evento.get("compra"), dict) and evento["compra"].get("idCompra"):
            evento["compra"]["idCompra"] = f"{evento['compra']['idCompra']}-{uuid.uuid4().hex[:6]}"
        if isinstance(evento.get("anulacion"), dict) and evento["anulacion"].get("idAnulacion"):
            evento["anulacion"]["idAnulacion"] = f"{evento['anulacion']['idAnulacion']}-{uuid.uuid4().hex[:6]}"
        if isinstance(evento.get("movimiento"), dict) and evento["movimiento"].get("idTransaccion"):
            evento["movimiento"]["idTransaccion"] = f"{evento['movimiento']['idTransaccion']}-{uuid.uuid4().hex[:6]}"
        if isinstance(evento.get("cliente"), dict) and evento["cliente"].get("idCliente"):
            evento["cliente"]["idCliente"] = f"{evento['cliente']['idCliente']}-{uuid.uuid4().hex[:6]}"
    if ahora:
        momento = datetime.datetime.now(datetime.timezone.utc).astimezone().isoformat(timespec="milliseconds")
        evento["fechaEmision"] = momento
        if isinstance(evento.get("cliente"), dict):
            evento["cliente"]["fechaActualizacion"] = momento
    return json.dumps(evento, ensure_ascii=False), RUTA_POR_TIPO.get(evento.get("tipoEvento"), RUTA_POR_DEFECTO)


def publicar(args, contenido, routing_key):
    url = f"{args.url}/api/exchanges/{urllib.parse.quote(args.vhost, safe='')}/{EXCHANGE}/publish"
    cuerpo = json.dumps({
        "properties": {"content_type": "application/json", "delivery_mode": 2},
        "routing_key": routing_key,
        "payload": contenido,
        "payload_encoding": "string",
    }).encode("utf-8")
    credenciales = base64.b64encode(f"{args.usuario}:{args.password}".encode()).decode()
    solicitud = urllib.request.Request(url, data=cuerpo, method="POST", headers={
        "Content-Type": "application/json", "Authorization": f"Basic {credenciales}"})
    with urllib.request.urlopen(solicitud, timeout=10) as respuesta:
        return json.load(respuesta).get("routed", False)


def publicar_pubsub(args, contenido, ruta):
    url = f"{args.pubsub_url}/v1/projects/{args.proyecto}/topics/{EXCHANGE}:publish"
    cuerpo = json.dumps({"messages": [{
        "data": base64.b64encode(contenido.encode("utf-8")).decode(),
        "attributes": {"ruta": ruta, "contentType": "application/json"},
    }]}).encode("utf-8")
    solicitud = urllib.request.Request(url, data=cuerpo, method="POST",
                                       headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(solicitud, timeout=10) as respuesta:
        return bool(json.load(respuesta).get("messageIds"))


def main():
    parser = argparse.ArgumentParser(description="Publica eventos de ejemplo de Marketplace y Ventas en RabbitMQ.")
    parser.add_argument("archivos", nargs="+", help="archivos con el contenido de cada evento")
    parser.add_argument("--veces", type=int, default=1, help="cuántas veces publicar cada archivo (por defecto 1)")
    parser.add_argument("--nuevo", action="store_true",
                        help="genera identificadores nuevos (idEvento, idCompra, idTransaccion o idCliente) en cada publicación")
    parser.add_argument("--ahora", action="store_true",
                        help="usa la fecha actual como fecha del cambio (para que un evento de cliente se aplique)")
    parser.add_argument("--url", default="http://localhost:15672", help="API de administración de RabbitMQ")
    parser.add_argument("--vhost", default="/")
    parser.add_argument("--usuario", default="guest")
    parser.add_argument("--password", default="guest")
    parser.add_argument("--pubsub", action="store_true",
                        help="publica en Google Cloud Pub/Sub (emulador) en lugar de RabbitMQ")
    parser.add_argument("--pubsub-url", default="http://localhost:8085",
                        help="API REST del emulador de Pub/Sub (por defecto http://localhost:8085)")
    parser.add_argument("--proyecto", default="crm-maxiconecta", help="proyecto de Pub/Sub (PUBSUB_PROYECTO)")
    args = parser.parse_args()

    errores = 0
    for ruta_archivo in args.archivos:
        for _ in range(args.veces):
            contenido, routing_key = contenido_a_publicar(ruta_archivo, args.nuevo, args.ahora)
            try:
                enrutado = (publicar_pubsub if args.pubsub else publicar)(args, contenido, routing_key)
            except urllib.error.URLError as error:
                print(f"✗ {ruta_archivo}: no se pudo publicar ({error})", file=sys.stderr)
                errores += 1
                continue
            if enrutado:
                print(f"✓ {ruta_archivo} → {EXCHANGE} [{routing_key}]")
            else:
                print(f"✗ {ruta_archivo}: ninguna cola recibió el mensaje (¿está corriendo el servicio consumidor?)"
                      if not args.pubsub else f"✗ {ruta_archivo}: Pub/Sub no confirmó la publicación",
                      file=sys.stderr)
                errores += 1
    sys.exit(1 if errores else 0)


if __name__ == "__main__":
    main()

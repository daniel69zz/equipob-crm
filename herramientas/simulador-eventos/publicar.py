#!/usr/bin/env python3
"""
Publica eventos de ejemplo en RabbitMQ, como si los enviara Marketplace y Ventas.

Usa la API HTTP del plugin de administración de RabbitMQ (puerto 15672), así que no necesita
librerías adicionales: solo Python 3.

Ejemplos:
    python3 publicar.py eventos/compra-ventas.json
    python3 publicar.py eventos/compra-ventas.json --veces 2        # el segundo queda DESCARTADO
    python3 publicar.py eventos/compra-marketplace.json --nuevo     # idEvento e idCompra nuevos
    python3 publicar.py eventos/*.json eventos/mensaje-ilegible.txt # todos los ejemplos
"""
import argparse
import base64
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

EXCHANGE = "ventas.eventos"
RUTA_POR_TIPO = {"COMPRA_CONFIRMADA": "compra.confirmada"}
RUTA_POR_DEFECTO = "compra.confirmada"


def contenido_a_publicar(ruta_archivo, nuevo):
    with open(ruta_archivo, encoding="utf-8") as archivo:
        texto = archivo.read()
    try:
        evento = json.loads(texto)
    except json.JSONDecodeError:
        return texto, RUTA_POR_DEFECTO
    if nuevo:
        evento["idEvento"] = str(uuid.uuid4())
        if isinstance(evento.get("compra"), dict) and evento["compra"].get("idCompra"):
            evento["compra"]["idCompra"] = f"{evento['compra']['idCompra']}-{uuid.uuid4().hex[:6]}"
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


def main():
    parser = argparse.ArgumentParser(description="Publica eventos de ejemplo de Marketplace y Ventas en RabbitMQ.")
    parser.add_argument("archivos", nargs="+", help="archivos con el contenido de cada evento")
    parser.add_argument("--veces", type=int, default=1, help="cuántas veces publicar cada archivo (por defecto 1)")
    parser.add_argument("--nuevo", action="store_true", help="genera idEvento e idCompra nuevos en cada publicación")
    parser.add_argument("--url", default="http://localhost:15672", help="API de administración de RabbitMQ")
    parser.add_argument("--vhost", default="/")
    parser.add_argument("--usuario", default="guest")
    parser.add_argument("--password", default="guest")
    args = parser.parse_args()

    errores = 0
    for ruta_archivo in args.archivos:
        for _ in range(args.veces):
            contenido, routing_key = contenido_a_publicar(ruta_archivo, args.nuevo)
            try:
                enrutado = publicar(args, contenido, routing_key)
            except urllib.error.URLError as error:
                print(f"✗ {ruta_archivo}: no se pudo publicar ({error})", file=sys.stderr)
                errores += 1
                continue
            if enrutado:
                print(f"✓ {ruta_archivo} → {EXCHANGE} [{routing_key}]")
            else:
                print(f"✗ {ruta_archivo}: ninguna cola recibió el mensaje (¿está corriendo servicio-comportamiento?)",
                      file=sys.stderr)
                errores += 1
    sys.exit(1 if errores else 0)


if __name__ == "__main__":
    main()

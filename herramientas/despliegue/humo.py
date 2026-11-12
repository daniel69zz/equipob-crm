#!/usr/bin/env python3
"""Pruebas de humo del CRM desplegado (SCRUM-632): web, API, inicio de sesión, ficha del cliente y servicios.

Solo usa la biblioteca estándar de Python 3. No publica eventos ni toca Pub/Sub.

    CRM_USUARIO=admin CRM_PASSWORD=... python3 herramientas/despliegue/humo.py \\
        --web https://crm.ejemplo.com --api https://api.crm.ejemplo.com

En el propio VPS, sin DNS, se apunta al nginx local y se envía el dominio en la cabecera Host:

    python3 herramientas/despliegue/humo.py --web http://localhost --host-web crm.localhost \\
        --api http://localhost --host-api api.crm.localhost

Sale con código 0 si todo pasa y 1 si falla alguna comprobación.
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

# Una consulta de lectura por microservicio, a través del API Gateway (los servicios no se exponen).
SERVICIOS = {
    "servicio-perfil": "/api/perfil/clientes?tamanio=1",
    "servicio-comportamiento": "/api/comportamiento/parametros",
    "servicio-segmentacion": "/api/segmentacion/criterios",
    "servicio-fidelizacion": "/api/fidelizacion/parametros",
    "servicio-interacciones": "/api/interacciones/agentes",
}


class Destino:
    """Una URL base de nginx y, opcionalmente, el dominio que se envía en Host."""

    def __init__(self, base, host=None):
        self.base = base.rstrip("/")
        self.host = host

    def pedir(self, ruta, metodo="GET", cuerpo=None, token=None, tiempo=20):
        cabeceras = {"Accept": "application/json, text/html"}
        if self.host:
            cabeceras["Host"] = self.host
        if token:
            cabeceras["Authorization"] = f"Bearer {token}"
        datos = None
        if cuerpo is not None:
            datos = json.dumps(cuerpo).encode()
            cabeceras["Content-Type"] = "application/json"
        solicitud = urllib.request.Request(self.base + ruta, data=datos, headers=cabeceras, method=metodo)
        try:
            with urllib.request.urlopen(solicitud, timeout=tiempo) as r:
                return r.status, r.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode("utf-8", "replace")


class Resultado:
    def __init__(self):
        self.fallas = 0

    def comprobar(self, nombre, ok, detalle=""):
        print(f"{'OK  ' if ok else 'FALLA'} {nombre}" + (f" ({detalle})" if detalle else ""))
        if not ok:
            self.fallas += 1
        return ok


def esperar_api(api, segundos):
    """Tras un despliegue, el Gateway tarda en arrancar: se reintenta hasta el límite."""
    limite = time.time() + segundos
    while True:
        try:
            estado, cuerpo = api.pedir("/actuator/health", tiempo=5)
            if estado == 200 and '"UP"' in cuerpo:
                return True
        except (urllib.error.URLError, OSError):
            pass
        if time.time() >= limite:
            return False
        time.sleep(5)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--web", required=True, help="URL del sitio (DOMINIO_WEB)")
    p.add_argument("--api", required=True, help="URL de la API (DOMINIO_API)")
    p.add_argument("--host-web", help="dominio del sitio en la cabecera Host (sin DNS)")
    p.add_argument("--host-api", help="dominio de la API en la cabecera Host (sin DNS)")
    p.add_argument("--espera", type=int, default=180, help="segundos máximos esperando al Gateway")
    p.add_argument("--exigir-cliente", action="store_true",
                   help="falla si no hay ningún cliente para abrir su ficha")
    args = p.parse_args()
    usuario, clave = os.environ.get("CRM_USUARIO"), os.environ.get("CRM_PASSWORD")
    if not usuario or not clave:
        sys.exit("Defina CRM_USUARIO y CRM_PASSWORD (usuario con permiso de consulta de clientes).")

    web, api = Destino(args.web, args.host_web), Destino(args.api, args.host_api)
    r = Resultado()

    # Criterio 1: frontend y API publicados con URL accesible.
    if not r.comprobar("API: Gateway en estado UP", esperar_api(api, args.espera)):
        print("El Gateway no respondió; se omiten las demás comprobaciones.")
        return 1
    estado, cuerpo = web.pedir("/")
    r.comprobar("Web: carga la aplicación Angular", estado == 200 and "<app-root" in cuerpo, f"HTTP {estado}")
    estado, cuerpo = web.pedir("/clientes")
    r.comprobar("Web: una ruta de la SPA carga index.html", estado == 200 and "<app-root" in cuerpo,
                f"HTTP {estado}")

    # Seguridad: sin token no se consultan datos de clientes.
    estado, _ = api.pedir("/api/perfil/clientes?tamanio=1")
    r.comprobar("API: sin token responde 401", estado == 401, f"HTTP {estado}")

    # Criterio 2: inicio de sesión desde el frontend público y ficha de un cliente.
    estado, cuerpo = web.pedir("/api/auth/login", "POST", {"usuario": usuario, "password": clave})
    token = json.loads(cuerpo).get("token") if estado == 200 else None
    if not r.comprobar("Web: inicio de sesión por /api del sitio", bool(token), f"HTTP {estado}"):
        return 1
    estado, cuerpo = api.pedir("/api/auth/login", "POST", {"usuario": usuario, "password": clave})
    r.comprobar("API: inicio de sesión en el dominio de la API", estado == 200, f"HTTP {estado}")

    estado, cuerpo = web.pedir("/api/perfil/clientes?tamanio=1", token=token)
    clientes = json.loads(cuerpo).get("clientes", []) if estado == 200 else []
    r.comprobar("Web: consulta el listado de clientes", estado == 200, f"HTTP {estado}")
    if clientes:
        cliente = clientes[0]["id"]
        estado, _ = web.pedir(f"/api/perfil/clientes/{cliente}", token=token)
        r.comprobar("Web: abre la ficha de un cliente", estado == 200, f"cliente {cliente}, HTTP {estado}")
    else:
        r.comprobar("Web: abre la ficha de un cliente", not args.exigir_cliente,
                    "no hay clientes cargados; ficha no verificada")

    # Cada microservicio responde a través del Gateway (5xx: caído o inaccesible).
    for servicio, ruta in SERVICIOS.items():
        estado, _ = api.pedir(ruta, token=token)
        r.comprobar(f"API → {servicio}", estado < 500, f"HTTP {estado}")

    print(f"\n{'Todas las comprobaciones pasaron.' if r.fallas == 0 else f'{r.fallas} comprobación(es) fallaron.'}")
    return 0 if r.fallas == 0 else 1


if __name__ == "__main__":
    sys.exit(main())

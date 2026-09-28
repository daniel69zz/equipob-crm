# crm-web — Aplicación Web del CRM

Aplicación **Angular 18** (componentes standalone) para el personal interno: Administrador de CRM, Agente de Atención al Cliente y Gerente Comercial.

Consume los microservicios **solo a través del API Gateway**. En desarrollo, `proxy.conf.json` redirige `/api` al Gateway en `http://localhost:8080`.

## Ejecutar en local

Requisitos: Node 18 o superior y el API Gateway en ejecución.

```bash
npm install
npm start
```

La aplicación queda en `http://localhost:4200`.

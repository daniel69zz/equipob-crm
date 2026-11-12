# Frontend Angular ya compilado (ng build) servido por Caddy. La entrada pública es el nginx del VPS.
FROM caddy:2.8.4-alpine
COPY docker/Caddyfile /etc/caddy/Caddyfile
COPY frontend/crm-web/dist/crm-web/browser /srv

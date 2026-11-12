# Nginx de entrada con el frontend Angular ya compilado (ng build): enruta por dominio al sitio web y a la API.
FROM nginx:1.27-alpine
RUN rm /etc/nginx/conf.d/default.conf
COPY docker/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY docker/proxy-cabeceras.inc /etc/nginx/conf.d/proxy-cabeceras.inc
COPY frontend/crm-web/dist/crm-web/browser /usr/share/nginx/html

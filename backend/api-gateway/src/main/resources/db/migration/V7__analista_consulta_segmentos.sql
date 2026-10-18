-- SCRUM-278: analista de consulta. No recibe permisos de configuración ni de ficha integral.
INSERT INTO seguridad.rol (codigo, nombre, descripcion)
VALUES ('ANALISTA_DATOS', 'Analista de Datos', 'Consulta clientes por segmento y nivel de consumo')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO seguridad.rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM seguridad.rol r CROSS JOIN seguridad.permiso p
WHERE r.codigo = 'ANALISTA_DATOS' AND p.codigo = 'SEGMENTOS_CONSULTAR'
ON CONFLICT DO NOTHING;

INSERT INTO seguridad.permiso(codigo, descripcion)
VALUES ('REPORTES_PROGRAMAR', 'Programar reportes periódicos y consultar sus ejecuciones');
INSERT INTO seguridad.rol_permiso(rol_id, permiso_id)
SELECT r.id,p.id FROM seguridad.rol r CROSS JOIN seguridad.permiso p
WHERE r.codigo IN ('ADMINISTRADOR_CRM','GERENTE_COMERCIAL') AND p.codigo='REPORTES_PROGRAMAR';

-- SCRUM-520: permiso para consultar y cambiar los parámetros generales del CRM (SCRUM-65): periodo de inactividad,
-- umbrales del nivel de consumo y vigencia de los puntos. Solo lo tiene el Administrador de CRM; los servicios
-- exigen además ese rol.
INSERT INTO seguridad.permiso(codigo, descripcion)
VALUES ('PARAMETROS_CONFIGURAR', 'Configurar los parámetros generales del CRM');
INSERT INTO seguridad.rol_permiso(rol_id, permiso_id)
SELECT r.id, p.id FROM seguridad.rol r CROSS JOIN seguridad.permiso p
WHERE r.codigo = 'ADMINISTRADOR_CRM' AND p.codigo = 'PARAMETROS_CONFIGURAR';

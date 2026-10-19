-- SCRUM-383 · Permiso propio de la sugerencia de categorias afines (SCRUM-44): solo Administrador
-- de CRM y Gerente Comercial, los perfiles de analisis comercial. No lo tiene el Agente de Atencion.

INSERT INTO seguridad.permiso (codigo, descripcion) VALUES
    ('SUGERENCIAS_CONSULTAR', 'Consultar las categorias afines sugeridas para un cliente');

INSERT INTO seguridad.rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM seguridad.rol r CROSS JOIN seguridad.permiso p
WHERE r.codigo IN ('ADMINISTRADOR_CRM', 'GERENTE_COMERCIAL')
  AND p.codigo = 'SUGERENCIAS_CONSULTAR';

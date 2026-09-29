-- SCRUM-153 · Permiso propio de la ficha integral del cliente (SCRUM-10): solo Administrador de
-- CRM y Agente de Atencion al Cliente, a diferencia de CLIENTE_CONSULTAR (que tambien tiene
-- Gerente Comercial y solo cubre el perfil basico).

INSERT INTO seguridad.permiso (codigo, descripcion) VALUES
    ('FICHA_INTEGRAL_CONSULTAR', 'Consultar la ficha integral del cliente');

INSERT INTO seguridad.rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM seguridad.rol r CROSS JOIN seguridad.permiso p
WHERE r.codigo IN ('ADMINISTRADOR_CRM', 'AGENTE_ATENCION')
  AND p.codigo = 'FICHA_INTEGRAL_CONSULTAR';

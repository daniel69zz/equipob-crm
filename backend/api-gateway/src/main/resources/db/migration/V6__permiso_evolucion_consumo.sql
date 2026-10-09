-- SCRUM-305 · Permiso propio de la evolucion del consumo por categoria (SCRUM-32): solo
-- Administrador de CRM y Gerente Comercial, los perfiles de analisis comercial. A diferencia de
-- INDICADORES_CONSULTAR, no lo tiene el Agente de Atencion al Cliente.

INSERT INTO seguridad.permiso (codigo, descripcion) VALUES
    ('EVOLUCION_CONSUMO_CONSULTAR', 'Consultar la evolucion del consumo del cliente por categoria');

INSERT INTO seguridad.rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM seguridad.rol r CROSS JOIN seguridad.permiso p
WHERE r.codigo IN ('ADMINISTRADOR_CRM', 'GERENTE_COMERCIAL')
  AND p.codigo = 'EVOLUCION_CONSUMO_CONSULTAR';

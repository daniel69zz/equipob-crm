-- SCRUM-462 · Permiso propio de los reportes de clientes (EPIC-10, SCRUM-57): solo Administrador de
-- CRM y Gerente Comercial, los perfiles de analisis comercial. No lo tiene el Agente de Atencion al
-- Cliente, que por eso recibe 403 al intentar abrir el reporte (criterio de aceptacion 3).
-- Es mas restrictivo que SEGMENTOS_CONSULTAR, igual que SUGERENCIAS_CONSULTAR en V7.

INSERT INTO seguridad.permiso (codigo, descripcion) VALUES
    ('REPORTES_CONSULTAR', 'Consultar los reportes de distribucion de clientes');

INSERT INTO seguridad.rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM seguridad.rol r CROSS JOIN seguridad.permiso p
WHERE r.codigo IN ('ADMINISTRADOR_CRM', 'GERENTE_COMERCIAL')
  AND p.codigo = 'REPORTES_CONSULTAR';

-- SCRUM-482 · Permiso propio para exportar los reportes de clientes a CSV y PDF (SCRUM-60). Lo tienen el
-- Administrador de CRM y el Gerente Comercial (perfil de analisis comercial y de datos). Es aparte de
-- REPORTES_CONSULTAR para que el Administrador pueda quitar la exportacion a un rol sin quitarle la consulta:
-- un rol sin este permiso recibe 403 al exportar (criterio de aceptacion 2).

INSERT INTO seguridad.permiso (codigo, descripcion) VALUES
    ('REPORTES_EXPORTAR', 'Exportar los reportes de clientes a CSV y PDF');

INSERT INTO seguridad.rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM seguridad.rol r CROSS JOIN seguridad.permiso p
WHERE r.codigo IN ('ADMINISTRADOR_CRM', 'GERENTE_COMERCIAL')
  AND p.codigo = 'REPORTES_EXPORTAR';

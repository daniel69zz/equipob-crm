-- Solo se ejecuta contra el contenedor temporal creado por ejecutar.ps1.
INSERT INTO perfil.cliente (id, nombres, apellidos, tipo_documento, numero_documento, estado)
VALUES (4301, 'Ana', 'Preferencias', 'CI', '4301', 'COMPLETO'),
       (4302, 'Bruno', 'Sin preferencias', 'CI', '4302', 'COMPLETO'),
       (4303, 'Carla', 'Pendiente', 'CI', '4303', 'COMPLETO'),
       (4304, 'Diego', 'Categoria unica', 'CI', '4304', 'COMPLETO'),
       (4305, 'Elena', 'Consolidada', 'CI', '4305', 'COMPLETO');
UPDATE perfil.cliente SET id_cliente_consolidado=4301 WHERE id=4305;
INSERT INTO comportamiento.perfil_preferencias (cliente_id, construido_en, version, criterio)
VALUES (4301, '2026-10-18T12:00:00Z', 2, 'MONTO_DESC_COMPRAS_DESC_NOMBRE_ASC_V1'),
       (4302, '2026-10-18T12:00:00Z', 1, 'MONTO_DESC_COMPRAS_DESC_NOMBRE_ASC_V1'),
       (4304, '2026-10-18T12:00:00Z', 1, 'MONTO_DESC_COMPRAS_DESC_NOMBRE_ASC_V1');
INSERT INTO comportamiento.preferencia_categoria (cliente_id, categoria, posicion, compras, unidades, monto)
VALUES (4301, 'Hogar', 2, 2, 4, 120.00), (4301, 'Deportes', 3, 1, 1, 40.00),
       (4301, 'Electrónica', 1, 1, 1, 300.00), (4304, 'Alimentos', 1, 1, 2, 50.00);

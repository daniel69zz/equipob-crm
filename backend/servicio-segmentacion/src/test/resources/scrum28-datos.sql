-- Exclusivamente para bases aisladas de pruebas. Nunca cargar en la base de operación.
INSERT INTO segmentacion.criterio (id, nombre, nombre_clave, indicador, condicion, minimo, maximo, actualizado_por)
VALUES (28001, 'Frecuentes SCRUM-28', 'frecuentes scrum-28', 'FRECUENCIA', 'MAYOR_IGUAL', 1, NULL, 'pruebas'),
       (28002, 'Sin asignaciones SCRUM-28', 'sin asignaciones scrum-28', 'VALOR', 'MAYOR_IGUAL', 999999, NULL, 'pruebas');
INSERT INTO perfil.cliente (id, nombres, apellidos, estado)
VALUES (280001, 'Ana', 'Unificada', 'COMPLETO'),
       (280002, 'Bruno', 'Sin compras', 'INCOMPLETO'),
       (280003, 'Carla', 'Compra anulada', 'COMPLETO'),
       (280004, 'Perfil', 'Absorbido', 'COMPLETO'),
       (280005, 'Elena', 'Segmento anterior', 'COMPLETO');
UPDATE perfil.cliente SET id_cliente_consolidado = 280001 WHERE id = 280004;
INSERT INTO perfil.cliente (id, nombres, apellidos, estado)
SELECT 280100 + n, 'Cliente ' || lpad(n::text, 3, '0'), 'Paginación', 'COMPLETO'
FROM generate_series(1, 125) n;
INSERT INTO perfil.cliente_origen (id_cliente_origen, id_cliente)
VALUES ('S28-ANA-A', 280001), ('S28-ANA-B', 280001), ('S28-CARLA', 280003), ('S28-ELENA', 280005);
INSERT INTO perfil.cliente_origen (id_cliente_origen, id_cliente)
SELECT 'S28-' || n, 280100 + n FROM generate_series(1, 125) n;
INSERT INTO segmentacion.segmento_cliente (cliente_id, segmento, criterio_id)
SELECT id, 'Frecuentes SCRUM-28', 28001 FROM perfil.cliente
WHERE id BETWEEN 280001 AND 280225 AND id <> 280005;
INSERT INTO segmentacion.segmento_cliente (cliente_id, segmento) VALUES (280005, 'Segmento anterior SCRUM-28');
INSERT INTO comportamiento.evento_recibido (id, tipo_evento, estado, contenido)
VALUES (280001, 'COMPRA_CONFIRMADA', 'PROCESADO', '{}');
INSERT INTO comportamiento.compra (id_compra_origen, id_cliente_origen, fecha, monto_total, monto_revertido, estado, id_evento)
VALUES ('S28-C-A', 'S28-ANA-A', '2026-10-10T12:00:00Z', 300, 0, 'CONFIRMADA', 280001),
       ('S28-C-B', 'S28-ANA-B', '2026-10-11T12:00:00Z', 400, 100, 'DEVOLUCION_PARCIAL', 280001),
       ('S28-C-C', 'S28-CARLA', '2026-10-15T12:00:00Z', 6000, 6000, 'ANULADA', 280001),
       ('S28-C-E', 'S28-ELENA', '2026-10-12T12:00:00Z', 5000, 0, 'CONFIRMADA', 280001);
INSERT INTO comportamiento.compra (id_compra_origen, id_cliente_origen, fecha, monto_total, id_evento)
SELECT 'S28-C-' || n, 'S28-' || n, '2026-10-01T12:00:00Z'::timestamptz + n * interval '1 minute', 1000, 280001
FROM generate_series(1, 125) n;

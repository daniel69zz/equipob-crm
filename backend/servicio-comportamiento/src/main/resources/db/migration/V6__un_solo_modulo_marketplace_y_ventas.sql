-- El CRM se integra con un solo módulo del ERP: Marketplace y Ventas. Las compras, anulaciones y
-- claves de idempotencia se identifican solo por su identificador en ese módulo.
-- Contratos: docs/contratos-eventos/RIO-CRM-02-compra-confirmada.md y RIO-CRM-05-anulacion-compra.md

-- Si el mismo identificador se había usado en los dos canales, el repetido conserva sus datos con
-- el canal como sufijo, para que ninguna compra ni anulación se pierda.
UPDATE comportamiento.compra c
SET id_compra_origen = c.id_compra_origen || '-' || c.origen
WHERE EXISTS (SELECT 1 FROM comportamiento.compra o
              WHERE o.id_compra_origen = c.id_compra_origen AND o.id < c.id);

UPDATE comportamiento.anulacion a
SET id_anulacion_origen = a.id_anulacion_origen || '-' || a.origen
WHERE EXISTS (SELECT 1 FROM comportamiento.anulacion o
              WHERE o.id_anulacion_origen = a.id_anulacion_origen AND o.id < a.id);

-- Compras: única por su identificador en el módulo.
DROP INDEX IF EXISTS comportamiento.idx_compra_cliente;
DROP INDEX IF EXISTS comportamiento.idx_compra_vinculacion_pendiente;
ALTER TABLE comportamiento.compra DROP CONSTRAINT uq_compra_origen;
ALTER TABLE comportamiento.compra DROP COLUMN origen;
ALTER TABLE comportamiento.compra ADD CONSTRAINT uq_compra_origen UNIQUE (id_compra_origen);
CREATE INDEX idx_compra_cliente ON comportamiento.compra (id_cliente_origen, fecha DESC);
CREATE INDEX idx_compra_vinculacion_pendiente ON comportamiento.compra (id_cliente_origen)
    WHERE estado_vinculacion = 'PENDIENTE';

-- Anulaciones: únicas por su identificador en el módulo.
ALTER TABLE comportamiento.anulacion DROP CONSTRAINT uq_anulacion_origen;
ALTER TABLE comportamiento.anulacion DROP COLUMN origen;
ALTER TABLE comportamiento.anulacion ADD CONSTRAINT uq_anulacion_origen UNIQUE (id_anulacion_origen);

-- Idempotencia: tipo de evento + identificador de la transacción. Si una transacción había quedado
-- registrada por los dos canales, se conserva la primera.
DELETE FROM comportamiento.evento_procesado p
USING comportamiento.evento_procesado o
WHERE p.tipo_evento = o.tipo_evento
  AND p.id_transaccion = o.id_transaccion
  AND (p.procesado_en > o.procesado_en OR (p.procesado_en = o.procesado_en AND p.origen > o.origen));
ALTER TABLE comportamiento.evento_procesado DROP CONSTRAINT pk_evento_procesado;
ALTER TABLE comportamiento.evento_procesado DROP COLUMN origen;
ALTER TABLE comportamiento.evento_procesado ADD CONSTRAINT pk_evento_procesado PRIMARY KEY (tipo_evento, id_transaccion);

-- Bitácora de ingesta: todos los mensajes vienen del mismo módulo.
ALTER TABLE comportamiento.evento_recibido DROP COLUMN origen;

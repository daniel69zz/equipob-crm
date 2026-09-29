-- El CRM se integra con un solo módulo del ERP: Marketplace y Ventas. Los datos dejan de
-- distinguir los canales MARKETPLACE y VENTAS; lo que venía del módulo pasa a MARKETPLACE_VENTAS
-- y las acciones internas siguen siendo CRM.
-- Modelo: docs/perfil/identificadores-origen.md y docs/perfil/mapeo-datos-perfil.md

-- Identificadores del cliente: uno por identificador del módulo. Si el mismo identificador estaba
-- vinculado por los dos canales, se conserva el vínculo más antiguo.
DELETE FROM perfil.cliente_origen a
USING perfil.cliente_origen b
WHERE a.id_cliente_origen = b.id_cliente_origen
  AND (a.fecha_vinculacion > b.fecha_vinculacion
       OR (a.fecha_vinculacion = b.fecha_vinculacion AND a.origen > b.origen));
ALTER TABLE perfil.cliente_origen DROP CONSTRAINT pk_cliente_origen;
ALTER TABLE perfil.cliente_origen DROP COLUMN origen;
ALTER TABLE perfil.cliente_origen ADD CONSTRAINT pk_cliente_origen PRIMARY KEY (id_cliente_origen);

-- Vinculaciones pendientes: igual criterio, se conserva la detectada primero.
DELETE FROM perfil.vinculacion_pendiente a
USING perfil.vinculacion_pendiente b
WHERE a.id_cliente_origen = b.id_cliente_origen
  AND (a.detectada_en > b.detectada_en
       OR (a.detectada_en = b.detectada_en AND a.origen > b.origen));
ALTER TABLE perfil.vinculacion_pendiente DROP CONSTRAINT pk_vinculacion_pendiente;
ALTER TABLE perfil.vinculacion_pendiente DROP COLUMN origen;
ALTER TABLE perfil.vinculacion_pendiente ADD CONSTRAINT pk_vinculacion_pendiente PRIMARY KEY (id_cliente_origen);

-- Direcciones: se identifican por su código en el módulo. Si un código se repetía, queda la primera.
DELETE FROM perfil.direccion a
USING perfil.direccion b
WHERE a.id_cliente = b.id_cliente
  AND a.id_direccion_origen = b.id_direccion_origen
  AND a.id > b.id;
ALTER TABLE perfil.direccion DROP CONSTRAINT uq_direccion_origen;
ALTER TABLE perfil.direccion DROP COLUMN origen;
ALTER TABLE perfil.direccion ADD CONSTRAINT uq_direccion_origen UNIQUE (id_cliente, id_direccion_origen);

-- Bitácora de sincronización: todos los mensajes vienen del mismo módulo.
DROP INDEX perfil.idx_evento_cliente_origen;
ALTER TABLE perfil.evento_cliente DROP COLUMN origen;
CREATE INDEX idx_evento_cliente_identificador ON perfil.evento_cliente (id_cliente_origen, estado);

-- Sin dos canales no hay conflictos entre ellos.
DROP TABLE perfil.conflicto_perfil;
DROP TABLE perfil.campo_origen;

-- Origen del último cambio del perfil.
ALTER TABLE perfil.cliente ALTER COLUMN actualizado_por_origen TYPE VARCHAR(20);
UPDATE perfil.cliente SET actualizado_por_origen = 'MARKETPLACE_VENTAS'
WHERE actualizado_por_origen IN ('MARKETPLACE', 'VENTAS');

-- Histórico de cambios: es de solo lectura, así que sus triggers se suspenden solo para
-- reetiquetar el origen; los datos del cambio no se tocan.
ALTER TABLE perfil.cambio_perfil ALTER COLUMN origen TYPE VARCHAR(20);
ALTER TABLE perfil.cambio_perfil DISABLE TRIGGER cambio_perfil_solo_lectura;
UPDATE perfil.cambio_perfil SET origen = 'MARKETPLACE_VENTAS' WHERE origen IN ('MARKETPLACE', 'VENTAS');
ALTER TABLE perfil.cambio_perfil ENABLE TRIGGER cambio_perfil_solo_lectura;

-- Detalle del histórico: los identificadores se guardaban como "CANAL/identificador".
ALTER TABLE perfil.cambio_perfil_detalle DISABLE TRIGGER cambio_perfil_detalle_solo_lectura;
UPDATE perfil.cambio_perfil_detalle
SET valor_anterior = regexp_replace(valor_anterior, '^(MARKETPLACE|VENTAS)/', ''),
    valor_nuevo    = regexp_replace(valor_nuevo, '^(MARKETPLACE|VENTAS)/', '')
WHERE campo = 'identificadoresOrigen';
ALTER TABLE perfil.cambio_perfil_detalle ENABLE TRIGGER cambio_perfil_detalle_solo_lectura;

-- Consentimiento: el canal MARKETPLACE_VENTAS reemplaza a MARKETPLACE y VENTAS.
ALTER TABLE perfil.consentimiento DROP CONSTRAINT consentimiento_canal_check;
ALTER TABLE perfil.consentimiento ALTER COLUMN canal TYPE VARCHAR(20);
UPDATE perfil.consentimiento SET canal = 'MARKETPLACE_VENTAS' WHERE canal IN ('MARKETPLACE', 'VENTAS');
ALTER TABLE perfil.consentimiento ADD CONSTRAINT consentimiento_canal_check
    CHECK (canal IN ('MARKETPLACE_VENTAS', 'PRESENCIAL', 'TELEFONICO', 'CORREO'));

ALTER TABLE perfil.consentimiento_historial ALTER COLUMN canal TYPE VARCHAR(20);
ALTER TABLE perfil.consentimiento_historial DISABLE TRIGGER consentimiento_historial_solo_lectura;
UPDATE perfil.consentimiento_historial SET canal = 'MARKETPLACE_VENTAS' WHERE canal IN ('MARKETPLACE', 'VENTAS');
ALTER TABLE perfil.consentimiento_historial ENABLE TRIGGER consentimiento_historial_solo_lectura;

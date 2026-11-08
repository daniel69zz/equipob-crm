-- SCRUM-51 · Ajuste manual del saldo de puntos por el Administrador de CRM.
-- Modelo de auditoría y reglas en docs/fidelizacion/ajuste-manual-puntos.md.

-- SCRUM-422: el ajuste es un movimiento más del historial. Como la caducidad, no viene de un evento ni de un
-- cliente de Marketplace y Ventas.
ALTER TABLE fidelizacion.movimiento_puntos
    DROP CONSTRAINT movimiento_puntos_tipo_movimiento_check,
    ADD CONSTRAINT movimiento_puntos_tipo_movimiento_check
        CHECK (tipo_movimiento IN ('ACUMULACION', 'REDENCION', 'REVERSA', 'CADUCIDAD', 'AJUSTE')),
    DROP CONSTRAINT movimiento_puntos_evento_obligatorio,
    ADD CONSTRAINT movimiento_puntos_evento_obligatorio CHECK (
        id_evento_movimiento IS NOT NULL OR tipo_movimiento IN ('CADUCIDAD', 'AJUSTE')),
    ALTER COLUMN id_cliente_origen DROP NOT NULL,
    -- Una caducidad cuya vigencia renovó un ajuste tampoco tiene cliente de Ventas.
    ADD CONSTRAINT movimiento_puntos_cliente_origen_obligatorio CHECK (
        id_cliente_origen IS NOT NULL OR tipo_movimiento IN ('CADUCIDAD', 'AJUSTE')),
    -- Variación con signo del ajuste; puntos guarda su valor absoluto, como en los demás movimientos.
    ADD COLUMN variacion_ajuste BIGINT;

ALTER TABLE fidelizacion.movimiento_puntos
    ADD CONSTRAINT movimiento_puntos_ajuste_completo CHECK (
        (tipo_movimiento = 'AJUSTE') = (variacion_ajuste IS NOT NULL)),
    ADD CONSTRAINT movimiento_puntos_ajuste_valido CHECK (
        tipo_movimiento <> 'AJUSTE' OR (variacion_ajuste <> 0 AND puntos = abs(variacion_ajuste)
                                        AND id_evento_movimiento IS NULL AND valor_consumido IS NULL
                                        AND version_regla IS NULL));

ALTER TABLE fidelizacion.cambio_nivel
    DROP CONSTRAINT cambio_nivel_tipo_movimiento_check,
    ADD CONSTRAINT cambio_nivel_tipo_movimiento_check
        CHECK (tipo_movimiento IN ('ACUMULACION', 'REDENCION', 'REVERSA', 'CADUCIDAD', 'AJUSTE'));

-- SCRUM-421: registro de cada ajuste con su motivo, responsable y fecha. Es de solo inserción.
CREATE TABLE fidelizacion.ajuste_puntos (
    id                BIGSERIAL     PRIMARY KEY,
    -- Lo genera la pantalla al confirmar: un doble clic o un reintento no aplica dos veces el ajuste.
    id_solicitud      UUID          NOT NULL UNIQUE,
    id_transaccion    VARCHAR(64)   NOT NULL UNIQUE,
    cliente_id        BIGINT        NOT NULL,
    puntos            BIGINT        NOT NULL CHECK (puntos <> 0),
    saldo_anterior    BIGINT        NOT NULL CHECK (saldo_anterior >= 0),
    saldo_resultante  BIGINT        NOT NULL CHECK (saldo_resultante >= 0),
    motivo            VARCHAR(250)  NOT NULL CHECK (char_length(btrim(motivo)) >= 10),
    referencia        VARCHAR(64),
    usuario           VARCHAR(100)  NOT NULL,
    rol               VARCHAR(60),
    registrado_en     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (saldo_resultante = saldo_anterior + puntos)
);
CREATE INDEX idx_ajuste_puntos_cliente ON fidelizacion.ajuste_puntos (cliente_id, registrado_en DESC, id DESC);

CREATE FUNCTION fidelizacion.impedir_cambio_ajuste() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Los ajustes de puntos son de solo lectura';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ajuste_puntos_solo_lectura
    BEFORE UPDATE OR DELETE ON fidelizacion.ajuste_puntos
    FOR EACH ROW EXECUTE FUNCTION fidelizacion.impedir_cambio_ajuste();

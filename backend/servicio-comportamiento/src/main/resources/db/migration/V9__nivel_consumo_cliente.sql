-- SCRUM-267 · Persistencia del nivel de consumo de cada cliente (SCRUM-27, RF-19)
-- Diseño y umbrales: docs/compra/nivel-consumo.md
-- Una fila por id_cliente_origen con compras vigentes. Un cliente sin compras vigentes queda "sin
-- nivel": no tiene fila, así que valor_acumulado siempre es mayor que cero.

CREATE TABLE comportamiento.cliente_nivel_consumo (
    id_cliente_origen VARCHAR(64)   PRIMARY KEY,
    nivel             VARCHAR(10)   NOT NULL CHECK (nivel IN ('BAJO', 'MEDIO', 'ALTO')),
    valor_acumulado   NUMERIC(14,2) NOT NULL CHECK (valor_acumulado > 0),
    actualizado_en    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Para listar los clientes de un nivel, del que más aporta al que menos (GET .../clientes?nivelConsumo=ALTO).
CREATE INDEX idx_cliente_nivel_consumo_nivel_valor
    ON comportamiento.cliente_nivel_consumo (nivel, valor_acumulado DESC);

-- Clasifica a los clientes que ya tienen compras vigentes con los umbrales por defecto (500 y 5000);
-- la ejecución programada vuelve a clasificarlos con los umbrales configurados.
INSERT INTO comportamiento.cliente_nivel_consumo (id_cliente_origen, nivel, valor_acumulado, actualizado_en)
SELECT id_cliente_origen,
       CASE WHEN SUM(monto_total - monto_revertido) >= 5000 THEN 'ALTO'
            WHEN SUM(monto_total - monto_revertido) >= 500 THEN 'MEDIO'
            ELSE 'BAJO' END,
       SUM(monto_total - monto_revertido),
       now()
FROM comportamiento.compra
WHERE monto_total - monto_revertido > 0
GROUP BY id_cliente_origen;

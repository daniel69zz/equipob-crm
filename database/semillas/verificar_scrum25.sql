-- Solo lectura. Ejecutar despues de scrum25_100_clientes.sql.
BEGIN TRANSACTION READ ONLY;
DO $validacion$
BEGIN
    IF (SELECT count(*) FROM perfil.cliente_origen WHERE id_cliente_origen LIKE 'TEST-S25-CLI-%') <> 100
       OR (SELECT count(*) FROM comportamiento.compra WHERE id_compra_origen LIKE 'TEST-S25-COM-%') <> 741
       OR (SELECT count(*) FROM comportamiento.compra_item i JOIN comportamiento.compra c ON c.id = i.id_compra
           WHERE c.id_compra_origen LIKE 'TEST-S25-COM-%') <> 1482 THEN
        RAISE EXCEPTION 'Cantidades inesperadas de clientes, compras o items';
    END IF;

    IF EXISTS (
        SELECT 1 FROM perfil.cliente_origen o
        LEFT JOIN perfil.cliente p ON p.id = o.id_cliente
        LEFT JOIN comportamiento.frecuencia_compra f ON f.id_cliente_origen = o.id_cliente_origen
        WHERE o.id_cliente_origen LIKE 'TEST-S25-CLI-%'
          AND (p.id IS NULL OR f.cantidad IS DISTINCT FROM
               (SELECT count(*) FROM comportamiento.compra c
                WHERE c.id_cliente_origen = o.id_cliente_origen AND c.monto_total > c.monto_revertido))
    ) THEN
        RAISE EXCEPTION 'Perfil o contador de frecuencia inconsistente';
    END IF;

    IF EXISTS (
        SELECT 1 FROM comportamiento.compra c
        LEFT JOIN perfil.cliente_origen o ON o.id_cliente_origen = c.id_cliente_origen
        LEFT JOIN comportamiento.evento_procesado e
            ON e.tipo_evento = 'COMPRA_CONFIRMADA' AND e.id_transaccion = c.id_compra_origen
        JOIN comportamiento.evento_recibido r ON r.id = c.id_evento
        WHERE c.id_compra_origen LIKE 'TEST-S25-COM-%'
          AND (c.id_cliente IS DISTINCT FROM o.id_cliente OR c.estado_vinculacion <> 'VINCULADA'
               OR c.monto_total IS DISTINCT FROM
                   (SELECT sum(i.monto) FROM comportamiento.compra_item i WHERE i.id_compra = c.id)
               OR e.id_evento_recibido IS DISTINCT FROM c.id_evento
               OR r.contenido::jsonb #>> '{compra,idCompra}' IS DISTINCT FROM c.id_compra_origen
               OR (r.contenido::jsonb #>> '{compra,montoTotal}')::numeric IS DISTINCT FROM c.monto_total)
    ) THEN
        RAISE EXCEPTION 'Compra con vinculacion, monto o evento inconsistente';
    END IF;

    IF EXISTS (
        SELECT 1 FROM perfil.cliente_origen o
        WHERE o.id_cliente_origen LIKE 'TEST-S25-CLI-%' AND NOT EXISTS (
            SELECT 1 FROM perfil.consentimiento c
            JOIN perfil.consentimiento_alcance a ON a.id_cliente = c.id_cliente
            WHERE c.id_cliente = o.id_cliente AND c.estado = 'OTORGADO'
              AND c.vigencia_desde <= current_date
              AND (c.vigencia_hasta IS NULL OR c.vigencia_hasta >= current_date)
              AND a.alcance = 'SEGMENTACION'
        )
    ) THEN
        RAISE EXCEPTION 'Cliente sin consentimiento vigente para segmentacion';
    END IF;
END;
$validacion$;

-- Resultado esperado: SIN_COMPRAS = 10, BAJA = 30, MEDIA = 30, ALTA = 30.
SELECT CASE WHEN f.cantidad = 0 THEN 'SIN_COMPRAS'
            WHEN f.cantidad BETWEEN 1 AND 3 THEN 'BAJA'
            WHEN f.cantidad BETWEEN 5 AND 9 THEN 'MEDIA'
            WHEN f.cantidad BETWEEN 12 AND 20 THEN 'ALTA'
            ELSE 'INESPERADA' END AS grupo_de_prueba,
       count(*) AS clientes, sum(f.cantidad) AS compras
FROM comportamiento.frecuencia_compra f
WHERE f.id_cliente_origen LIKE 'TEST-S25-CLI-%'
GROUP BY 1 ORDER BY 1;

-- Indicadores para comparar con la pantalla de segmentacion (sin umbrales de negocio).
SELECT o.id_cliente_origen, p.id AS id_cliente, p.nombres, p.apellidos,
       f.cantidad AS frecuencia, coalesce(sum(c.monto_total - c.monto_revertido), 0) AS valor_acumulado,
       round(avg(c.monto_total - c.monto_revertido), 2) AS ticket_promedio,
       max(c.fecha) AS ultima_compra
FROM perfil.cliente_origen o
JOIN perfil.cliente p ON p.id = o.id_cliente
JOIN comportamiento.frecuencia_compra f ON f.id_cliente_origen = o.id_cliente_origen
LEFT JOIN comportamiento.compra c ON c.id_cliente_origen = o.id_cliente_origen
WHERE o.id_cliente_origen LIKE 'TEST-S25-CLI-%'
GROUP BY o.id_cliente_origen, p.id, p.nombres, p.apellidos, f.cantidad
ORDER BY o.id_cliente_origen;
COMMIT;

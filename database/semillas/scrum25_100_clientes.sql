-- Datos ficticios para pruebas de SCRUM-25. Ejecutar manualmente en una BD de desarrollo.
-- Requiere migraciones de perfil V1..V9 y comportamiento V1..V7.
-- 100 clientes, 741 compras confirmadas, 1.482 items y seis categorias.
-- No modifica registros existentes. Una segunda ejecucion conserva la primera carga.
BEGIN;
SET LOCAL TIME ZONE 'America/La_Paz';
SELECT pg_advisory_xact_lock(2500100);

DO $seed$
DECLARE
    n integer;
    j integer;
    total_compras integer;
    cliente_id bigint;
    cambio_id bigint;
    evento_id bigint;
    compra_id bigint;
    codigo_cliente text;
    codigo_compra text;
    uuid_evento text;
    nombre text;
    apellido text;
    correo text;
    categoria_a text;
    categoria_b text;
    monto numeric(12,2);
    monto_a numeric(12,2);
    monto_b numeric(12,2);
    fecha_base timestamptz := current_date::timestamptz;
    fecha_alta timestamptz := fecha_base - interval '365 days';
    fecha_compra timestamptz;
    cambios jsonb;
    contenido jsonb;
    nombres text[] := ARRAY['Ana', 'Bruno', 'Carla', 'Diego', 'Elena',
                            'Fabian', 'Gabriela', 'Hugo', 'Irene', 'Javier'];
    apellidos text[] := ARRAY['Alvarez', 'Benitez', 'Castro', 'Delgado', 'Espinoza',
                              'Flores', 'Guzman', 'Herrera', 'Ibarra', 'Jimenez'];
    ciudades text[] := ARRAY['La Paz', 'El Alto', 'Cochabamba', 'Santa Cruz', 'Sucre'];
    categorias text[] := ARRAY['Electronica', 'Ropa', 'Hogar', 'Alimentos', 'Deportes', 'Libros'];
BEGIN
    FOR n IN 1..100 LOOP
        codigo_cliente := 'TEST-S25-CLI-' || lpad(n::text, 3, '0');
        correo := 'scrum25.cliente' || lpad(n::text, 3, '0') || '@example.test';

        -- Solo se omite un cliente si pertenece a esta misma carga de prueba.
        SELECT o.id_cliente INTO cliente_id
        FROM perfil.cliente_origen o WHERE o.id_cliente_origen = codigo_cliente;
        IF FOUND THEN
            IF NOT EXISTS (
                SELECT 1 FROM perfil.cliente c
                WHERE c.id = cliente_id AND c.actualizado_por = 'semilla-scrum25-v1'
                  AND c.email = correo
            ) THEN
                RAISE EXCEPTION 'El identificador % ya pertenece a otro registro; carga cancelada', codigo_cliente;
            END IF;
            CONTINUE;
        END IF;

        nombre := nombres[1 + (n - 1) % 10];
        apellido := apellidos[1 + (n - 1) / 10];
        INSERT INTO perfil.cliente
            (nombres, apellidos, tipo_documento, numero_documento, email, estado,
             creado_en, actualizado_en, actualizado_por_origen, actualizado_por)
        VALUES (nombre, apellido, 'CI', (9900000 + n)::text, correo, 'COMPLETO',
                fecha_alta, fecha_alta, 'CRM', 'semilla-scrum25-v1')
        RETURNING id INTO cliente_id;

        INSERT INTO perfil.cliente_origen
            (id_cliente_origen, id_cliente, fecha_vinculacion, motivo_vinculacion,
             ultima_actualizacion_origen, vinculado_por)
        VALUES (codigo_cliente, cliente_id, fecha_alta, 'ALTA_AUTOMATICA',
                fecha_alta, 'semilla-scrum25-v1');

        INSERT INTO perfil.direccion
            (id_cliente, id_direccion_origen, tipo, calle, numero, zona, ciudad,
             referencia, principal, actualizada_en)
        VALUES (cliente_id, 'TEST-S25-DIR-' || lpad(n::text, 3, '0'), 'ENTREGA',
                'Calle Ficticia de Pruebas', n::text, 'Zona de Pruebas',
                ciudades[1 + (n - 1) % 5], 'Direccion sintetica, no realizar entregas',
                true, fecha_alta);

        SELECT jsonb_agg(jsonb_build_object('campo', campo, 'anterior', NULL,
                                           'nuevo', valor) ORDER BY orden)
        INTO cambios
        FROM (VALUES (1, 'nombres', nombre), (2, 'apellidos', apellido),
                     (3, 'tipoDocumento', 'CI'), (4, 'numeroDocumento', (9900000 + n)::text),
                     (5, 'email', correo), (6, 'identificadoresOrigen', codigo_cliente))
             AS datos(orden, campo, valor);

        INSERT INTO perfil.cambio_perfil
            (id_cliente, fecha, tipo, origen, responsable, cambios)
        VALUES (cliente_id, fecha_alta, 'CREACION', 'CRM', 'semilla-scrum25-v1', cambios::text)
        RETURNING id INTO cambio_id;
        INSERT INTO perfil.cambio_perfil_detalle
            (id_cambio, orden, campo, valor_anterior, valor_nuevo)
        SELECT cambio_id, orden, valor ->> 'campo', NULL, valor ->> 'nuevo'
        FROM jsonb_array_elements(cambios) WITH ORDINALITY AS detalle(valor, orden);

        INSERT INTO perfil.consentimiento
            (id_cliente, estado, canal, fecha_otorgamiento, vigencia_desde,
             actualizado_en, actualizado_por)
        VALUES (cliente_id, 'OTORGADO', 'MARKETPLACE_VENTAS', fecha_alta,
                fecha_alta::date, fecha_alta, 'semilla-scrum25-v1');
        INSERT INTO perfil.consentimiento_alcance (id_cliente, alcance)
        VALUES (cliente_id, 'GESTION_CLIENTE'), (cliente_id, 'ANALISIS_COMPORTAMIENTO'),
               (cliente_id, 'SEGMENTACION');
        INSERT INTO perfil.consentimiento_historial
            (id_cliente, fecha, operacion, estado, canal, alcances,
             fecha_otorgamiento, vigencia_desde, responsable)
        VALUES (cliente_id, fecha_alta, 'OTORGAMIENTO', 'OTORGADO', 'MARKETPLACE_VENTAS',
                'ANALISIS_COMPORTAMIENTO,GESTION_CLIENTE,SEGMENTACION',
                fecha_alta, fecha_alta::date, 'semilla-scrum25-v1');

        -- Grupos de prueba, no reglas comerciales ni segmentos asignados.
        total_compras := CASE
            WHEN n <= 10 THEN 0
            WHEN n <= 40 THEN 1 + (n - 11) % 3
            WHEN n <= 70 THEN 5 + (n - 41) % 5
            ELSE 12 + (n - 71) % 9
        END;
        categoria_a := categorias[1 + (n - 1) % 6];
        categoria_b := categorias[1 + n % 6];

        FOR j IN 1..total_compras LOOP
            codigo_compra := 'TEST-S25-COM-' || lpad(n::text, 3, '0') || '-' || lpad(j::text, 2, '0');
            uuid_evento := md5('semilla-scrum25-v1:' || codigo_compra)::uuid::text;
            -- Los clientes 11..20 tienen todas sus compras con mas de 90 dias de antiguedad.
            fecha_compra := fecha_base - make_interval(days => j * 7 + n % 7
                                   + CASE WHEN n BETWEEN 11 AND 20 THEN 90 ELSE 0 END);
            monto := (CASE WHEN n <= 40 THEN 25 WHEN n <= 70 THEN 150 ELSE 650 END)
                     * (1 + (n + j) % 4) + j * 0.25;
            monto_a := round(monto * 0.70, 2);
            monto_b := monto - monto_a;
            contenido := jsonb_build_object(
                'idEvento', uuid_evento, 'tipoEvento', 'COMPRA_CONFIRMADA',
                'fechaEmision', fecha_compra + interval '5 seconds',
                'compra', jsonb_build_object(
                    'idCompra', codigo_compra, 'idCliente', codigo_cliente,
                    'fecha', fecha_compra, 'montoTotal', monto,
                    'items', jsonb_build_array(
                        jsonb_build_object('categoria', categoria_a, 'cantidad', 1, 'monto', monto_a),
                        jsonb_build_object('categoria', categoria_b, 'cantidad', 2, 'monto', monto_b))));

            INSERT INTO comportamiento.evento_recibido
                (id_evento_origen, tipo_evento, estado, contenido, recibido_en,
                 procesado_en, id_transaccion)
            VALUES (uuid_evento, 'COMPRA_CONFIRMADA', 'PROCESADO', contenido::text,
                    fecha_compra + interval '10 seconds', fecha_compra + interval '11 seconds', codigo_compra)
            RETURNING id INTO evento_id;
            INSERT INTO comportamiento.compra
                (id_compra_origen, id_cliente_origen, fecha, monto_total, estado,
                 id_evento, registrada_en, id_cliente, estado_vinculacion)
            VALUES (codigo_compra, codigo_cliente, fecha_compra, monto, 'CONFIRMADA',
                    evento_id, fecha_compra + interval '11 seconds', cliente_id, 'VINCULADA')
            RETURNING id INTO compra_id;
            INSERT INTO comportamiento.compra_item (id_compra, categoria, cantidad, monto)
            VALUES (compra_id, categoria_a, 1, monto_a), (compra_id, categoria_b, 2, monto_b);
            INSERT INTO comportamiento.evento_procesado
                (tipo_evento, id_transaccion, id_evento_recibido, procesado_en)
            VALUES ('COMPRA_CONFIRMADA', codigo_compra, evento_id, fecha_compra + interval '11 seconds');
        END LOOP;

        -- El contador acumulado debe coincidir con las compras vigentes.
        INSERT INTO comportamiento.frecuencia_compra (id_cliente_origen, cantidad, actualizada_en)
        VALUES (codigo_cliente, total_compras, fecha_base);
    END LOOP;

    IF (SELECT count(*) FROM perfil.cliente_origen
        WHERE id_cliente_origen LIKE 'TEST-S25-CLI-%') <> 100
       OR (SELECT count(*) FROM comportamiento.compra
           WHERE id_cliente_origen LIKE 'TEST-S25-CLI-%') <> 741 THEN
        RAISE EXCEPTION 'La carga no coincide con los 100 clientes y 741 compras esperados; transaccion cancelada';
    END IF;
END;
$seed$;

COMMIT;

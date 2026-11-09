package com.maxiconecta.crm.fidelizacion.salida;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.maxiconecta.crm.fidelizacion.movimiento.ClientesPerfil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** SCRUM-619/620: una transaccion corta por mensaje; distintas instancias no toman la misma fila. */
@Component
@ConditionalOnProperty(name = "fidelizacion.salida.enabled", havingValue = "true")
public class PublicadorSaldoPubSub {
    public static final String RUTA = "puntos.saldo.actualizado";
    private final JdbcTemplate jdbc;
    private final PubSubTemplate pubsub;
    private final ObjectMapper json;
    private final ClientesPerfil perfiles;
    private final TransactionTemplate transaccion;
    private final String topico;
    private final int lote;
    private final long esperaSegundos;

    public PublicadorSaldoPubSub(JdbcTemplate jdbc, PubSubTemplate pubsub, ObjectMapper json,
                                ClientesPerfil perfiles, PlatformTransactionManager gestor,
                                @Value("${fidelizacion.salida.topico:ucb-sis323-sub-b}") String topico,
                                @Value("${fidelizacion.salida.lote:20}") int lote,
                                @Value("${fidelizacion.salida.espera-segundos:10}") long esperaSegundos) {
        if (topico.isBlank() || lote < 1 || esperaSegundos < 1) {
            throw new IllegalArgumentException("Configuracion de salida Pub/Sub invalida");
        }
        this.jdbc = jdbc;
        this.pubsub = pubsub;
        this.json = json;
        this.perfiles = perfiles;
        this.transaccion = new TransactionTemplate(gestor);
        this.topico = topico;
        this.lote = lote;
        this.esperaSegundos = esperaSegundos;
    }

    @Scheduled(fixedDelayString = "${fidelizacion.salida.intervalo-ms:5000}",
            initialDelayString = "${fidelizacion.salida.intervalo-ms:5000}")
    public void publicarPendientes() {
        for (int i = 0; i < lote && !Thread.currentThread().isInterrupted(); i++) {
            if (!Boolean.TRUE.equals(transaccion.execute(estado -> publicarUno()))) {
                return;
            }
        }
    }

    private boolean publicarUno() {
        List<Pendiente> filas = jdbc.query("""
                SELECT s.secuencia, s.id_evento, s.cliente_id, s.contenido::text, s.intentos
                FROM fidelizacion.salida_saldo_puntos s
                WHERE s.publicado_en IS NULL AND s.proximo_intento <= now()
                  AND NOT EXISTS (SELECT 1 FROM fidelizacion.salida_saldo_puntos anterior
                      WHERE anterior.cliente_id = s.cliente_id AND anterior.secuencia < s.secuencia
                        AND anterior.publicado_en IS NULL)
                ORDER BY s.secuencia LIMIT 1 FOR UPDATE OF s SKIP LOCKED
                """, (r, n) -> new Pendiente(r.getLong("secuencia"), r.getObject("id_evento", UUID.class),
                r.getLong("cliente_id"), r.getString("contenido"), r.getInt("intentos")));
        if (filas.isEmpty()) {
            return false;
        }
        Pendiente evento = filas.get(0);
        String idMensaje;
        try {
            ObjectNode cuerpo = (ObjectNode) json.readTree(evento.contenido());
            if (!cuerpo.hasNonNull("idCliente")) {
                cuerpo.put("idCliente", perfiles.identificador(evento.cliente()));
                // El destino resuelto queda estable tambien si la publicacion falla.
                jdbc.update("UPDATE fidelizacion.salida_saldo_puntos SET contenido = ?::jsonb WHERE secuencia = ?",
                        cuerpo.toString(), evento.secuencia());
            }
            cuerpo.put("secuencia", evento.secuencia());
            PubsubMessage mensaje = PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(cuerpo.toString()))
                    .putAttributes("ruta", RUTA).putAttributes("idEvento", evento.id().toString())
                    .putAttributes("contentType", "application/json").build();
            idMensaje = pubsub.publish(topico, mensaje).get(esperaSegundos, TimeUnit.SECONDS);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            long demora = Math.min(3600L, 5L * (1L << Math.min(evento.intentos(), 10)));
            String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            jdbc.update("""
                    UPDATE fidelizacion.salida_saldo_puntos
                    SET intentos = intentos + 1, ultimo_error = ?, proximo_intento = now() + (? * interval '1 second')
                    WHERE secuencia = ?
                    """, error.substring(0, Math.min(1000, error.length())), demora, evento.secuencia());
            return true;
        }
        // Un error de BD despues del envio revierte la transaccion: se reentrega con el mismo idEvento.
        jdbc.update("""
                UPDATE fidelizacion.salida_saldo_puntos SET publicado_en = now(), id_mensaje = ?,
                    intentos = intentos + 1, ultimo_error = NULL WHERE secuencia = ?
                """, idMensaje, evento.secuencia());
        return true;
    }

    private record Pendiente(long secuencia, UUID id, long cliente, String contenido, int intentos) { }
}

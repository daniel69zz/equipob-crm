package com.maxiconecta.crm.fidelizacion.ajuste;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Auditoría completa de cada ajuste manual (SCRUM-425, docs/seguridad/alcance-auditoria.md): registra en la
 * auditoría compartida de solo inserción quién ajustó el saldo de qué cliente, cuánto, el saldo antes y después
 * y por qué. Va en la transacción del ajuste: si el registro falla, el ajuste no se aplica. Complementa el
 * registro del API Gateway, que guarda el acceso a la ruta pero no el cambio.
 */
@Repository
public class AuditoriaAjustes {

    static final String OPERACION = "PUNTOS_AJUSTADOS";
    static final String ENTIDAD_CLIENTE = "CLIENTE";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditoriaAjustes(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void registrar(Ajuste ajuste) {
        ObjectNode detalle = json.createObjectNode();
        detalle.put("idAjuste", ajuste.id());
        detalle.put("idTransaccion", ajuste.idTransaccion());
        detalle.put("puntos", ajuste.puntos());
        detalle.put("saldoAnterior", ajuste.saldoAnterior());
        detalle.put("saldoResultante", ajuste.saldoResultante());
        detalle.put("motivo", ajuste.motivo());
        detalle.put("referencia", ajuste.referencia());
        detalle.put("rol", ajuste.rol());
        jdbc.update("""
                INSERT INTO auditoria.evento(usuario, operacion, entidad, entidad_id, detalle)
                VALUES (?, ?, ?, ?, ?)
                """, ajuste.usuario(), OPERACION, ENTIDAD_CLIENTE, String.valueOf(ajuste.clienteId()), detalle.toString());
    }
}

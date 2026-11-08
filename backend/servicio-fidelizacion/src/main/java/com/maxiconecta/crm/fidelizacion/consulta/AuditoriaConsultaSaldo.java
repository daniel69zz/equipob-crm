package com.maxiconecta.crm.fidelizacion.consulta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Trazabilidad de las consultas de saldo de puntos (SCRUM-401, docs/seguridad/alcance-auditoria.md):
 * registra en la auditoría compartida de solo inserción quién consultó el saldo de qué cliente, con qué
 * rol y cuántos movimientos se le mostraron. Complementa el registro del API Gateway, que guarda el
 * acceso a la ruta pero no qué se respondió. El detalle no lleva datos personales ni el saldo.
 */
@Repository
public class AuditoriaConsultaSaldo {

    static final String OPERACION = "SALDO_PUNTOS_CONSULTADO";
    static final String ENTIDAD_CLIENTE = "CLIENTE";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditoriaConsultaSaldo(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Se llama antes de responder: si el registro falla, la consulta falla y los datos no se muestran,
     * así ninguna consulta autorizada queda sin rastro.
     */
    public void registrar(String usuario, String rol, ConsultaSaldoPuntos.SaldoCliente saldo) {
        ObjectNode detalle = json.createObjectNode();
        detalle.put("rol", rol);
        detalle.put("tienePuntos", saldo.tienePuntos());
        detalle.put("movimientosMostrados", saldo.ultimosMovimientos().size());
        detalle.put("totalMovimientos", saldo.totalMovimientos());
        jdbc.update("""
                INSERT INTO auditoria.evento(usuario, operacion, entidad, entidad_id, detalle)
                VALUES (?, ?, ?, ?, ?)
                """, usuario, OPERACION, ENTIDAD_CLIENTE, String.valueOf(saldo.clienteId()), detalle.toString());
    }
}

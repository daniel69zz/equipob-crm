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
 * <p>
 * También registra cada consulta de Marketplace y Ventas por RIO-CRM-04 (SCRUM-546).
 */
@Repository
public class AuditoriaConsultaSaldo {

    static final String OPERACION = "SALDO_PUNTOS_CONSULTADO";
    static final String OPERACION_VENTAS = "PUNTOS_CONSULTADOS_POR_VENTAS";
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

    /**
     * SCRUM-546: consulta de Marketplace y Ventas (RIO-CRM-04). La entidad es el cliente del CRM; si aún no se
     * conoce, queda sin número y el detalle lleva el identificador de Ventas. Igual que la consulta del
     * agente, se registra antes de responder.
     */
    public void registrarConsultaVentas(String clienteServicio, ConsultaPuntosVentas.PuntosParaVentas puntos) {
        ObjectNode detalle = json.createObjectNode();
        detalle.put("idClienteVentas", puntos.idClienteVentas());
        detalle.put("tieneCuenta", puntos.tieneCuenta());
        detalle.put("puntosCaducados", puntos.puntosCaducados() > 0);
        jdbc.update("""
                INSERT INTO auditoria.evento(usuario, operacion, entidad, entidad_id, detalle)
                VALUES (?, ?, ?, ?, ?)
                """, clienteServicio, OPERACION_VENTAS, ENTIDAD_CLIENTE,
                puntos.clienteId() == null ? null : String.valueOf(puntos.clienteId()), detalle.toString());
    }
}

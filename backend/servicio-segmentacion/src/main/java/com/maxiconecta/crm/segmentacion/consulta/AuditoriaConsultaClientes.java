package com.maxiconecta.crm.segmentacion.consulta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maxiconecta.crm.segmentacion.consulta.OrdenClientes.Direccion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Registra en la auditoría compartida de solo inserción qué grupo de clientes consultó cada usuario
 * y qué clientes se le mostraron (SCRUM-568, docs/seguridad/alcance-auditoria.md). Complementa el
 * registro del API Gateway, que guarda el acceso a GET /api/segmentacion/clientes pero no sus
 * parámetros. El detalle no lleva datos personales: el grupo, los filtros, la página y los números
 * de cliente.
 */
@Repository
public class AuditoriaConsultaClientes {

    static final String OPERACION = "CLIENTES_SEGMENTO_CONSULTADOS";
    static final String ENTIDAD_SEGMENTO = "SEGMENTO";
    static final String ENTIDAD_NIVEL_CONSUMO = "NIVEL_CONSUMO";

    private static final int LARGO_MAXIMO_ENTIDAD_ID = 60;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditoriaConsultaClientes(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Se llama antes de responder: si el registro falla, la consulta falla y los datos no se
     * muestran ("cada consulta autorizada queda registrada", criterio de aceptación 4).
     */
    public void registrar(String usuario, FiltroClientes filtro, OrdenClientes orden, Direccion direccion,
                          int pagina, int tamanio, long total, List<Long> clientes) {
        ObjectNode detalle = json.createObjectNode();
        detalle.put("segmento", filtro.segmento());
        detalle.put("nivelConsumo", filtro.nivelConsumo() == null ? null : filtro.nivelConsumo().name());
        detalle.put("ultimaCompraDesde", texto(filtro.ultimaCompraDesde()));
        detalle.put("ultimaCompraHasta", texto(filtro.ultimaCompraHasta()));
        detalle.put("orden", orden.parametro());
        detalle.put("direccion", direccion.parametro());
        detalle.put("pagina", pagina);
        detalle.put("tamanio", tamanio);
        detalle.put("total", total);
        detalle.set("clientes", json.valueToTree(clientes));

        // La entidad es el grupo consultado: el segmento, o el nivel si se listó solo por nivel.
        String entidad = filtro.segmento() != null ? ENTIDAD_SEGMENTO : ENTIDAD_NIVEL_CONSUMO;
        String entidadId = filtro.segmento() != null ? recortar(filtro.segmento()) : filtro.nivelConsumo().name();
        jdbc.update("""
                INSERT INTO auditoria.evento(usuario, operacion, entidad, entidad_id, detalle)
                VALUES (?, ?, ?, ?, ?)
                """, usuario, OPERACION, entidad, entidadId, detalle.toString());
    }

    private static String texto(LocalDate fecha) {
        return fecha == null ? null : fecha.toString();
    }

    /** El segmento completo queda en el detalle; la columna admite hasta 60 caracteres. */
    private static String recortar(String segmento) {
        return segmento.length() > LARGO_MAXIMO_ENTIDAD_ID ? segmento.substring(0, LARGO_MAXIMO_ENTIDAD_ID) : segmento;
    }
}

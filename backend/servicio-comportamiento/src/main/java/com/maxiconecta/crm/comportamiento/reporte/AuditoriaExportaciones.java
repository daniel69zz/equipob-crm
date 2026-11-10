package com.maxiconecta.crm.comportamiento.reporte;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Registra cada exportación de un reporte de clientes en la auditoría compartida de solo inserción (SCRUM-484,
 * docs/compra/exportacion-reportes.md, docs/seguridad/alcance-auditoria.md): quién exportó ({@code usuario}),
 * cuándo ({@code ocurrido_en}) y qué (el reporte en {@code entidad_id} y, en el detalle, el formato, el archivo y
 * los filtros), que es el criterio de aceptación 3 de SCRUM-60. El detalle no lleva datos personales.
 *
 * <p>Se llama antes de entregar el archivo: si el registro falla, la exportación falla y el archivo no sale del CRM,
 * igual que el reporte de distribución de servicio-segmentacion.
 */
@Repository
public class AuditoriaExportaciones {

    static final String OPERACION = "REPORTE_EXPORTADO";
    static final String ENTIDAD = "REPORTE";
    static final String COMPORTAMIENTO = "COMPORTAMIENTO_COMPRA";
    static final String COMPARATIVO = "COMPARATIVO_PERIODOS";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditoriaExportaciones(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void comportamiento(String usuario, ReporteComportamiento reporte, ArchivoExportado archivo) {
        ObjectNode detalle = detalle(archivo, reporte.alcance().segmento(), reporte.sinDatos());
        detalle.put("desde", reporte.desde().toString());
        detalle.put("hasta", reporte.hasta().toString());
        registrar(usuario, COMPORTAMIENTO, detalle);
    }

    public void comparativo(String usuario, ReporteComparativo reporte, ArchivoExportado archivo) {
        ObjectNode detalle = detalle(archivo, reporte.alcance().segmento(), reporte.sinDatos());
        detalle.put("desdeA", reporte.periodoA().desde().toString());
        detalle.put("hastaA", reporte.periodoA().hasta().toString());
        detalle.put("desdeB", reporte.periodoB().desde().toString());
        detalle.put("hastaB", reporte.periodoB().hasta().toString());
        registrar(usuario, COMPARATIVO, detalle);
    }

    private ObjectNode detalle(ArchivoExportado archivo, String segmento, boolean sinDatos) {
        ObjectNode detalle = json.createObjectNode();
        detalle.put("formato", archivo.formato().name());
        detalle.put("archivo", archivo.nombre());
        detalle.put("bytes", archivo.contenido().length);
        detalle.put("segmento", segmento);
        detalle.put("sinDatos", sinDatos);
        return detalle;
    }

    private void registrar(String usuario, String reporte, ObjectNode detalle) {
        jdbc.update("""
                INSERT INTO auditoria.evento(usuario, operacion, entidad, entidad_id, detalle)
                VALUES (?, ?, ?, ?, ?)
                """, usuario, OPERACION, ENTIDAD, reporte, detalle.toString());
    }
}

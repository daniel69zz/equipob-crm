package com.maxiconecta.crm.segmentacion.reporte;

import com.maxiconecta.crm.segmentacion.reporte.PanelIndicadores.Activos;
import com.maxiconecta.crm.segmentacion.reporte.PanelIndicadores.Puntos;
import com.maxiconecta.crm.segmentacion.reporte.ReporteDistribucion.Grupo;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Consolida el panel de indicadores de clientes (SCRUM-471): la distribución por segmento la calcula la
 * segmentación con las mismas consultas del reporte de distribución (SCRUM-57), y le suma los clientes
 * activos y los puntos acumulados de los otros dos servicios. Deja la consulta en la auditoría antes de
 * responder, como los demás reportes.
 */
@Service
public class ServicioPanelIndicadores {

    private static final FiltroReporte TODA_LA_CARTERA = FiltroReporte.de(null, null);

    private final DistribucionRepository distribucion;
    private final IndicadoresExternos externos;
    private final AuditoriaReportes auditoria;
    private final Clock reloj;

    public ServicioPanelIndicadores(DistribucionRepository distribucion, IndicadoresExternos externos,
                                    AuditoriaReportes auditoria, Clock reloj) {
        this.distribucion = distribucion;
        this.externos = externos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public PanelIndicadores consultar(String usuario) {
        OffsetDateTime generadoEn = OffsetDateTime.now(reloj);
        long clientes = distribucion.total(TODA_LA_CARTERA);
        List<Grupo> segmentos = clientes == 0 ? List.of() : distribucion.porSegmento(TODA_LA_CARTERA).stream()
                .map(conteo -> Grupo.de(conteo.valor(), conteo.clientes(), clientes)).toList();
        Activos activos = externos.clientesActivos().map(a -> Activos.de(a, clientes)).orElseGet(Activos::noDisponible);
        Puntos puntos = externos.puntosAcumulados().map(Puntos::de).orElseGet(Puntos::noDisponible);
        auditoria.registrarPanel(usuario, clientes, activos.disponible(), puntos.disponible());
        return new PanelIndicadores(generadoEn, clientes, clientes == 0, activos, segmentos, puntos);
    }
}

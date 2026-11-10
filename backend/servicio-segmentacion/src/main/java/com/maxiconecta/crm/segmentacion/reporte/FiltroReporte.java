package com.maxiconecta.crm.segmentacion.reporte;

import com.maxiconecta.crm.segmentacion.criterio.ReglasCriterio;
import com.maxiconecta.crm.segmentacion.segmento.NivelConsumo;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Alcance del reporte de distribución de clientes (SCRUM-57,
 * docs/segmentacion/reportes-distribucion.md). A diferencia del listado de clientes
 * ({@code consulta.FiltroClientes}), aquí los dos filtros son opcionales: sin ninguno el reporte
 * cubre a todos los clientes conocidos, que es el caso normal del Gerente Comercial. Si llegan los
 * dos, el cliente debe cumplir ambos.
 */
public record FiltroReporte(String segmento, NivelConsumo nivelConsumo) {

    public FiltroReporte {
        segmento = ReglasCriterio.limpiar(segmento);
        if (segmento.isEmpty()) {
            segmento = null;
        }
    }

    /** Lee los parámetros de la consulta; un nivel que no sea BAJO, MEDIO ni ALTO se rechaza. */
    public static FiltroReporte de(String segmento, String nivelConsumo) {
        NivelConsumo nivel = null;
        if (nivelConsumo != null && !nivelConsumo.isBlank()) {
            nivel = NivelConsumo.de(nivelConsumo)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Nivel de consumo inválido: '" + nivelConsumo + "' (se espera BAJO, MEDIO o ALTO)"));
        }
        return new FiltroReporte(segmento, nivel);
    }

    /** Sin filtros el reporte cubre a todos los clientes: no hace falta enumerarlos para fidelización. */
    public boolean cubreTodos() {
        return segmento == null && nivelConsumo == null;
    }

    public String nivelConsumoNombre() {
        return nivelConsumo == null ? null : nivelConsumo.name();
    }
}

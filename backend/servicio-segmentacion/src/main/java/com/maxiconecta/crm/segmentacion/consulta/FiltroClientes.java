package com.maxiconecta.crm.segmentacion.consulta;

import com.maxiconecta.crm.segmentacion.criterio.ReglasCriterio;
import com.maxiconecta.crm.segmentacion.segmento.NivelConsumo;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Grupo de clientes y filtros del listado por segmento (SCRUM-273,
 * docs/segmentacion/criterio-consulta-clientes.md). Exige un segmento o un nivel de consumo; si
 * llegan los dos, el cliente debe cumplir ambos. Las fechas de última compra son días completos en
 * la zona horaria del servidor.
 */
public record FiltroClientes(String segmento, NivelConsumo nivelConsumo, LocalDate ultimaCompraDesde,
                             LocalDate ultimaCompraHasta) {

    public FiltroClientes {
        segmento = ReglasCriterio.limpiar(segmento);
        if (segmento.isEmpty()) {
            segmento = null;
        }
        if (segmento == null && nivelConsumo == null) {
            throw invalido("Elija un segmento o un nivel de consumo");
        }
        if (ultimaCompraDesde != null && ultimaCompraHasta != null && ultimaCompraDesde.isAfter(ultimaCompraHasta)) {
            throw invalido("La fecha 'ultimaCompraDesde' no puede ser posterior a 'ultimaCompraHasta'");
        }
    }

    /** Lee los parámetros de la consulta; un nivel que no sea BAJO, MEDIO ni ALTO se rechaza. */
    public static FiltroClientes de(String segmento, String nivelConsumo, LocalDate ultimaCompraDesde,
                                    LocalDate ultimaCompraHasta) {
        NivelConsumo nivel = null;
        if (nivelConsumo != null && !nivelConsumo.isBlank()) {
            nivel = NivelConsumo.de(nivelConsumo).orElseThrow(() -> invalido("Nivel de consumo inválido: '"
                    + nivelConsumo + "' (se espera BAJO, MEDIO o ALTO)"));
        }
        return new FiltroClientes(segmento, nivel, ultimaCompraDesde, ultimaCompraHasta);
    }

    /** Inicio del día {@code ultimaCompraDesde}, incluido; {@code null} si no se filtra. */
    OffsetDateTime ultimaCompraDesdeInclusive(ZoneId zona) {
        return ultimaCompraDesde == null ? null : ultimaCompraDesde.atStartOfDay(zona).toOffsetDateTime();
    }

    /** Inicio del día siguiente a {@code ultimaCompraHasta}, excluido; {@code null} si no se filtra. */
    OffsetDateTime ultimaCompraHastaExclusive(ZoneId zona) {
        return ultimaCompraHasta == null ? null : ultimaCompraHasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime();
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}

package com.maxiconecta.crm.perfil.consulta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Alertas de cambio de comportamiento del cliente, tomadas de servicio-comportamiento (SCRUM-34,
 * docs/compra/cambios-comportamiento.md). {@code estado} es el de la última evaluación
 * (CON_VARIACION, ESTABLE, SIN_LINEA_BASE o SIN_EVALUAR). {@code sinDatos=true} cuando el cliente no
 * tiene identificadores de origen o el servicio no respondió (la ficha integral no debe fallar por
 * eso: CA2 de SCRUM-10).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CambiosComportamientoResponse(String estado, OffsetDateTime evaluadoEn, List<Alerta> alertas,
                                            boolean sinDatos) {

    static final CambiosComportamientoResponse SIN_DATOS = new CambiosComportamientoResponse(null, null, List.of(), true);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Alerta(String tipo, String direccion, BigDecimal valorActual, BigDecimal valorHabitual,
                         BigDecimal variacion, OffsetDateTime periodoDesde, OffsetDateTime periodoHasta,
                         OffsetDateTime detectadoEn, String mensaje) {
    }
}

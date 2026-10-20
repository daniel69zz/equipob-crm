package com.maxiconecta.crm.perfil.consulta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Clasificación del cliente por frecuencia de compra (OCASIONAL, HABITUAL o FRECUENTE) con la
 * frecuencia que la determina, tomada de {@code clasificacionFrecuencia} de los indicadores de
 * servicio-comportamiento (SCRUM-38, docs/compra/clasificacion-frecuencia.md). {@code sinDatos=true}
 * cuando el cliente no tiene compras vigentes ("sin clasificación"), no tiene identificadores de origen
 * o el servicio no respondió (la ficha integral no debe fallar por eso: CA2 de SCRUM-10).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClasificacionFrecuenciaResponse(String clasificacion, Long frecuencia, boolean sinDatos) {

    static final ClasificacionFrecuenciaResponse SIN_DATOS = new ClasificacionFrecuenciaResponse(null, null, true);
}

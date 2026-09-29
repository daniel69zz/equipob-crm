package com.maxiconecta.crm.perfil.consulta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/**
 * Segmento del cliente, espejo de {@code SegmentoResponse} de servicio-segmentacion (SCRUM-149).
 * {@code sinDatos=true} cuando el cliente todavía no tiene segmento asignado, o cuando el servicio
 * no respondió (la ficha integral no debe fallar por eso: CA2 de SCRUM-10).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SegmentoResponse(String segmento, OffsetDateTime asignadoEn, boolean sinDatos) {

    static final SegmentoResponse SIN_DATOS = new SegmentoResponse(null, null, true);
}

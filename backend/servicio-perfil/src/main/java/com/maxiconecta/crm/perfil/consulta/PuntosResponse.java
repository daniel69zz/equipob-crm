package com.maxiconecta.crm.perfil.consulta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/**
 * Saldo de puntos del cliente, espejo de {@code PuntosResponse} de servicio-fidelizacion
 * (SCRUM-154). {@code sinDatos=true} cuando el cliente todavía no tiene puntos asignados, o
 * cuando el servicio no respondió (la ficha integral no debe fallar por eso: CA2 de SCRUM-10).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PuntosResponse(Integer saldo, String nivel, OffsetDateTime actualizadoEn, boolean sinDatos) {

    static final PuntosResponse SIN_DATOS = new PuntosResponse(null, null, null, true);
}

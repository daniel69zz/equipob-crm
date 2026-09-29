package com.maxiconecta.crm.perfil.consulta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Ítem de una compra, tal como lo devuelve servicio-comportamiento. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemResponse(String categoria, int cantidad, BigDecimal monto) {
}

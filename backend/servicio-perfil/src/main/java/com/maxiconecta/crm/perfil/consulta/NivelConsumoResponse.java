package com.maxiconecta.crm.perfil.consulta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Nivel de consumo del cliente (BAJO, MEDIO o ALTO), tomado de {@code nivelConsumo} de los
 * indicadores de servicio-comportamiento (SCRUM-27, docs/compra/nivel-consumo.md).
 * {@code sinDatos=true} cuando el cliente no tiene compras vigentes que clasificar ("sin nivel"), no
 * tiene identificadores de origen, o cuando el servicio no respondió (la ficha integral no debe
 * fallar por eso: CA2 de SCRUM-10).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NivelConsumoResponse(String nivel, BigDecimal valorAcumulado, boolean sinDatos) {

    static final NivelConsumoResponse SIN_DATOS = new NivelConsumoResponse(null, null, true);
}

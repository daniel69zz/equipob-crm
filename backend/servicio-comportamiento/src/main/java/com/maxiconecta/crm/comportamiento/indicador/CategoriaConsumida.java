package com.maxiconecta.crm.comportamiento.indicador;

import java.math.BigDecimal;

/** Resultado del ranking de SCRUM-18, reutilizado por el perfil de preferencias. */
public record CategoriaConsumida(String categoria, long compras, long unidades, BigDecimal monto,
                                 boolean sinCategoria) {
}

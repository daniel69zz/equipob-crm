package com.maxiconecta.crm.comportamiento.parametro;

import java.math.BigDecimal;

/**
 * El valor nuevo de un parámetro y la versión que el usuario tenía a la vista: si otro lo cambió mientras tanto, el
 * cambio se rechaza en lugar de pisarlo.
 */
public record CambioParametro(String codigo, BigDecimal valor, Long version) {
}

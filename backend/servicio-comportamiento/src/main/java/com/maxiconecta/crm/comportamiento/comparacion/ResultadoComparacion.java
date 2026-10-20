package com.maxiconecta.crm.comportamiento.comparacion;

import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;
import com.maxiconecta.crm.comportamiento.periodo.RangoPeriodo;

/**
 * Comparación de la frecuencia y el valor de compra entre dos periodos (SCRUM-40,
 * docs/compra/comparacion-periodos.md). La frecuencia es la cantidad de compras vigentes del periodo
 * y el valor, lo que suman. {@code sinDatos} avisa que no hubo compras vigentes en ninguno de los dos
 * periodos; un solo periodo vacío no lo activa: aparece en cero dentro de la comparación.
 */
public record ResultadoComparacion(AlcanceComparacion alcance, String zonaHoraria, RangoPeriodo periodoA,
                                   RangoPeriodo periodoB, VariacionConsumo frecuencia, VariacionConsumo valor,
                                   boolean sinDatos, String mensaje) {

    public static ResultadoComparacion de(AlcanceComparacion alcance, String zonaHoraria, PeriodosAComparar periodos,
                                          ResumenCompras a, ResumenCompras b, String mensajeSinClientes) {
        boolean sinDatos = a.compras() == 0 && b.compras() == 0;
        String mensaje = null;
        if (sinDatos) {
            mensaje = mensajeSinClientes != null ? mensajeSinClientes
                    : "No hay compras vigentes en ninguno de los dos periodos.";
        }
        return new ResultadoComparacion(alcance, zonaHoraria, periodos.a(), periodos.b(),
                VariacionConsumo.calcular(a.compras(), b.compras()), VariacionConsumo.calcular(a.monto(), b.monto()),
                sinDatos, mensaje);
    }

    /** A qué se refiere la comparación: un cliente, o los clientes de un segmento. */
    public record AlcanceComparacion(String tipo, String segmento, Integer clientes) {

        public static AlcanceComparacion cliente() {
            return new AlcanceComparacion("CLIENTE", null, null);
        }

        public static AlcanceComparacion segmento(String segmento, int clientes) {
            return new AlcanceComparacion("SEGMENTO", segmento, clientes);
        }
    }
}

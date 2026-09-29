package com.maxiconecta.crm.comportamiento.compra;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

/**
 * Condiciones para seleccionar las compras de un cliente a partir de sus identificadores en
 * Marketplace y Ventas.
 * Las comparten el historial y los indicadores (docs/compra/historial-compras.md).
 */
public final class ComprasDelCliente {

    private ComprasDelCliente() {
    }

    /**
     * Compras de cualquiera de los identificadores. Sirve tanto sobre la raíz de la consulta como
     * sobre una compra alcanzada por un join.
     */
    public static Predicate deIdentificadores(From<?, Compra> compra, CriteriaBuilder criterios,
                                              List<Identificador> identificadores) {
        return compra.get("idClienteOrigen").in(identificadores.stream().map(Identificador::idClienteOrigen).toList());
    }

    public static Specification<Compra> deIdentificadores(List<Identificador> identificadores) {
        return (compra, consulta, criterios) -> deIdentificadores(compra, criterios, identificadores);
    }

    /** Lo que sigue vigente de la compra (monto total menos lo revertido) es mayor que cero. */
    public static Predicate vigente(From<?, Compra> compra, CriteriaBuilder criterios) {
        return criterios.greaterThan(montoVigente(compra, criterios), BigDecimal.ZERO);
    }

    public static Expression<BigDecimal> montoVigente(From<?, Compra> compra, CriteriaBuilder criterios) {
        return criterios.diff(compra.<BigDecimal>get("montoTotal"), compra.<BigDecimal>get("montoRevertido"));
    }
}

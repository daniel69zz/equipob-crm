package com.maxiconecta.crm.comportamiento.compra;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

/**
 * Condiciones para seleccionar las compras de un cliente a partir de sus identificadores por canal.
 * Las comparten el historial y los indicadores (docs/compra/historial-compras.md).
 */
public final class ComprasDelCliente {

    private ComprasDelCliente() {
    }

    /**
     * Un OR de pares (origen, idClienteOrigen): un IN por columna mezclaría pares que no
     * corresponden al mismo cliente si dos clientes comparten un identificador de canal.
     * Sirve tanto sobre la raíz de la consulta como sobre una compra alcanzada por un join.
     */
    public static Predicate deIdentificadores(From<?, Compra> compra, CriteriaBuilder criterios,
                                              List<Identificador> identificadores) {
        List<Predicate> pares = identificadores.stream()
                .map(id -> criterios.and(criterios.equal(compra.get("origen"), id.origen()),
                        criterios.equal(compra.get("idClienteOrigen"), id.idClienteOrigen())))
                .toList();
        return criterios.or(pares.toArray(Predicate[]::new));
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

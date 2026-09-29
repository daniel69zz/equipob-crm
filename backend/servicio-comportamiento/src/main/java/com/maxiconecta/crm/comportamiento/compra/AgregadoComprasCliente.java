package com.maxiconecta.crm.comportamiento.compra;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agrega en la base los montos de compra de un cliente, sin traer las compras a memoria.
 * Cuenta solo lo vigente: una compra anulada no suma y una devolución parcial aporta lo que
 * quedó (docs/compra/ticket-promedio.md).
 */
@Service
public class AgregadoComprasCliente {

    @PersistenceContext
    private EntityManager entityManager;

    /** Sin identificadores no hay compras que buscar: el resumen viene vacío. */
    @Transactional(readOnly = true)
    public ResumenCompras resumir(List<Identificador> identificadores) {
        if (identificadores.isEmpty()) {
            return ResumenCompras.VACIO;
        }
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> consulta = criterios.createTupleQuery();
        Root<Compra> compra = consulta.from(Compra.class);
        consulta.multiselect(criterios.count(compra),
                criterios.sum(ComprasDelCliente.montoVigente(compra, criterios)));
        consulta.where(ComprasDelCliente.deIdentificadores(compra, criterios, identificadores),
                ComprasDelCliente.vigente(compra, criterios));

        Tuple fila = entityManager.createQuery(consulta).getSingleResult();
        BigDecimal monto = fila.get(1, BigDecimal.class);
        return new ResumenCompras(fila.get(0, Long.class), monto == null ? BigDecimal.ZERO : monto);
    }
}

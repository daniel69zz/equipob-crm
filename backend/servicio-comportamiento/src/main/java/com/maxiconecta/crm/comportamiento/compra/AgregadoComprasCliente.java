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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Agrega en la base las compras de un cliente, sin traer su historial a memoria. Los indicadores
 * consideran solo lo vigente: una compra anulada no participa y una devolución parcial sí
 * (docs/compra/ticket-promedio.md y docs/compra/recencia-compra.md).
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

    /**
     * Fecha de la compra vigente más reciente del cliente. La agregación se hace en la base para
     * no cargar su historial en memoria; sin identificadores o compras vigentes no hay fecha.
     */
    @Transactional(readOnly = true)
    public Optional<OffsetDateTime> ultimaCompraVigente(List<Identificador> identificadores) {
        if (identificadores.isEmpty()) {
            return Optional.empty();
        }
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<OffsetDateTime> consulta = criterios.createQuery(OffsetDateTime.class);
        Root<Compra> compra = consulta.from(Compra.class);
        consulta.select(criterios.greatest(compra.<OffsetDateTime>get("fecha")));
        consulta.where(ComprasDelCliente.deIdentificadores(compra, criterios, identificadores),
                ComprasDelCliente.vigente(compra, criterios));
        return Optional.ofNullable(entityManager.createQuery(consulta).getSingleResult());
    }
}

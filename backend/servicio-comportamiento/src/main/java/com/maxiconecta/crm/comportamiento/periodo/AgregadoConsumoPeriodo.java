package com.maxiconecta.crm.comportamiento.periodo;

import com.maxiconecta.crm.comportamiento.compra.Compra;
import com.maxiconecta.crm.comportamiento.compra.ComprasDelCliente;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;
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

/**
 * Agrega en la base las compras vigentes de un cliente dentro de un periodo (SCRUM-344), sin traer
 * su historial a memoria. Aplica las mismas condiciones de "vigente" que el resto de los
 * indicadores ({@link ComprasDelCliente#vigente}): una compra anulada no participa y una devolución
 * parcial sí, por lo que le queda vigente (docs/compra/consumo-por-periodo.md).
 */
@Service
public class AgregadoConsumoPeriodo {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Compras vigentes con fecha desde {@code inicio} (inclusive) hasta {@code fin} (exclusive) y el
     * monto vigente que suman. Sin identificadores no hay compras que buscar: el resumen viene vacío.
     */
    @Transactional(readOnly = true)
    public ResumenCompras resumir(List<Identificador> identificadores, OffsetDateTime inicio, OffsetDateTime fin) {
        if (identificadores.isEmpty()) {
            return ResumenCompras.VACIO;
        }
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> consulta = criterios.createTupleQuery();
        Root<Compra> compra = consulta.from(Compra.class);
        consulta.multiselect(criterios.count(compra),
                criterios.sum(ComprasDelCliente.montoVigente(compra, criterios)));
        consulta.where(ComprasDelCliente.deIdentificadores(compra, criterios, identificadores),
                ComprasDelCliente.vigente(compra, criterios),
                criterios.greaterThanOrEqualTo(compra.<OffsetDateTime>get("fecha"), inicio),
                criterios.lessThan(compra.<OffsetDateTime>get("fecha"), fin));

        Tuple fila = entityManager.createQuery(consulta).getSingleResult();
        BigDecimal monto = fila.get(1, BigDecimal.class);
        return new ResumenCompras(fila.get(0, Long.class), monto == null ? BigDecimal.ZERO : monto);
    }
}

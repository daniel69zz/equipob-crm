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

    static final int LOTE_DE_CLIENTES = 1000;

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

    /**
     * Lo mismo para un conjunto de clientes identificados por su perfil (SCRUM-357): las compras
     * vinculadas ({@code compra.id_cliente}) de los clientes de un segmento. Las compras todavía
     * pendientes de vinculación no tienen perfil y no entran. Los clientes se consultan por lotes
     * para no armar una lista de parámetros sin límite en segmentos grandes.
     */
    @Transactional(readOnly = true)
    public ResumenCompras resumirPerfiles(List<Long> idsPerfil, OffsetDateTime inicio, OffsetDateTime fin) {
        long compras = 0;
        BigDecimal monto = BigDecimal.ZERO;
        for (int desde = 0; desde < idsPerfil.size(); desde += LOTE_DE_CLIENTES) {
            List<Long> lote = idsPerfil.subList(desde, Math.min(desde + LOTE_DE_CLIENTES, idsPerfil.size()));
            CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
            CriteriaQuery<Tuple> consulta = criterios.createTupleQuery();
            Root<Compra> compra = consulta.from(Compra.class);
            consulta.multiselect(criterios.count(compra),
                    criterios.sum(ComprasDelCliente.montoVigente(compra, criterios)));
            consulta.where(compra.get("idCliente").in(lote), ComprasDelCliente.vigente(compra, criterios),
                    criterios.greaterThanOrEqualTo(compra.<OffsetDateTime>get("fecha"), inicio),
                    criterios.lessThan(compra.<OffsetDateTime>get("fecha"), fin));
            Tuple fila = entityManager.createQuery(consulta).getSingleResult();
            compras += fila.get(0, Long.class);
            BigDecimal parcial = fila.get(1, BigDecimal.class);
            monto = parcial == null ? monto : monto.add(parcial);
        }
        return new ResumenCompras(compras, monto);
    }
}

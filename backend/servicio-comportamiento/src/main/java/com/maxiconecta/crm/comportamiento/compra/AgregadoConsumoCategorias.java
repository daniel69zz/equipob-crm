package com.maxiconecta.crm.comportamiento.compra;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Agrega en la base lo comprado y lo devuelto por categoría en las compras de un cliente dentro
 * de un rango de fechas, una fila por compra y categoría (docs/compra/evolucion-consumo-categoria.md).
 * Solo considera las compras vigentes; de las anuladas solo cuenta cuántas quedaron fuera. El
 * descuento de lo devuelto y la agrupación por periodo se resuelven aparte, sobre estas filas.
 */
@Service
public class AgregadoConsumoCategorias {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Compras vigentes con fecha en {@code [desde, hasta)}, con lo comprado y lo devuelto de cada
     * categoría. Sin identificadores no hay compras que buscar: el consumo viene vacío.
     */
    @Transactional(readOnly = true)
    public ConsumoEnRango consumir(List<Identificador> identificadores, OffsetDateTime desde, OffsetDateTime hasta) {
        if (identificadores.isEmpty()) {
            return ConsumoEnRango.VACIO;
        }
        return new ConsumoEnRango(compras(identificadores, desde, hasta), comprado(identificadores, desde, hasta),
                devuelto(identificadores, desde, hasta), anuladas(identificadores, desde, hasta));
    }

    private List<CompraEnRango> compras(List<Identificador> identificadores, OffsetDateTime desde, OffsetDateTime hasta) {
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> consulta = criterios.createTupleQuery();
        Root<Compra> compra = consulta.from(Compra.class);
        consulta.multiselect(compra.get("id"), compra.get("fecha"), ComprasDelCliente.montoVigente(compra, criterios));
        consulta.where(enRango(compra, criterios, identificadores, desde, hasta),
                ComprasDelCliente.vigente(compra, criterios));
        consulta.orderBy(criterios.asc(compra.get("fecha")), criterios.asc(compra.get("id")));
        return entityManager.createQuery(consulta).getResultList().stream()
                .map(fila -> new CompraEnRango(fila.get(0, Long.class), fila.get(1, OffsetDateTime.class),
                        fila.get(2, BigDecimal.class)))
                .toList();
    }

    private List<LineaConsumoCategoria> comprado(List<Identificador> identificadores, OffsetDateTime desde,
                                                 OffsetDateTime hasta) {
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> consulta = criterios.createTupleQuery();
        Root<Compra> compra = consulta.from(Compra.class);
        Join<Compra, CompraItem> item = compra.join("items");
        consulta.multiselect(compra.get("id"), item.get("categoria"),
                criterios.sum(item.<Integer>get("cantidad")), criterios.sum(item.<BigDecimal>get("monto")));
        consulta.where(enRango(compra, criterios, identificadores, desde, hasta),
                ComprasDelCliente.vigente(compra, criterios));
        consulta.groupBy(compra.get("id"), item.get("categoria"));
        return lineas(consulta);
    }

    private List<LineaConsumoCategoria> devuelto(List<Identificador> identificadores, OffsetDateTime desde,
                                                 OffsetDateTime hasta) {
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> consulta = criterios.createTupleQuery();
        Root<AnulacionItem> item = consulta.from(AnulacionItem.class);
        Join<AnulacionItem, Anulacion> anulacion = item.join("anulacion");
        Join<Anulacion, Compra> compra = anulacion.join("compra");
        consulta.multiselect(compra.get("id"), item.get("categoria"),
                criterios.sum(item.<Integer>get("cantidad")), criterios.sum(item.<BigDecimal>get("monto")));
        consulta.where(enRango(compra, criterios, identificadores, desde, hasta),
                ComprasDelCliente.vigente(compra, criterios));
        consulta.groupBy(compra.get("id"), item.get("categoria"));
        return lineas(consulta);
    }

    /** Compras del rango que no siguen vigentes: quedan fuera del cálculo, solo se informa cuántas. */
    private long anuladas(List<Identificador> identificadores, OffsetDateTime desde, OffsetDateTime hasta) {
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> consulta = criterios.createQuery(Long.class);
        Root<Compra> compra = consulta.from(Compra.class);
        consulta.select(criterios.count(compra));
        consulta.where(enRango(compra, criterios, identificadores, desde, hasta),
                criterios.not(ComprasDelCliente.vigente(compra, criterios)));
        return entityManager.createQuery(consulta).getSingleResult();
    }

    private static Predicate enRango(From<?, Compra> compra, CriteriaBuilder criterios,
                                     List<Identificador> identificadores, OffsetDateTime desde, OffsetDateTime hasta) {
        return criterios.and(ComprasDelCliente.deIdentificadores(compra, criterios, identificadores),
                criterios.greaterThanOrEqualTo(compra.get("fecha"), desde),
                criterios.lessThan(compra.get("fecha"), hasta));
    }

    private List<LineaConsumoCategoria> lineas(CriteriaQuery<Tuple> consulta) {
        return entityManager.createQuery(consulta).getResultList().stream()
                .map(fila -> {
                    Number unidades = fila.get(2, Number.class);
                    return new LineaConsumoCategoria(fila.get(0, Long.class), fila.get(1, String.class),
                            unidades == null ? 0 : unidades.longValue(), fila.get(3, BigDecimal.class));
                })
                .toList();
    }

    /** Compra vigente del rango: su fecha decide el periodo y su monto vigente permite verificar el desglose. */
    public record CompraEnRango(long idCompra, OffsetDateTime fecha, BigDecimal montoVigente) {
    }

    /**
     * Lo que se necesita para la evolución: las compras vigentes del rango, lo comprado y lo devuelto
     * de cada una por categoría, y cuántas compras anuladas del rango quedaron fuera.
     */
    public record ConsumoEnRango(List<CompraEnRango> compras, List<LineaConsumoCategoria> compradas,
                                 List<LineaConsumoCategoria> devueltas, long comprasAnuladas) {

        public static final ConsumoEnRango VACIO = new ConsumoEnRango(List.of(), List.of(), List.of(), 0);
    }
}

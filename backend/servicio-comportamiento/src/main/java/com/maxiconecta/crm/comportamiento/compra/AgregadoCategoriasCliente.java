package com.maxiconecta.crm.comportamiento.compra;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agrega en la base lo comprado y lo devuelto por categoría en las compras vigentes de un
 * cliente, una fila por compra y categoría (docs/compra/categorias-mas-consumidas.md). El
 * descuento de lo devuelto y el orden se resuelven aparte, sobre estas filas.
 */
@Service
public class AgregadoCategoriasCliente {

    @PersistenceContext
    private EntityManager entityManager;

    /** Lo comprado y lo devuelto por compra y categoría. Sin identificadores, ambas listas vienen vacías. */
    @Transactional(readOnly = true)
    public ConsumoPorCategoria consumir(List<Identificador> identificadores) {
        if (identificadores.isEmpty()) {
            return new ConsumoPorCategoria(List.of(), List.of());
        }
        return new ConsumoPorCategoria(comprado(identificadores), devuelto(identificadores));
    }

    private List<LineaCategoria> comprado(List<Identificador> identificadores) {
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> consulta = criterios.createTupleQuery();
        Root<Compra> compra = consulta.from(Compra.class);
        Join<Compra, CompraItem> item = compra.join("items");
        consulta.multiselect(compra.get("id"), item.get("categoria"),
                criterios.sum(item.<Integer>get("cantidad")), criterios.sum(item.<BigDecimal>get("monto")));
        consulta.where(ComprasDelCliente.deIdentificadores(compra, criterios, identificadores),
                ComprasDelCliente.vigente(compra, criterios));
        consulta.groupBy(compra.get("id"), item.get("categoria"));
        return lineas(consulta);
    }

    private List<LineaCategoria> devuelto(List<Identificador> identificadores) {
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> consulta = criterios.createTupleQuery();
        Root<AnulacionItem> item = consulta.from(AnulacionItem.class);
        Join<AnulacionItem, Anulacion> anulacion = item.join("anulacion");
        Join<Anulacion, Compra> compra = anulacion.join("compra");
        consulta.multiselect(compra.get("id"), item.get("categoria"),
                criterios.sum(item.<Integer>get("cantidad")), criterios.sum(item.<BigDecimal>get("monto")));
        consulta.where(ComprasDelCliente.deIdentificadores(compra, criterios, identificadores),
                ComprasDelCliente.vigente(compra, criterios));
        consulta.groupBy(compra.get("id"), item.get("categoria"));
        return lineas(consulta);
    }

    private List<LineaCategoria> lineas(CriteriaQuery<Tuple> consulta) {
        return entityManager.createQuery(consulta).getResultList().stream()
                .map(fila -> new LineaCategoria(fila.get(0, Long.class), fila.get(1, String.class),
                        fila.get(2, Number.class).longValue(), fila.get(3, BigDecimal.class)))
                .toList();
    }

    public record ConsumoPorCategoria(List<LineaCategoria> compradas, List<LineaCategoria> devueltas) {
    }
}

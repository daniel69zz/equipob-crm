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
import java.util.ArrayList;
import java.util.List;

/**
 * Agrega en la base lo comprado y lo devuelto por categoría en las compras de un cliente dentro
 * de un rango de fechas, una fila por compra y categoría (docs/compra/evolucion-consumo-categoria.md).
 * Solo considera las compras vigentes; de las anuladas solo cuenta cuántas quedaron fuera. El
 * descuento de lo devuelto y la agrupación por periodo se resuelven aparte, sobre estas filas.
 */
@Service
public class AgregadoConsumoCategorias {

    static final int LOTE_DE_PERFILES = 1000;

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
        return consumirAlcance((compra, criterios) -> ComprasDelCliente.deIdentificadores(compra, criterios,
                identificadores), desde, hasta);
    }

    /**
     * Lo mismo para un grupo de clientes identificados por su perfil (SCRUM-468, reporte de
     * comportamiento de compra por segmento): las compras vinculadas ({@code compra.id_cliente}) de
     * esos perfiles. Se consultan por lotes, como {@code periodo.AgregadoConsumoPeriodo#resumirPerfiles},
     * para no armar una lista de parámetros sin límite; cada compra es de un solo perfil, así que los
     * lotes no se solapan.
     */
    @Transactional(readOnly = true)
    public ConsumoEnRango consumirPerfiles(List<Long> idsPerfil, OffsetDateTime desde, OffsetDateTime hasta) {
        List<CompraEnRango> compras = new ArrayList<>();
        List<LineaConsumoCategoria> compradas = new ArrayList<>();
        List<LineaConsumoCategoria> devueltas = new ArrayList<>();
        long anuladas = 0;
        for (int inicio = 0; inicio < idsPerfil.size(); inicio += LOTE_DE_PERFILES) {
            List<Long> lote = idsPerfil.subList(inicio, Math.min(inicio + LOTE_DE_PERFILES, idsPerfil.size()));
            ConsumoEnRango parcial = consumirAlcance((compra, criterios) -> compra.get("idCliente").in(lote),
                    desde, hasta);
            compras.addAll(parcial.compras());
            compradas.addAll(parcial.compradas());
            devueltas.addAll(parcial.devueltas());
            anuladas += parcial.comprasAnuladas();
        }
        return new ConsumoEnRango(compras, compradas, devueltas, anuladas);
    }

    /** Todas las compras vinculadas a algún perfil: el reporte de la cartera completa, sin segmento. */
    @Transactional(readOnly = true)
    public ConsumoEnRango consumirVinculadas(OffsetDateTime desde, OffsetDateTime hasta) {
        return consumirAlcance((compra, criterios) -> criterios.isNotNull(compra.get("idCliente")), desde, hasta);
    }

    private ConsumoEnRango consumirAlcance(Alcance alcance, OffsetDateTime desde, OffsetDateTime hasta) {
        return new ConsumoEnRango(compras(alcance, desde, hasta), comprado(alcance, desde, hasta),
                devuelto(alcance, desde, hasta), anuladas(alcance, desde, hasta));
    }

    /** Todo el historial, sin imponer a las preferencias la ventana de los reportes (SCRUM-361). */
    @Transactional(readOnly = true)
    public ConsumoEnRango consumirTodo(List<Identificador> identificadores) {
        return consumir(identificadores, null, null);
    }

    private List<CompraEnRango> compras(Alcance alcance, OffsetDateTime desde, OffsetDateTime hasta) {
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> consulta = criterios.createTupleQuery();
        Root<Compra> compra = consulta.from(Compra.class);
        consulta.multiselect(compra.get("id"), compra.get("fecha"), ComprasDelCliente.montoVigente(compra, criterios));
        consulta.where(enRango(compra, criterios, alcance, desde, hasta),
                ComprasDelCliente.vigente(compra, criterios));
        consulta.orderBy(criterios.asc(compra.get("fecha")), criterios.asc(compra.get("id")));
        return entityManager.createQuery(consulta).getResultList().stream()
                .map(fila -> new CompraEnRango(fila.get(0, Long.class), fila.get(1, OffsetDateTime.class),
                        fila.get(2, BigDecimal.class)))
                .toList();
    }

    private List<LineaConsumoCategoria> comprado(Alcance alcance, OffsetDateTime desde, OffsetDateTime hasta) {
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> consulta = criterios.createTupleQuery();
        Root<Compra> compra = consulta.from(Compra.class);
        Join<Compra, CompraItem> item = compra.join("items");
        consulta.multiselect(compra.get("id"), item.get("categoria"),
                criterios.sum(item.<Integer>get("cantidad")), criterios.sum(item.<BigDecimal>get("monto")));
        consulta.where(enRango(compra, criterios, alcance, desde, hasta),
                ComprasDelCliente.vigente(compra, criterios));
        consulta.groupBy(compra.get("id"), item.get("categoria"));
        return lineas(consulta);
    }

    private List<LineaConsumoCategoria> devuelto(Alcance alcance, OffsetDateTime desde, OffsetDateTime hasta) {
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> consulta = criterios.createTupleQuery();
        Root<AnulacionItem> item = consulta.from(AnulacionItem.class);
        Join<AnulacionItem, Anulacion> anulacion = item.join("anulacion");
        Join<Anulacion, Compra> compra = anulacion.join("compra");
        consulta.multiselect(compra.get("id"), item.get("categoria"),
                criterios.sum(item.<Integer>get("cantidad")), criterios.sum(item.<BigDecimal>get("monto")));
        consulta.where(enRango(compra, criterios, alcance, desde, hasta),
                ComprasDelCliente.vigente(compra, criterios));
        consulta.groupBy(compra.get("id"), item.get("categoria"));
        return lineas(consulta);
    }

    /** Compras del rango que no siguen vigentes: quedan fuera del cálculo, solo se informa cuántas. */
    private long anuladas(Alcance alcance, OffsetDateTime desde, OffsetDateTime hasta) {
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> consulta = criterios.createQuery(Long.class);
        Root<Compra> compra = consulta.from(Compra.class);
        consulta.select(criterios.count(compra));
        consulta.where(enRango(compra, criterios, alcance, desde, hasta),
                criterios.not(ComprasDelCliente.vigente(compra, criterios)));
        return entityManager.createQuery(consulta).getSingleResult();
    }

    private static Predicate enRango(From<?, Compra> compra, CriteriaBuilder criterios, Alcance alcance,
                                     OffsetDateTime desde, OffsetDateTime hasta) {
        Predicate cliente = alcance.aplicar(compra, criterios);
        if (desde != null) {
            cliente = criterios.and(cliente, criterios.greaterThanOrEqualTo(compra.get("fecha"), desde));
        }
        if (hasta != null) {
            cliente = criterios.and(cliente, criterios.lessThan(compra.get("fecha"), hasta));
        }
        return cliente;
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

    /** Qué compras entran: las de un cliente, las de un grupo de perfiles o todas las vinculadas. */
    @FunctionalInterface
    private interface Alcance {
        Predicate aplicar(From<?, Compra> compra, CriteriaBuilder criterios);
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

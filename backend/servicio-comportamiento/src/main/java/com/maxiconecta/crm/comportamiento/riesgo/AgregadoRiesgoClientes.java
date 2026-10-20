package com.maxiconecta.crm.comportamiento.riesgo;

import com.maxiconecta.crm.comportamiento.cambio.DireccionVariacion;
import com.maxiconecta.crm.comportamiento.compra.Compra;
import com.maxiconecta.crm.comportamiento.compra.ComprasDelCliente;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Datos de entrada del riesgo de abandono agregados en la base por cliente del CRM (SCRUM-322): las
 * compras vigentes de todos sus identificadores (las vinculadas a su perfil) y las disminuciones de
 * comportamiento detectadas para cualquiera de ellos. Usa las mismas reglas de vigencia que los
 * indicadores ({@link ComprasDelCliente}).
 */
@Repository
public class AgregadoRiesgoClientes {

    @PersistenceContext
    private EntityManager entityManager;

    /** Compras vigentes, valor vigente y última compra de cada cliente del CRM con compras vigentes. */
    public List<ComprasCliente> porCliente() {
        return consulta(null);
    }

    /** Lo mismo para un solo cliente; vacío si no tiene compras vigentes. */
    public Optional<ComprasCliente> deCliente(long clienteId) {
        return consulta(clienteId).stream().findFirst();
    }

    /**
     * Clientes del CRM con una disminución de frecuencia o de monto detectada desde {@code desde}
     * (SCRUM-34) en alguno de sus identificadores.
     */
    public Set<Long> conDisminucionDesde(OffsetDateTime desde) {
        return new HashSet<>(entityManager.createQuery("""
                        select distinct c.idCliente from Compra c, DeteccionCambioComportamiento d
                        where d.idClienteOrigen = c.idClienteOrigen and c.idCliente is not null
                          and d.direccion = :disminucion and d.detectadoEn >= :desde
                        """, Long.class)
                .setParameter("disminucion", DireccionVariacion.DISMINUCION)
                .setParameter("desde", desde)
                .getResultList());
    }

    private List<ComprasCliente> consulta(Long clienteId) {
        CriteriaBuilder criterios = entityManager.getCriteriaBuilder();
        CriteriaQuery<ComprasCliente> consulta = criterios.createQuery(ComprasCliente.class);
        Root<Compra> compra = consulta.from(Compra.class);
        consulta.select(criterios.construct(ComprasCliente.class, compra.get("idCliente"), criterios.count(compra),
                criterios.sum(ComprasDelCliente.montoVigente(compra, criterios)),
                criterios.greatest(compra.<OffsetDateTime>get("fecha"))));
        Predicate delCliente = clienteId == null ? criterios.isNotNull(compra.get("idCliente"))
                : criterios.equal(compra.get("idCliente"), clienteId);
        consulta.where(delCliente, ComprasDelCliente.vigente(compra, criterios));
        consulta.groupBy(compra.get("idCliente"));
        return entityManager.createQuery(consulta).getResultList();
    }

    /** Compras vigentes de un cliente del CRM sumando todos sus identificadores. */
    public record ComprasCliente(Long clienteId, Long compras, BigDecimal valorAcumulado, OffsetDateTime ultimaCompra) {
    }
}

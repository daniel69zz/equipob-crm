package com.maxiconecta.crm.comportamiento.compra;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;

/**
 * Historial de compras de un cliente, combinando sus identificadores en cada canal
 * (docs/compra/historial-compras.md).
 */
@Service
public class ConsultaHistorialCompras {

    public static final int TAMANIO_MAXIMO_PAGINA = 100;

    private final CompraRepository repository;

    public ConsultaHistorialCompras(CompraRepository repository) {
        this.repository = repository;
    }

    /**
     * Compras de cualquiera de los identificadores dados, de la más reciente a la más antigua.
     * Sin identificadores, la página viene vacía (cliente sin compras registradas).
     * <p>
     * Cada compra se convierte dentro de la transacción: sus ítems se cargan al leerlos y, con
     * open-in-view desactivado, ya no podrían leerse después de devolver la página.
     */
    @Transactional(readOnly = true)
    public <T> Page<T> buscar(List<Identificador> identificadores, int pagina, int tamanio,
                              Function<Compra, T> conversion) {
        PageRequest solicitud = PageRequest.of(Math.max(pagina, 0),
                Math.min(Math.max(tamanio, 1), TAMANIO_MAXIMO_PAGINA),
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id")));
        if (identificadores.isEmpty()) {
            return Page.empty(solicitud);
        }
        return repository.findAll(deIdentificadores(identificadores), solicitud).map(conversion);
    }

    /**
     * Un OR de pares (origen, idClienteOrigen): un IN por columna mezclaría pares que no
     * corresponden al mismo cliente si dos clientes comparten un identificador de canal.
     */
    private static Specification<Compra> deIdentificadores(List<Identificador> identificadores) {
        return (compra, consulta, criterios) -> {
            List<Predicate> pares = identificadores.stream()
                    .map(id -> criterios.and(criterios.equal(compra.get("origen"), id.origen()),
                            criterios.equal(compra.get("idClienteOrigen"), id.idClienteOrigen())))
                    .toList();
            return criterios.or(pares.toArray(Predicate[]::new));
        };
    }
}

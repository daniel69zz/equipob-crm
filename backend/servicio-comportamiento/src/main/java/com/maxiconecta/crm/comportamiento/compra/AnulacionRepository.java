package com.maxiconecta.crm.comportamiento.compra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AnulacionRepository extends JpaRepository<Anulacion, Long> {

    Optional<Anulacion> findByOrigenAndIdAnulacionOrigen(Origen origen, String idAnulacionOrigen);

    List<Anulacion> findByCompraIdOrderByFechaAscIdAsc(Long idCompra);

    /** Unidades y monto ya devueltos de cada categoría de una compra. */
    @Query("""
            select i.categoria as categoria, sum(i.cantidad) as cantidad, sum(i.monto) as monto
            from AnulacionItem i join i.anulacion a
            where a.compra.id = :idCompra
            group by i.categoria
            """)
    List<DevueltoPorCategoria> devueltoPorCategoria(Long idCompra);

    interface DevueltoPorCategoria {

        String getCategoria();

        Long getCantidad();

        BigDecimal getMonto();
    }
}

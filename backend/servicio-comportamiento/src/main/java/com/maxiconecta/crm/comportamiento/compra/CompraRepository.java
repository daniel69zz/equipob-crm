package com.maxiconecta.crm.comportamiento.compra;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long>, JpaSpecificationExecutor<Compra> {

    Optional<Compra> findByIdCompraOrigen(String idCompraOrigen);

    /**
     * Igual que {@link #findByIdCompraOrigen}, pero bloquea la compra hasta el fin de la
     * transacción: dos devoluciones de la misma compra procesadas a la vez se aplican una tras otra.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.idCompraOrigen = :idCompraOrigen")
    Optional<Compra> bloquear(String idCompraOrigen);

    /**
     * Identificadores en Marketplace y Ventas con compras vinculadas al perfil del cliente: las
     * métricas que se publican para la segmentación (SCRUM-26) suman todos los del mismo perfil.
     */
    @Query("select distinct c.idClienteOrigen from Compra c where c.idCliente = :idCliente order by c.idClienteOrigen")
    List<String> identificadoresDe(@Param("idCliente") Long idCliente);

    /** Perfiles con al menos una compra vinculada, para republicar sus métricas a la segmentación. */
    @Query("select distinct c.idCliente from Compra c where c.idCliente is not null order by c.idCliente")
    List<Long> clientesConCompras();
}

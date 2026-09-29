package com.maxiconecta.crm.comportamiento.compra;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long>, JpaSpecificationExecutor<Compra> {

    Optional<Compra> findByOrigenAndIdCompraOrigen(Origen origen, String idCompraOrigen);

    /**
     * Igual que {@link #findByOrigenAndIdCompraOrigen}, pero bloquea la compra hasta el fin de la
     * transacción: dos devoluciones de la misma compra procesadas a la vez se aplican una tras otra.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.origen = :origen and c.idCompraOrigen = :idCompraOrigen")
    Optional<Compra> bloquear(Origen origen, String idCompraOrigen);
}

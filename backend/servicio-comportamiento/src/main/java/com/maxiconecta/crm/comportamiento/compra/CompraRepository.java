package com.maxiconecta.crm.comportamiento.compra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    Optional<Compra> findByOrigenAndIdCompraOrigen(Origen origen, String idCompraOrigen);
}

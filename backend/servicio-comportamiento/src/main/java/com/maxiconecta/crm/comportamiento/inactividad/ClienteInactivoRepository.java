package com.maxiconecta.crm.comportamiento.inactividad;

import com.maxiconecta.crm.comportamiento.compra.Origen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteInactivoRepository extends JpaRepository<ClienteInactivo, Long> {

    Optional<ClienteInactivo> findByOrigenAndIdClienteOrigen(Origen origen, String idClienteOrigen);

    void deleteByOrigenAndIdClienteOrigen(Origen origen, String idClienteOrigen);

    /** Del que lleva más tiempo sin comprar al que lleva menos. */
    Page<ClienteInactivo> findAllByOrderByUltimaCompraAsc(Pageable pageable);
}

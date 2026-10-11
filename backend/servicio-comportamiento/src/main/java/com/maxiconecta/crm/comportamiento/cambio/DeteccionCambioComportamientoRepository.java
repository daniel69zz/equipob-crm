package com.maxiconecta.crm.comportamiento.cambio;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface DeteccionCambioComportamientoRepository extends JpaRepository<DeteccionCambioComportamiento, Long> {

    /** Si la misma variación ya se detectó dentro del periodo actual, no se vuelve a registrar. */
    boolean existsByIdClienteOrigenAndTipoAndDireccionAndDetectadoEnAfter(String idClienteOrigen, TipoVariacion tipo,
                                                                         DireccionVariacion direccion,
                                                                         OffsetDateTime desde);

    /** Alertas de los identificadores de un cliente, de la más reciente a la más antigua. */
    List<DeteccionCambioComportamiento> findByIdClienteOrigenInOrderByDetectadoEnDescIdDesc(
            Collection<String> identificadores, Pageable pageable);
}

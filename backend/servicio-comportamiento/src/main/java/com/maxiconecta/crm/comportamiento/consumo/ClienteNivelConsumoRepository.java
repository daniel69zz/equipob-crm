package com.maxiconecta.crm.comportamiento.consumo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteNivelConsumoRepository extends JpaRepository<ClienteNivelConsumo, String> {

    /** Del cliente que más aporta al que menos; a igual valor, por identificador para un orden estable. */
    Page<ClienteNivelConsumo> findByNivelOrderByValorAcumuladoDescIdClienteOrigenAsc(NivelConsumo nivel,
                                                                                    Pageable pageable);
}

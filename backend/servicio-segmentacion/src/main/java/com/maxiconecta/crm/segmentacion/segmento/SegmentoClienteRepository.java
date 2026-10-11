package com.maxiconecta.crm.segmentacion.segmento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SegmentoClienteRepository extends JpaRepository<SegmentoCliente, Long> {

    /** Clientes de un segmento por identificador, para recorrerlos página a página (SCRUM-357). */
    Page<SegmentoCliente> findBySegmentoOrderByClienteId(String segmento, Pageable pageable);
}

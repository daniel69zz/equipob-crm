package com.maxiconecta.crm.segmentacion.segmento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SegmentoClienteRepository extends JpaRepository<SegmentoCliente, Long> {

    /** Clientes de un segmento por identificador, para recorrerlos página a página (SCRUM-357). */
    Page<SegmentoCliente> findBySegmentoOrderByClienteId(String segmento, Pageable pageable);

    /** SCRUM-378: los demás clientes con el mismo segmento vigente, para la sugerencia de categorías (SCRUM-44). */
    @Query("""
            select s.clienteId from SegmentoCliente s
            where s.segmento = :segmento and s.clienteId <> :clienteId
            order by s.clienteId
            """)
    List<Long> companeros(@Param("segmento") String segmento, @Param("clienteId") Long clienteId);
}

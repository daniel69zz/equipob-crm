package com.maxiconecta.crm.gateway.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, Long> {

    List<EventoAuditoria> findTop100ByOrderByOcurridoEnDesc();

    List<EventoAuditoria> findTop100ByOperacionOrderByOcurridoEnDesc(String operacion);
}

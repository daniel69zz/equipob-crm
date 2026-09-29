package com.maxiconecta.crm.perfil.consentimiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroConsentimientoRepository extends JpaRepository<RegistroConsentimiento, Long> {

    List<RegistroConsentimiento> findByIdClienteOrderByFechaDescIdDesc(Long idCliente);
}

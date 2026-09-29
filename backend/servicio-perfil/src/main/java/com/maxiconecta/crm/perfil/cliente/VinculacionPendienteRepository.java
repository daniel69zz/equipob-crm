package com.maxiconecta.crm.perfil.cliente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VinculacionPendienteRepository extends JpaRepository<VinculacionPendiente, ClienteOrigen.Clave> {

    List<VinculacionPendiente> findByEstadoOrderByDetectadaEn(EstadoVinculacion estado);

    List<VinculacionPendiente> findByIdClienteSugeridoAndEstado(Long idCliente, EstadoVinculacion estado);
}

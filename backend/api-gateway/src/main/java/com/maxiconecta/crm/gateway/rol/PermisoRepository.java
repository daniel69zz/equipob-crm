package com.maxiconecta.crm.gateway.rol;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PermisoRepository extends JpaRepository<Permiso, Integer> {

    List<Permiso> findByCodigoIn(Collection<String> codigos);
}

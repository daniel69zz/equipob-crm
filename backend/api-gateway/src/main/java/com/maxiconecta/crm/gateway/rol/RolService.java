package com.maxiconecta.crm.gateway.rol;

import com.maxiconecta.crm.gateway.comun.ConflictoException;
import com.maxiconecta.crm.gateway.comun.RecursoNoEncontradoException;
import com.maxiconecta.crm.gateway.comun.ReglaNegocioException;
import com.maxiconecta.crm.gateway.rol.RolDtos.ActualizarRolRequest;
import com.maxiconecta.crm.gateway.rol.RolDtos.CrearRolRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RolService {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;

    public RolService(RolRepository rolRepository, PermisoRepository permisoRepository) {
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
    }

    @Transactional(readOnly = true)
    public List<Rol> listar() {
        return rolRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Permiso> listarPermisos() {
        return permisoRepository.findAll();
    }

    @Transactional
    public Rol crear(CrearRolRequest solicitud, String actor) {
        if (rolRepository.existsByCodigo(solicitud.codigo())) {
            throw new ConflictoException("Ya existe un rol con el código " + solicitud.codigo());
        }
        Rol rol = new Rol(solicitud.codigo(), solicitud.nombre(), solicitud.descripcion(),
                permisosExistentes(solicitud.permisos()));
        return rolRepository.save(rol);
    }

    @Transactional
    public Rol actualizar(Integer id, ActualizarRolRequest solicitud, String actor) {
        Rol rol = buscar(id);
        rol.actualizar(solicitud.nombre(), solicitud.descripcion(), permisosExistentes(solicitud.permisos()));
        return rol;
    }

    @Transactional
    public Rol desactivar(Integer id, String actor) {
        Rol rol = buscar(id);
        rol.desactivar();
        return rol;
    }

    @Transactional(readOnly = true)
    public Rol buscarActivoPorCodigo(String codigo) {
        Rol rol = rolRepository.findByCodigo(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el rol " + codigo));
        if (!rol.isActivo()) {
            throw new ReglaNegocioException("El rol " + codigo + " está desactivado y no se puede asignar");
        }
        return rol;
    }

    private Rol buscar(Integer id) {
        return rolRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el rol con id " + id));
    }

    private Set<Permiso> permisosExistentes(Set<String> codigos) {
        List<Permiso> encontrados = permisoRepository.findByCodigoIn(codigos);
        Set<String> codigosEncontrados = encontrados.stream().map(Permiso::getCodigo).collect(Collectors.toSet());
        Set<String> desconocidos = new HashSet<>(codigos);
        desconocidos.removeAll(codigosEncontrados);
        if (!desconocidos.isEmpty()) {
            throw new ReglaNegocioException("Permisos inexistentes: " + String.join(", ", desconocidos));
        }
        return new HashSet<>(encontrados);
    }
}

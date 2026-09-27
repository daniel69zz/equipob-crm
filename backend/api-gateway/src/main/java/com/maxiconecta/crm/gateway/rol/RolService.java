package com.maxiconecta.crm.gateway.rol;

import com.maxiconecta.crm.gateway.auditoria.AuditoriaService;
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
    private final AuditoriaService auditoriaService;

    public RolService(RolRepository rolRepository, PermisoRepository permisoRepository,
                      AuditoriaService auditoriaService) {
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
        this.auditoriaService = auditoriaService;
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
        Rol guardado = rolRepository.save(rol);
        auditoriaService.registrar(actor, AuditoriaService.ROL_CREADO, "ROL", guardado.getId(),
                "codigo=" + guardado.getCodigo() + "; permisos=" + guardado.codigosDePermisos());
        return guardado;
    }

    @Transactional
    public Rol actualizar(Integer id, ActualizarRolRequest solicitud, String actor) {
        Rol rol = buscar(id);
        var permisosAnteriores = rol.codigosDePermisos();
        rol.actualizar(solicitud.nombre(), solicitud.descripcion(), permisosExistentes(solicitud.permisos()));
        auditoriaService.registrar(actor, AuditoriaService.ROL_ACTUALIZADO, "ROL", rol.getId(),
                "codigo=" + rol.getCodigo() + "; permisos antes=" + permisosAnteriores
                        + "; permisos despues=" + rol.codigosDePermisos());
        return rol;
    }

    @Transactional
    public Rol desactivar(Integer id, String actor) {
        Rol rol = buscar(id);
        rol.desactivar();
        auditoriaService.registrar(actor, AuditoriaService.ROL_DESACTIVADO, "ROL", rol.getId(),
                "codigo=" + rol.getCodigo());
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

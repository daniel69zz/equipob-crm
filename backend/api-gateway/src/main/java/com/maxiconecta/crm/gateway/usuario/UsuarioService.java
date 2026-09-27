package com.maxiconecta.crm.gateway.usuario;

import com.maxiconecta.crm.gateway.comun.ConflictoException;
import com.maxiconecta.crm.gateway.comun.RecursoNoEncontradoException;
import com.maxiconecta.crm.gateway.comun.ReglaNegocioException;
import com.maxiconecta.crm.gateway.rol.Rol;
import com.maxiconecta.crm.gateway.rol.RolService;
import com.maxiconecta.crm.gateway.usuario.UsuarioDtos.CrearUsuarioRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolService rolService;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, RolService rolService, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolService = rolService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarioRepository.findAll();
    }

    @Transactional
    public Usuario crear(CrearUsuarioRequest solicitud, String actor) {
        if (usuarioRepository.existsByNombreUsuario(solicitud.nombreUsuario())) {
            throw new ConflictoException("Ya existe el usuario " + solicitud.nombreUsuario());
        }
        Rol rol = rolService.buscarActivoPorCodigo(solicitud.rol());
        Usuario usuario = new Usuario(solicitud.nombreUsuario(), solicitud.nombreCompleto(),
                passwordEncoder.encode(solicitud.password()), rol);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario asignarRol(Long id, String codigoRol, String actor) {
        Usuario usuario = buscar(id);
        String rolAnterior = usuario.getRol().getCodigo();
        if (rolAnterior.equals(codigoRol)) {
            return usuario;
        }
        usuario.asignarRol(rolService.buscarActivoPorCodigo(codigoRol));
        return usuario;
    }

    @Transactional
    public Usuario desactivar(Long id, String actor) {
        Usuario usuario = buscar(id);
        if (usuario.getNombreUsuario().equals(actor)) {
            throw new ReglaNegocioException("Un usuario no puede desactivarse a sí mismo");
        }
        usuario.desactivar();
        return usuario;
    }

    @Transactional
    public void crearAdministradorInicialSiNoHayUsuarios(String nombreUsuario, String nombreCompleto, String password) {
        if (usuarioRepository.count() > 0) {
            return;
        }
        Rol administrador = rolService.buscarActivoPorCodigo("ADMINISTRADOR_CRM");
        usuarioRepository.save(new Usuario(nombreUsuario, nombreCompleto, passwordEncoder.encode(password), administrador));
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario con id " + id));
    }
}

package com.maxiconecta.crm.gateway.usuario;

import com.maxiconecta.crm.gateway.usuario.UsuarioDtos.AsignarRolRequest;
import com.maxiconecta.crm.gateway.usuario.UsuarioDtos.CrearUsuarioRequest;
import com.maxiconecta.crm.gateway.usuario.UsuarioDtos.UsuarioResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarioService.listar().stream().map(UsuarioResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(@Valid @RequestBody CrearUsuarioRequest solicitud, Authentication autenticacion) {
        return UsuarioResponse.de(usuarioService.crear(solicitud, actor(autenticacion)));
    }

    @PutMapping("/{id}/rol")
    public UsuarioResponse asignarRol(@PathVariable Long id, @Valid @RequestBody AsignarRolRequest solicitud,
                                      Authentication autenticacion) {
        return UsuarioResponse.de(usuarioService.asignarRol(id, solicitud.rol(), actor(autenticacion)));
    }

    @PatchMapping("/{id}/desactivar")
    public UsuarioResponse desactivar(@PathVariable Long id, Authentication autenticacion) {
        return UsuarioResponse.de(usuarioService.desactivar(id, actor(autenticacion)));
    }

    private static String actor(Authentication autenticacion) {
        return autenticacion != null ? autenticacion.getName() : "sistema";
    }
}

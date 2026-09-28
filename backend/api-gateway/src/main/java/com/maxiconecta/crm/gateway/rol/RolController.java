package com.maxiconecta.crm.gateway.rol;

import com.maxiconecta.crm.gateway.rol.RolDtos.ActualizarRolRequest;
import com.maxiconecta.crm.gateway.rol.RolDtos.CrearRolRequest;
import com.maxiconecta.crm.gateway.rol.RolDtos.PermisoResponse;
import com.maxiconecta.crm.gateway.rol.RolDtos.RolResponse;
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
@RequestMapping("/api/admin")
public class RolController {

    private final RolService rolService;

    public RolController(RolService rolService) {
        this.rolService = rolService;
    }

    @GetMapping("/roles")
    public List<RolResponse> listarRoles() {
        return rolService.listar().stream().map(RolResponse::de).toList();
    }

    @GetMapping("/permisos")
    public List<PermisoResponse> listarPermisos() {
        return rolService.listarPermisos().stream().map(PermisoResponse::de).toList();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RolResponse crear(@Valid @RequestBody CrearRolRequest solicitud, Authentication autenticacion) {
        return RolResponse.de(rolService.crear(solicitud, actor(autenticacion)));
    }

    @PutMapping("/roles/{id}")
    public RolResponse actualizar(@PathVariable Integer id, @Valid @RequestBody ActualizarRolRequest solicitud,
                                  Authentication autenticacion) {
        return RolResponse.de(rolService.actualizar(id, solicitud, actor(autenticacion)));
    }

    @PatchMapping("/roles/{id}/desactivar")
    public RolResponse desactivar(@PathVariable Integer id, Authentication autenticacion) {
        return RolResponse.de(rolService.desactivar(id, actor(autenticacion)));
    }

    private static String actor(Authentication autenticacion) {
        return autenticacion != null ? autenticacion.getName() : "sistema";
    }
}

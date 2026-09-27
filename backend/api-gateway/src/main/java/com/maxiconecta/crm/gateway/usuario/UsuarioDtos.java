package com.maxiconecta.crm.gateway.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    public record UsuarioResponse(Long id, String nombreUsuario, String nombreCompleto, String rol, boolean activo) {

        public static UsuarioResponse de(Usuario usuario) {
            return new UsuarioResponse(usuario.getId(), usuario.getNombreUsuario(), usuario.getNombreCompleto(),
                    usuario.getRol().getCodigo(), usuario.isActivo());
        }
    }

    public record CrearUsuarioRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9._-]{3,60}", message = "solo minúsculas, números, punto, guion y guion bajo (3 a 60)")
            String nombreUsuario,
            @NotBlank String nombreCompleto,
            @NotBlank @Size(min = 8, message = "debe tener al menos 8 caracteres") String password,
            @NotBlank String rol) {
    }

    public record AsignarRolRequest(@NotBlank String rol) {
    }
}

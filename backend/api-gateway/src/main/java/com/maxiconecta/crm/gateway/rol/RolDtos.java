package com.maxiconecta.crm.gateway.rol;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Set;

public final class RolDtos {

    private RolDtos() {
    }

    public record RolResponse(Integer id, String codigo, String nombre, String descripcion,
                              boolean activo, List<String> permisos) {

        public static RolResponse de(Rol rol) {
            return new RolResponse(rol.getId(), rol.getCodigo(), rol.getNombre(), rol.getDescripcion(),
                    rol.isActivo(), rol.codigosDePermisos());
        }
    }

    public record PermisoResponse(String codigo, String descripcion) {

        public static PermisoResponse de(Permiso permiso) {
            return new PermisoResponse(permiso.getCodigo(), permiso.getDescripcion());
        }
    }

    public record CrearRolRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z_]*", message = "debe estar en mayúsculas y usar guion bajo")
            String codigo,
            @NotBlank String nombre,
            String descripcion,
            @NotEmpty Set<String> permisos) {
    }

    public record ActualizarRolRequest(
            @NotBlank String nombre,
            String descripcion,
            @NotEmpty Set<String> permisos) {
    }
}

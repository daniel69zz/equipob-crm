package com.maxiconecta.crm.gateway.auth;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String usuario, @NotBlank String password) {
    }

    public record LoginResponse(String token, String tipo, Instant expiraEn, String usuario, String nombreCompleto,
                                String rol, List<String> permisos) {
    }

    public record SesionResponse(String usuario, String nombreCompleto, String rol, List<String> permisos) {
    }
}

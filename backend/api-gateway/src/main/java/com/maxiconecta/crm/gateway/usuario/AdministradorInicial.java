package com.maxiconecta.crm.gateway.usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Crea el primer Administrador de CRM cuando la base no tiene usuarios.
 * Las credenciales vienen de variables de entorno, nunca del repositorio.
 */
@Component
@EnableConfigurationProperties(AdministradorInicial.Propiedades.class)
public class AdministradorInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);

    private final UsuarioService usuarioService;
    private final Propiedades propiedades;

    public AdministradorInicial(UsuarioService usuarioService, Propiedades propiedades) {
        this.usuarioService = usuarioService;
        this.propiedades = propiedades;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (propiedades.password() == null || propiedades.password().isBlank()) {
            log.info("ADMIN_PASSWORD no definido: no se crea el administrador inicial");
            return;
        }
        usuarioService.crearAdministradorInicialSiNoHayUsuarios(
                propiedades.usuario(), propiedades.nombreCompleto(), propiedades.password());
    }

    @ConfigurationProperties(prefix = "crm.seguridad.admin-inicial")
    public record Propiedades(String usuario, String nombreCompleto, String password) {
    }
}

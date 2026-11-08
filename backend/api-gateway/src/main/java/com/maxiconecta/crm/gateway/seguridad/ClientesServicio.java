package com.maxiconecta.crm.gateway.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Sistemas del ERP que consultan el CRM con un token JWT de servicio, como Marketplace y Ventas en RIO-CRM-04
 * (SCRUM-545, docs/contratos-eventos/RIO-CRM-04-consulta-puntos.md). No son usuarios internos: no inician
 * sesión con contraseña, tienen el rol {@value #ROL} y solo los permisos de su configuración.
 * <p>
 * Los secretos vienen de variables de entorno, nunca del repositorio. Un cliente sin secreto, o con uno de
 * menos de {@value #LONGITUD_MINIMA_SECRETO} caracteres, queda deshabilitado: no obtiene token y los que ya
 * tenía dejan de valer, porque el filtro JWT vuelve a buscarlo en cada petición.
 */
public class ClientesServicio {

    public static final String ROL = "SISTEMA";
    static final int LONGITUD_MINIMA_SECRETO = 32;

    private static final Logger log = LoggerFactory.getLogger(ClientesServicio.class);

    private final Map<String, Propiedades.Cliente> habilitados = new TreeMap<>();

    public ClientesServicio(Propiedades propiedades) {
        Map<String, Propiedades.Cliente> configurados = propiedades.clientes() == null ? Map.of() : propiedades.clientes();
        configurados.forEach((id, cliente) -> {
            String secreto = cliente.secreto();
            if (secreto == null || secreto.isBlank()) {
                log.info("Cliente de servicio {} sin secreto: queda deshabilitado", id);
            } else if (secreto.getBytes(StandardCharsets.UTF_8).length < LONGITUD_MINIMA_SECRETO) {
                log.warn("El secreto del cliente de servicio {} tiene menos de {} caracteres: queda deshabilitado",
                        id, LONGITUD_MINIMA_SECRETO);
            } else {
                habilitados.put(id, cliente);
            }
        });
    }

    /** @return la sesión del cliente si el secreto coincide; vacío en cualquier otro caso */
    public Optional<SesionToken> autenticar(String id, String secreto) {
        Propiedades.Cliente cliente = id == null ? null : habilitados.get(id);
        if (cliente == null || secreto == null) {
            return Optional.empty();
        }
        // Comparación en tiempo constante, para no revelar cuántos caracteres del secreto coinciden.
        boolean coincide = MessageDigest.isEqual(cliente.secreto().getBytes(StandardCharsets.UTF_8),
                secreto.getBytes(StandardCharsets.UTF_8));
        return coincide ? sesion(id, cliente) : Optional.empty();
    }

    /** @return la sesión de un cliente que sigue habilitado, para validar su token en cada petición */
    public Optional<SesionToken> sesionVigente(String id) {
        Propiedades.Cliente cliente = id == null ? null : habilitados.get(id);
        return cliente == null ? Optional.empty() : sesion(id, cliente);
    }

    private static Optional<SesionToken> sesion(String id, Propiedades.Cliente cliente) {
        List<String> permisos = cliente.permisos() == null ? List.of() : List.copyOf(cliente.permisos());
        return Optional.of(new SesionToken(id, cliente.nombre() == null ? id : cliente.nombre(), ROL, permisos));
    }

    @ConfigurationProperties(prefix = "crm.seguridad.servicios")
    public record Propiedades(Map<String, Cliente> clientes) {

        public record Cliente(String nombre, String secreto, List<String> permisos) {
        }
    }
}

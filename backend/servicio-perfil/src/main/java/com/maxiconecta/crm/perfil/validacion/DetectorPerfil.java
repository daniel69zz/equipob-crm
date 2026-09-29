package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.cliente.CambioCampo;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.Direccion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Motor de detección de perfiles incompletos o inconsistentes (SCRUM-12). Vuelve a evaluar un
 * perfil ya guardado contra el catálogo de reglas (docs/perfil/catalogo-reglas-validacion.md),
 * sin esperar un nuevo evento de sincronización, y lo etiqueta con la regla incumplida.
 */
@Component
public class DetectorPerfil {

    private static final Pattern NUMERO_CI = Pattern.compile("^\\d{5,8}(-[A-Za-z0-9]{1,3})?$");
    private static final Pattern NUMERO_NIT = Pattern.compile("^\\d{6,13}$");

    /** Etiqueta el perfil según las reglas incumplidas y devuelve los cambios aplicados. */
    public List<CambioCampo> detectar(Cliente cliente) {
        List<String> motivosIncompleto = completitud(cliente);
        if (!motivosIncompleto.isEmpty()) {
            return cliente.marcarEstado(motivosIncompleto);
        }
        List<String> motivosInconsistencia = coherencia(cliente);
        if (!motivosInconsistencia.isEmpty()) {
            return cliente.marcarInconsistente(motivosInconsistencia);
        }
        return cliente.marcarEstado(List.of());
    }

    private static List<String> completitud(Cliente cliente) {
        List<String> motivos = new ArrayList<>();
        requerido(cliente.getNombres(), "nombres", motivos);
        requerido(cliente.getApellidos(), "apellidos", motivos);
        requerido(cliente.getTipoDocumento(), "tipoDocumento", motivos);
        requerido(cliente.getNumeroDocumento(), "numeroDocumento", motivos);
        if (cliente.getEmail() == null && cliente.getTelefono() == null) {
            motivos.add("contacto: se necesita al menos un correo o un teléfono válido");
        }
        return motivos;
    }

    private static void requerido(String valor, String campo, List<String> motivos) {
        if (valor == null || valor.isBlank()) {
            motivos.add(campo + ": vacío");
        }
    }

    private static List<String> coherencia(Cliente cliente) {
        List<String> motivos = new ArrayList<>();
        formatoDocumento(cliente, motivos);
        direccionPrincipal(cliente, motivos);
        return motivos;
    }

    /** El formato del número no corresponde al tipo de documento declarado. */
    private static void formatoDocumento(Cliente cliente, List<String> motivos) {
        String tipo = cliente.getTipoDocumento();
        String numero = cliente.getNumeroDocumento();
        if (tipo == null || numero == null) {
            return;
        }
        Pattern esperado = switch (tipo) {
            case "CI" -> NUMERO_CI;
            case "NIT" -> NUMERO_NIT;
            default -> null;
        };
        if (esperado != null && !esperado.matcher(numero).matches()) {
            motivos.add("numeroDocumento: no coincide con el formato de " + tipo);
        }
    }

    /** Hay direcciones activas pero ninguna está marcada como principal. */
    private static void direccionPrincipal(Cliente cliente, List<String> motivos) {
        List<Direccion> activas = cliente.getDireccionesActivas();
        if (!activas.isEmpty() && activas.stream().noneMatch(Direccion::isPrincipal)) {
            motivos.add("direcciones: ninguna dirección principal");
        }
    }
}

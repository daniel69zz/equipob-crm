package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.sincronizacion.EventoClienteRecibido;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normaliza los datos del perfil antes de validarlos y compararlos, para que un cambio que solo es
 * de formato (mayúsculas, espacios, guiones del teléfono) no cuente como cambio. Las reglas están en
 * docs/perfil/mapeo-datos-perfil.md. Un valor que no se puede normalizar se deja como vino, para que
 * la validación lo marque como mal formado.
 */
@Component
public class NormalizadorPerfil {

    private static final Locale ES = Locale.forLanguageTag("es");
    private static final Set<String> PARTICULAS = Set.of("de", "del", "la", "las", "los", "y", "e");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");
    private static final Pattern CARACTERES_DE_TELEFONO = Pattern.compile("^[0-9+ ()\\-.]+$");
    private static final String CODIGO_BOLIVIA = "591";

    public EventoClienteRecibido.DatosCliente normalizar(EventoClienteRecibido.DatosCliente datos) {
        EventoClienteRecibido.Contacto contacto = datos.contacto();
        return new EventoClienteRecibido.DatosCliente(datos.idCliente(), datos.fechaActualizacion(),
                nombrePropio(datos.nombres()), nombrePropio(datos.apellidos()),
                mayusculas(datos.tipoDocumento()), documento(datos.numeroDocumento()),
                new EventoClienteRecibido.Contacto(email(contacto.email()), telefono(contacto.telefono())),
                datos.direcciones().stream().map(this::direccion).toList(), datos.camposInformados());
    }

    private EventoClienteRecibido.DatosDireccion direccion(EventoClienteRecibido.DatosDireccion d) {
        return new EventoClienteRecibido.DatosDireccion(recortar(d.idDireccion()), mayusculas(d.tipo()),
                texto(d.calle()), texto(d.numero()), texto(d.zona()), nombrePropio(d.ciudad()), texto(d.referencia()),
                d.principal());
    }

    /** "  ANA   maría " → "Ana María"; "pérez de la cruz" → "Pérez de la Cruz". */
    static String nombrePropio(String valor) {
        String limpio = texto(valor);
        if (limpio == null || limpio.isEmpty()) {
            return limpio;
        }
        String[] palabras = limpio.toLowerCase(ES).split(" ");
        StringBuilder resultado = new StringBuilder();
        for (int i = 0; i < palabras.length; i++) {
            if (i > 0) {
                resultado.append(' ');
            }
            resultado.append(i > 0 && PARTICULAS.contains(palabras[i]) ? palabras[i] : capitalizarPartes(palabras[i]));
        }
        return resultado.toString();
    }

    /** Capitaliza cada parte de una palabra compuesta: "pérez-rojas" → "Pérez-Rojas". */
    private static String capitalizarPartes(String palabra) {
        StringBuilder resultado = new StringBuilder(palabra.length());
        boolean inicio = true;
        for (char c : palabra.toCharArray()) {
            resultado.append(inicio ? Character.toUpperCase(c) : c);
            inicio = c == '-' || c == '\'';
        }
        return resultado.toString();
    }

    static String documento(String valor) {
        String limpio = mayusculas(valor);
        return limpio == null ? null : limpio.replace(" ", "").replace(".", "");
    }

    static String email(String valor) {
        return valor == null ? null : valor.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
    }

    /** "+591 700-12345" → "+59170012345"; "70012345" → "+59170012345". */
    static String telefono(String valor) {
        String limpio = recortar(valor);
        if (limpio == null || limpio.isEmpty() || !CARACTERES_DE_TELEFONO.matcher(limpio).matches()) {
            return limpio;
        }
        boolean internacional = limpio.startsWith("+");
        String digitos = limpio.replaceAll("[^0-9]", "");
        if (internacional) {
            return "+" + digitos;
        }
        if (digitos.length() == 8) {
            return "+" + CODIGO_BOLIVIA + digitos;
        }
        if (digitos.length() == 11 && digitos.startsWith(CODIGO_BOLIVIA)) {
            return "+" + digitos;
        }
        return digitos;
    }

    private static String mayusculas(String valor) {
        String limpio = recortar(valor);
        return limpio == null ? null : limpio.toUpperCase(ES);
    }

    private static String texto(String valor) {
        String limpio = recortar(valor);
        return limpio == null ? null : ESPACIOS.matcher(limpio).replaceAll(" ");
    }

    private static String recortar(String valor) {
        return valor == null ? null : valor.trim();
    }
}

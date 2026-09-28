package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.cliente.Direccion;
import com.maxiconecta.crm.perfil.cliente.TipoDireccion;
import com.maxiconecta.crm.perfil.sincronizacion.EventoClienteRecibido;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Valida los datos del perfil que llegan en un evento RIO-CRM-01.
 * <p>
 * Un dato obligatorio vacío o mal formado no se guarda (queda vacío) y se anota como motivo:
 * el perfil queda incompleto. Las reglas están en docs/contratos-eventos/RIO-CRM-01-datos-cliente.md.
 */
@Component
public class ValidadorPerfil {

    static final Set<String> TIPOS_DOCUMENTO = Set.of("CI", "NIT", "PASAPORTE", "CE");

    private static final Pattern NOMBRE = Pattern.compile("^[\\p{L}][\\p{L} .'\\-]*$");
    private static final Pattern DOCUMENTO = Pattern.compile("^[A-Za-z0-9\\-]{4,20}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TELEFONO = Pattern.compile("^\\+?[0-9 ()\\-]{7,20}$");

    public PerfilValidado validar(EventoClienteRecibido.DatosCliente datos) {
        List<String> motivos = new ArrayList<>();
        String nombres = nombre(datos.nombres(), "nombres", motivos);
        String apellidos = nombre(datos.apellidos(), "apellidos", motivos);
        String tipoDocumento = tipoDocumento(datos.tipoDocumento(), motivos);
        String numeroDocumento = conFormato(datos.numeroDocumento(), "numeroDocumento", DOCUMENTO, true, motivos);

        EventoClienteRecibido.Contacto contacto = datos.contacto();
        String email = conFormato(contacto.email(), "email", EMAIL, false, motivos);
        if (email != null && email.length() > 150) {
            motivos.add("email: excede 150 caracteres");
            email = null;
        }
        String telefono = telefono(contacto.telefono(), motivos);
        if (email == null && telefono == null) {
            motivos.add("contacto: se necesita al menos un correo o un teléfono válido");
        }

        return new PerfilValidado(nombres, apellidos, tipoDocumento, numeroDocumento, email, telefono,
                direcciones(datos.direcciones(), motivos), List.copyOf(motivos));
    }

    private static String nombre(String valor, String campo, List<String> motivos) {
        String texto = limpio(valor);
        if (texto == null) {
            motivos.add(campo + ": vacío");
            return null;
        }
        if (texto.length() > 100) {
            motivos.add(campo + ": excede 100 caracteres");
            return null;
        }
        if (!NOMBRE.matcher(texto).matches()) {
            motivos.add(campo + ": formato inválido");
            return null;
        }
        return texto;
    }

    private static String tipoDocumento(String valor, List<String> motivos) {
        String texto = limpio(valor);
        if (texto == null) {
            motivos.add("tipoDocumento: vacío");
            return null;
        }
        String tipo = texto.toUpperCase();
        if (!TIPOS_DOCUMENTO.contains(tipo)) {
            motivos.add("tipoDocumento: valor no válido (" + texto + ")");
            return null;
        }
        return tipo;
    }

    private static String telefono(String valor, List<String> motivos) {
        String texto = conFormato(valor, "telefono", TELEFONO, false, motivos);
        if (texto != null && texto.chars().filter(Character::isDigit).count() < 7) {
            motivos.add("telefono: formato inválido");
            return null;
        }
        return texto;
    }

    private static String conFormato(String valor, String campo, Pattern formato, boolean obligatorio,
                                     List<String> motivos) {
        String texto = limpio(valor);
        if (texto == null) {
            if (obligatorio) {
                motivos.add(campo + ": vacío");
            }
            return null;
        }
        if (!formato.matcher(texto).matches()) {
            motivos.add(campo + ": formato inválido");
            return null;
        }
        return texto;
    }

    private static List<Direccion.DatosDireccion> direcciones(List<EventoClienteRecibido.DatosDireccion> recibidas,
                                                              List<String> motivos) {
        List<Direccion.DatosDireccion> validas = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        for (int i = 0; i < recibidas.size(); i++) {
            EventoClienteRecibido.DatosDireccion d = recibidas.get(i);
            String id = limpio(d.idDireccion());
            String etiqueta = "direcciones[" + (id != null ? id : String.valueOf(i)) + "]";
            if (id == null) {
                motivos.add(etiqueta + ".idDireccion: vacío");
                continue;
            }
            if (id.length() > 64 || !vistas.add(id)) {
                motivos.add(etiqueta + ".idDireccion: " + (id.length() > 64 ? "excede 64 caracteres" : "repetido"));
                continue;
            }
            String calle = texto(d.calle(), 200, etiqueta + ".calle", true, motivos);
            String ciudad = texto(d.ciudad(), 100, etiqueta + ".ciudad", true, motivos);
            if (calle == null || ciudad == null) {
                continue;
            }
            validas.add(new Direccion.DatosDireccion(id, TipoDireccion.de(d.tipo()), calle,
                    texto(d.numero(), 20, etiqueta + ".numero", false, motivos),
                    texto(d.zona(), 100, etiqueta + ".zona", false, motivos),
                    ciudad,
                    texto(d.referencia(), 300, etiqueta + ".referencia", false, motivos),
                    d.principal()));
        }
        return validas;
    }

    private static String texto(String valor, int largoMaximo, String campo, boolean obligatorio,
                                List<String> motivos) {
        String texto = limpio(valor);
        if (texto == null) {
            if (obligatorio) {
                motivos.add(campo + ": vacío");
            }
            return null;
        }
        if (texto.length() > largoMaximo) {
            motivos.add(campo + ": excede " + largoMaximo + " caracteres");
            return null;
        }
        return texto;
    }

    private static String limpio(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }
}

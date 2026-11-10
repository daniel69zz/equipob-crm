package com.maxiconecta.crm.interacciones.compras;

import com.maxiconecta.crm.interacciones.comun.ReglaInvalidaException;
import com.maxiconecta.crm.interacciones.perfil.ClientesPerfil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Búsqueda de las compras del cliente para vincularlas a una interacción (SCRUM-446,
 * docs/interacciones/vinculacion-compras.md).
 */
@Service
public class ServicioComprasElegibles {

    static final int LIMITE_POR_DEFECTO = 20;
    static final int LIMITE_MAXIMO = 100;
    static final int MAXIMO_TEXTO = 64;

    private final ClientesPerfil perfiles;
    private final ComprasCliente compras;

    public ServicioComprasElegibles(ClientesPerfil perfiles, ComprasCliente compras) {
        this.perfiles = perfiles;
        this.compras = compras;
    }

    public ComprasElegibles consultar(long clienteId, String texto, Integer limite) {
        Map<String, String> errores = new TreeMap<>();
        String buscado = texto == null || texto.isBlank() ? null : texto.strip();
        if (buscado != null && buscado.length() > MAXIMO_TEXTO) {
            errores.put("texto", "admite hasta " + MAXIMO_TEXTO + " caracteres");
        }
        int cantidad = limite == null ? LIMITE_POR_DEFECTO : limite;
        if (cantidad < 1 || cantidad > LIMITE_MAXIMO) {
            errores.put("limite", "debe estar entre 1 y " + LIMITE_MAXIMO);
        }
        if (!errores.isEmpty()) {
            throw new ReglaInvalidaException("Búsqueda de compras no válida: " + String.join(", ",
                    errores.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).toList()), errores);
        }
        List<String> identificadores = clienteId <= 0 ? null : perfiles.identificadores(clienteId).orElse(null);
        if (identificadores == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el cliente " + clienteId + " en el CRM");
        }
        // Un cliente sin identificadores en Marketplace y Ventas no tiene compras: no se consulta.
        if (identificadores.isEmpty()) {
            return new ComprasElegibles(List.of(), true, List.of(ComprasElegibles.SIN_COMPRAS));
        }
        ComprasRecientes encontradas = compras.elegibles(clienteId, identificadores, buscado, cantidad);
        if (!encontradas.disponible()) {
            return new ComprasElegibles(List.of(), false, List.of(ComprasElegibles.NO_DISPONIBLES));
        }
        return new ComprasElegibles(encontradas.compras(), true, List.of());
    }
}

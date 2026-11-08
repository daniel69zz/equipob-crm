package com.maxiconecta.crm.interacciones.historial;

import com.maxiconecta.crm.interacciones.comun.ReglaInvalidaException;
import com.maxiconecta.crm.interacciones.interaccion.FiltroHistorial;
import com.maxiconecta.crm.interacciones.interaccion.PaginaInteracciones;
import com.maxiconecta.crm.interacciones.interaccion.RegistroInteracciones;
import com.maxiconecta.crm.interacciones.perfil.ClientesPerfil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Consulta unificada del historial del cliente (SCRUM-440, diseño de SCRUM-437). */
@Service
public class ServicioHistorialCliente {

    static final int LIMITE_POR_DEFECTO = 50;
    static final int LIMITE_MAXIMO = 100;

    private final ClientesPerfil perfiles;
    private final RegistroInteracciones interacciones;

    public ServicioHistorialCliente(ClientesPerfil perfiles, RegistroInteracciones interacciones) {
        this.perfiles = perfiles;
        this.interacciones = interacciones;
    }

    public HistorialCliente consultar(long clienteId, Integer limite) {
        int cantidad = limite == null ? LIMITE_POR_DEFECTO : limite;
        if (cantidad < 1 || cantidad > LIMITE_MAXIMO) {
            throw new ReglaInvalidaException("El límite debe estar entre 1 y " + LIMITE_MAXIMO,
                    Map.of("limite", "debe estar entre 1 y " + LIMITE_MAXIMO));
        }
        if (clienteId <= 0 || perfiles.identificadores(clienteId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el cliente " + clienteId + " en el CRM");
        }
        PaginaInteracciones recientes = interacciones.historial(clienteId,
                new FiltroHistorial(null, null, null, null, null, 0, cantidad));

        List<EventoHistorial> eventos = new ArrayList<>(recientes.interacciones().stream()
                .map(EventoHistorial::de).toList());
        List<String> avisos = new ArrayList<>();
        boolean sinInteracciones = recientes.total() == 0;
        if (sinInteracciones) {
            avisos.add(HistorialCliente.SIN_INTERACCIONES);
        }
        return new HistorialCliente(clienteId, eventos, recientes.total(), sinInteracciones,
                recientes.total() > eventos.size(), avisos);
    }
}

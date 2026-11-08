package com.maxiconecta.crm.interacciones.historial;

import com.maxiconecta.crm.interacciones.comun.ReglaInvalidaException;
import com.maxiconecta.crm.interacciones.compras.ComprasCliente;
import com.maxiconecta.crm.interacciones.compras.ComprasRecientes;
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

/**
 * Consulta unificada del historial del cliente (SCRUM-440 y SCRUM-441, diseño de SCRUM-437): las interacciones del
 * servicio y las compras de servicio-comportamiento, unidas en orden cronológico.
 */
@Service
public class ServicioHistorialCliente {

    static final int LIMITE_POR_DEFECTO = 50;
    static final int LIMITE_MAXIMO = 100;

    private final ClientesPerfil perfiles;
    private final RegistroInteracciones interacciones;
    private final ComprasCliente compras;

    public ServicioHistorialCliente(ClientesPerfil perfiles, RegistroInteracciones interacciones,
                                    ComprasCliente compras) {
        this.perfiles = perfiles;
        this.interacciones = interacciones;
        this.compras = compras;
    }

    public HistorialCliente consultar(long clienteId, Integer limite) {
        int cantidad = limite == null ? LIMITE_POR_DEFECTO : limite;
        if (cantidad < 1 || cantidad > LIMITE_MAXIMO) {
            throw new ReglaInvalidaException("El límite debe estar entre 1 y " + LIMITE_MAXIMO,
                    Map.of("limite", "debe estar entre 1 y " + LIMITE_MAXIMO));
        }
        List<String> identificadores = clienteId <= 0 ? null : perfiles.identificadores(clienteId).orElse(null);
        if (identificadores == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el cliente " + clienteId + " en el CRM");
        }
        PaginaInteracciones recientes = interacciones.historial(clienteId,
                new FiltroHistorial(null, null, null, null, null, 0, cantidad));
        // Un cliente sin identificadores en Marketplace y Ventas no tiene compras: no se consulta.
        ComprasRecientes deVentas = identificadores.isEmpty() ? ComprasRecientes.ninguna()
                : compras.recientes(clienteId, identificadores, cantidad);

        List<EventoHistorial> todos = new ArrayList<>();
        recientes.interacciones().forEach(i -> todos.add(EventoHistorial.de(i)));
        deVentas.compras().forEach(c -> todos.add(EventoHistorial.de(c)));
        todos.sort(EventoHistorial.CRONOLOGICO);
        List<EventoHistorial> eventos = List.copyOf(todos.subList(0, Math.min(cantidad, todos.size())));

        List<String> avisos = new ArrayList<>();
        boolean sinInteracciones = recientes.total() == 0;
        if (sinInteracciones) {
            avisos.add(HistorialCliente.SIN_INTERACCIONES);
        }
        if (!deVentas.disponible()) {
            avisos.add(HistorialCliente.COMPRAS_NO_DISPONIBLES);
        }
        return new HistorialCliente(clienteId, eventos, recientes.total(), deVentas.total(), sinInteracciones,
                deVentas.disponible(), recientes.total() + deVentas.total() > eventos.size(), avisos);
    }
}

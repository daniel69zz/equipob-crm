package com.maxiconecta.crm.perfil.sincronizacion;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigenRepository;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica un evento de datos del cliente al perfil. Todo ocurre en una transacción: si algo
 * falla, el perfil no queda a medias.
 * <p>
 * El evento se resuelve por su identificador de origen (RF-62): si el identificador no está
 * vinculado a ningún perfil, se crea uno nuevo y se vincula.
 */
@Service
public class ProcesadorClientes {

    private final EventoClienteRepository eventos;
    private final ClienteRepository clientes;
    private final ClienteOrigenRepository origenes;
    private final LectorEventosCliente lector;

    public ProcesadorClientes(EventoClienteRepository eventos, ClienteRepository clientes,
                              ClienteOrigenRepository origenes, LectorEventosCliente lector) {
        this.eventos = eventos;
        this.clientes = clientes;
        this.origenes = origenes;
        this.lector = lector;
    }

    @Transactional
    public ResultadoSincronizacion aplicar(Long idEvento) {
        EventoCliente registro = eventos.findById(idEvento)
                .orElseThrow(() -> new IllegalStateException("No existe el evento " + idEvento + " en la bitácora"));
        EventoClienteRecibido evento = lector.leer(registro.getContenido());
        EventoClienteRecibido.DatosCliente datos = evento.cliente();
        ClienteOrigen.Clave clave = new ClienteOrigen.Clave(evento.origen(), datos.idCliente());

        ClienteOrigen vinculo = origenes.findById(clave).orElse(null);
        if (vinculo != null) {
            throw new EventoDescartadoException(vinculo.getIdCliente(), "El cliente " + clave
                    + " ya tiene perfil; la actualización de perfiles existentes aún no está disponible");
        }

        Cliente cliente = new Cliente();
        cliente.identificar(datos.nombres(), datos.apellidos(), datos.tipoDocumento(), datos.numeroDocumento());
        cliente.actualizarContacto(datos.contacto().email(), datos.contacto().telefono());
        cliente.registrarActualizacion(evento.origen(), evento.responsableDelCambio());
        clientes.save(cliente);
        origenes.saveAndFlush(new ClienteOrigen(evento.origen(), datos.idCliente(), cliente.getId(),
                evento.fechaCambio()));
        return new ResultadoSincronizacion(cliente.getId(), EstadoEventoCliente.PROCESADO, null);
    }
}

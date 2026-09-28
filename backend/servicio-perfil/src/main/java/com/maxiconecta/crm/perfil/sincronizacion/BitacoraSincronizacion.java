package com.maxiconecta.crm.perfil.sincronizacion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe en la bitácora de sincronización. Cada método es su propia transacción, para que el
 * registro de un mensaje no se pierda aunque su procesamiento falle.
 */
@Service
public class BitacoraSincronizacion {

    private final EventoClienteRepository repository;
    private final LectorEventosCliente lector;

    public BitacoraSincronizacion(EventoClienteRepository repository, LectorEventosCliente lector) {
        this.repository = repository;
        this.lector = lector;
    }

    @Transactional
    public Long registrarRecepcion(String contenido) {
        LectorEventosCliente.Cabecera cabecera = lector.cabecera(contenido);
        return repository.save(new EventoCliente(contenido, cabecera.idEvento(), cabecera.tipoEvento(),
                cabecera.origen(), cabecera.idClienteOrigen())).getId();
    }

    @Transactional
    public void cerrar(Long idEvento, EstadoEventoCliente estado, Long idCliente, String causa, int intentos) {
        repository.findById(idEvento).ifPresent(evento -> evento.cerrar(estado, idCliente, causa, intentos));
    }
}

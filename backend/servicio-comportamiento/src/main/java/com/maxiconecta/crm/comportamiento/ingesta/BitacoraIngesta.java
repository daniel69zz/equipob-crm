package com.maxiconecta.crm.comportamiento.ingesta;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe en la bitácora de ingesta. Cada método es su propia transacción, para que el registro
 * de un mensaje no se pierda aunque su procesamiento falle.
 */
@Service
public class BitacoraIngesta {

    private final EventoRecibidoRepository repository;
    private final LectorEventos lector;

    public BitacoraIngesta(EventoRecibidoRepository repository, LectorEventos lector) {
        this.repository = repository;
        this.lector = lector;
    }

    @Transactional
    public Long registrarRecepcion(String contenido) {
        LectorEventos.Cabecera cabecera = lector.cabecera(contenido);
        return repository.save(new EventoRecibido(contenido, cabecera.idEvento(), cabecera.tipoEvento(),
                cabecera.origen(), cabecera.idTransaccion())).getId();
    }

    /** Tipo de evento que declaraba el mensaje al recibirlo, o null si no se pudo leer. */
    @Transactional(readOnly = true)
    public String tipoEvento(Long idEvento) {
        return repository.findById(idEvento).map(EventoRecibido::getTipoEvento).orElse(null);
    }

    @Transactional
    public void marcarFallido(Long idEvento, String causa) {
        repository.findById(idEvento).ifPresent(evento -> evento.marcarFallido(causa));
    }

    @Transactional
    public void marcarDescartado(Long idEvento, String causa) {
        repository.findById(idEvento).ifPresent(evento -> evento.marcarDescartado(causa));
    }
}

package com.maxiconecta.crm.comportamiento.ingesta;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Persiste el inicio y el resultado de cada reproceso en transacciones independientes. */
@Service
public class RegistroIntentosReproceso {

    private final EventoRecibidoRepository eventos;
    private final IntentoReprocesoRepository intentos;

    public RegistroIntentosReproceso(EventoRecibidoRepository eventos, IntentoReprocesoRepository intentos) {
        this.eventos = eventos;
        this.intentos = intentos;
    }

    @Transactional
    public IntentoReproceso iniciar(Long idEvento, String usuario) {
        EventoRecibido evento = eventos.buscarParaActualizar(idEvento)
                .orElseThrow(() -> new EventoNoEncontradoException(idEvento));
        if (evento.getEstado() != EstadoEvento.FALLIDO) {
            throw new EventoNoReprocesableException("El evento " + idEvento + " está " + evento.getEstado()
                    + " y solo se pueden reprocesar eventos FALLIDO");
        }
        if (intentos.existsByEvento_IdAndResultado(idEvento, ResultadoReproceso.EN_PROCESO)) {
            throw new EventoNoReprocesableException("El evento " + idEvento + " ya tiene un reproceso en curso");
        }
        int numero = intentos.ultimoNumero(idEvento) + 1;
        return intentos.saveAndFlush(new IntentoReproceso(evento, numero, usuario));
    }

    @Transactional
    public IntentoReproceso completar(Long idIntento, EstadoEvento estado, String causa) {
        IntentoReproceso intento = intentos.findById(idIntento)
                .orElseThrow(() -> new IllegalStateException("No existe el intento " + idIntento));
        ResultadoReproceso resultado = switch (estado) {
            case PROCESADO -> ResultadoReproceso.PROCESADO;
            case FALLIDO -> ResultadoReproceso.FALLIDO;
            case DESCARTADO -> ResultadoReproceso.DESCARTADO;
            case RECIBIDO -> throw new IllegalArgumentException("RECIBIDO no es un resultado de reproceso");
        };
        intento.completar(resultado, causa);
        return intento;
    }

    @Transactional(readOnly = true)
    public List<IntentoReproceso> listar(Long idEvento) {
        if (!eventos.existsById(idEvento)) {
            throw new EventoNoEncontradoException(idEvento);
        }
        return intentos.findByEvento_IdOrderByNumeroDesc(idEvento);
    }
}

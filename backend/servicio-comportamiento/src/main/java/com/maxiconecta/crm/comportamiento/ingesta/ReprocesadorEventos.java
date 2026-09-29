package com.maxiconecta.crm.comportamiento.ingesta;

import org.springframework.stereotype.Service;

import java.util.List;

/** Reprocesa un evento ya registrado sin crear una segunda entrada en la bitácora. */
@Service
public class ReprocesadorEventos {

    private final RegistroIntentosReproceso registro;
    private final IngestaCompras ingesta;

    public ReprocesadorEventos(RegistroIntentosReproceso registro, IngestaCompras ingesta) {
        this.registro = registro;
        this.ingesta = ingesta;
    }

    public IntentoReproceso reprocesar(Long idEvento, String usuario) {
        IntentoReproceso intento = registro.iniciar(idEvento, usuario);
        IngestaCompras.ResultadoProcesamiento resultado;
        try {
            resultado = ingesta.procesarRegistrado(idEvento);
        } catch (RuntimeException ex) {
            registro.completar(intento.getId(), EstadoEvento.FALLIDO, IngestaCompras.causa(ex));
            throw ex;
        }
        return registro.completar(intento.getId(), resultado.estado(), resultado.causa());
    }

    public List<IntentoReproceso> intentos(Long idEvento) {
        return registro.listar(idEvento);
    }
}

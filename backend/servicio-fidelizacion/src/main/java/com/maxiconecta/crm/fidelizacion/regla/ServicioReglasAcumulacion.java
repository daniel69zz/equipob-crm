package com.maxiconecta.crm.fidelizacion.regla;

import com.maxiconecta.crm.fidelizacion.comun.ConflictoException;
import com.maxiconecta.crm.fidelizacion.comun.ReglaInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Administración de la regla de acumulación de puntos (SCRUM-46). Guardar una regla crea una versión
 * nueva que se aplica a los movimientos que se procesen desde ese momento; los puntos ya otorgados no se
 * recalculan, porque cada movimiento guarda los puntos y la versión de la regla con la que se calculó.
 */
@Service
public class ServicioReglasAcumulacion {

    private static final Logger log = LoggerFactory.getLogger(ServicioReglasAcumulacion.class);

    private final ReglasAcumulacion reglas;

    public ServicioReglasAcumulacion(ReglasAcumulacion reglas) {
        this.reglas = reglas;
    }

    public ReglaAcumulacion vigente() {
        return reglas.vigente();
    }

    public List<ReglasAcumulacion.CambioRegla> historial() {
        return reglas.historial();
    }

    @Transactional
    public ReglaAcumulacion actualizar(SolicitudReglaAcumulacion solicitud, String usuario) {
        ReglaAcumulacion actual = reglas.vigente();
        if (actual.version() != solicitud.version()) {
            throw new ConflictoException("La regla cambió mientras la editaba: la versión vigente es la "
                    + actual.version() + ". Revise la regla actual y vuelva a guardar.");
        }
        if (actual.mismosValores(solicitud.bolivianosPorPunto(), solicitud.montoMinimo())) {
            throw new ReglaInvalidaException("La regla no cambió: tiene la misma tasa y el mismo monto mínimo que la vigente");
        }
        String motivo = solicitud.motivo() == null || solicitud.motivo().isBlank() ? null : solicitud.motivo().strip();
        try {
            ReglaAcumulacion nueva = reglas.guardar(actual.version() + 1, solicitud.bolivianosPorPunto(),
                    solicitud.montoMinimo(), motivo, usuario);
            log.info("Regla de acumulación v{} guardada por {}: Bs {} por punto, monto mínimo Bs {}", nueva.version(),
                    usuario, nueva.bolivianosPorPunto(), nueva.montoMinimo());
            return nueva;
        } catch (DuplicateKeyException ex) {
            throw new ConflictoException("Otra persona guardó la regla al mismo tiempo. Revise la regla actual y vuelva a guardar.");
        }
    }
}

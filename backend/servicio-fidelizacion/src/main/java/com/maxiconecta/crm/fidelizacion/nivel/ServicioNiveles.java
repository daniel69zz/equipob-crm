package com.maxiconecta.crm.fidelizacion.nivel;

import com.maxiconecta.crm.fidelizacion.comun.ConflictoException;
import com.maxiconecta.crm.fidelizacion.comun.ReglaInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Gestión de los niveles de fidelización por el Administrador de CRM (SCRUM-413). Guardar una
 * configuración crea una versión nueva y reclasifica en el acto a todos los clientes con saldo.
 */
@Service
public class ServicioNiveles {

    private static final Logger log = LoggerFactory.getLogger(ServicioNiveles.class);

    private final ConfiguracionesNiveles configuraciones;
    private final AsignacionNiveles asignacion;

    public ServicioNiveles(ConfiguracionesNiveles configuraciones, AsignacionNiveles asignacion) {
        this.configuraciones = configuraciones;
        this.asignacion = asignacion;
    }

    public ConfiguracionNiveles vigente() {
        return configuraciones.vigente();
    }

    public List<ConfiguracionNiveles> historial() {
        return configuraciones.historial();
    }

    @Transactional
    public Resultado actualizar(SolicitudNiveles solicitud, String usuario) {
        List<UmbralNivel> umbrales = ReglasNiveles.validar(solicitud.niveles());
        ConfiguracionNiveles actual = configuraciones.vigente();
        if (actual.version() != solicitud.version()) {
            throw new ConflictoException("Los niveles cambiaron mientras los editaba: la versión vigente es la "
                    + actual.version() + ". Revise la configuración actual y vuelva a guardar.");
        }
        if (actual.periodoMeses() == solicitud.periodoMeses() && mismos(actual.umbrales(), umbrales)) {
            throw new ReglaInvalidaException("Los niveles no cambiaron: tienen el mismo periodo y los mismos umbrales que los vigentes");
        }
        String motivo = solicitud.motivo() == null || solicitud.motivo().isBlank() ? null : solicitud.motivo().strip();
        try {
            configuraciones.guardar(actual.version() + 1, solicitud.periodoMeses(), umbrales, motivo, usuario);
        } catch (DuplicateKeyException ex) {
            throw new ConflictoException("Otra persona guardó los niveles al mismo tiempo. Revise la configuración actual y vuelva a guardar.");
        }
        int reclasificados = asignacion.recalcularTodos();
        ConfiguracionNiveles nueva = configuraciones.vigente();
        log.info("Niveles v{} guardados por {} (periodo {} meses); {} clientes cambiaron de nivel", nueva.version(),
                usuario, nueva.periodoMeses(), reclasificados);
        return new Resultado(nueva, reclasificados);
    }

    /** Reclasifica a todos los clientes con la configuración vigente. */
    @Transactional
    public int recalcularTodos() {
        return asignacion.recalcularTodos();
    }

    private static boolean mismos(List<UmbralNivel> actuales, List<UmbralNivel> nuevos) {
        if (actuales.size() != nuevos.size()) {
            return false;
        }
        for (UmbralNivel nuevo : nuevos) {
            boolean igual = actuales.stream().anyMatch(a -> a.nivel() == nuevo.nivel()
                    && Objects.equals(a.puntosMinimos(), nuevo.puntosMinimos())
                    && Objects.equals(a.puntosMaximos(), nuevo.puntosMaximos()));
            if (!igual) {
                return false;
            }
        }
        return true;
    }

    /** Configuración guardada y cuántos clientes cambiaron de nivel con ella. */
    public record Resultado(ConfiguracionNiveles configuracion, int clientesReclasificados) {
    }
}

package com.maxiconecta.crm.comportamiento.consumo;

import com.maxiconecta.crm.comportamiento.metrica.RepublicacionMetricas;
import com.maxiconecta.crm.comportamiento.parametro.CodigosParametros;
import com.maxiconecta.crm.comportamiento.parametro.ParametrosModificados;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * SCRUM-518: al cambiar un umbral del nivel de consumo se reclasifica a todos los clientes y se vuelven a publicar
 * sus métricas, que llevan el nivel, para que Segmentación agrupe con los umbrales nuevos sin esperar a la próxima
 * compra de cada cliente ni al proceso de la madrugada.
 */
@Component
public class ReclasificacionPorParametros {

    private final ClasificadorConsumo clasificador;
    private final RepublicacionMetricas metricas;

    public ReclasificacionPorParametros(ClasificadorConsumo clasificador, RepublicacionMetricas metricas) {
        this.clasificador = clasificador;
        this.metricas = metricas;
    }

    @EventListener
    public void alModificar(ParametrosModificados modificados) {
        if (modificados.incluye(CodigosParametros.NIVEL_CONSUMO_UMBRAL_MEDIO)
                || modificados.incluye(CodigosParametros.NIVEL_CONSUMO_UMBRAL_ALTO)) {
            clasificador.clasificarTodos();
            metricas.republicarTodos();
        }
    }
}

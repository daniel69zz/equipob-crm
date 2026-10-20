package com.maxiconecta.crm.comportamiento.riesgo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clientes de un nivel de riesgo de abandono para el seguimiento comercial (SCRUM-326,
 * docs/compra/riesgo-abandono.md): con {@code ALTO} es la lista de trabajo de retención. Se lee de lo
 * que ya dejó persistido {@link CalculadoraRiesgoAbandono}.
 */
@Service
public class ConsultaClientesEnRiesgo {

    public static final int TAMANIO_MAXIMO_PAGINA = 100;

    private final RiesgoAbandonoClienteRepository riesgos;

    public ConsultaClientesEnRiesgo(RiesgoAbandonoClienteRepository riesgos) {
        this.riesgos = riesgos;
    }

    /** Del puntaje más alto al más bajo; a igual puntaje, por número de cliente. */
    @Transactional(readOnly = true)
    public Page<RiesgoAbandonoCliente> buscar(NivelRiesgo nivel, int pagina, int tamanio) {
        PageRequest solicitud = PageRequest.of(Math.max(pagina, 0),
                Math.min(Math.max(tamanio, 1), TAMANIO_MAXIMO_PAGINA));
        return riesgos.findByNivelOrderByPuntajeDescClienteIdAsc(nivel, solicitud);
    }
}

package com.maxiconecta.crm.comportamiento.consumo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listado de clientes de un nivel de consumo para usuarios de negocio (SCRUM-269), leído de lo que
 * ya dejó persistido {@link ClasificadorConsumo} (SCRUM-267).
 */
@Service
public class ConsultaClientesPorNivel {

    public static final int TAMANIO_MAXIMO_PAGINA = 100;

    private final ClienteNivelConsumoRepository repository;

    public ConsultaClientesPorNivel(ClienteNivelConsumoRepository repository) {
        this.repository = repository;
    }

    /** Clientes del nivel, del que más aporta al que menos. */
    @Transactional(readOnly = true)
    public Page<ClienteNivelConsumo> buscar(NivelConsumo nivel, int pagina, int tamanio) {
        PageRequest solicitud = PageRequest.of(Math.max(pagina, 0),
                Math.min(Math.max(tamanio, 1), TAMANIO_MAXIMO_PAGINA));
        return repository.findByNivelOrderByValorAcumuladoDescIdClienteOrigenAsc(nivel, solicitud);
    }
}

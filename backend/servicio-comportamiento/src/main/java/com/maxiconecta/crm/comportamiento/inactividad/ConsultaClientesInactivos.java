package com.maxiconecta.crm.comportamiento.inactividad;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listado de clientes inactivos para usuarios de negocio (SCRUM-298), leído de lo que ya dejó
 * persistido {@link DetectorClientesInactivos} (SCRUM-296).
 */
@Service
public class ConsultaClientesInactivos {

    public static final int TAMANIO_MAXIMO_PAGINA = 100;

    private final ClienteInactivoRepository repository;

    public ConsultaClientesInactivos(ClienteInactivoRepository repository) {
        this.repository = repository;
    }

    /** Clientes inactivos, del que lleva más tiempo sin comprar al que lleva menos. */
    @Transactional(readOnly = true)
    public Page<ClienteInactivo> buscar(int pagina, int tamanio) {
        PageRequest solicitud = PageRequest.of(Math.max(pagina, 0),
                Math.min(Math.max(tamanio, 1), TAMANIO_MAXIMO_PAGINA));
        return repository.findAllByOrderByUltimaCompraAsc(solicitud);
    }
}

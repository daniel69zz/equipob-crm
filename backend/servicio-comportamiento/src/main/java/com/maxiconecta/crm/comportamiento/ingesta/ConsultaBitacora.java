package com.maxiconecta.crm.comportamiento.ingesta;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

/**
 * Consulta de la bitácora de ingesta por periodo.
 */
@Service
public class ConsultaBitacora {

    public static final int TAMANIO_MAXIMO_PAGINA = 100;

    private final EventoRecibidoRepository repository;

    public ConsultaBitacora(EventoRecibidoRepository repository) {
        this.repository = repository;
    }

    /**
     * Eventos del periodo, del más reciente al más antiguo.
     */
    @Transactional(readOnly = true)
    public Page<EventoRecibido> buscar(FiltroBitacora filtro, int pagina, int tamanio) {
        PageRequest solicitud = PageRequest.of(Math.max(pagina, 0),
                Math.min(Math.max(tamanio, 1), TAMANIO_MAXIMO_PAGINA),
                Sort.by(Sort.Order.desc("recibidoEn"), Sort.Order.desc("id")));
        return repository.findAll(filtro.comoEspecificacion(ZoneId.systemDefault()), solicitud);
    }

    /**
     * Cantidad de eventos del periodo en cada estado (sin tener en cuenta el filtro de estado).
     */
    @Transactional(readOnly = true)
    public Map<EstadoEvento, Long> resumen(FiltroBitacora filtro) {
        Map<EstadoEvento, Long> resumen = new EnumMap<>(EstadoEvento.class);
        for (EstadoEvento estado : EstadoEvento.values()) {
            resumen.put(estado, repository.count(filtro.conEstado(estado).comoEspecificacion(ZoneId.systemDefault())));
        }
        return resumen;
    }
}

package com.maxiconecta.crm.gateway.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;

/**
 * Registra en auditoria.evento los cambios de seguridad y los accesos a datos de clientes.
 * Los cambios de seguridad se registran dentro de la misma transacción que el cambio: si el
 * registro falla, el cambio tampoco se guarda.
 */
@Service
public class AuditoriaService {

    public static final String ROL_CREADO = "ROL_CREADO";
    public static final String ROL_ACTUALIZADO = "ROL_ACTUALIZADO";
    public static final String ROL_DESACTIVADO = "ROL_DESACTIVADO";
    public static final String USUARIO_CREADO = "USUARIO_CREADO";
    public static final String ROL_ASIGNADO = "ROL_ASIGNADO";
    public static final String USUARIO_DESACTIVADO = "USUARIO_DESACTIVADO";
    public static final String CLIENTE_CONSULTADO = "CLIENTE_CONSULTADO";
    public static final String CLIENTE_MODIFICADO = "CLIENTE_MODIFICADO";
    public static final String ACCESO_DENEGADO = "ACCESO_DENEGADO";

    public static final String ENTIDAD_CLIENTE = "CLIENTE";

    /** Operaciones que se pueden encontrar en la auditoría, para filtrar la consulta. */
    public static final List<String> OPERACIONES = List.of(
            CLIENTE_CONSULTADO, CLIENTE_MODIFICADO, ACCESO_DENEGADO,
            ROL_CREADO, ROL_ACTUALIZADO, ROL_DESACTIVADO, USUARIO_CREADO, ROL_ASIGNADO, USUARIO_DESACTIVADO);

    public static final int TAMANIO_MAXIMO_PAGINA = 100;

    private final EventoAuditoriaRepository repository;

    public AuditoriaService(EventoAuditoriaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrar(String usuario, String operacion, String entidad, Object entidadId, String detalle) {
        repository.save(new EventoAuditoria(usuario, operacion, entidad,
                entidadId != null ? entidadId.toString() : null, detalle));
    }

    /**
     * Eventos que cumplen el filtro, del más reciente al más antiguo.
     */
    @Transactional(readOnly = true)
    public Page<EventoAuditoria> buscar(FiltroAuditoria filtro, int pagina, int tamanio) {
        PageRequest solicitud = PageRequest.of(Math.max(pagina, 0),
                Math.min(Math.max(tamanio, 1), TAMANIO_MAXIMO_PAGINA),
                Sort.by(Sort.Order.desc("ocurridoEn"), Sort.Order.desc("id")));
        return repository.findAll(filtro.comoEspecificacion(ZoneId.systemDefault()), solicitud);
    }
}

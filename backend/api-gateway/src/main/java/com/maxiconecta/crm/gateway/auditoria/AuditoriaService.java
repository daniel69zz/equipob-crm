package com.maxiconecta.crm.gateway.auditoria;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final EventoAuditoriaRepository repository;

    public AuditoriaService(EventoAuditoriaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrar(String usuario, String operacion, String entidad, Object entidadId, String detalle) {
        repository.save(new EventoAuditoria(usuario, operacion, entidad,
                entidadId != null ? entidadId.toString() : null, detalle));
    }

    @Transactional(readOnly = true)
    public List<EventoAuditoria> ultimos(String operacion) {
        return operacion == null || operacion.isBlank()
                ? repository.findTop100ByOrderByOcurridoEnDesc()
                : repository.findTop100ByOperacionOrderByOcurridoEnDesc(operacion);
    }
}

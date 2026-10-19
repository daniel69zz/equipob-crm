package com.maxiconecta.crm.segmentacion.criterio;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class CriterioService {
    private final CriterioRepository repository;
    private final AuditoriaCriterios auditoria;
    public CriterioService(CriterioRepository repository, AuditoriaCriterios auditoria) {
        this.repository = repository; this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<Criterio> listar(Boolean activo, Criterio.Indicador indicador) {
        return repository.listar().stream().filter(c -> activo == null || c.activo() == activo)
                .filter(c -> indicador == null || c.indicador() == indicador).toList();
    }

    @Transactional(readOnly = true)
    public Criterio obtener(long id) {
        return repository.obtener(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el criterio " + id));
    }

    @Transactional
    public Criterio crear(SolicitudCriterio solicitud, String usuario) {
        var s = ReglasCriterio.normalizar(solicitud);
        repository.bloquearConfiguracion();
        validarConflictos(s, null, true);
        var nuevo = repository.crear(s, usuario);
        auditoria.registrar("CRITERIO_CREADO", usuario, null, nuevo);
        return nuevo;
    }

    @Transactional
    public Criterio editar(long id, SolicitudCriterio solicitud, String usuario) {
        var s = ReglasCriterio.normalizar(solicitud);
        repository.bloquearConfiguracion();
        var anterior = obtener(id);
        validarVersion(anterior, s.version());
        validarConflictos(s, id, anterior.activo());
        var nuevo = repository.editar(id, s, usuario);
        auditoria.registrar("CRITERIO_ACTUALIZADO", usuario, anterior, nuevo);
        return nuevo;
    }

    @Transactional
    public Criterio cambiarEstado(long id, boolean activo, long version, String usuario) {
        repository.bloquearConfiguracion();
        var anterior = obtener(id);
        validarVersion(anterior, version);
        if (anterior.activo() == activo) return anterior;
        if (activo) validarConflictos(new SolicitudCriterio(anterior.nombre(), anterior.descripcion(), anterior.indicador(),
                anterior.condicion(), anterior.minimo(), anterior.maximo(), anterior.categoria(), anterior.version()), id, true);
        var nuevo = repository.cambiarEstado(id, activo, usuario);
        auditoria.registrar(activo ? "CRITERIO_ACTIVADO" : "CRITERIO_DESACTIVADO", usuario, anterior, nuevo);
        return nuevo;
    }

    @Transactional(readOnly = true)
    public List<AuditoriaCriterios.Cambio> historial(long id) { obtener(id); return auditoria.historial(id); }

    /** SCRUM-286: mismas reglas que guardar, sin bloquear ni escribir configuración. */
    @Transactional(readOnly = true)
    public Criterio prepararSimulacion(Long id, SolicitudCriterio solicitud) {
        var s = ReglasCriterio.normalizar(solicitud);
        var anterior = id == null ? null : obtener(id);
        if (anterior != null) validarVersion(anterior, s.version());
        boolean activo = anterior == null || anterior.activo();
        validarConflictos(s, id, activo);
        // Un alta futura tiene menor prioridad por antigüedad que todos los criterios existentes.
        return new Criterio(id == null ? Long.MAX_VALUE : id, s.nombre(), s.descripcion(), s.indicador(),
                s.condicion(), s.minimo(), s.maximo(), s.categoria(), activo,
                anterior == null ? 0 : anterior.version(), null, null, null, s.clasificacion());
    }

    private void validarConflictos(SolicitudCriterio s, Long id, boolean activo) {
        for (var otro : repository.listar()) {
            if (otro.id().equals(id)) continue;
            if (ReglasCriterio.clave(otro.nombre()).equals(ReglasCriterio.clave(s.nombre()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un criterio con ese nombre: " + otro.nombre());
            }
            if (activo && ReglasCriterio.seSuperponen(s, otro)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El criterio se superpone con el criterio activo «" + otro.nombre() + "» (#" + otro.id() + "). Revise los límites o la categoría.");
            }
        }
    }

    private void validarVersion(Criterio actual, Long version) {
        if (version == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la versión del criterio");
        if (actual.version() != version) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Otro usuario modificó el criterio. Actualice la lista y vuelva a abrir el criterio antes de guardar.");
    }
}

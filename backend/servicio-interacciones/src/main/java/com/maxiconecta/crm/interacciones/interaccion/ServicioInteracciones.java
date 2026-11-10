package com.maxiconecta.crm.interacciones.interaccion;

import com.maxiconecta.crm.interacciones.agente.AgentesAtencion;
import com.maxiconecta.crm.interacciones.comun.ConflictoException;
import com.maxiconecta.crm.interacciones.comun.IdentidadGateway;
import com.maxiconecta.crm.interacciones.comun.IdentidadGateway.Identidad;
import com.maxiconecta.crm.interacciones.comun.ReglaInvalidaException;
import com.maxiconecta.crm.interacciones.compras.Compra;
import com.maxiconecta.crm.interacciones.compras.ComprasCliente;
import com.maxiconecta.crm.interacciones.perfil.ClientesPerfil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Registro, edición y consulta del historial unificado de interacciones (SCRUM-432). Reglas en
 * docs/interacciones/registro-interacciones.md.
 */
@Service
public class ServicioInteracciones {

    /** Zona de las fechas que elige el usuario en el filtro del historial. */
    static final ZoneId ZONA = ZoneId.of("America/La_Paz");
    static final int MAXIMO_DESCRIPCION = 4000;
    static final int TAMANIO_POR_DEFECTO = 20;
    static final int TAMANIO_MAXIMO = 100;
    /** Largo mínimo del comentario al reabrir una interacción cerrada (SCRUM-453). */
    static final int MINIMO_COMENTARIO_REAPERTURA = 10;

    private final RegistroInteracciones registro;
    private final ClientesPerfil perfiles;
    private final AuditoriaInteracciones auditoria;
    private final TransactionTemplate transacciones;
    private final ComprasCliente compras;
    private final AgentesAtencion agentes;

    public ServicioInteracciones(RegistroInteracciones registro, ClientesPerfil perfiles,
                                 AuditoriaInteracciones auditoria, TransactionTemplate transacciones,
                                 ComprasCliente compras, AgentesAtencion agentes) {
        this.registro = registro;
        this.perfiles = perfiles;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.compras = compras;
        this.agentes = agentes;
    }

    public Interaccion registrar(long clienteId, SolicitudInteraccion solicitud, Identidad identidad) {
        RegistroInteracciones.Campos campos = normalizar(solicitud.tipo(), solicitud.canal(), solicitud.asunto(),
                solicitud.descripcion(), solicitud.referenciaCompra());
        // Fuera de la transacción: la consulta a Perfil no retiene una conexión de la base.
        if (clienteId <= 0 || !perfiles.existe(clienteId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el cliente " + clienteId + " en el CRM");
        }
        // La interacción y su auditoría (SCRUM-436) se guardan juntas o no se guarda ninguna.
        return transacciones.execute(transaccion -> {
            Interaccion creada = registro.registrar(clienteId, campos, identidad.usuario(), identidad.rol());
            auditoria.creada(creada);
            return creada;
        });
    }

    public Interaccion modificar(long clienteId, long id, SolicitudCambio solicitud, Identidad identidad) {
        RegistroInteracciones.Campos campos = normalizar(solicitud.tipo(), solicitud.canal(), solicitud.asunto(),
                solicitud.descripcion(), solicitud.referenciaCompra());
        return transacciones.execute(transaccion -> {
            Interaccion actual = registro.bloquear(clienteId, id).orElseThrow(() -> noEncontrada(clienteId, id));
            if (!identidad.esAdministrador() && !identidad.usuario().equals(actual.registradoPor())) {
                throw IdentidadGateway.prohibido("Solo puede modificar las interacciones que usted registró");
            }
            verificarVersion(actual, solicitud.version());
            exigirNoCerrada(actual);
            Map<String, Cambio> cambios = cambios(actual, campos);
            if (cambios.isEmpty()) {
                throw new ReglaInvalidaException("La interacción no tiene cambios para guardar");
            }
            Interaccion modificada = registro.modificar(id, campos, identidad.usuario());
            auditoria.modificada(modificada, identidad.rol(), cambios);
            return modificada;
        });
    }

    /**
     * Cambia el estado de seguimiento o el tipo (SCRUM-452). Lo hace cualquier Agente de Atención o el Administrador,
     * aunque no la haya registrado: el caso pasa de un agente a otro durante la atención.
     */
    public Interaccion seguimiento(long clienteId, long id, SolicitudSeguimiento solicitud, Identidad identidad) {
        String estadoNuevo = vacioANulo(solicitud.estado());
        String tipoNuevo = vacioANulo(solicitud.tipo());
        if (estadoNuevo == null && tipoNuevo == null) {
            throw new ReglaInvalidaException("Indique el estado o el tipo nuevo de la interacción",
                    Map.of("estado", "indique el estado o el tipo nuevo"));
        }
        String comentario = vacioANulo(solicitud.comentario());
        return transacciones.execute(transaccion -> {
            Interaccion actual = registro.bloquear(clienteId, id).orElseThrow(() -> noEncontrada(clienteId, id));
            boolean reabre = actual.cerrada() && estadoNuevo != null && !estadoNuevo.equals(actual.estado());
            // Criterio de aceptación 2: reabrir una interacción cerrada exige ser Administrador de CRM.
            if (reabre && !identidad.esAdministrador()) {
                throw IdentidadGateway.prohibido("Solo el Administrador de CRM puede reabrir una interacción cerrada");
            }
            verificarVersion(actual, solicitud.version());
            if (reabre && (comentario == null || comentario.length() < MINIMO_COMENTARIO_REAPERTURA)) {
                throw new ReglaInvalidaException("Indique por qué se reabre la interacción",
                        Map.of("comentario", "es obligatorio al reabrir: de " + MINIMO_COMENTARIO_REAPERTURA
                                + " a 500 caracteres"));
            }
            if (!reabre && tipoNuevo != null && !tipoNuevo.equals(actual.tipo())) {
                exigirNoCerrada(actual);
            }
            Map<String, Cambio> cambios = new LinkedHashMap<>();
            if (estadoNuevo != null && !estadoNuevo.equals(actual.estado())) {
                cambios.put("estado", new Cambio(actual.estado(), estadoNuevo));
            }
            if (tipoNuevo != null && !tipoNuevo.equals(actual.tipo())) {
                cambios.put("tipo", new Cambio(actual.tipo(), tipoNuevo));
            }
            if (cambios.isEmpty()) {
                throw new ReglaInvalidaException("La interacción ya tiene ese estado y ese tipo");
            }
            Interaccion actualizada = registro.actualizarSeguimiento(id, tipoNuevo != null ? tipoNuevo : actual.tipo(),
                    estadoNuevo != null ? estadoNuevo : actual.estado(), cambios.containsKey("estado"),
                    identidad.usuario());
            auditoria.seguimiento(actualizada, identidad.usuario(), identidad.rol(), cambios, comentario);
            return actualizada;
        });
    }

    /**
     * Vincula una compra del historial del cliente a la interacción, o reemplaza la que tenía (SCRUM-54,
     * docs/interacciones/vinculacion-compras.md). La compra se busca entre las del cliente: una de otro cliente se
     * rechaza (criterio de aceptación 2).
     */
    public Interaccion vincularCompra(long clienteId, long id, SolicitudVinculacion solicitud, Identidad identidad) {
        String referencia = solicitud.referencia().strip();
        // SCRUM-447: lo que no depende de la compra se rechaza antes de consultar Comportamiento, y se vuelve a
        // comprobar con la interacción bloqueada.
        validarVinculacion(detalle(clienteId, id), solicitud.version(), referencia);
        // Fuera de la transacción: Perfil y Comportamiento no retienen una conexión de la base.
        List<String> identificadores = perfiles.identificadores(clienteId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el cliente " + clienteId + " en el CRM"));
        Compra compra = (identificadores.isEmpty() ? Optional.<Compra>empty()
                : compras.buscar(clienteId, identificadores, referencia)).orElseThrow(() ->
                new ReglaInvalidaException("La compra " + referencia + " no es una compra del cliente " + clienteId,
                        Map.of("referencia", "no es una compra del cliente")));
        return transacciones.execute(transaccion -> {
            Interaccion actual = registro.bloquear(clienteId, id).orElseThrow(() -> noEncontrada(clienteId, id));
            validarVinculacion(actual, solicitud.version(), compra.referencia());
            Interaccion vinculada = registro.vincularCompra(id, compra.referencia(), compra.fecha(),
                    compra.montoTotal(), compra.estado(), identidad.usuario());
            // SCRUM-448: en la misma transacción; si la auditoría falla, la compra no queda vinculada.
            auditoria.compra(vinculada, identidad.usuario(), identidad.rol(),
                    actual.compra() == null ? null : actual.compra().referencia(), compra.referencia());
            return vinculada;
        });
    }

    /** Quita la compra vinculada a la interacción (SCRUM-54), con las mismas reglas de estado y versión. */
    public Interaccion desvincularCompra(long clienteId, long id, long version, Identidad identidad) {
        return transacciones.execute(transaccion -> {
            Interaccion actual = registro.bloquear(clienteId, id).orElseThrow(() -> noEncontrada(clienteId, id));
            verificarVersion(actual, version);
            exigirNoCerrada(actual);
            if (actual.compra() == null) {
                throw new ReglaInvalidaException("La interacción no tiene una compra vinculada");
            }
            Interaccion desvinculada = registro.desvincularCompra(id);
            auditoria.compra(desvinculada, identidad.usuario(), identidad.rol(), actual.compra().referencia(), null);
            return desvinculada;
        });
    }

    /**
     * Consistencia de la vinculación (SCRUM-447): la versión que el usuario tenía en pantalla, una interacción no
     * cerrada y una compra distinta de la ya vinculada.
     */
    private static void validarVinculacion(Interaccion actual, long version, String referencia) {
        verificarVersion(actual, version);
        exigirNoCerrada(actual);
        if (actual.compra() != null && actual.compra().referencia().equals(referencia)) {
            throw new ReglaInvalidaException("La interacción ya está vinculada a esa compra",
                    Map.of("referencia", "ya es la compra vinculada"));
        }
    }

    public Interaccion detalle(long clienteId, long id) {
        return registro.buscar(clienteId, id).orElseThrow(() -> noEncontrada(clienteId, id));
    }

    /** La auditoría de una interacción, de la creación al último cambio. */
    public List<AuditoriaInteracciones.RegistroAuditoria> auditoria(long clienteId, long id) {
        detalle(clienteId, id);
        return auditoria.de(id);
    }

    public PaginaInteracciones historial(long clienteId, String tipo, String estado, String texto, LocalDate desde,
                                         LocalDate hasta, Integer pagina, Integer tamanio, String responsable) {
        Map<String, String> errores = new TreeMap<>();
        String responsableFiltro = vacioANulo(responsable);
        if (responsableFiltro != null && responsableFiltro.length() > 100) {
            errores.put("responsable", "admite hasta 100 caracteres");
        }
        String tipoFiltro = vacioANulo(tipo);
        if (tipoFiltro != null) {
            tipoFiltro = tipoFiltro.toUpperCase(Locale.ROOT);
            if (!tipoFiltro.matches(TipoInteraccion.PATRON)) {
                errores.put("tipo", TipoInteraccion.MENSAJE);
            }
        }
        String estadoFiltro = vacioANulo(estado);
        if (estadoFiltro != null) {
            estadoFiltro = estadoFiltro.toUpperCase(Locale.ROOT);
            if (!estadoFiltro.matches(EstadoInteraccion.PATRON)) {
                errores.put("estado", EstadoInteraccion.MENSAJE);
            }
        }
        String textoFiltro = vacioANulo(texto);
        if (textoFiltro != null && textoFiltro.length() > 100) {
            errores.put("texto", "admite hasta 100 caracteres");
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            errores.put("desde", "no puede ser posterior a la fecha hasta");
        }
        int numero = pagina == null ? 0 : pagina;
        int cantidad = tamanio == null ? TAMANIO_POR_DEFECTO : tamanio;
        if (numero < 0) {
            errores.put("pagina", "debe ser 0 o mayor");
        }
        if (cantidad < 1 || cantidad > TAMANIO_MAXIMO) {
            errores.put("tamanio", "debe estar entre 1 y " + TAMANIO_MAXIMO);
        }
        if (!errores.isEmpty()) {
            throw new ReglaInvalidaException("Filtros del historial no válidos: " + String.join(", ",
                    errores.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).toList()), errores);
        }
        // Las fechas son días completos en la hora de Bolivia: hasta incluye todo ese día.
        FiltroHistorial filtro = new FiltroHistorial(tipoFiltro, estadoFiltro, textoFiltro,
                desde == null ? null : desde.atStartOfDay(ZONA).toOffsetDateTime(),
                hasta == null ? null : hasta.plusDays(1).atStartOfDay(ZONA).toOffsetDateTime(), numero, cantidad,
                responsableFiltro);
        return registro.historial(clienteId, filtro);
    }

    /**
     * Asigna la interacción a un agente responsable, o la reasigna (SCRUM-457,
     * docs/interacciones/asignacion-interacciones.md). El responsable debe ser un Agente de Atención activo.
     */
    public Interaccion asignar(long clienteId, long id, SolicitudAsignacion solicitud, Identidad identidad) {
        String responsable = solicitud.responsable().strip();
        if (agentes.activo(responsable).isEmpty()) {
            throw new ReglaInvalidaException("El usuario " + responsable + " no es un Agente de Atención activo",
                    Map.of("responsable", "no es un Agente de Atención activo"));
        }
        return transacciones.execute(transaccion -> {
            Interaccion actual = registro.bloquear(clienteId, id).orElseThrow(() -> noEncontrada(clienteId, id));
            verificarVersion(actual, solicitud.version());
            exigirNoCerrada(actual);
            if (responsable.equals(actual.responsable())) {
                throw new ReglaInvalidaException("La interacción ya tiene ese responsable",
                        Map.of("responsable", "ya es el responsable de la interacción"));
            }
            Interaccion asignada = registro.asignar(id, responsable, identidad.usuario());
            // SCRUM-458: el cambio de responsable queda en el historial en la misma transacción.
            auditoria.asignada(asignada, identidad.usuario(), identidad.rol(), actual.responsable(), responsable,
                    vacioANulo(solicitud.comentario()));
            return asignada;
        });
    }

    /** Una interacción cerrada queda como constancia del caso: para modificarla hay que reabrirla (SCRUM-453). */
    private static void exigirNoCerrada(Interaccion actual) {
        if (actual.cerrada()) {
            throw new ConflictoException("La interacción está cerrada: reábrala para modificarla");
        }
    }

    /** Una edición sobre una versión anterior se rechaza: otra persona cambió la interacción mientras tanto. */
    private static void verificarVersion(Interaccion actual, long version) {
        if (actual.version() != version) {
            throw new ConflictoException("Otra persona modificó la interacción mientras la editaba (versión "
                    + actual.version() + "): vuelva a cargarla y repita el cambio",
                    Map.of("version", "la interacción ya está en la versión " + actual.version()));
        }
    }

    /** Campos que cambian, con su valor anterior y el nuevo (también lo usa la auditoría de SCRUM-436). */
    static Map<String, Cambio> cambios(Interaccion actual, RegistroInteracciones.Campos nuevos) {
        Map<String, String> antes = actual.editables();
        Map<String, String> despues = new LinkedHashMap<>();
        despues.put("tipo", nuevos.tipo());
        despues.put("canal", nuevos.canal());
        despues.put("asunto", nuevos.asunto());
        despues.put("descripcion", nuevos.descripcion());
        despues.put("referenciaCompra", nuevos.referenciaCompra());
        Map<String, Cambio> cambios = new LinkedHashMap<>();
        despues.forEach((campo, valor) -> {
            if (!Objects.equals(antes.get(campo), valor)) {
                cambios.put(campo, new Cambio(antes.get(campo), valor));
            }
        });
        return cambios;
    }

    private static RegistroInteracciones.Campos normalizar(String tipo, String canal, String asunto,
                                                           String descripcion, String referenciaCompra) {
        String texto = descripcion.strip();
        if (texto.length() > MAXIMO_DESCRIPCION) {
            throw new ReglaInvalidaException("La descripción admite hasta 4.000 caracteres y tiene " + texto.length(),
                    Map.of("descripcion", "admite hasta 4.000 caracteres"));
        }
        return new RegistroInteracciones.Campos(tipo.strip(), vacioANulo(canal), vacioANulo(asunto), texto,
                vacioANulo(referenciaCompra));
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static ResponseStatusException noEncontrada(long clienteId, long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "El cliente " + clienteId + " no tiene la interacción " + id);
    }

    /** Valor anterior y nuevo de un campo. */
    public record Cambio(String anterior, String nuevo) {
    }
}

package com.maxiconecta.crm.perfil.sincronizacion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.perfil.cliente.CambioCampo;
import com.maxiconecta.crm.perfil.cliente.CambioPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigenRepository;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.EstadoVinculacion;
import com.maxiconecta.crm.perfil.cliente.TipoCambio;
import com.maxiconecta.crm.perfil.cliente.VinculacionPendiente;
import com.maxiconecta.crm.perfil.cliente.VinculacionPendienteRepository;
import com.maxiconecta.crm.perfil.validacion.NormalizadorPerfil;
import com.maxiconecta.crm.perfil.validacion.PerfilValidado;
import com.maxiconecta.crm.perfil.validacion.ValidadorPerfil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Aplica un evento de datos del cliente al perfil. Todo ocurre en una transacción: si algo
 * falla, el perfil no queda a medias.
 * <p>
 * Conciliación: el evento se resuelve por su identificador de origen (RF-62,
 * docs/perfil/identificadores-origen.md). Si el identificador ya está vinculado, se actualiza ese
 * perfil y nunca se crea otro. Si es desconocido pero su documento coincide con un perfil existente,
 * queda pendiente de vinculación; si no, se crea un perfil nuevo y se vincula. Los eventos de un
 * mismo identificador se ordenan por la fecha del cambio en el sistema de origen: uno igual o más
 * antiguo que el último aplicado se descarta.
 * <p>
 * Cada creación o modificación queda en {@link CambioPerfil} con fecha, origen, responsable y campos.
 * <p>
 * Si un dato obligatorio llega vacío o mal formado, se guarda lo válido, el dato queda vacío y el
 * perfil se marca INCOMPLETO con el motivo; el evento queda INCOMPLETO en la bitácora.
 */
@Service
public class ProcesadorClientes {

    private final EventoClienteRepository eventos;
    private final ClienteRepository clientes;
    private final ClienteOrigenRepository origenes;
    private final CambioPerfilRepository cambiosPerfil;
    private final VinculacionPendienteRepository vinculaciones;
    private final LectorEventosCliente lector;
    private final NormalizadorPerfil normalizador;
    private final ValidadorPerfil validador;
    private final ObjectMapper objectMapper;

    public ProcesadorClientes(EventoClienteRepository eventos, ClienteRepository clientes,
                              ClienteOrigenRepository origenes, CambioPerfilRepository cambiosPerfil,
                              VinculacionPendienteRepository vinculaciones, LectorEventosCliente lector,
                              NormalizadorPerfil normalizador, ValidadorPerfil validador, ObjectMapper objectMapper) {
        this.eventos = eventos;
        this.clientes = clientes;
        this.origenes = origenes;
        this.cambiosPerfil = cambiosPerfil;
        this.vinculaciones = vinculaciones;
        this.lector = lector;
        this.normalizador = normalizador;
        this.validador = validador;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResultadoSincronizacion aplicar(Long idEvento) {
        EventoCliente registro = eventos.findById(idEvento)
                .orElseThrow(() -> new IllegalStateException("No existe el evento " + idEvento + " en la bitácora"));
        EventoClienteRecibido evento = lector.leer(registro.getContenido());
        // Normalizar antes de validar y comparar: un cambio que solo es de formato no es un cambio.
        EventoClienteRecibido.DatosCliente datos = normalizador.normalizar(evento.cliente());
        ClienteOrigen.Clave clave = new ClienteOrigen.Clave(evento.origen(), datos.idCliente());
        OffsetDateTime fechaCambio = evento.fechaCambio();
        PerfilValidado perfil = validador.validar(datos);

        ClienteOrigen vinculo = origenes.findById(clave).orElse(null);
        Cliente cliente;
        TipoCambio tipo;
        if (vinculo == null) {
            ResultadoSincronizacion pendiente = pendienteDeVinculacion(clave, perfil);
            if (pendiente != null) {
                return pendiente;
            }
            cliente = new Cliente();
            tipo = TipoCambio.CREACION;
        } else {
            descartarSiNoEsPosterior(vinculo, fechaCambio);
            cliente = perfilVigente(vinculo);
            tipo = TipoCambio.ACTUALIZACION;
        }

        List<CambioCampo> cambios = new ArrayList<>();
        cambios.addAll(cliente.identificar(perfil.nombres(), perfil.apellidos(), perfil.tipoDocumento(),
                perfil.numeroDocumento()));
        cambios.addAll(cliente.actualizarContacto(perfil.email(), perfil.telefono()));
        cambios.addAll(cliente.sincronizarDirecciones(evento.origen(), perfil.direcciones()));
        cambios.addAll(cliente.marcarEstado(perfil.motivos()));

        if (vinculo == null) {
            cliente.registrarActualizacion(evento.origen(), evento.responsableDelCambio());
            clientes.save(cliente);
            // Si otra copia del alta se adelantó, la llave primaria de cliente_origen lo detiene aquí.
            origenes.saveAndFlush(new ClienteOrigen(evento.origen(), datos.idCliente(), cliente.getId(), fechaCambio));
        } else {
            vinculo.registrarActualizacion(fechaCambio);
            if (!cambios.isEmpty()) {
                cliente.registrarActualizacion(evento.origen(), evento.responsableDelCambio());
            }
        }

        if (!cambios.isEmpty()) {
            cambiosPerfil.save(new CambioPerfil(cliente.getId(), tipo, evento.origen(),
                    evento.responsableDelCambio(), comoJson(cambios), idEvento));
        }
        if (!perfil.completo()) {
            return new ResultadoSincronizacion(cliente.getId(), EstadoEventoCliente.INCOMPLETO,
                    "Perfil incompleto: " + perfil.motivosComoTexto());
        }
        return new ResultadoSincronizacion(cliente.getId(), EstadoEventoCliente.PROCESADO,
                cambios.isEmpty() ? "Sin cambios en el perfil" : null);
    }

    /**
     * Para un identificador desconocido: si ya tiene una vinculación pendiente, o si su documento
     * coincide con un perfil existente, el evento espera la decisión de un administrador. Devuelve
     * null si corresponde crear un perfil nuevo.
     */
    private ResultadoSincronizacion pendienteDeVinculacion(ClienteOrigen.Clave clave, PerfilValidado perfil) {
        VinculacionPendiente vinculacion = vinculaciones.findById(clave).orElse(null);
        if (vinculacion != null) {
            if (vinculacion.getEstado() == EstadoVinculacion.NUEVO_PERFIL) {
                return null;
            }
            return new ResultadoSincronizacion(null, EstadoEventoCliente.PENDIENTE, "El identificador " + clave
                    + " está pendiente de vinculación" + sugerido(vinculacion.getIdClienteSugerido()));
        }
        if (perfil.tipoDocumento() == null || perfil.numeroDocumento() == null) {
            return null;
        }
        List<Cliente> coincidentes = clientes.findByTipoDocumentoAndNumeroDocumentoAndIdClienteConsolidadoIsNullOrderById(
                perfil.tipoDocumento(), perfil.numeroDocumento());
        if (coincidentes.isEmpty()) {
            return null;
        }
        Long sugerido = coincidentes.get(0).getId();
        String motivo = "Identificador desconocido con el documento " + perfil.tipoDocumento() + " "
                + perfil.numeroDocumento() + ", que ya tiene el cliente " + sugerido
                + (coincidentes.size() > 1 ? " (y " + (coincidentes.size() - 1) + " perfil(es) más)" : "");
        // Si otro mensaje del mismo identificador la registró a la vez, la llave primaria lo detiene aquí.
        vinculaciones.saveAndFlush(new VinculacionPendiente(clave.origen(), clave.idClienteOrigen(), sugerido, motivo));
        return new ResultadoSincronizacion(null, EstadoEventoCliente.PENDIENTE,
                "Pendiente de vinculación: " + motivo);
    }

    private static String sugerido(Long idCliente) {
        return idCliente != null ? " (perfil sugerido: " + idCliente + ")" : "";
    }

    /** El perfil del vínculo; si fue absorbido en una unificación, el perfil que lo absorbió. */
    private Cliente perfilVigente(ClienteOrigen vinculo) {
        Cliente cliente = buscarCliente(vinculo.getIdCliente(), vinculo);
        for (int saltos = 0; cliente.fueConsolidado(); saltos++) {
            if (saltos > 10) {
                throw new IllegalStateException("Cadena de perfiles consolidados demasiado larga desde " + vinculo.getId());
            }
            cliente = buscarCliente(cliente.getIdClienteConsolidado(), vinculo);
        }
        return cliente;
    }

    private Cliente buscarCliente(Long idCliente, ClienteOrigen vinculo) {
        return clientes.findById(idCliente).orElseThrow(() -> new IllegalStateException(
                "El identificador " + vinculo.getId() + " apunta a un perfil inexistente: " + idCliente));
    }

    private static void descartarSiNoEsPosterior(ClienteOrigen vinculo, OffsetDateTime fechaCambio) {
        OffsetDateTime ultima = vinculo.getUltimaActualizacionOrigen();
        if (ultima == null || fechaCambio.isAfter(ultima)) {
            return;
        }
        // La base devuelve la fecha en UTC: se muestra en la misma zona horaria que la del evento.
        OffsetDateTime ultimaEnZonaDelEvento = ultima.withOffsetSameInstant(fechaCambio.getOffset());
        String motivo = fechaCambio.isEqual(ultima)
                ? "El cambio del " + fechaCambio + " de " + vinculo.getId() + " ya fue aplicado"
                : "Evento obsoleto: el cambio del " + fechaCambio + " de " + vinculo.getId()
                + " es anterior al último aplicado (" + ultimaEnZonaDelEvento + ")";
        throw new EventoDescartadoException(vinculo.getIdCliente(), motivo);
    }

    private String comoJson(List<CambioCampo> cambios) {
        try {
            return objectMapper.writeValueAsString(cambios);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo registrar el detalle de los cambios", ex);
        }
    }
}

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
import com.maxiconecta.crm.perfil.cliente.TipoCambio;
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
 * Conciliación: el evento se resuelve por su identificador de origen (RF-62). Si el identificador
 * ya está vinculado, se actualiza ese perfil y nunca se crea otro; si no, se crea un perfil nuevo
 * y se vincula. Los eventos de un mismo cliente se ordenan por la fecha del cambio en el sistema
 * de origen: uno igual o más antiguo que el último aplicado se descarta.
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
    private final LectorEventosCliente lector;
    private final ValidadorPerfil validador;
    private final ObjectMapper objectMapper;

    public ProcesadorClientes(EventoClienteRepository eventos, ClienteRepository clientes,
                              ClienteOrigenRepository origenes, CambioPerfilRepository cambiosPerfil,
                              LectorEventosCliente lector, ValidadorPerfil validador, ObjectMapper objectMapper) {
        this.eventos = eventos;
        this.clientes = clientes;
        this.origenes = origenes;
        this.cambiosPerfil = cambiosPerfil;
        this.lector = lector;
        this.validador = validador;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResultadoSincronizacion aplicar(Long idEvento) {
        EventoCliente registro = eventos.findById(idEvento)
                .orElseThrow(() -> new IllegalStateException("No existe el evento " + idEvento + " en la bitácora"));
        EventoClienteRecibido evento = lector.leer(registro.getContenido());
        EventoClienteRecibido.DatosCliente datos = evento.cliente();
        ClienteOrigen.Clave clave = new ClienteOrigen.Clave(evento.origen(), datos.idCliente());
        OffsetDateTime fechaCambio = evento.fechaCambio();

        ClienteOrigen vinculo = origenes.findById(clave).orElse(null);
        Cliente cliente;
        TipoCambio tipo;
        if (vinculo == null) {
            cliente = new Cliente();
            tipo = TipoCambio.CREACION;
        } else {
            descartarSiNoEsPosterior(vinculo, fechaCambio);
            cliente = clientes.findById(vinculo.getIdCliente()).orElseThrow(() -> new IllegalStateException(
                    "El identificador " + clave + " apunta a un perfil inexistente: " + vinculo.getIdCliente()));
            tipo = TipoCambio.ACTUALIZACION;
        }

        PerfilValidado perfil = validador.validar(datos);
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

    private static void descartarSiNoEsPosterior(ClienteOrigen vinculo, OffsetDateTime fechaCambio) {
        OffsetDateTime ultima = vinculo.getUltimaActualizacionOrigen();
        if (ultima == null || fechaCambio.isAfter(ultima)) {
            return;
        }
        String motivo = fechaCambio.isEqual(ultima)
                ? "El cambio del " + fechaCambio + " de " + vinculo.getId() + " ya fue aplicado"
                : "Evento obsoleto: el cambio del " + fechaCambio + " de " + vinculo.getId()
                + " es anterior al último aplicado (" + ultima + ")";
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

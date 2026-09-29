package com.maxiconecta.crm.perfil.consentimiento;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.comun.ConflictoException;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import com.maxiconecta.crm.perfil.comun.ReglaNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Registro, actualización y revocación del consentimiento de tratamiento de datos (SCRUM-14,
 * docs/perfil/consentimiento-datos.md). Cada cambio y su entrada en el historial se guardan en la
 * misma transacción.
 */
@Service
public class GestionConsentimiento {

    private final ConsentimientoRepository consentimientos;
    private final RegistroConsentimientoRepository historial;
    private final ClienteRepository clientes;
    private final ValidadorConsentimiento validador = new ValidadorConsentimiento(ZoneId.systemDefault());

    public GestionConsentimiento(ConsentimientoRepository consentimientos, RegistroConsentimientoRepository historial,
                                 ClienteRepository clientes) {
        this.consentimientos = consentimientos;
        this.historial = historial;
        this.clientes = clientes;
    }

    /**
     * Otorga el consentimiento, o actualiza el que ya tiene el cliente. Si llegan los mismos datos
     * que ya estaban registrados, no cambia nada ni genera historial.
     */
    @Transactional
    public Consentimiento registrar(Long idCliente, SolicitudConsentimiento solicitud, String responsable) {
        exigirClienteVigente(idCliente);
        Consentimiento actual = consentimientos.findById(idCliente).orElse(null);
        DatosConsentimiento datos = validador.validar(solicitud, actual, OffsetDateTime.now());

        if (actual == null) {
            Consentimiento nuevo = consentimientos.save(Consentimiento.otorgar(idCliente, datos, responsable));
            historial.save(new RegistroConsentimiento(nuevo, OperacionConsentimiento.OTORGAMIENTO, responsable));
            return nuevo;
        }
        OperacionConsentimiento operacion = actual.aplicar(datos, responsable);
        if (operacion != null) {
            historial.save(new RegistroConsentimiento(actual, operacion, responsable));
        }
        return actual;
    }

    @Transactional
    public Consentimiento revocar(Long idCliente, String motivo, String responsable) {
        exigirClienteVigente(idCliente);
        Consentimiento actual = consentimientos.findById(idCliente)
                .filter(c -> c.getEstado() == EstadoConsentimiento.OTORGADO)
                .orElseThrow(() -> new ConflictoException("El cliente " + idCliente
                        + " no tiene un consentimiento otorgado que revocar"));
        actual.revocar(motivo == null || motivo.isBlank() ? null : motivo.trim(), responsable);
        historial.save(new RegistroConsentimiento(actual, OperacionConsentimiento.REVOCACION, responsable));
        return actual;
    }

    @Transactional(readOnly = true)
    public Consentimiento consultar(Long idCliente) {
        exigirClienteExistente(idCliente);
        return consentimientos.findById(idCliente).orElseThrow(() -> new RecursoNoEncontradoException(
                "El cliente " + idCliente + " no tiene un consentimiento registrado"));
    }

    /** Todos los estados del consentimiento, del más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public List<RegistroConsentimiento> historial(Long idCliente) {
        exigirClienteExistente(idCliente);
        return historial.findByIdClienteOrderByFechaDescIdDesc(idCliente);
    }

    /**
     * Para los módulos que usan datos del cliente: true solo si hoy tiene un consentimiento vigente
     * que incluye esa finalidad.
     */
    @Transactional(readOnly = true)
    public boolean autoriza(Long idCliente, AlcanceConsentimiento alcance) {
        return consentimientos.findById(idCliente)
                .map(c -> c.autoriza(alcance, LocalDate.now()))
                .orElse(false);
    }

    private void exigirClienteExistente(Long idCliente) {
        if (!clientes.existsById(idCliente)) {
            throw new RecursoNoEncontradoException("No existe el cliente " + idCliente);
        }
    }

    private void exigirClienteVigente(Long idCliente) {
        Cliente cliente = clientes.findById(idCliente)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + idCliente));
        if (cliente.fueConsolidado()) {
            throw new ReglaNegocioException("El cliente " + idCliente + " ya fue unificado en el cliente "
                    + cliente.getIdClienteConsolidado());
        }
    }
}

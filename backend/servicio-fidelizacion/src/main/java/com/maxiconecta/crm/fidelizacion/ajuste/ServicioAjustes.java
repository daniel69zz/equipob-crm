package com.maxiconecta.crm.fidelizacion.ajuste;

import com.maxiconecta.crm.fidelizacion.caducidad.ProcesoCaducidad;
import com.maxiconecta.crm.fidelizacion.comun.ConflictoException;
import com.maxiconecta.crm.fidelizacion.comun.ReglaInvalidaException;
import com.maxiconecta.crm.fidelizacion.movimiento.TipoMovimiento;
import com.maxiconecta.crm.fidelizacion.nivel.AsignacionNiveles;
import com.maxiconecta.crm.fidelizacion.nivel.Nivel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Ajuste manual del saldo de puntos de un cliente por el Administrador de CRM (SCRUM-422,
 * docs/fidelizacion/ajuste-manual-puntos.md). Todo va en una transacción: la caducidad pendiente, el saldo, el
 * movimiento {@code AJUSTE}, el registro con el motivo y el responsable, y el nivel se guardan juntos o no se
 * guarda nada.
 */
@Service
public class ServicioAjustes {

    public static final long PUNTOS_MAXIMOS = 100_000;
    static final int LARGO_MINIMO_MOTIVO = 10;

    private static final Logger log = LoggerFactory.getLogger(ServicioAjustes.class);

    private final RegistroAjustes registro;
    private final ProcesoCaducidad caducidad;
    private final AsignacionNiveles niveles;

    public ServicioAjustes(RegistroAjustes registro, ProcesoCaducidad caducidad, AsignacionNiveles niveles) {
        this.registro = registro;
        this.caducidad = caducidad;
        this.niveles = niveles;
    }

    /**
     * @param usuario responsable del ajuste (identidad del API Gateway)
     * @throws ReglaInvalidaException si el ajuste no es válido o deja el saldo negativo (criterio de aceptación 2)
     * @throws ConflictoException     si ya se aplicó o si el saldo cambió desde que el Administrador lo revisó
     */
    @Transactional
    public Ajuste ajustar(long clienteId, SolicitudAjuste solicitud, String usuario, String rol) {
        String motivo = solicitud.motivo().strip();
        validar(solicitud.puntos(), motivo);
        String referencia = solicitud.referencia() == null || solicitud.referencia().isBlank()
                ? null : solicitud.referencia().strip();
        if (registro.yaAplicado(solicitud.idSolicitud())) {
            throw new ConflictoException("Este ajuste ya se aplicó");
        }
        // SCRUM-429: un saldo vencido se caduca primero; el ajuste parte del saldo disponible.
        caducidad.caducarSiVencio(clienteId, OffsetDateTime.now(ZoneOffset.UTC));
        long anterior = registro.bloquearSaldo(clienteId);
        if (anterior != solicitud.saldoActual()) {
            throw new ConflictoException("El saldo del cliente cambió mientras revisaba el ajuste: ahora es " + anterior
                    + " puntos. Revise el ajuste y vuelva a confirmarlo.");
        }
        long puntos = solicitud.puntos();
        long resultante = anterior + puntos;
        if (resultante < 0) {
            throw new ReglaInvalidaException("El ajuste dejaría el saldo en " + resultante + ": el saldo disponible es "
                    + anterior + " y se quieren restar " + Math.abs(puntos) + " puntos",
                    Map.of("puntos", "no puede restar más que el saldo disponible (" + anterior + ")"));
        }
        String idTransaccion = "AJUSTE-" + solicitud.idSolicitud();
        Ajuste ajuste;
        try {
            registro.registrarMovimiento(idTransaccion, clienteId, puntos, resultante);
            ajuste = registro.registrar(solicitud.idSolicitud(), idTransaccion, clienteId, puntos, anterior, resultante,
                    motivo, referencia, usuario, rol);
        } catch (DuplicateKeyException ex) {
            // La misma solicitud confirmada dos veces a la vez: la otra copia ya la aplicó.
            throw new ConflictoException("Este ajuste ya se aplicó");
        }
        registro.actualizarSaldo(clienteId, resultante);
        Nivel nivel = niveles.recalcular(clienteId, idTransaccion, TipoMovimiento.AJUSTE.name());
        log.info("Ajuste {} de {} puntos al cliente {} por {}: saldo {} → {}", ajuste.id(), puntos, clienteId, usuario,
                anterior, resultante);
        return ajuste.conNivel(nivel == null ? null : nivel.name());
    }

    @Transactional(readOnly = true)
    public List<Ajuste> historial(long clienteId) {
        return registro.deCliente(clienteId);
    }

    private static void validar(long puntos, String motivo) {
        if (puntos == 0) {
            throw new ReglaInvalidaException("El ajuste no es válido: puntos debe ser distinto de 0",
                    Map.of("puntos", "debe ser distinto de 0 (positivo suma, negativo resta)"));
        }
        if (motivo.length() < LARGO_MINIMO_MOTIVO) {
            throw new ReglaInvalidaException("El ajuste no es válido: motivo debe tener al menos "
                    + LARGO_MINIMO_MOTIVO + " caracteres", Map.of("motivo", "debe tener al menos "
                    + LARGO_MINIMO_MOTIVO + " caracteres"));
        }
    }
}

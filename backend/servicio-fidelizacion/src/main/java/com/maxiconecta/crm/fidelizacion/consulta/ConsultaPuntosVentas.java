package com.maxiconecta.crm.fidelizacion.consulta;

import com.maxiconecta.crm.fidelizacion.caducidad.SaldoVigente;
import com.maxiconecta.crm.fidelizacion.movimiento.ClientesPerfil;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntos;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntosRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Saldo disponible, nivel y caducidad de los puntos de un cliente para Marketplace y Ventas, antes de aplicar
 * una redención (SCRUM-544, RIO-CRM-04, docs/contratos-eventos/RIO-CRM-04-consulta-puntos.md).
 * <p>
 * El cliente llega con su identificador de Ventas y se vincula con su perfil del CRM, como los movimientos
 * RIO-CRM-03. Un cliente sin cuenta de puntos, o que el CRM aún no conoce, responde saldo cero sin error
 * (criterio de aceptación 3).
 * <p>
 * Caducidad: el saldo vence {@code vigenciaMeses} meses después de la última acumulación. Si ya venció y el
 * proceso de caducidad (SCRUM-50) aún no lo descontó, se informa como {@code puntosCaducados} y el saldo
 * disponible es cero, para que Ventas no acepte una redención con puntos vencidos.
 */
@Service
public class ConsultaPuntosVentas {

    private final ClientesPerfil perfiles;
    private final SaldoPuntosRepository saldos;
    private final TransactionTemplate lectura;
    private final SaldoVigente vigente;

    public ConsultaPuntosVentas(ClientesPerfil perfiles, SaldoPuntosRepository saldos, SaldoVigente vigente,
                                PlatformTransactionManager transacciones) {
        this.perfiles = perfiles;
        this.saldos = saldos;
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
        this.vigente = vigente;
    }

    /**
     * @throws ClientesPerfil.PerfilNoDisponibleException si el servicio de Perfil no responde
     */
    public PuntosParaVentas consultar(String idClienteVentas) {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        // La llamada a Perfil va fuera de la transacción, para no tener una conexión abierta mientras espera.
        Optional<Long> cliente = perfiles.buscar(idClienteVentas);
        if (cliente.isEmpty()) {
            return sinCuenta(idClienteVentas, null, ahora);
        }
        long clienteId = cliente.get();
        return lectura.execute(transaccion -> {
            Optional<SaldoPuntos> saldo = saldos.findById(clienteId);
            if (saldo.isEmpty()) {
                return sinCuenta(idClienteVentas, clienteId, ahora);
            }
            SaldoVigente.Estado estado = vigente.evaluar(clienteId, saldo.get().getSaldo(), ahora);
            return new PuntosParaVentas(idClienteVentas, clienteId, true, estado.saldoDisponible(),
                    saldo.get().getNivel(), estado.fechaCaducidad(), estado.puntosCaducados(), vigente.vigenciaMeses(),
                    ahora);
        });
    }

    private PuntosParaVentas sinCuenta(String idClienteVentas, Long clienteId, OffsetDateTime ahora) {
        return new PuntosParaVentas(idClienteVentas, clienteId, false, 0, null, null, 0, vigente.vigenciaMeses(), ahora);
    }

    /** Respuesta de RIO-CRM-04. {@code clienteId} es nulo si el CRM aún no conoce al cliente de Ventas. */
    public record PuntosParaVentas(String idClienteVentas, Long clienteId, boolean tieneCuenta, long saldoDisponible,
                                   String nivel, OffsetDateTime fechaCaducidad, long puntosCaducados,
                                   int vigenciaMeses, OffsetDateTime consultadoEn) {
    }
}

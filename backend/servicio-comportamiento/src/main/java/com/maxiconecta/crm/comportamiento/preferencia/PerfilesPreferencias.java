package com.maxiconecta.crm.comportamiento.preferencia;

import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;

/** Construcción explícita (SCRUM-41). La suscripción a eventos corresponde a SCRUM-42. */
@Service
public class PerfilesPreferencias {
    private final IdentificadoresPreferencias identificadores;
    private final AgregadoConsumoCategorias consumo;
    private final PerfilPreferenciasRepository perfiles;
    private final Clock reloj;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public PerfilesPreferencias(IdentificadoresPreferencias identificadores, AgregadoConsumoCategorias consumo,
                               PerfilPreferenciasRepository perfiles, Clock reloj, PlatformTransactionManager manager) {
        this.identificadores = identificadores;
        this.consumo = consumo;
        this.perfiles = perfiles;
        this.reloj = reloj;
        escritura = new TransactionTemplate(manager);
        escritura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        lectura = new TransactionTemplate(manager);
        lectura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        lectura.setReadOnly(true);
    }

    public PerfilPreferencias construir(long clienteId) {
        // La red se consulta antes de abrir la transacción. Nunca se aceptan IDs externos del solicitante.
        var ids = identificadores.obtener(clienteId);
        for (int intento = 0; intento < 3; intento++) {
            try {
                return escritura.execute(estado -> {
                    perfiles.bloquear(clienteId);
                    var categorias = ConstructorPreferencias.construir(consumo.consumirTodo(ids));
                    return perfiles.guardar(clienteId, OffsetDateTime.now(reloj), categorias);
                });
            } catch (PessimisticLockingFailureException ex) {
                // PostgreSQL invalida una instantánea si otro constructor guardó mientras se esperaba el bloqueo.
                // Reintentar toda la transacción vuelve a leer las compras y evita sobrescribir datos más recientes.
                if (intento == 2) {
                    throw new PreferenciasException(HttpStatus.CONFLICT,
                            "Otra construcción está actualizando el perfil; intente nuevamente");
                }
            }
        }
        throw new IllegalStateException("No se completó la construcción del perfil");
    }

    public PerfilPreferencias consultar(long clienteId) {
        identificadores.obtener(clienteId);
        return lectura.execute(estado -> perfiles.obtener(clienteId));
    }
}

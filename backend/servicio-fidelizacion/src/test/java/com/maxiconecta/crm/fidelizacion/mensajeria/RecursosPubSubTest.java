package com.maxiconecta.crm.fidelizacion.mensajeria;

import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.cloud.spring.pubsub.PubSubAdmin;
import com.google.pubsub.v1.Subscription;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Varios servicios arrancan a la vez y comparten tópicos: el que pierde la carrera no debe caerse. */
class RecursosPubSubTest {

    private static AlreadyExistsException yaExiste() {
        return new AlreadyExistsException(new RuntimeException("ALREADY_EXISTS"),
                GrpcStatusCode.of(Status.Code.ALREADY_EXISTS), false);
    }

    @Test
    void siOtroServicioCreaLosRecursosAlMismoTiempoLosTomaComoExistentes() {
        PubSubAdmin admin = mock(PubSubAdmin.class);
        when(admin.createTopic(anyString())).thenThrow(yaExiste());
        when(admin.createSubscription(any(Subscription.Builder.class))).thenThrow(yaExiste());
        when(admin.createSubscription(anyString(), anyString())).thenThrow(yaExiste());
        RecursosPubSub recursos = new RecursosPubSub(admin, () -> "crm-prueba", 5);

        assertThatCode(() -> recursos.crear(new SuscripcionPubSub("crm.prueba", "ventas.eventos",
                "compra.confirmada", datos -> { }))).doesNotThrowAnyException();
        verify(admin, times(2)).createTopic(anyString());
        verify(admin).createSubscription(any(Subscription.Builder.class));
    }

    @Test
    void otrosErroresDePubSubSiguenImpidiendoArrancar() {
        PubSubAdmin admin = mock(PubSubAdmin.class);
        when(admin.createTopic(anyString())).thenThrow(new IllegalStateException("sin permisos"));
        RecursosPubSub recursos = new RecursosPubSub(admin, () -> "crm-prueba", 5);

        assertThatCode(() -> recursos.crear(new SuscripcionPubSub("crm.prueba", "ventas.eventos",
                "compra.confirmada", datos -> { }))).isInstanceOf(IllegalStateException.class);
    }
}

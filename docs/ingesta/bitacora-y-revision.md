# Bitácora y simulación del envío a revisión

Al abrir Eventos de venta o pulsar Limpiar, se muestra todo el historial con
paginación. Las fechas son opcionales y pueden usarse por separado.

El botón Reprocesar de Inicio y de la bitácora simula el envío a revisión:

1. Llama a POST /api/comportamiento/eventos/{id}/revision.
2. Registra una espera de revisión, sin ejecutar la ingesta ni modificar compras,
   puntos, preferencias o segmentos.
3. El botón pasa a Reprocesado y se muestra Evento enviado a revisión.
4. El evento se presenta como En espera y se puede encontrar con ese filtro.
   La espera persiste al recargar y no se ejecuta automáticamente.

Se reutiliza el historial: un intento EN_PROCESO junto a un evento fallido
representa la espera. La API presenta esa combinación como estado=EN_ESPERA,
reprocesado=true y enRevision=true. El fallo original y su causa se conservan.
Los contadores separan Fallidos y En espera para no contar dos veces un evento.
Repetir el envío devuelve el mismo intento.

El endpoint de reproceso real sigue disponible para las integraciones existentes,
pero estos botones no lo llaman. Un evento en espera no admite un reproceso real
mientras conserve el intento pendiente. No se envían mensajes a otras personas.

Pruebas: ReprocesoEventosIntegracionTest verifica persistencia, idempotencia y
que no se procese la compra. e2e/ingesta.spec.ts verifica botones, notificaciones,
filtros y recarga con respuestas controladas.

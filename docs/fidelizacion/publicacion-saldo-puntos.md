# Publicación del saldo y nivel en Pub/Sub

SCRUM-594 (SCRUM-617 a SCRUM-622). Contrato: [RIO-CRM-06](../contratos-eventos/RIO-CRM-06-saldo-puntos-actualizado.md).
La migración Flyway V11 crea la bandeja de salida. El registro local está siempre activo;
desactivar la publicación conserva los pendientes para publicarlos al habilitarla.

## Configuración por entorno

Para recibir por RabbitMQ y publicar por Pub/Sub:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'salida-pubsub'
$env:PUBSUB_PROYECTO = 'ID-REAL-DEL-PROYECTO'
$env:FIDELIZACION_SALIDA_TOPICO = 'ucb-sis323-sub-b'
```

Para usar Pub/Sub también en la entrada: `SPRING_PROFILES_ACTIVE=pubsub,salida-pubsub`.
El perfil de salida no crea ni modifica el tópico ni la suscripción del curso.
Requiere que existan y que la cuenta tenga `roles/pubsub.publisher` sobre el tópico.
No necesita permisos de suscriptor en producción.

Autenticación mediante Application Default Credentials: cuenta de servicio adjunta en la nube,
Workload Identity o `gcloud auth application-default login` en desarrollo. Si se requiere archivo,
`GOOGLE_APPLICATION_CREDENTIALS` debe apuntar a una ruta externa al repositorio.
No copiar claves ni tokens a YAML, `.env.example`, documentación o commits.

Con emulador: establecer `PUBSUB_EMULADOR=localhost:8085` y un proyecto local;
no usar credenciales reales. El emulador debe tener el tópico y una suscripción de prueba creados.
Las pruebas automatizadas crean ambos en contenedores aislados.

Variables de operación:

- `FIDELIZACION_SALIDA_ENABLED`: `false` por defecto; `true` en el perfil `salida-pubsub`.
- `FIDELIZACION_SALIDA_TOPICO`: `ucb-sis323-sub-b`; admite nombre completo de tópico.
- `FIDELIZACION_SALIDA_INTERVALO_MS`: 5000 entre lotes.
- `FIDELIZACION_SALIDA_LOTE`: 20 filas por ejecución.
- `FIDELIZACION_SALIDA_ESPERA_SEGUNDOS`: 10 por publicación.
- `FIDELIZACION_SALIDA_PROYECTO`: proyecto real del tópico cuando la mensajería interna usa otro
  proyecto o el emulador. Con valor, el saldo se publica con un publicador propio que ignora
  `PUBSUB_EMULADOR`; vacío, usa el mismo Pub/Sub que los eventos internos.
- `FIDELIZACION_SALIDA_CREDENCIALES`: ruta a la clave JSON de la cuenta de servicio (fuera del
  repositorio) para ese publicador; vacía, usa las Application Default Credentials.
- `PUBSUB_PROYECTO`: proyecto de Google Cloud, requerido para el entorno real.
- `PUBSUB_EMULADOR`: vacío en la nube; host y puerto en local.

Para pausar envíos, iniciar con `FIDELIZACION_SALIDA_ENABLED=false`. Los movimientos siguen
guardando su evento. No vaciar la bandeja para corregir un fallo de credenciales, permisos o conectividad.

## Pruebas automatizadas

Desde `backend/servicio-fidelizacion`, con JDK 17+, Maven y Docker:

```powershell
mvn test
mvn '-Dtest=SalidaSaldoIntegracionTest,SalidaSaldoPubSubIntegracionTest' test
```

`SalidaSaldoIntegracionTest` usa PostgreSQL real y un transporte controlado para verificar los
cinco tipos de movimiento, cambios de nivel, duplicados, rechazos, rollback, caída y recuperación,
orden por cliente, dos despachadores concurrentes y fallos del servicio Perfil.
`SalidaSaldoPubSubIntegracionTest` usa PostgreSQL y el emulador oficial de Pub/Sub: publica en
el tópico del grupo, inspecciona la suscripción y devuelve el mensaje sin hacer `ack`.
Las pruebas no usan el tópico real. Sin Docker, Testcontainers las omite; eso no constituye una validación.

## Validación en el tópico del curso (entorno autorizado)

1. Configurar el proyecto real y ADC, sin `PUBSUB_EMULADOR`; comprobar que existen el tópico
   `ucb-sis323-sub-b` y la suscripción `ucb-sis323-sub-b-sub` y que su filtro admite
   `attributes.ruta="puntos.saldo.actualizado"` (o que no tiene filtro).
2. Iniciar el servicio con el perfil de salida y aplicar un movimiento de prueba acordado con
   Marketplace y Ventas. Guardar su `id_transaccion` y `id_evento` consultando la bandeja.
3. Inspeccionar sin consumir (no agregar `--auto-ack`):

   ```powershell
   gcloud pubsub subscriptions pull ucb-sis323-sub-b-sub --project=ID-REAL-DEL-PROYECTO --limit=10 --format=json
   ```

4. Decodificar `message.data` de Base64 a UTF-8 y cotejar `idEvento`, `idCliente`, saldo,
   nivel, secuencia y atributo `ruta`. Cotejar `messageId` con `id_mensaje` en la bandeja.
   El pull sin ack oculta temporalmente el mensaje hasta su plazo de confirmación, luego lo reentrega.
5. Registrar fecha, proyecto, UUID e ID de mensaje como evidencia, sin tokens ni datos personales.
   No confirmar ni eliminar mensajes de la suscripción compartida.

Esta validación requiere credenciales y acceso al proyecto del curso; los resultados del emulador
no la sustituyen. La revisión por un par, los adjuntos en Jira y el merge también son pasos de entrega separados.

## Diagnóstico

```sql
SELECT secuencia, id_evento, cliente_id, id_transaccion, tipo_movimiento,
       intentos, proximo_intento, publicado_en, id_mensaje, ultimo_error
FROM fidelizacion.salida_saldo_puntos
WHERE publicado_en IS NULL
ORDER BY secuencia;
```

Corregir la causa de `ultimo_error`. El reintento es automático; una fila sin identificador único
requiere resolver el vínculo en Perfil. Conservar las filas publicadas según la política de retención
del equipo; esta historia no implementa su purga automática.

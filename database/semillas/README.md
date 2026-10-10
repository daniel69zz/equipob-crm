# 100 clientes ficticios para SCRUM-25

Esta carga interpreta los «100 datos» como **100 clientes**, con las relaciones necesarias
para probar frecuencia, valor de compra y categorías. Se carga manualmente; no es una
migración Flyway ni forma parte del arranque de la aplicación.

## Contenido

| Clientes de origen | Cantidad de clientes | Compras por cliente | Compras totales |
|---|---:|---:|---:|
| `TEST-S25-CLI-001` a `010` | 10 | 0 | 0 |
| `TEST-S25-CLI-011` a `040` | 30 | 1–3 | 60 |
| `TEST-S25-CLI-041` a `070` | 30 | 5–9 | 210 |
| `TEST-S25-CLI-071` a `100` | 30 | 12–20 | 471 |
| **Total** | **100** | | **741** |

- 100 perfiles completos, identificadores de origen y direcciones ficticias.
- 100 consentimientos vigentes para gestión del cliente, análisis y segmentación, con historial.
- 100 registros de creación de perfil y sus 600 detalles.
- 741 compras confirmadas, vinculadas al perfil, con 1.482 ítems, sus eventos y claves de idempotencia.
- 100 contadores de frecuencia consistentes con las compras; los clientes sin compras tienen cero.
- Seis categorías: `Electronica`, `Ropa`, `Hogar`, `Alimentos`, `Deportes` y `Libros`.
- Cada compra tiene dos categorías y sus montos suman exactamente el total, en bolivianos.
- Fechas relativas al día de la primera carga, en `America/La_Paz`. Los clientes 011–020 tienen
  compras de más de 90 días de antigüedad para probar inactividad.
- Correos bajo `example.test`, documentos de prueba y ningún teléfono real.

Los grupos baja/media/alta son **escenarios de prueba**: no definen las reglas de SCRUM-25.
No se crean tablas ni criterios de segmentación, pues ese servicio todavía no tiene migraciones
en esta rama. Los indicadores se preparan en las tablas existentes de perfil y comportamiento.

## Cargar

Usar una base PostgreSQL de desarrollo con las migraciones de `servicio-perfil` hasta V9
y de `servicio-comportamiento` hasta V7 aplicadas. La carga escribe en ambos esquemas
como herramienta de preparación de pruebas, con un usuario que tenga permiso de inserción.

Desde la raíz del repositorio, con `psql` instalado:

```powershell
psql -h localhost -p 5432 -U crm -d crm_maxiconecta -W -v ON_ERROR_STOP=1 -f database/semillas/scrum25_100_clientes.sql
psql -h localhost -p 5432 -U crm -d crm_maxiconecta -W -v ON_ERROR_STOP=1 -f database/semillas/verificar_scrum25.sql
```

`-W` solicita la contraseña sin guardarla en el script. Ajustar servidor, usuario y base al entorno.
También se puede abrir el SQL en el editor de consultas de pgAdmin y ejecutarlo completo.

La carga es una transacción: ante un error, no deja inserciones parciales. Usa las secuencias
de la base para los IDs internos y no sobrescribe clientes preexistentes. Los códigos de origen
`TEST-S25-*` están reservados para este conjunto. Al ejecutarlo de nuevo se omiten los clientes
ya cargados sin duplicar compras ni historial; tampoco se refrescan las fechas. Si esos clientes
fueron modificados durante una prueba, puede detectar una colisión o cantidades diferentes y
cancelarse, en lugar de reemplazar lo probado. Para volver al escenario original, usar una
base de pruebas nueva con las migraciones aplicadas.

La consulta de verificación falla si encuentra cantidades, montos, vínculos, frecuencia o
consentimientos inconsistentes; después muestra los grupos y los indicadores de los 100 clientes.

Esta carga inserta directamente en la BD y prepara estados equivalentes a eventos procesados.
Para probar el consumo real por RabbitMQ, utilizar `herramientas/simulador-eventos/`.

## Validación realizada

Se ejecutaron las 16 migraciones del repositorio y ambos scripts en PostgreSQL embebido
(PGlite), en una base temporal aislada. Se comprobaron las cantidades, las seis categorías,
los montos de los eventos, las relaciones, los consentimientos y los contadores. Una segunda
carga dejó todas las tablas sin cambios; una colisión en el cliente 100 revirtió las inserciones
anteriores de esa transacción. También se comprobó que un cliente preexistente se conservaba.
Esta validación no carga los datos en el servidor local del proyecto ni prueba la interfaz o RabbitMQ.

# Probar SCRUM-25 en el proyecto original

SCRUM-25 permite al administrador definir y mantener las reglas que se utilizarán para agrupar
clientes: frecuencia de compra, valor acumulado o categoría consumida. La asignación automática
de segmentos corresponde a SCRUM-26 y el listado de clientes por segmento a SCRUM-28.

## Entrar

Con el proyecto habitual encendido, abrir http://localhost:4200 e ingresar con `admin` y su
contraseña original. En el menú: **Administración → Criterios de segmentación**.
La base es `crm_maxiconecta` en el contenedor `crm-postgres`, puerto 5432.

Al terminar la verificación del 10 de octubre de 2026 quedaron **3 criterios, 3 activos y 0
inactivos**. Sus nombres empiezan con `Ejemplo SCRUM-25`. Los contadores cambian al modificarlos.

## Comprobar cada ejemplo

Abrir **Historial y prueba**, escribir el dato y pulsar **Comprobar regla**.

| Ejemplo | Regla guardada | Dato que cumple | Dato que no cumple |
|---|---|---|---|
| compradores frecuentes | Entre 12 y 20 compras, inclusive | 14 | 11 |
| alto valor | Valor acumulado mayor o igual a Bs 20.000 | 22776.25 | 19999.99 |
| consumo de alimentos | Ha consumido Alimentos | ALIMENTOS, Deportes | Libros, Deportes |

La pantalla muestra **El caso cumple el criterio** o **El caso no cumple el criterio**, junto
con la versión comprobada. Esta herramienta comprueba el valor introducido manualmente; no
busca ni asigna automáticamente clientes. El cliente 100 mostraba 14 compras, Bs 22.776,25 y
consumo de Alimentos: sirve como ejemplo para entender las tres reglas.

## Validaciones

En **Nuevo criterio**, usar un nombre de prueba distinto cada vez:

1. Dejar el nombre vacío y guardar: debe indicar que falta el nombre, sin crear un registro.
2. Frecuencia, condición Entre, mínimo 20 y máximo 10: debe rechazar el rango invertido.
3. Frecuencia, condición Entre, mínimo 20 y máximo 30: debe indicar superposición con el
   criterio de compradores frecuentes, porque ambos incluyen 20.
4. Cancelar ese formulario. Crear `Manual: compras ocasionales`, frecuencia Entre 1 y 3:
   debe guardarse como Activo si no existe otra regla activa que ocupe ese rango.
5. Recargar la página: el criterio recién creado debe seguir en la lista.

## Edición, historial y desactivación

1. Editar `Ejemplo SCRUM-25: compradores frecuentes`, cambiar mínimo 12 a 13 y guardar.
2. En **Historial y prueba**, revisar el evento de Edición: debe mostrar `admin`, fecha,
   Antes 12–20 y Después 13–20. Ahora 12 no cumple y 13 sí cumple.
3. Desactivar el criterio y confirmar. Debe pasar a Inactivo y conservar el historial.
4. Filtrar Estado = Activos: ya no debe aparecer. En Inactivos sí debe aparecer.
5. Para dejar el ejemplo como estaba: editar el mínimo a 12, guardar, activar y confirmar.
   La reactivación también queda registrada y vuelve a validar superposiciones.

## Permisos

Con una cuenta existente de Agente o Gerente, la opción de administrar criterios no debe
aparecer. Abrir directamente `/segmentacion/criterios` debe mostrar acceso denegado. Esto
requiere probar con otra cuenta; `admin` tiene permiso y no sirve para comprobar la denegación.

## Estado de la historia

Los cuatro criterios de aceptación están implementados y cuentan con pruebas. El recorrido
manual del 10 de octubre verificó creación, reglas de los tres indicadores, errores y auditoría;
la denegación a otros roles se verificó con pruebas automatizadas anteriores.

Para cerrar toda la definición de terminado aún faltan revisión por un par, merge y adjuntar
los diagramas a Jira. Los diagramas ya existen en este directorio.

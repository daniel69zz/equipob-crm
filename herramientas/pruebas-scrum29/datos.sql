-- Exclusivo del contenedor temporal del ejecutor. No usa datos de desarrollo.
INSERT INTO segmentacion.criterio(id,nombre,nombre_clave,indicador,condicion,minimo,maximo,actualizado_por)
VALUES (2901,'Frecuentes','frecuentes','FRECUENCIA','ENTRE',5,9,'datos-prueba');
SELECT setval('segmentacion.criterio_id_seq',2901);
INSERT INTO segmentacion.metricas_cliente(cliente_id,frecuencia,valor,categorias,calculadas_en,id_evento_origen)
VALUES (1,7,100,ARRAY['Hogar'],now(),'e1'), (2,1,10,ARRAY['Alimentos'],now(),'e2'),
       (3,7,1000,ARRAY['Electrónica'],now(),'e3');
INSERT INTO segmentacion.segmento_cliente(cliente_id,segmento,criterio_id,indicador)
VALUES (1,'Frecuentes',2901,'FRECUENCIA'), (3,'Frecuentes',2901,'FRECUENCIA'), (99,'Legado',null,null);

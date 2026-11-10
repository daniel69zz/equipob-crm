-- SCRUM-458 · Cada asignación y reasignación del responsable queda en la auditoría de la interacción (SCRUM-56).
ALTER TABLE interacciones.interaccion_auditoria DROP CONSTRAINT interaccion_auditoria_operacion_check;
ALTER TABLE interacciones.interaccion_auditoria
    ADD CONSTRAINT interaccion_auditoria_operacion_check
        CHECK (operacion IN ('CREADA', 'MODIFICADA', 'SEGUIMIENTO', 'COMPRA_VINCULADA', 'COMPRA_DESVINCULADA',
                             'ASIGNADA'));

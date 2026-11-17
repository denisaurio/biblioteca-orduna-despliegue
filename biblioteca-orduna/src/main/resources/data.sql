-- =====================================================================
--  Datos iniciales del Sistema de Biblioteca Orduña
--  Se ejecuta automáticamente al arrancar (spring.sql.init.mode=always).
--  INSERT IGNORE: si el registro ya existe (mismo DNI/email/id) no hace nada.
--
--  Usuarios de prueba:
--    GERENTE        -> admin@biblioteca.pe   / admin123
--    BIBLIOTECARIO  -> biblio@biblioteca.pe  / biblio123
--    LECTOR         -> lector@biblioteca.pe  / lector123
--  (El registro público /api/usuarios/registro solo crea LECTORES.)
-- =====================================================================

INSERT IGNORE INTO usuarios (nombre, apellido, dni, email, telefono, tipo_usuario, contrasena_hash, rol, activo, fecha_registro)
VALUES ('Gerente', 'General', '00000001', 'admin@biblioteca.pe', NULL, 'EXTERNO',
        '$2a$10$YSOktMwMpFoJBQF6goQCbeEBqDT0Sm8sU.C11xV1KSzfVoBVcJgn.', 'GERENTE', 1, NOW());

INSERT IGNORE INTO usuarios (nombre, apellido, dni, email, telefono, tipo_usuario, contrasena_hash, rol, activo, fecha_registro)
VALUES ('Bibliotecario', 'Principal', '00000002', 'biblio@biblioteca.pe', NULL, 'EXTERNO',
        '$2a$10$e/cxg4Kc31hmLXSGAHyZdu6Pj06U85fPBJPTY/TH58KBGajH4EqDe', 'BIBLIOTECARIO', 1, NOW());

INSERT IGNORE INTO usuarios (nombre, apellido, dni, email, telefono, tipo_usuario, contrasena_hash, rol, activo, fecha_registro)
VALUES ('Lector', 'de Prueba', '00000003', 'lector@biblioteca.pe', NULL, 'ESTUDIANTE',
        '$2a$10$NCEK4v8hb.uL0siWxE7P7eHz.nuJ7X50J0kjRJYMB/fpXsZGvrTNu', 'LECTOR', 1, NOW());

-- Configuración general y avisos (RF13 / RF11)
INSERT IGNORE INTO configuracion (id_configuracion, nombre_biblioteca, correo_contacto, telefono_contacto,
                                  hora_apertura, hora_cierre, aviso_vencimiento, aviso_reserva, aviso_multa,
                                  aviso_nuevo_libro, fecha_actualizacion)
VALUES (1, 'Biblioteca Orduña', 'contacto@biblioteca.pe', NULL, '08:00:00', '20:00:00', 1, 1, 1, 1, NOW());

-- Multas y reservas vigentes (configuracion_multas): S/ 1.00 por día, préstamos de 7 días, 2 días para recoger
INSERT IGNORE INTO configuracion_multas (id_configuracion, tarifa_multa_diaria, dias_max_prestamo,
                                         dias_retiro_reserva, fecha_vigencia_desde, fecha_vigencia_hasta)
VALUES (1, 1.00, 7, 2, NOW(), NULL);

-- Géneros base (tabla generos -> libros_generos)
INSERT IGNORE INTO generos (nombre) VALUES ('Novela');
INSERT IGNORE INTO generos (nombre) VALUES ('Cuento');
INSERT IGNORE INTO generos (nombre) VALUES ('Poesía');
INSERT IGNORE INTO generos (nombre) VALUES ('Historia');
INSERT IGNORE INTO generos (nombre) VALUES ('Ciencia');
INSERT IGNORE INTO generos (nombre) VALUES ('Ingeniería');

-- Préstamos: tipo_prestamo pasa a VARCHAR(20). Hibernate la creó como ENUM de MySQL
-- ('FECHA_FIJA','ABIERTO') y "ddl-auto=update" no cambia columnas existentes, así que
-- sin esto no se podría guardar el tipo nuevo PERSONALIZADO. Es idempotente: si ya es
-- VARCHAR(20) no cambia nada y los datos se conservan.
ALTER TABLE prestamos MODIFY COLUMN tipo_prestamo VARCHAR(20) NOT NULL;

-- Préstamos: estado pasa a VARCHAR(20) para que entren POR_COBRAR y ANULADO
-- (Hibernate la creó como ENUM('ACTIVO','DEVUELTO')). Idempotente, conserva los datos.
ALTER TABLE prestamos MODIFY COLUMN estado VARCHAR(20) NOT NULL;

-- Auditoría: accion pasa a VARCHAR(40) para las acciones nuevas (encargado de caja,
-- arqueo, cobrar/anular préstamo, notas del CRM). Idempotente.
ALTER TABLE auditorias MODIFY COLUMN accion VARCHAR(40) NOT NULL;

-- Préstamos anteriores al cobro al prestar: su precio se cobra AL DEVOLVER, como se
-- acordó cuando salieron. Los préstamos nuevos siempre guardan true o false, así que
-- esto solo toca filas antiguas.
UPDATE prestamos SET cobro_al_prestar = 0 WHERE cobro_al_prestar IS NULL;

-- Cajas anteriores al encargado: el encargado es quien la abrió.
UPDATE cajas SET id_encargado = id_usuario_apertura WHERE id_encargado IS NULL;

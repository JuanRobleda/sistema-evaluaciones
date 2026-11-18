-- SOLO BD de desarrollo con perfil gen02-draft. Datos completamente ficticios.
-- No representa contenido psicológico ni una edición oficial validada.
INSERT INTO gen02_sujeto(id, referencia) VALUES (25, 'DEMO-FICTICIO-25') ON CONFLICT DO NOTHING;
INSERT INTO gen02_periodo(id, nombre, activo) VALUES (3, 'PERIODO DEMO', true) ON CONFLICT DO NOTHING;
INSERT INTO gen02_test(id, codigo) VALUES (1, 'BFA') ON CONFLICT DO NOTHING;
INSERT INTO gen02_version(id, test_id, estado) VALUES (10, 1, 'PUBLICADA') ON CONFLICT DO NOTHING;
INSERT INTO gen02_configuracion(id, version_id, codigo, orden, duracion_segundos) VALUES
 (101, 10, 'VOC1', 1, 300), (102, 10, 'VOC2', 2, 300), (103, 10, 'FV', 3, 300)
 ON CONFLICT DO NOTHING;
-- El nombre debe coincidir con GEN02_LOCAL_USER; aquí se propone demo-gen02.
INSERT INTO gen02_permiso(usuario, rol, sujeto_id, periodo_id, permiso) VALUES
 ('demo-gen02', 'ACADEMICO', 25, 3, 'ASIGNAR_APLICACION'),
 ('demo-gen02', 'ACADEMICO', 25, 3, 'EVALUACION_LEER') ON CONFLICT DO NOTHING;

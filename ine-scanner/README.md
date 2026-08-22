# INE Scanner Android MVP

## v0.3
- Android nativo.
- Cámara o galería.
- OCR local con ML Kit.
- CURP con normalización de errores OCR comunes.
- Nombre, apellidos, clave de elector, fecha de nacimiento, sexo, año de registro, emisión, domicilio, CP, municipio, estado, sección y vigencia.
- Búsqueda local por nombre, CURP o clave de elector.
- SQLite local con datos sensibles cifrados mediante AES-256/GCM y Android Keystore.
- Migración desde la base de v0.2 conservando registros previos.
- No verifica autenticidad ante INE; es captura estructurada.

Build v0.3 listo para CI.

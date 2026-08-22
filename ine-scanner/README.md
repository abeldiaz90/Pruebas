# INE Scanner Android MVP

- Android nativo.
- Cámara o galería.
- OCR local con ML Kit bundled Latin model.
- Extracción heurística de nombre, CURP, clave de elector, domicilio, CP, sección y vigencia.
- Revisión manual antes de guardar.
- SQLite local.
- Datos sensibles cifrados con AES-256/GCM y llave en Android Keystore.
- La foto temporal tomada por la app se elimina después del OCR.
- No verifica autenticidad ante INE; es un MVP de captura estructurada.

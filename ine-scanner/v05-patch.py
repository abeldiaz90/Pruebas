from pathlib import Path

p = Path('app/src/main/java/com/syncro/inescanner/MainActivity.java')
s = p.read_text(encoding='utf-8')

s = s.replace('INE Scanner v0.4', 'INE Scanner v0.5')
s = s.replace('Motor multi-formato IFE/INE: captura frente y reverso, revisa los datos y guárdalos cifrados.',
              'Motor multi-formato IFE/INE: lee frente y reverso y conserva ambas lecturas cifradas en el expediente.')
s = s.replace('Reconoce distintos modelos IFE/INE y combina frente + reverso. El OCR no certifica autenticidad; revisa los datos antes de guardar.',
              'Reconoce distintos modelos IFE/INE, combina frente + reverso y guarda ambas lecturas OCR cifradas. No certifica autenticidad.')

old = '''            long id = db.insert(p);\n            Toast.makeText(this, "Guardado local cifrado. ID " + id, Toast.LENGTH_LONG).show();'''
new = '''            long id = db.insert(p, rawFront, rawBack);\n            String sides = (!rawFront.trim().isEmpty() ? "frente" : "") +\n                    (!rawFront.trim().isEmpty() && !rawBack.trim().isEmpty() ? " + " : "") +\n                    (!rawBack.trim().isEmpty() ? "reverso" : "");\n            Toast.makeText(this, "Guardado cifrado (" + sides + "). ID " + id, Toast.LENGTH_LONG).show();'''
if old not in s:
    raise SystemExit('savePerson target not found')
s = s.replace(old, new)

s = s.replace('super(context, "ine_scanner.db", null, 3);', 'super(context, "ine_scanner.db", null, 4);')

old_create_end = '''                    "modelo_credencial TEXT NOT NULL DEFAULT ''," +\n                    "confianza_modelo TEXT NOT NULL DEFAULT ''," +\n                    "created_at TEXT NOT NULL)");\n        }'''
new_create_end = '''                    "modelo_credencial TEXT NOT NULL DEFAULT ''," +\n                    "confianza_modelo TEXT NOT NULL DEFAULT ''," +\n                    "created_at TEXT NOT NULL)");\n            createScansTable(db);\n        }'''
if old_create_end not in s:
    raise SystemExit('onCreate target not found')
s = s.replace(old_create_end, new_create_end)

old_upgrade = '''            if (oldVersion < 3) {\n                addColumn(db, "numero_emision");\n                addColumn(db, "cic");\n                addColumn(db, "codigo_ocr");\n                addColumn(db, "modelo_credencial");\n                addColumn(db, "confianza_modelo");\n            }\n        }'''
new_upgrade = '''            if (oldVersion < 3) {\n                addColumn(db, "numero_emision");\n                addColumn(db, "cic");\n                addColumn(db, "codigo_ocr");\n                addColumn(db, "modelo_credencial");\n                addColumn(db, "confianza_modelo");\n            }\n            if (oldVersion < 4) {\n                createScansTable(db);\n            }\n        }\n\n        private void createScansTable(SQLiteDatabase db) {\n            db.execSQL("CREATE TABLE IF NOT EXISTS document_scans (" +\n                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +\n                    "person_id INTEGER NOT NULL," +\n                    "side TEXT NOT NULL," +\n                    "raw_text TEXT NOT NULL DEFAULT ''," +\n                    "created_at TEXT NOT NULL," +\n                    "FOREIGN KEY(person_id) REFERENCES people(id) ON DELETE CASCADE)");\n            db.execSQL("CREATE INDEX IF NOT EXISTS idx_document_scans_person ON document_scans(person_id)");\n        }'''
if old_upgrade not in s:
    raise SystemExit('onUpgrade target not found')
s = s.replace(old_upgrade, new_upgrade)

old_insert = '''        long insert(Map<String, String> p) throws Exception {\n            ContentValues v = new ContentValues();\n            for (String key : DATA) v.put(key, crypto.encrypt(p.get(key)));\n            v.put("created_at", new SimpleDateFormat(\n                    "yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));\n            return getWritableDatabase().insertOrThrow("people", null, v);\n        }'''
new_insert = '''        long insert(Map<String, String> p, String rawFront, String rawBack) throws Exception {\n            SQLiteDatabase db = getWritableDatabase();\n            db.beginTransaction();\n            try {\n                ContentValues v = new ContentValues();\n                for (String key : DATA) v.put(key, crypto.encrypt(p.get(key)));\n                String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());\n                v.put("created_at", now);\n                long personId = db.insertOrThrow("people", null, v);\n                if (rawFront != null && !rawFront.trim().isEmpty()) {\n                    insertScan(db, personId, "FRENTE", rawFront, now);\n                }\n                if (rawBack != null && !rawBack.trim().isEmpty()) {\n                    insertScan(db, personId, "REVERSO", rawBack, now);\n                }\n                db.setTransactionSuccessful();\n                return personId;\n            } finally {\n                db.endTransaction();\n            }\n        }\n\n        private void insertScan(SQLiteDatabase db, long personId, String side, String rawText, String createdAt) throws Exception {\n            ContentValues scan = new ContentValues();\n            scan.put("person_id", personId);\n            scan.put("side", side);\n            scan.put("raw_text", crypto.encrypt(rawText));\n            scan.put("created_at", createdAt);\n            db.insertOrThrow("document_scans", null, scan);\n        }'''
if old_insert not in s:
    raise SystemExit('insert target not found')
s = s.replace(old_insert, new_insert)

# Show whether the saved expediente has front/back evidence.
old_line = '''                        .append("\\nCaptura: ").append(r.get("created_at"))\n                        .append("\\n\\n");'''
new_line = '''                        .append("\\nLados guardados: ").append(db.sidesFor(r.get("id")))\n                        .append("\\nCaptura: ").append(r.get("created_at"))\n                        .append("\\n\\n");'''
if old_line not in s:
    raise SystemExit('records target not found')
s = s.replace(old_line, new_line)

needle = '''        private String normalizeSearch(String s) {'''
method = '''        String sidesFor(String personId) {\n            try (Cursor c = getReadableDatabase().rawQuery(\n                    "SELECT side FROM document_scans WHERE person_id=? ORDER BY id",\n                    new String[]{personId})) {\n                List<String> sides = new ArrayList<>();\n                while (c.moveToNext()) sides.add(c.getString(0));\n                return sides.isEmpty() ? "sin OCR" : android.text.TextUtils.join(" + ", sides);\n            } catch (Exception e) {\n                return "sin OCR";\n            }\n        }\n\n'''
if needle not in s:
    raise SystemExit('normalizeSearch target not found')
s = s.replace(needle, method + needle)

p.write_text(s, encoding='utf-8')
print('v0.5 patch applied')

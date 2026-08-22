package com.syncro.inescanner;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class PeopleDbV3 extends SQLiteOpenHelper {
    private static final String[] DATA = new String[]{
            "nombre", "apellido_paterno", "apellido_materno", "curp", "clave_elector",
            "fecha_nacimiento", "sexo", "anio_registro", "emision", "domicilio", "colonia",
            "codigo_postal", "municipio", "estado", "seccion", "vigencia"
    };
    private final Crypto crypto = new Crypto();

    PeopleDbV3(Context context) { super(context, "ine_scanner.db", null, 2); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE people (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "nombre TEXT NOT NULL DEFAULT '', apellido_paterno TEXT NOT NULL DEFAULT '', apellido_materno TEXT NOT NULL DEFAULT ''," +
                "curp TEXT NOT NULL DEFAULT '', clave_elector TEXT NOT NULL DEFAULT ''," +
                "fecha_nacimiento TEXT NOT NULL DEFAULT '', sexo TEXT NOT NULL DEFAULT '', anio_registro TEXT NOT NULL DEFAULT '', emision TEXT NOT NULL DEFAULT ''," +
                "domicilio TEXT NOT NULL DEFAULT '', colonia TEXT NOT NULL DEFAULT '', codigo_postal TEXT NOT NULL DEFAULT '', municipio TEXT NOT NULL DEFAULT ''," +
                "estado TEXT NOT NULL DEFAULT '', seccion TEXT NOT NULL DEFAULT '', vigencia TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            addColumn(db, "fecha_nacimiento"); addColumn(db, "sexo");
            addColumn(db, "anio_registro"); addColumn(db, "emision");
        }
    }

    private void addColumn(SQLiteDatabase db, String name) {
        try { db.execSQL("ALTER TABLE people ADD COLUMN " + name + " TEXT NOT NULL DEFAULT ''"); }
        catch (Exception ignored) {}
    }

    long insert(Map<String,String> p) throws Exception {
        ContentValues v = new ContentValues();
        for (String key : DATA) v.put(key, crypto.encrypt(p.get(key)));
        v.put("created_at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        return getWritableDatabase().insertOrThrow("people", null, v);
    }

    List<Map<String,String>> search(String query, int limit) throws Exception {
        List<Map<String,String>> all = list(500);
        String q = norm(query);
        if (q.isEmpty()) return all.size() <= limit ? all : new ArrayList<>(all.subList(0, limit));
        List<Map<String,String>> out = new ArrayList<>();
        for (Map<String,String> r : all) {
            String h = norm(safe(r.get("nombre")) + " " + safe(r.get("apellido_paterno")) + " " +
                    safe(r.get("apellido_materno")) + " " + safe(r.get("curp")) + " " + safe(r.get("clave_elector")));
            if (h.contains(q)) { out.add(r); if (out.size() >= limit) break; }
        }
        return out;
    }

    private List<Map<String,String>> list(int limit) throws Exception {
        List<Map<String,String>> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM people ORDER BY id DESC LIMIT ?", new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                Map<String,String> r = new HashMap<>();
                r.put("id", String.valueOf(c.getLong(c.getColumnIndexOrThrow("id"))));
                r.put("created_at", c.getString(c.getColumnIndexOrThrow("created_at")));
                for (String key : DATA) {
                    int idx = c.getColumnIndex(key);
                    r.put(key, idx >= 0 ? crypto.decrypt(c.getString(idx)) : "");
                }
                out.add(r);
            }
        }
        return out;
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Z0-9]", "");
    }

    static final class Crypto {
        private static final String STORE = "AndroidKeyStore";
        private static final String ALIAS = "INE_SCANNER_AES_V1";

        private SecretKey key() throws Exception {
            KeyStore ks = KeyStore.getInstance(STORE); ks.load(null);
            if (!ks.containsAlias(ALIAS)) {
                KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
                gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256).build());
                gen.generateKey();
            }
            return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS, null)).getSecretKey();
        }

        String encrypt(String plain) throws Exception {
            if (plain == null || plain.isEmpty()) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV(); byte[] enc = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer all = ByteBuffer.allocate(1 + iv.length + enc.length); all.put((byte)iv.length).put(iv).put(enc);
            return Base64.encodeToString(all.array(), Base64.NO_WRAP);
        }

        String decrypt(String packed) throws Exception {
            if (packed == null || packed.isEmpty()) return "";
            ByteBuffer b = ByteBuffer.wrap(Base64.decode(packed, Base64.NO_WRAP));
            int ivLen = b.get() & 0xff; byte[] iv = new byte[ivLen]; b.get(iv); byte[] enc = new byte[b.remaining()]; b.get(enc);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(enc), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}

package com.syncro.inescanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA = 1001;
    private static final int REQ_GALLERY = 1002;

    private Uri currentImageUri;
    private File currentCameraFile;
    private ImageView preview;
    private TextView status;
    private TextView records;
    private Button ocrButton;
    private final Map<String, EditText> fields = new HashMap<>();
    private PeopleDb db;

    private final String[] fieldOrder = new String[]{
            "nombre", "apellido_paterno", "apellido_materno", "curp", "clave_elector",
            "domicilio", "colonia", "codigo_postal", "municipio", "estado",
            "seccion", "vigencia"
    };

    private final String[] fieldLabels = new String[]{
            "Nombre(s)", "Apellido paterno", "Apellido materno", "CURP", "Clave de elector",
            "Domicilio", "Colonia", "Código postal", "Municipio", "Estado",
            "Sección electoral", "Vigencia"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new PeopleDb(this);
        setContentView(buildUi());
        loadRecords();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(32));
        root.setBackgroundColor(Color.rgb(245, 247, 251));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("INE Scanner v0.2", 28, true);
        title.setTextColor(Color.rgb(17, 24, 39));
        root.addView(title);

        TextView subtitle = text("Escanea una INE, revisa los datos y guárdalos cifrados en este teléfono.", 15, false);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button camera = button("Tomar foto");
        Button gallery = button("Galería");
        actions.addView(camera, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, dp(52), 1);
        gp.setMarginStart(dp(8));
        actions.addView(gallery, gp);
        root.addView(actions);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        preview.setBackgroundColor(Color.rgb(229, 231, 235));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250));
        ip.topMargin = dp(12);
        root.addView(preview, ip);

        ocrButton = button("Leer INE con OCR");
        ocrButton.setEnabled(false);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        op.topMargin = dp(10);
        root.addView(ocrButton, op);

        status = text("Toma una foto con buena luz, sin reflejos y con la credencial ocupando casi todo el encuadre.", 13, false);
        status.setTextColor(Color.rgb(71, 85, 105));
        status.setPadding(0, dp(10), 0, dp(10));
        root.addView(status);

        TextView warning = text("Extrae texto visible; no certifica autenticidad. En esta versión se mejoró la captura por cámara y el parser de nombre/domicilio.", 12, false);
        warning.setTextColor(Color.rgb(146, 64, 14));
        warning.setBackgroundColor(Color.rgb(255, 247, 237));
        warning.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(warning);

        TextView formTitle = text("Datos detectados", 20, true);
        formTitle.setPadding(0, dp(18), 0, dp(4));
        root.addView(formTitle);

        for (int i = 0; i < fieldOrder.length; i++) {
            TextView label = text(fieldLabels[i], 13, true);
            label.setPadding(0, dp(8), 0, dp(4));
            root.addView(label);
            EditText input = new EditText(this);
            input.setTextSize(16);
            input.setSingleLine(!fieldOrder[i].equals("domicilio"));
            input.setPadding(dp(12), dp(8), dp(12), dp(8));
            input.setBackgroundColor(Color.WHITE);
            fields.put(fieldOrder[i], input);
            root.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    fieldOrder[i].equals("domicilio") ? dp(84) : dp(50)));
        }

        Button save = button("Guardar persona cifrada");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        sp.topMargin = dp(18);
        root.addView(save, sp);

        Button clear = button("Limpiar formulario");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        cp.topMargin = dp(8);
        root.addView(clear, cp);

        TextView dbTitle = text("Registros locales", 20, true);
        dbTitle.setPadding(0, dp(20), 0, dp(8));
        root.addView(dbTitle);
        records = text("", 14, false);
        records.setTextColor(Color.rgb(30, 41, 59));
        records.setPadding(dp(12), dp(10), dp(12), dp(10));
        records.setBackgroundColor(Color.WHITE);
        root.addView(records);

        camera.setOnClickListener(v -> capturePhoto());
        gallery.setOnClickListener(v -> chooseImage());
        ocrButton.setOnClickListener(v -> runOcr());
        save.setOnClickListener(v -> savePerson());
        clear.setOnClickListener(v -> clearForm());
        return scroll;
    }

    private void capturePhoto() {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("No se pudo crear almacenamiento temporal.");
            currentCameraFile = File.createTempFile("INE_", ".jpg", dir);
            currentImageUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", currentCameraFile);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) == null) {
                throw new IOException("No encontré una aplicación de cámara.");
            }
            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentImageUri);
            intent.setClipData(ClipData.newRawUri("INE", currentImageUri));
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_CAMERA);
        } catch (Exception e) {
            cleanupCameraFile();
            showError("No se pudo abrir la cámara: " + e.getMessage());
        }
    }

    private void chooseImage() {
        cleanupCameraFile();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAMERA) {
            if (resultCode == RESULT_OK && currentImageUri != null && currentCameraFile != null
                    && currentCameraFile.exists() && currentCameraFile.length() > 0) {
                showImage(currentImageUri);
                status.setText("Foto cargada correctamente. Pulsa “Leer INE con OCR”.");
            } else {
                cleanupCameraFile();
                status.setText("La cámara no devolvió una foto válida. Intenta de nuevo.");
            }
        } else if (requestCode == REQ_GALLERY && resultCode == RESULT_OK && data != null && data.getData() != null) {
            currentImageUri = data.getData();
            currentCameraFile = null;
            try {
                getContentResolver().takePersistableUriPermission(currentImageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            showImage(currentImageUri);
            status.setText("Imagen cargada desde galería. Pulsa “Leer INE con OCR”.");
        }
    }

    private void showImage(Uri uri) {
        try {
            preview.setImageDrawable(null);
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                Bitmap bmp = BitmapFactory.decodeStream(in);
                if (bmp == null) throw new IOException("La imagen no pudo decodificarse.");
                preview.setImageBitmap(bmp);
            }
            ocrButton.setEnabled(true);
        } catch (Exception e) {
            preview.setImageDrawable(null);
            ocrButton.setEnabled(false);
            status.setText("No pude cargar la imagen: " + e.getMessage());
        }
    }

    private void runOcr() {
        if (currentImageUri == null) return;
        ocrButton.setEnabled(false);
        status.setText("Leyendo la credencial…");
        try {
            InputImage image = InputImage.fromFilePath(this, currentImageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        String raw = result.getText() == null ? "" : result.getText();
                        Map<String, String> parsed = IneParser.parse(raw);
                        clearDetectedFields();
                        for (String k : fieldOrder) setField(k, parsed.get(k));
                        int populated = 0;
                        for (String k : fieldOrder) if (!value(k).isEmpty()) populated++;
                        status.setText(raw.trim().isEmpty()
                                ? "No pude leer texto. Intenta otra foto con más luz y menos reflejo."
                                : "OCR completado: " + populated + " campos detectados. Revisa antes de guardar.");
                        recognizer.close();
                        ocrButton.setEnabled(true);
                    })
                    .addOnFailureListener(e -> {
                        recognizer.close();
                        status.setText("No se pudo procesar la imagen: " + e.getMessage());
                        ocrButton.setEnabled(true);
                    });
        } catch (IOException e) {
            status.setText("No se pudo abrir la imagen: " + e.getMessage());
            ocrButton.setEnabled(true);
        }
    }

    private void clearDetectedFields() {
        for (EditText e : fields.values()) e.setText("");
    }

    private void cleanupCameraFile() {
        if (currentCameraFile != null) {
            try { if (currentCameraFile.exists()) currentCameraFile.delete(); } catch (Exception ignored) {}
        }
        currentCameraFile = null;
        currentImageUri = null;
    }

    private void savePerson() {
        String nombre = value("nombre");
        String curp = value("curp");
        if (nombre.isEmpty() && curp.isEmpty()) {
            Toast.makeText(this, "Captura al menos nombre o CURP.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Map<String, String> p = new HashMap<>();
            for (String key : fieldOrder) p.put(key, value(key));
            long id = db.insert(p);
            Toast.makeText(this, "Guardado local cifrado. ID " + id, Toast.LENGTH_LONG).show();
            clearForm();
            loadRecords();
        } catch (Exception e) {
            showError("No se pudo guardar: " + e.getMessage());
        }
    }

    private void loadRecords() {
        try {
            List<Map<String, String>> list = db.list(30);
            if (list.isEmpty()) {
                records.setText("Aún no hay personas guardadas.");
                return;
            }
            StringBuilder out = new StringBuilder();
            for (Map<String, String> r : list) {
                out.append("#").append(r.get("id")).append("  ")
                        .append(r.get("nombre")).append(" ")
                        .append(r.get("apellido_paterno")).append(" ")
                        .append(r.get("apellido_materno")).append("\n")
                        .append("CURP: ").append(mask(r.get("curp"))).append("   Vigencia: ").append(r.get("vigencia"))
                        .append("\nCaptura: ").append(r.get("created_at")).append("\n\n");
            }
            records.setText(out.toString().trim());
        } catch (Exception e) {
            records.setText("No se pudo abrir la base local: " + e.getMessage());
        }
    }

    private String mask(String s) {
        if (s == null || s.length() < 8) return s == null ? "" : s;
        return s.substring(0, 4) + "••••••" + s.substring(s.length() - 4);
    }

    private void clearForm() {
        clearDetectedFields();
        preview.setImageDrawable(null);
        cleanupCameraFile();
        ocrButton.setEnabled(false);
        status.setText("Toma una foto con buena luz, sin reflejos y con la credencial ocupando casi todo el encuadre.");
    }

    private String value(String key) {
        EditText e = fields.get(key);
        return e == null ? "" : e.getText().toString().trim();
    }

    private void setField(String key, String value) {
        EditText e = fields.get(key);
        if (e != null && value != null && !value.trim().isEmpty()) e.setText(value.trim());
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(15);
        b.setAllCaps(false);
        return b;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void showError(String message) {
        new AlertDialog.Builder(this).setTitle("INE Scanner").setMessage(message).setPositiveButton("Aceptar", null).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (db != null) db.close();
        cleanupCameraFile();
    }

    static class IneParser {
        private static final Pattern CURP = Pattern.compile("\\b[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d\\b");
        private static final Pattern SECTION = Pattern.compile("SECCI[ÓO]N\\s*[:#-]?\\s*(\\d{3,5})");
        private static final Pattern VIG_RANGE = Pattern.compile("VIGENCIA\\s*[:#-]?\\s*(20\\d{2})\\s*[-/]\\s*(20\\d{2})");
        private static final Pattern VIG_SINGLE = Pattern.compile("VIGENCIA\\s*[:#-]?\\s*(20\\d{2})");
        private static final Pattern CP = Pattern.compile("(?:C\\.?P\\.?\\s*)?(\\d{5})");
        private static final Pattern ELECTOR_KEY = Pattern.compile("\\b[A-Z]{5,8}\\d{6,10}[HM]\\d{2,4}\\b");

        static Map<String, String> parse(String text) {
            Map<String, String> f = new HashMap<>();
            if (text == null) return f;

            String normalized = text.replace('|', 'I').replaceAll("[ \\t]+", " ");
            String upper = normalized.toUpperCase(Locale.ROOT);
            List<String> lines = new ArrayList<>();
            for (String raw : upper.split("\\r?\\n")) {
                String line = clean(raw);
                if (!line.isEmpty()) lines.add(line);
            }

            String compact = upper.replaceAll("[^A-Z0-9]", "");
            Matcher m = CURP.matcher(compact);
            if (m.find()) f.put("curp", m.group());

            m = SECTION.matcher(upper);
            if (m.find()) f.put("seccion", m.group(1));

            m = VIG_RANGE.matcher(upper);
            if (m.find()) f.put("vigencia", m.group(1) + "-" + m.group(2));
            else {
                m = VIG_SINGLE.matcher(upper);
                if (m.find()) f.put("vigencia", m.group(1));
            }

            parseName(lines, f);
            parseElectorKey(lines, upper, f);
            parseAddress(lines, f);

            return f;
        }

        private static void parseName(List<String> lines, Map<String, String> f) {
            int start = labelIndex(lines, "NOMBRE");
            if (start < 0) return;
            List<String> block = collectUntil(lines, start, "NOMBRE",
                    new String[]{"DOMICILIO", "CLAVE DE ELECTOR", "CURP", "FECHA DE NACIMIENTO", "SEXO"}, 6);

            List<String> names = new ArrayList<>();
            for (String s : block) {
                s = stripKnownLabels(s);
                if (s.isEmpty() || isMetadata(s) || containsDigits(s)) continue;
                if (s.length() < 2) continue;
                names.add(s);
            }

            if (names.size() >= 3) {
                f.put("apellido_paterno", names.get(0));
                f.put("apellido_materno", names.get(1));
                f.put("nombre", join(names, 2));
            } else if (names.size() == 2) {
                f.put("apellido_paterno", names.get(0));
                f.put("nombre", names.get(1));
            } else if (names.size() == 1) {
                f.put("nombre", names.get(0));
            }
        }

        private static void parseElectorKey(List<String> lines, String upper, Map<String, String> f) {
            int idx = labelIndex(lines, "CLAVE DE ELECTOR");
            if (idx >= 0) {
                String candidate = inlineAfter(lines.get(idx), "CLAVE DE ELECTOR");
                if (candidate.isEmpty() && idx + 1 < lines.size()) candidate = lines.get(idx + 1);
                candidate = candidate.replaceAll("[^A-Z0-9]", "");
                if (candidate.length() >= 15 && candidate.length() <= 22) {
                    f.put("clave_elector", candidate);
                    return;
                }
            }
            Matcher m = ELECTOR_KEY.matcher(upper.replace(" ", ""));
            if (m.find()) f.put("clave_elector", m.group());
        }

        private static void parseAddress(List<String> lines, Map<String, String> f) {
            int start = labelIndex(lines, "DOMICILIO");
            if (start < 0) return;

            List<String> block = collectUntil(lines, start, "DOMICILIO",
                    new String[]{"CLAVE DE ELECTOR", "CURP", "FECHA DE NACIMIENTO", "SEXO", "AÑO DE REGISTRO", "SECCIÓN", "SECCION", "VIGENCIA"}, 6);

            List<String> good = new ArrayList<>();
            for (String s : block) {
                s = stripKnownLabels(s);
                if (s.isEmpty() || isMetadata(s)) continue;
                good.add(s);
            }
            if (good.isEmpty()) return;

            String address = String.join(", ", good);
            f.put("domicilio", address);

            Matcher cp = CP.matcher(address);
            String cpValue = "";
            while (cp.find()) cpValue = cp.group(1);
            if (!cpValue.isEmpty()) f.put("codigo_postal", cpValue);

            for (String line : good) {
                String u = line.toUpperCase(Locale.ROOT);
                if (u.contains("COL.") || u.contains("COLONIA")) {
                    String c = u.replaceFirst("^.*?\\b(?:COL\\.?|COLONIA)\\s*", "");
                    c = c.replaceAll("\\bC\\.?P\\.?\\s*\\d{5}\\b", "");
                    c = c.replaceAll("\\b\\d{5}\\b", "");
                    c = clean(c.replace(",", " "));
                    if (!c.isEmpty()) f.put("colonia", c);
                }
            }

            if (good.size() >= 2) {
                String last = good.get(good.size() - 1).replaceAll("\\b\\d{5}\\b", "").trim();
                String[] parts = last.split("[,;/]");
                if (parts.length >= 2) {
                    String municipio = clean(parts[0]);
                    String estado = clean(parts[parts.length - 1]);
                    if (!municipio.isEmpty()) f.put("municipio", municipio);
                    if (!estado.isEmpty()) f.put("estado", estado);
                }
            }
        }

        private static List<String> collectUntil(List<String> lines, int idx, String label, String[] stops, int max) {
            List<String> out = new ArrayList<>();
            String inline = inlineAfter(lines.get(idx), label);
            if (!inline.isEmpty()) out.add(inline);
            for (int i = idx + 1; i < lines.size() && out.size() < max; i++) {
                String line = lines.get(i);
                boolean stop = false;
                for (String s : stops) {
                    if (line.contains(s)) { stop = true; break; }
                }
                if (stop) break;
                out.add(line);
            }
            return out;
        }

        private static int labelIndex(List<String> lines, String label) {
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains(label)) return i;
            return -1;
        }

        private static String inlineAfter(String line, String label) {
            int p = line.indexOf(label);
            if (p < 0) return "";
            return clean(line.substring(p + label.length()).replaceFirst("^[:# -]+", ""));
        }

        private static String stripKnownLabels(String s) {
            return clean(s.replace("NOMBRE", "")
                    .replace("DOMICILIO", "")
                    .replace("CLAVE DE ELECTOR", "")
                    .replace("CURP", "")
                    .replace("SECCIÓN", "")
                    .replace("SECCION", "")
                    .replace("VIGENCIA", ""));
        }

        private static boolean containsDigits(String s) {
            return s.matches(".*\\d.*");
        }

        private static boolean isMetadata(String s) {
            return s.contains("DOMICILIO") || s.contains("CLAVE DE ELECTOR") || s.startsWith("CURP")
                    || s.contains("SECCI") || s.contains("VIGENCIA") || s.contains("AÑO DE REGISTRO")
                    || s.contains("FECHA DE NACIMIENTO") || s.equals("SEXO") || s.startsWith("SEXO ")
                    || s.contains("INSTITUTO NACIONAL ELECTORAL") || s.contains("CREDENCIAL PARA VOTAR")
                    || s.contains("MÉXICO") || s.contains("MEXICO");
        }

        private static String join(List<String> list, int from) {
            StringBuilder b = new StringBuilder();
            for (int i = from; i < list.size(); i++) {
                if (b.length() > 0) b.append(' ');
                b.append(list.get(i));
            }
            return b.toString();
        }

        private static String clean(String s) {
            return s == null ? "" : s.replaceAll("\\s+", " ").replaceAll("^[,;:.-]+|[,;:.-]+$", "").trim();
        }
    }

    static class CryptoManager {
        private static final String STORE = "AndroidKeyStore";
        private static final String ALIAS = "INE_SCANNER_AES_V1";

        private SecretKey key() throws Exception {
            KeyStore ks = KeyStore.getInstance(STORE);
            ks.load(null);
            if (!ks.containsAlias(ALIAS)) {
                KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
                gen.init(new KeyGenParameterSpec.Builder(ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                gen.generateKey();
            }
            return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        }

        String encrypt(String plain) throws Exception {
            if (plain == null || plain.isEmpty()) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] enc = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer all = ByteBuffer.allocate(1 + iv.length + enc.length);
            all.put((byte) iv.length).put(iv).put(enc);
            return Base64.encodeToString(all.array(), Base64.NO_WRAP);
        }

        String decrypt(String packed) throws Exception {
            if (packed == null || packed.isEmpty()) return "";
            byte[] all = Base64.decode(packed, Base64.NO_WRAP);
            ByteBuffer b = ByteBuffer.wrap(all);
            int ivLen = b.get() & 0xff;
            byte[] iv = new byte[ivLen];
            b.get(iv);
            byte[] enc = new byte[b.remaining()];
            b.get(enc);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(enc), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    static class PeopleDb extends SQLiteOpenHelper {
        private final CryptoManager crypto = new CryptoManager();
        private static final String[] DATA = new String[]{
                "nombre", "apellido_paterno", "apellido_materno", "curp", "clave_elector",
                "domicilio", "colonia", "codigo_postal", "municipio", "estado", "seccion", "vigencia"
        };

        PeopleDb(Context context) { super(context, "ine_scanner.db", null, 1); }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE people (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "nombre TEXT NOT NULL DEFAULT '', apellido_paterno TEXT NOT NULL DEFAULT '', apellido_materno TEXT NOT NULL DEFAULT ''," +
                    "curp TEXT NOT NULL DEFAULT '', clave_elector TEXT NOT NULL DEFAULT '', domicilio TEXT NOT NULL DEFAULT ''," +
                    "colonia TEXT NOT NULL DEFAULT '', codigo_postal TEXT NOT NULL DEFAULT '', municipio TEXT NOT NULL DEFAULT ''," +
                    "estado TEXT NOT NULL DEFAULT '', seccion TEXT NOT NULL DEFAULT '', vigencia TEXT NOT NULL DEFAULT ''," +
                    "created_at TEXT NOT NULL)");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

        long insert(Map<String, String> p) throws Exception {
            ContentValues v = new ContentValues();
            for (String key : DATA) v.put(key, crypto.encrypt(p.get(key)));
            v.put("created_at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            return getWritableDatabase().insertOrThrow("people", null, v);
        }

        List<Map<String, String>> list(int limit) throws Exception {
            List<Map<String, String>> out = new ArrayList<>();
            try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM people ORDER BY id DESC LIMIT ?", new String[]{String.valueOf(limit)})) {
                while (c.moveToNext()) {
                    Map<String, String> r = new HashMap<>();
                    r.put("id", String.valueOf(c.getLong(c.getColumnIndexOrThrow("id"))));
                    r.put("created_at", c.getString(c.getColumnIndexOrThrow("created_at")));
                    for (String key : DATA) r.put(key, crypto.decrypt(c.getString(c.getColumnIndexOrThrow(key))));
                    out.add(r);
                }
            }
            return out;
        }
    }
}

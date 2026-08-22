package com.syncro.inescanner;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class IneParserV3 {
    private static final Pattern VALID_CURP = Pattern.compile("^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d$");
    private static final Pattern DATE = Pattern.compile("(\\d{1,2})\\s*[/\\-.]\\s*(\\d{1,2})\\s*[/\\-.]\\s*((?:19|20)?\\d{2})");
    private static final Pattern SECTION = Pattern.compile("SECCI[ÓO]N\\s*[:#-]?\\s*(\\d{3,5})");
    private static final Pattern VIG_RANGE = Pattern.compile("VIGENCIA\\s*[:#-]?\\s*(20\\d{2})\\s*[-/]\\s*(20\\d{2})");
    private static final Pattern VIG_SINGLE = Pattern.compile("VIGENCIA\\s*[:#-]?\\s*(20\\d{2})");
    private static final Pattern YEAR = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");
    private static final Pattern CP = Pattern.compile("(?:C\\.?P\\.?\\s*)?(\\d{5})");
    private static final Pattern ELECTOR_KEY = Pattern.compile("\\b[A-Z]{5,8}\\d{6,10}[HM]\\d{2,4}\\b");

    static Map<String, String> parse(String text) {
        Map<String, String> f = new HashMap<>();
        if (text == null) return f;
        String upper = text.replace('|', 'I').replaceAll("[ \\t]+", " ").toUpperCase(Locale.ROOT);
        List<String> lines = lines(upper);

        parseCurp(lines, upper, f);
        parseName(lines, f);
        parseElectorKey(lines, upper, f);
        parseDateSexYears(lines, upper, f);
        parseSectionVigency(upper, f);
        parseAddress(lines, f);

        if (!empty(f.get("curp"))) {
            String curp = f.get("curp");
            if (empty(f.get("sexo"))) {
                char sex = curp.charAt(10);
                if (sex == 'H' || sex == 'M') f.put("sexo", String.valueOf(sex));
            }
            if (empty(f.get("fecha_nacimiento"))) {
                try {
                    int yy = Integer.parseInt(curp.substring(4, 6));
                    int mm = Integer.parseInt(curp.substring(6, 8));
                    int dd = Integer.parseInt(curp.substring(8, 10));
                    int nowYY = Calendar.getInstance().get(Calendar.YEAR) % 100;
                    int yyyy = yy <= nowYY ? 2000 + yy : 1900 + yy;
                    f.put("fecha_nacimiento", String.format(Locale.US, "%02d/%02d/%04d", dd, mm, yyyy));
                } catch (Exception ignored) {}
            }
        }
        return f;
    }

    private static void parseCurp(List<String> lines, String upper, Map<String, String> f) {
        int idx = labelIndex(lines, "CURP");
        if (idx >= 0) {
            List<String> candidates = new ArrayList<>();
            String inline = inlineAfter(lines.get(idx), "CURP");
            if (!inline.isEmpty()) candidates.add(inline);
            for (int i = idx + 1; i < lines.size() && i <= idx + 3; i++) {
                if (isHardStop(lines.get(i)) && !looksLikeCurp(lines.get(i))) break;
                candidates.add(lines.get(i));
            }
            for (String s : candidates) {
                String curp = normalizeCurpCandidate(s);
                if (!curp.isEmpty()) { f.put("curp", curp); return; }
            }
        }
        for (String line : lines) {
            String curp = normalizeCurpCandidate(line);
            if (!curp.isEmpty()) { f.put("curp", curp); return; }
        }
        String compact = upper.replaceAll("[^A-Z0-9]", "");
        for (int i = 0; i + 18 <= compact.length(); i++) {
            String curp = normalizeCurp18(compact.substring(i, i + 18));
            if (!curp.isEmpty()) { f.put("curp", curp); return; }
        }
    }

    private static String normalizeCurpCandidate(String raw) {
        if (raw == null) return "";
        String s = raw.toUpperCase(Locale.ROOT).replace("CURP", "").replaceAll("[^A-Z0-9]", "");
        if (s.length() == 18) return normalizeCurp18(s);
        if (s.length() > 18) {
            for (int i = 0; i + 18 <= s.length(); i++) {
                String n = normalizeCurp18(s.substring(i, i + 18));
                if (!n.isEmpty()) return n;
            }
        }
        return "";
    }

    private static String normalizeCurp18(String raw) {
        if (raw == null || raw.length() != 18) return "";
        char[] c = raw.toCharArray();
        int[] letters = {0,1,2,3,10,11,12,13,14,15};
        int[] digits = {4,5,6,7,8,9,17};
        for (int p : letters) c[p] = asLetter(c[p]);
        for (int p : digits) c[p] = asDigit(c[p]);
        if (c[10] != 'H' && c[10] != 'M') return "";
        c[16] = Character.toUpperCase(c[16]);
        String n = new String(c);
        return VALID_CURP.matcher(n).matches() ? n : "";
    }

    private static char asLetter(char ch) {
        ch = Character.toUpperCase(ch);
        switch (ch) {
            case '0': return 'O'; case '1': return 'I'; case '2': return 'Z';
            case '5': return 'S'; case '8': return 'B'; case '6': return 'G';
            default: return ch;
        }
    }

    private static char asDigit(char ch) {
        ch = Character.toUpperCase(ch);
        switch (ch) {
            case 'O': case 'Q': case 'D': return '0';
            case 'I': case 'L': return '1'; case 'Z': return '2';
            case 'S': return '5'; case 'G': return '6'; case 'B': return '8';
            default: return ch;
        }
    }

    private static boolean looksLikeCurp(String s) {
        return s != null && s.replaceAll("[^A-Z0-9]", "").length() >= 16;
    }

    private static void parseDateSexYears(List<String> lines, String upper, Map<String, String> f) {
        String dateRaw = valueNearLabel(lines, "FECHA DE NACIMIENTO", 2);
        Matcher dm = DATE.matcher(dateRaw);
        if (dm.find()) {
            String year = dm.group(3);
            if (year.length() == 2) {
                int yy = Integer.parseInt(year);
                int nowYY = Calendar.getInstance().get(Calendar.YEAR) % 100;
                year = String.valueOf(yy <= nowYY ? 2000 + yy : 1900 + yy);
            }
            f.put("fecha_nacimiento", String.format(Locale.US, "%02d/%02d/%s",
                    Integer.parseInt(dm.group(1)), Integer.parseInt(dm.group(2)), year));
        }

        String sex = valueNearLabel(lines, "SEXO", 1).replaceAll("[^HM]", "");
        if (!sex.isEmpty()) f.put("sexo", String.valueOf(sex.charAt(0)));

        String reg = valueNearAnyLabel(lines, new String[]{"AÑO DE REGISTRO", "ANO DE REGISTRO"}, 2);
        Matcher ym = YEAR.matcher(reg);
        if (ym.find()) f.put("anio_registro", ym.group(1));

        String em = valueNearAnyLabel(lines, new String[]{"EMISIÓN", "EMISION"}, 2);
        ym = YEAR.matcher(em);
        if (ym.find()) f.put("emision", ym.group(1));

        if (empty(f.get("anio_registro"))) {
            Matcher m = Pattern.compile("(?:AÑO|ANO)\\s+DE\\s+REGISTRO\\s*[:#-]?\\s*(19\\d{2}|20\\d{2})").matcher(upper);
            if (m.find()) f.put("anio_registro", m.group(1));
        }
        if (empty(f.get("emision"))) {
            Matcher m = Pattern.compile("EMISI[ÓO]N\\s*[:#-]?\\s*(20\\d{2})").matcher(upper);
            if (m.find()) f.put("emision", m.group(1));
        }
    }

    private static void parseSectionVigency(String upper, Map<String, String> f) {
        Matcher m = SECTION.matcher(upper);
        if (m.find()) f.put("seccion", m.group(1));
        m = VIG_RANGE.matcher(upper);
        if (m.find()) f.put("vigencia", m.group(1) + "-" + m.group(2));
        else {
            m = VIG_SINGLE.matcher(upper);
            if (m.find()) f.put("vigencia", m.group(1));
        }
    }

    private static void parseName(List<String> lines, Map<String, String> f) {
        int start = labelIndex(lines, "NOMBRE");
        if (start < 0) return;
        List<String> block = collectUntil(lines, start, "NOMBRE",
                new String[]{"DOMICILIO", "CLAVE DE ELECTOR", "CURP", "FECHA DE NACIMIENTO", "SEXO"}, 6);
        List<String> names = new ArrayList<>();
        for (String s : block) {
            s = stripKnownLabels(s);
            if (s.isEmpty() || isMetadata(s) || s.matches(".*\\d.*") || s.length() < 2) continue;
            names.add(s);
        }
        if (names.size() >= 3) {
            f.put("apellido_paterno", names.get(0)); f.put("apellido_materno", names.get(1)); f.put("nombre", join(names, 2));
        } else if (names.size() == 2) {
            f.put("apellido_paterno", names.get(0)); f.put("nombre", names.get(1));
        } else if (names.size() == 1) f.put("nombre", names.get(0));
    }

    private static void parseElectorKey(List<String> lines, String upper, Map<String, String> f) {
        int idx = labelIndex(lines, "CLAVE DE ELECTOR");
        if (idx >= 0) {
            String c = inlineAfter(lines.get(idx), "CLAVE DE ELECTOR");
            if (c.isEmpty() && idx + 1 < lines.size()) c = lines.get(idx + 1);
            c = c.replaceAll("[^A-Z0-9]", "");
            if (c.length() >= 15 && c.length() <= 22) { f.put("clave_elector", c); return; }
        }
        Matcher m = ELECTOR_KEY.matcher(upper.replace(" ", ""));
        if (m.find()) f.put("clave_elector", m.group());
    }

    private static void parseAddress(List<String> lines, Map<String, String> f) {
        int start = labelIndex(lines, "DOMICILIO");
        if (start < 0) return;
        List<String> block = collectUntil(lines, start, "DOMICILIO",
                new String[]{"CLAVE DE ELECTOR", "CURP", "FECHA DE NACIMIENTO", "SEXO", "AÑO DE REGISTRO", "ANO DE REGISTRO", "SECCIÓN", "SECCION", "VIGENCIA", "EMISIÓN", "EMISION"}, 7);
        List<String> good = new ArrayList<>();
        for (String s : block) {
            s = stripKnownLabels(s);
            if (!s.isEmpty() && !isMetadata(s)) good.add(s);
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
                String c = u.replaceFirst("^.*?\\b(?:COL\\.?|COLONIA)\\s*", "")
                        .replaceAll("\\bC\\.?P\\.?\\s*\\d{5}\\b", "")
                        .replaceAll("\\b\\d{5}\\b", "");
                c = clean(c.replace(",", " "));
                if (!c.isEmpty()) f.put("colonia", c);
            }
        }
        if (good.size() >= 2) {
            String last = good.get(good.size() - 1).replaceAll("\\b\\d{5}\\b", "").trim();
            String[] parts = last.split("[,;/]");
            if (parts.length >= 2) {
                if (!clean(parts[0]).isEmpty()) f.put("municipio", clean(parts[0]));
                if (!clean(parts[parts.length - 1]).isEmpty()) f.put("estado", clean(parts[parts.length - 1]));
            }
        }
    }

    private static String valueNearLabel(List<String> lines, String label, int lookAhead) {
        int idx = labelIndex(lines, label);
        if (idx < 0) return "";
        StringBuilder b = new StringBuilder();
        String inline = inlineAfter(lines.get(idx), label);
        if (!inline.isEmpty()) b.append(inline);
        for (int i = idx + 1; i < lines.size() && i <= idx + lookAhead; i++) {
            if (isHardStop(lines.get(i))) break;
            if (b.length() > 0) b.append(' ');
            b.append(lines.get(i));
        }
        return b.toString();
    }

    private static String valueNearAnyLabel(List<String> lines, String[] labels, int lookAhead) {
        for (String l : labels) {
            String v = valueNearLabel(lines, l, lookAhead);
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    private static List<String> collectUntil(List<String> lines, int idx, String label, String[] stops, int max) {
        List<String> out = new ArrayList<>();
        String inline = inlineAfter(lines.get(idx), label);
        if (!inline.isEmpty()) out.add(inline);
        for (int i = idx + 1; i < lines.size() && out.size() < max; i++) {
            String line = lines.get(i);
            boolean stop = false;
            for (String s : stops) if (line.contains(s)) { stop = true; break; }
            if (stop) break;
            out.add(line);
        }
        return out;
    }

    private static List<String> lines(String upper) {
        List<String> out = new ArrayList<>();
        for (String raw : upper.split("\\r?\\n")) {
            String line = clean(raw);
            if (!line.isEmpty()) out.add(line);
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
        return clean(s.replace("NOMBRE", "").replace("DOMICILIO", "").replace("CLAVE DE ELECTOR", "")
                .replace("CURP", "").replace("FECHA DE NACIMIENTO", "").replace("AÑO DE REGISTRO", "")
                .replace("ANO DE REGISTRO", "").replace("EMISIÓN", "").replace("EMISION", "")
                .replace("SECCIÓN", "").replace("SECCION", "").replace("VIGENCIA", ""));
    }

    private static boolean isHardStop(String s) {
        return s.contains("NOMBRE") || s.contains("DOMICILIO") || s.contains("CLAVE DE ELECTOR")
                || s.startsWith("CURP") || s.contains("FECHA DE NACIMIENTO") || s.startsWith("SEXO")
                || s.contains("AÑO DE REGISTRO") || s.contains("ANO DE REGISTRO") || s.contains("SECCI")
                || s.contains("VIGENCIA") || s.contains("EMISIÓN") || s.contains("EMISION");
    }

    private static boolean isMetadata(String s) {
        return isHardStop(s) || s.contains("INSTITUTO NACIONAL ELECTORAL") || s.contains("CREDENCIAL PARA VOTAR")
                || s.equals("MÉXICO") || s.equals("MEXICO");
    }

    private static String join(List<String> list, int from) {
        StringBuilder b = new StringBuilder();
        for (int i = from; i < list.size(); i++) { if (b.length() > 0) b.append(' '); b.append(list.get(i)); }
        return b.toString();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").replaceAll("^[,;:.-]+|[,;:.-]+$", "").trim();
    }

    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }
}

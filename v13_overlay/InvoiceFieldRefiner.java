package com.syncro.expediente;

import java.util.*;
import java.util.regex.*;

public final class InvoiceFieldRefiner {
    public static void enrich(InvoiceParser.Invoice inv,String raw){
        String text=normalize(raw==null?"":raw);
        if(inv.uuid.isEmpty()) inv.uuid=match(text,"(?i)(?:UUID|FOLIO\\s+FISCAL|FOLIO\\s+UUID)\\s*[:#-]?\\s*([0-9A-F]{8}[- ]?[0-9A-F]{4}[- ]?[0-9A-F]{4}[- ]?[0-9A-F]{4}[- ]?[0-9A-F]{12})").replace(" ","").toUpperCase(Locale.ROOT);
        ArrayList<String> rfcs=all(text,"\\b[A-Z&Ñ]{3,4}\\s*\\d{6}\\s*[A-Z0-9]{3}\\b",8);
        for(int i=0;i<rfcs.size();i++)rfcs.set(i,rfcs.get(i).replaceAll("\\s+","").toUpperCase(Locale.ROOT));
        if(inv.emisorRfc.isEmpty()) inv.emisorRfc=firstNonEmpty(rfcNear(text,"EMISOR"),rfcs.size()>0?rfcs.get(0):"");
        if(inv.receptorRfc.isEmpty()) { String x=rfcNear(text,"RECEPTOR"); if(x.isEmpty())for(String r:rfcs)if(!r.equals(inv.emisorRfc)){x=r;break;} inv.receptorRfc=x; }
        inv.emisorRfc=inv.emisorRfc.replaceAll("\\s+","").toUpperCase(Locale.ROOT);
        inv.receptorRfc=inv.receptorRfc.replaceAll("\\s+","").toUpperCase(Locale.ROOT);

        if(inv.emisorNombre.isEmpty()) inv.emisorNombre=partyName(text,"EMISOR",inv.emisorRfc);
        if(inv.receptorNombre.isEmpty()) inv.receptorNombre=partyName(text,"RECEPTOR",inv.receptorRfc);
        if(inv.fecha.isEmpty()) inv.fecha=firstNonEmpty(
            match(text,"(?i)(?:FECHA(?:\\s+Y\\s+HORA)?(?:\\s+DE\\s+(?:EMISION|EXPEDICION))?|EMITIDO)\\s*[:#-]?\\s*((?:20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/]\\d{1,2}[-/]20\\d{2})(?:[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)?)"),
            match(text,"\\b(20\\d{2}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})\\b"));
        if(inv.serie.isEmpty()) inv.serie=match(text,"(?i)SERIE\\s*[:#-]?\\s*([A-Z0-9.-]{1,24})");
        if(inv.folio.isEmpty()) inv.folio=match(text,"(?i)(?:FOLIO(?!\\s*(?:FISCAL|UUID))|NO\\.?\\s*FACTURA|FACTURA\\s*(?:NO\\.?)?)\\s*[:#-]?\\s*([A-Z0-9.-]{1,30})");
        if(inv.subtotal<=0) inv.subtotal=money(text,new String[]{"SUBTOTAL","SUB TOTAL"},false);
        if(inv.impuestos<=0) inv.impuestos=money(text,new String[]{"TOTAL IMPUESTOS TRASLADADOS","IMPUESTOS TRASLADADOS","IVA","IMPUESTOS"},false);
        if(inv.total<=0) inv.total=money(text,new String[]{"TOTAL A PAGAR","TOTAL MXN","TOTAL"},true);
        if(inv.metodoPago.isEmpty())inv.metodoPago=match(text,"(?i)METODO\\s+(?:DE\\s+)?PAGO\\s*[:#-]?\\s*([^\\r\\n]{1,70})");
        if(inv.formaPago.isEmpty())inv.formaPago=match(text,"(?i)FORMA\\s+(?:DE\\s+)?PAGO\\s*[:#-]?\\s*([^\\r\\n]{1,70})");
        String mon=match(text,"(?i)(?:MONEDA|CURRENCY)\\s*[:#-]?\\s*([A-Z]{3})"); if(!mon.isEmpty())inv.moneda=mon.toUpperCase(Locale.ROOT);
        if(inv.items.isEmpty())recoverItems(text,inv);
    }

    private static void recoverItems(String text,InvoiceParser.Invoice inv){
        String[] lines=text.split("\\r?\\n");String pending="";
        Pattern p=Pattern.compile("(?i)^\\s*(\\d+(?:[.,]\\d{1,6})?)\\s+(.{4,140}?)\\s+\\$?([0-9,]+(?:\\.\\d{2,6})?)\\s*(?:\\$?([0-9,]+(?:\\.\\d{2,6})?))?\\s*$");
        for(String line:lines){String s=line.trim(),u=s.toUpperCase(Locale.ROOT);if(s.length()<3)continue;if(u.contains("SUBTOTAL")||u.matches(".*\\bTOTAL\\b.*")||u.contains("IMPUEST")||u.contains("IVA")){pending="";continue;}Matcher m=p.matcher(s);if(m.matches()){InvoiceParser.Item it=new InvoiceParser.Item();it.cantidad=d(m.group(1).replace(',','.'));it.descripcion=(pending+" "+m.group(2)).trim();it.valorUnitario=d(m.group(3));it.importe=m.group(4)==null?it.cantidad*it.valorUnitario:d(m.group(4));it.rawLine=s;inv.items.add(it);pending="";if(inv.items.size()>=100)break;}else if(s.length()>=5&&s.length()<=140&&!Pattern.compile("\\$?\\s*[0-9,]+\\.\\d{2}").matcher(s).find()){pending=(pending+" "+s).trim();if(pending.length()>180)pending=pending.substring(pending.length()-180);}}
    }

    private static String normalize(String s){return s.replace('\u00A0',' ').replace('–','-').replace('—','-').replaceAll("(?i)R\\s*F\\s*C","RFC").replaceAll("(?i)U\\s*U\\s*I\\s*D","UUID").replaceAll("[ \\t]+"," ");}
    private static String rfcNear(String text,String anchor){String[] ls=text.split("\\r?\\n");for(int i=0;i<ls.length;i++)if(ls[i].toUpperCase(Locale.ROOT).contains(anchor)){for(int j=i;j<Math.min(ls.length,i+6);j++){String x=match(ls[j],"(?i)([A-Z&Ñ]{3,4}\\s*\\d{6}\\s*[A-Z0-9]{3})");if(!x.isEmpty())return x.replaceAll("\\s+","");}}return "";}
    private static String partyName(String text,String party,String rfc){String[] ls=text.split("\\r?\\n");for(int i=0;i<ls.length;i++)if(ls[i].toUpperCase(Locale.ROOT).contains(party)){for(int j=i;j<Math.min(ls.length,i+7);j++){String line=ls[j].trim();String v=line.replaceFirst("(?i)^.*?(?:RAZON\\s+SOCIAL|NOMBRE)\\s*[:#-]?\\s*","").trim();if(!v.equals(line)&&validName(v,rfc))return v;if(j>i&&validName(line,rfc)&&!line.toUpperCase(Locale.ROOT).contains("RFC"))return line;}}return "";}
    private static boolean validName(String s,String rfc){if(s==null)return false;String v=s.trim();if(v.length()<4||v.length()>100)return false;if(!rfc.isEmpty()&&v.replaceAll("\\s+","").contains(rfc))return false;return v.matches(".*[A-Za-zÁÉÍÓÚÜÑáéíóúüñ].*");}
    private static double money(String text,String[] labels,boolean last){ArrayList<Double> vals=new ArrayList<>();for(String line:text.split("\\r?\\n")){String u=line.toUpperCase(Locale.ROOT);for(String label:labels)if(u.contains(label)){if(label.equals("TOTAL")&&(u.contains("SUBTOTAL")||u.contains("IMPUEST")))continue;Matcher m=Pattern.compile("\\$?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.\\d{2,6})|[0-9]+(?:\\.\\d{2,6}))").matcher(line);while(m.find())vals.add(d(m.group(1)));break;}}if(vals.isEmpty())return 0;if(last)return vals.get(vals.size()-1);double max=0;for(double x:vals)max=Math.max(max,x);return max;}
    private static String firstNonEmpty(String...x){for(String s:x)if(s!=null&&!s.trim().isEmpty())return s.trim();return "";}
    private static String match(String s,String re){Matcher m=Pattern.compile(re).matcher(s);return m.find()?m.group(1).trim():"";}
    private static ArrayList<String> all(String s,String re,int max){ArrayList<String>a=new ArrayList<>();Matcher m=Pattern.compile(re,Pattern.CASE_INSENSITIVE).matcher(s);while(m.find()&&a.size()<max)a.add(m.group());return a;}
    private static double d(String s){try{return Double.parseDouble(s.replace(",","").trim());}catch(Exception e){return 0;}}
}

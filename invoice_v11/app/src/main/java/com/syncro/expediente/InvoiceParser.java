package com.syncro.expediente;

import org.xmlpull.v1.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public final class InvoiceParser {
    public static class Item { public String claveProdServ="",noIdentificacion="",unidad="",descripcion="",rawLine=""; public double cantidad=0,valorUnitario=0,importe=0,descuento=0,impuestos=0; }
    public static class Invoice {
        public long id,documentId; public String uuid="",serie="",folio="",fecha="",emisorRfc="",emisorNombre="",receptorRfc="",receptorNombre="",moneda="MXN",metodoPago="",formaPago=""; public double subtotal=0,impuestos=0,total=0; public final ArrayList<Item> items=new ArrayList<>();
        public String searchableText(){StringBuilder s=new StringBuilder();s.append(uuid).append(' ').append(serie).append(' ').append(folio).append(' ').append(fecha).append(' ').append(emisorRfc).append(' ').append(emisorNombre).append(' ').append(receptorRfc).append(' ').append(receptorNombre).append(' ').append(total);for(Item x:items)s.append(' ').append(x.claveProdServ).append(' ').append(x.noIdentificacion).append(' ').append(x.descripcion);return s.toString();}
    }
    private static String a(XmlPullParser p,String name){for(int i=0;i<p.getAttributeCount();i++)if(name.equalsIgnoreCase(p.getAttributeName(i)))return nz(p.getAttributeValue(i));return "";}
    private static String nz(String s){return s==null?"":s.trim();}
    private static double d(String s){try{return Double.parseDouble(nz(s).replace(",",""));}catch(Exception e){return 0;}}
    public static Invoice fromCfdiXml(File f)throws Exception{
        Invoice inv=new Invoice(); XmlPullParserFactory fac=XmlPullParserFactory.newInstance();fac.setNamespaceAware(false);XmlPullParser p=fac.newPullParser();try(InputStream in=new FileInputStream(f)){p.setInput(in,"UTF-8");Item current=null;int ev=p.getEventType();while(ev!=XmlPullParser.END_DOCUMENT){if(ev==XmlPullParser.START_TAG){String n=p.getName();String low=n==null?"":n.toLowerCase(Locale.ROOT);if(low.endsWith("comprobante")){inv.serie=a(p,"Serie");inv.folio=a(p,"Folio");inv.fecha=a(p,"Fecha");inv.moneda=a(p,"Moneda");inv.metodoPago=a(p,"MetodoPago");inv.formaPago=a(p,"FormaPago");inv.subtotal=d(a(p,"SubTotal"));inv.total=d(a(p,"Total"));}else if(low.endsWith("emisor")){inv.emisorRfc=a(p,"Rfc");inv.emisorNombre=a(p,"Nombre");}else if(low.endsWith("receptor")){inv.receptorRfc=a(p,"Rfc");inv.receptorNombre=a(p,"Nombre");}else if(low.endsWith("concepto")){current=new Item();current.claveProdServ=a(p,"ClaveProdServ");current.noIdentificacion=a(p,"NoIdentificacion");current.cantidad=d(a(p,"Cantidad"));current.unidad=a(p,"Unidad");if(current.unidad.isEmpty())current.unidad=a(p,"ClaveUnidad");current.descripcion=a(p,"Descripcion");current.valorUnitario=d(a(p,"ValorUnitario"));current.importe=d(a(p,"Importe"));current.descuento=d(a(p,"Descuento"));inv.items.add(current);}else if(low.endsWith("timbrefiscaldigital")){inv.uuid=a(p,"UUID");}else if(low.endsWith("impuestos") && current==null){double t=d(a(p,"TotalImpuestosTrasladados"));double r=d(a(p,"TotalImpuestosRetenidos"));inv.impuestos=t-r;}}ev=p.next();}}
        if(inv.uuid.isEmpty()&&inv.folio.isEmpty()&&inv.emisorRfc.isEmpty())throw new IllegalArgumentException("El XML no parece ser un CFDI válido");return inv;
    }
    public static Invoice fromOcr(String raw, DocumentClassifier.Result r){
        Invoice inv=new Invoice();String text=raw==null?"":raw;
        inv.uuid=field(r,"UUID / Folio fiscal");inv.emisorRfc=match(text,"(?i)(?:RFC\\s*(?:EMISOR)?|EMISOR\\s*RFC)\\s*[:#-]?\\s*([A-Z&Ñ]{3,4}\\d{6}[A-Z0-9]{3})");inv.receptorRfc=match(text,"(?i)(?:RFC\\s*RECEPTOR|RECEPTOR\\s*RFC)\\s*[:#-]?\\s*([A-Z&Ñ]{3,4}\\d{6}[A-Z0-9]{3})");
        ArrayList<String> rfcs=all(text,"\\b[A-Z&Ñ]{3,4}\\d{6}[A-Z0-9]{3}\\b",4);if(inv.emisorRfc.isEmpty()&&rfcs.size()>0)inv.emisorRfc=rfcs.get(0);if(inv.receptorRfc.isEmpty()&&rfcs.size()>1)inv.receptorRfc=rfcs.get(1);
        inv.serie=match(text,"(?i)SERIE\\s*[:#-]?\\s*([A-Z0-9-]{1,20})");inv.folio=match(text,"(?i)(?:FOLIO(?!\\s*FISCAL)|FACTURA)\\s*[:#-]?\\s*([A-Z0-9-]{1,30})");inv.fecha=match(text,"(?i)FECHA(?:\\s+DE\\s+EMISION)?\\s*[:#-]?\\s*([0-9T:/.-]{8,25})");
        inv.emisorNombre=afterLabel(text,new String[]{"EMISOR","RAZON SOCIAL EMISOR","NOMBRE EMISOR"});inv.receptorNombre=afterLabel(text,new String[]{"RECEPTOR","RAZON SOCIAL RECEPTOR","NOMBRE RECEPTOR"});
        inv.subtotal=moneyAfter(text,"SUBTOTAL");inv.total=moneyAfter(text,"TOTAL(?!\\s+IMPUESTOS)");inv.impuestos=moneyAfter(text,"(?:IVA|IMPUESTOS|TOTAL IMPUESTOS)");String m=match(text,"(?i)MONEDA\\s*[:#-]?\\s*([A-Z]{3})");if(!m.isEmpty())inv.moneda=m;
        inv.metodoPago=match(text,"(?i)METODO\\s+DE\\s+PAGO\\s*[:#-]?\\s*([^\\r\\n]{1,60})");inv.formaPago=match(text,"(?i)FORMA\\s+DE\\s+PAGO\\s*[:#-]?\\s*([^\\r\\n]{1,60})");parseItems(text,inv);return inv;
    }
    private static void parseItems(String text,Invoice inv){
        String[] lines=text.split("\\r?\\n");Pattern amounts=Pattern.compile("(?<!\\d)(\\d+(?:[.,]\\d{1,2})?)\\s+(?:([A-Z]{2,6})\\s+)?(.{4,100}?)\\s+\\$?([0-9,]+(?:\\.\\d{2})?)\\s+\\$?([0-9,]+(?:\\.\\d{2})?)$");
        for(String line:lines){String s=line.trim();if(s.length()<8)continue;Matcher m=amounts.matcher(s);if(m.find()){String desc=m.group(3).trim();String up=desc.toUpperCase(Locale.ROOT);if(up.contains("SUBTOTAL")||up.equals("TOTAL")||up.contains("IMPUEST"))continue;Item it=new Item();it.cantidad=d(m.group(1).replace(',','.'));it.unidad=nz(m.group(2));it.descripcion=desc;it.valorUnitario=d(m.group(4));it.importe=d(m.group(5));it.rawLine=s;inv.items.add(it);if(inv.items.size()>=80)break;}}
        if(inv.items.isEmpty()){Pattern p=Pattern.compile("(?i)^(\\d+(?:[.,]\\d+)?)\\s+(.{5,80}?)\\s+([0-9,]+\\.\\d{2})$");for(String line:lines){Matcher m=p.matcher(line.trim());if(m.find()){Item it=new Item();it.cantidad=d(m.group(1).replace(',','.'));it.descripcion=m.group(2).trim();it.importe=d(m.group(3));it.rawLine=line.trim();inv.items.add(it);if(inv.items.size()>=80)break;}}}
    }
    private static String field(DocumentClassifier.Result r,String k){String v=r.fields.get(k);return v==null?"":v;}
    private static String match(String s,String re){Matcher m=Pattern.compile(re).matcher(s);return m.find()?nz(m.group(1)):"";}
    private static ArrayList<String> all(String s,String re,int max){ArrayList<String>a=new ArrayList<>();Matcher m=Pattern.compile(re,Pattern.CASE_INSENSITIVE).matcher(s);while(m.find()&&a.size()<max)a.add(m.group());return a;}
    private static double moneyAfter(String s,String label){String v=match(s,"(?i)"+label+"\\s*[:$-]?\\s*\\$?\\s*([0-9,]+(?:\\.\\d{2})?)");return d(v);}
    private static String afterLabel(String text,String[] labels){String[] ls=text.split("\\r?\\n");for(int i=0;i<ls.length;i++){String u=ls[i].toUpperCase(Locale.ROOT);for(String l:labels)if(u.startsWith(l)){String v=ls[i].replaceFirst("(?i)^"+Pattern.quote(l)+"\\s*[:#-]?\\s*","").trim();if(v.length()>2)return v;if(i+1<ls.length)return ls[i+1].trim();}}return "";}
}

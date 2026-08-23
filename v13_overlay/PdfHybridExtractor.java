package com.syncro.expediente;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.util.*;
import java.util.regex.*;

public final class PdfHybridExtractor {
    public static final class Result {
        public String text="";
        public String mode="OCR mejorado";
        public int pages=0;
        public double quality=0;
    }

    public static Result extract(Context context, File file, TextRecognizer recognizer) throws Exception {
        Result nativeResult = extractNative(context, file);
        if (nativeResult.quality >= 0.58 || looksFiscal(nativeResult.text)) return nativeResult;
        Result ocr = extractOcr(file, recognizer);
        if (nativeResult.quality > ocr.quality + 0.18) return nativeResult;
        return ocr;
    }

    private static Result extractNative(Context context, File file) {
        Result r=new Result(); r.mode="Texto nativo del PDF";
        try {
            PDFBoxResourceLoader.init(context.getApplicationContext());
            try(PDDocument doc=PDDocument.load(file)) {
                r.pages=doc.getNumberOfPages();
                PDFTextStripper stripper=new PDFTextStripper();
                stripper.setSortByPosition(true);
                r.text=clean(stripper.getText(doc));
                r.quality=score(r.text);
            }
        } catch(Exception ignored) { r.text=""; r.quality=0; }
        return r;
    }

    private static Result extractOcr(File file, TextRecognizer recognizer) throws Exception {
        Result r=new Result(); r.mode="OCR mejorado de PDF escaneado";
        StringBuilder out=new StringBuilder();
        try(ParcelFileDescriptor p=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer=new PdfRenderer(p)) {
            int pages=Math.min(renderer.getPageCount(),12); r.pages=pages;
            for(int i=0;i<pages;i++) {
                PdfRenderer.Page pg=renderer.openPage(i);
                Bitmap base=render(pg); pg.close();
                String normal=recognize(recognizer, base);
                Bitmap enhanced=enhance(base);
                String strong=recognize(recognizer, enhanced);
                double sn=score(normal), ss=score(strong);
                String best=ss>sn?strong:normal;
                out.append("\n--- Página ").append(i+1).append(" ---\n").append(best);
                base.recycle(); enhanced.recycle();
            }
        }
        r.text=clean(out.toString()); r.quality=score(r.text); return r;
    }

    private static Bitmap render(PdfRenderer.Page pg) {
        int w=pg.getWidth(), h=pg.getHeight();
        double scale=Math.min(3.5, Math.min(3000.0/Math.max(1,w), 4200.0/Math.max(1,h)));
        scale=Math.max(2.2,scale);
        int bw=(int)Math.min(3000,Math.max(1500,w*scale));
        int bh=(int)Math.min(4200,Math.max(1900,h*scale));
        Bitmap bm=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bm); c.drawColor(Color.WHITE);
        pg.render(bm,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        return bm;
    }

    private static String recognize(TextRecognizer recognizer, Bitmap bm) throws Exception {
        Text t=Tasks.await(recognizer.process(InputImage.fromBitmap(bm,0)));
        StringBuilder s=new StringBuilder();
        ArrayList<Text.TextBlock> blocks=new ArrayList<>(t.getTextBlocks());
        blocks.sort((a,b)->{
            Rect ra=a.getBoundingBox(), rb=b.getBoundingBox();
            if(ra==null||rb==null)return 0;
            int row=Integer.compare(ra.top/35,rb.top/35);
            return row!=0?row:Integer.compare(ra.left,rb.left);
        });
        for(Text.TextBlock b:blocks) for(Text.Line line:b.getLines()) s.append(line.getText()).append('\n');
        return s.toString();
    }

    private static Bitmap enhance(Bitmap src) {
        Bitmap dst=Bitmap.createBitmap(src.getWidth(),src.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(dst);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        ColorMatrix gray=new ColorMatrix(); gray.setSaturation(0);
        ColorMatrix contrast=new ColorMatrix(new float[]{
            1.45f,0,0,0,-45,
            0,1.45f,0,0,-45,
            0,0,1.45f,0,-45,
            0,0,0,1,0});
        gray.postConcat(contrast); p.setColorFilter(new ColorMatrixColorFilter(gray));
        c.drawBitmap(src,0,0,p); return dst;
    }

    private static boolean looksFiscal(String s) {
        String u=s.toUpperCase(Locale.ROOT);
        return (u.contains("RFC") && (u.contains("TOTAL")||u.contains("SUBTOTAL"))) ||
               Pattern.compile("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}",Pattern.CASE_INSENSITIVE).matcher(s).find();
    }

    private static double score(String s) {
        if(s==null||s.trim().isEmpty()) return 0;
        String u=s.toUpperCase(Locale.ROOT); double x=0;
        int chars=s.replaceAll("\\s","").length(); x+=Math.min(.28,chars/5000.0);
        if(u.contains("RFC"))x+=.12; if(u.contains("TOTAL"))x+=.10; if(u.contains("SUBTOTAL"))x+=.08;
        if(u.contains("FOLIO"))x+=.07; if(u.contains("FECHA"))x+=.06; if(u.contains("EMISOR"))x+=.06; if(u.contains("RECEPTOR"))x+=.06;
        if(Pattern.compile("[A-Z&Ñ]{3,4}\\d{6}[A-Z0-9]{3}").matcher(u).find())x+=.10;
        if(Pattern.compile("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}",Pattern.CASE_INSENSITIVE).matcher(s).find())x+=.12;
        return Math.min(1,x);
    }

    private static String clean(String s){return s==null?"":s.replace('\u00A0',' ').replaceAll("[ \\t]+"," ").replaceAll("\\n{3,}","\\n\\n").trim();}
}

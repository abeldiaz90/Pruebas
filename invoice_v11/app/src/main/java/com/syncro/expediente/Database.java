package com.syncro.expediente;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public class Database extends SQLiteOpenHelper {
    public Database(Context c){super(c,"expedientes.db",null,2);}
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE documents(id INTEGER PRIMARY KEY AUTOINCREMENT, createdAt TEXT NOT NULL, type TEXT NOT NULL, confidence REAL NOT NULL, sourcePath TEXT, rawText TEXT NOT NULL, fieldsJson TEXT NOT NULL, tags TEXT, title TEXT)");
        db.execSQL("CREATE INDEX idx_documents_type ON documents(type)");
        db.execSQL("CREATE INDEX idx_documents_created ON documents(createdAt)");
        createInvoiceTables(db);
    }
    private void createInvoiceTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS invoices(id INTEGER PRIMARY KEY AUTOINCREMENT, documentId INTEGER NOT NULL UNIQUE, uuid TEXT, serie TEXT, folio TEXT, fecha TEXT, emisorRfc TEXT, emisorNombre TEXT, receptorRfc TEXT, receptorNombre TEXT, subtotal REAL, impuestos REAL, total REAL, moneda TEXT, metodoPago TEXT, formaPago TEXT, searchableText TEXT, FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS invoice_items(id INTEGER PRIMARY KEY AUTOINCREMENT, invoiceId INTEGER NOT NULL, lineNo INTEGER NOT NULL, claveProdServ TEXT, noIdentificacion TEXT, cantidad REAL, unidad TEXT, descripcion TEXT, valorUnitario REAL, importe REAL, descuento REAL, impuestos REAL, rawLine TEXT, FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_invoice_uuid ON invoices(uuid)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_invoice_emisor_rfc ON invoices(emisorRfc)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_invoice_receptor_rfc ON invoices(receptorRfc)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_invoice_folio ON invoices(folio)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_invoice_date ON invoices(fecha)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_item_desc ON invoice_items(descripcion)");
    }
    public void onUpgrade(SQLiteDatabase db,int o,int n){if(o<2)createInvoiceTables(db);}
    public long insert(String type,double confidence,String sourcePath,String rawText,String fieldsJson,String tags,String title){
        ContentValues v=new ContentValues();
        v.put("createdAt",new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date()));
        v.put("type",type); v.put("confidence",confidence); v.put("sourcePath",sourcePath); v.put("rawText",rawText); v.put("fieldsJson",fieldsJson); v.put("tags",tags); v.put("title",title);
        return getWritableDatabase().insert("documents",null,v);
    }
    public long insertInvoice(long documentId, InvoiceParser.Invoice inv){
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try{
            ContentValues v=new ContentValues();v.put("documentId",documentId);v.put("uuid",inv.uuid);v.put("serie",inv.serie);v.put("folio",inv.folio);v.put("fecha",inv.fecha);v.put("emisorRfc",inv.emisorRfc);v.put("emisorNombre",inv.emisorNombre);v.put("receptorRfc",inv.receptorRfc);v.put("receptorNombre",inv.receptorNombre);v.put("subtotal",inv.subtotal);v.put("impuestos",inv.impuestos);v.put("total",inv.total);v.put("moneda",inv.moneda);v.put("metodoPago",inv.metodoPago);v.put("formaPago",inv.formaPago);v.put("searchableText",inv.searchableText());
            long invoiceId=db.insertOrThrow("invoices",null,v);
            int line=1; for(InvoiceParser.Item it:inv.items){ContentValues x=new ContentValues();x.put("invoiceId",invoiceId);x.put("lineNo",line++);x.put("claveProdServ",it.claveProdServ);x.put("noIdentificacion",it.noIdentificacion);x.put("cantidad",it.cantidad);x.put("unidad",it.unidad);x.put("descripcion",it.descripcion);x.put("valorUnitario",it.valorUnitario);x.put("importe",it.importe);x.put("descuento",it.descuento);x.put("impuestos",it.impuestos);x.put("rawLine",it.rawLine);db.insertOrThrow("invoice_items",null,x);}
            db.setTransactionSuccessful(); return invoiceId;
        } finally {db.endTransaction();}
    }
    public Cursor search(String q){
        String query=q==null?"":q.trim(), like="%"+query+"%";
        return getReadableDatabase().rawQuery("SELECT id,createdAt,type,confidence,title,fieldsJson,tags FROM documents WHERE ?='' OR type LIKE ? OR rawText LIKE ? OR fieldsJson LIKE ? OR title LIKE ? ORDER BY id DESC LIMIT 200",new String[]{query,like,like,like,like});
    }
    public Cursor searchInvoices(String q){
        String query=q==null?"":q.trim(), like="%"+query+"%";
        return getReadableDatabase().rawQuery("SELECT DISTINCT i.id,i.documentId,i.fecha,i.uuid,i.serie,i.folio,i.emisorRfc,i.emisorNombre,i.receptorRfc,i.receptorNombre,i.subtotal,i.impuestos,i.total,i.moneda FROM invoices i LEFT JOIN invoice_items x ON x.invoiceId=i.id WHERE ?='' OR i.searchableText LIKE ? OR x.descripcion LIKE ? OR x.claveProdServ LIKE ? OR x.noIdentificacion LIKE ? ORDER BY i.id DESC LIMIT 300",new String[]{query,like,like,like,like});
    }
    public JSONObject get(long id)throws JSONException{
        Cursor c=getReadableDatabase().rawQuery("SELECT id,createdAt,type,confidence,sourcePath,rawText,fieldsJson,tags,title FROM documents WHERE id=?",new String[]{String.valueOf(id)});
        JSONObject o=new JSONObject(); if(c.moveToFirst()){o.put("id",c.getLong(0));o.put("createdAt",c.getString(1));o.put("type",c.getString(2));o.put("confidence",c.getDouble(3));o.put("sourcePath",c.getString(4));o.put("rawText",c.getString(5));o.put("fields",new JSONObject(c.getString(6)));o.put("tags",c.getString(7));o.put("title",c.getString(8));} c.close(); return o;
    }
    public InvoiceParser.Invoice getInvoice(long invoiceId){
        InvoiceParser.Invoice inv=new InvoiceParser.Invoice(); Cursor c=getReadableDatabase().rawQuery("SELECT documentId,uuid,serie,folio,fecha,emisorRfc,emisorNombre,receptorRfc,receptorNombre,subtotal,impuestos,total,moneda,metodoPago,formaPago FROM invoices WHERE id=?",new String[]{String.valueOf(invoiceId)});
        if(c.moveToFirst()){inv.id=invoiceId;inv.documentId=c.getLong(0);inv.uuid=c.getString(1);inv.serie=c.getString(2);inv.folio=c.getString(3);inv.fecha=c.getString(4);inv.emisorRfc=c.getString(5);inv.emisorNombre=c.getString(6);inv.receptorRfc=c.getString(7);inv.receptorNombre=c.getString(8);inv.subtotal=c.getDouble(9);inv.impuestos=c.getDouble(10);inv.total=c.getDouble(11);inv.moneda=c.getString(12);inv.metodoPago=c.getString(13);inv.formaPago=c.getString(14);} c.close();
        Cursor x=getReadableDatabase().rawQuery("SELECT claveProdServ,noIdentificacion,cantidad,unidad,descripcion,valorUnitario,importe,descuento,impuestos,rawLine FROM invoice_items WHERE invoiceId=? ORDER BY lineNo",new String[]{String.valueOf(invoiceId)});
        while(x.moveToNext()){InvoiceParser.Item it=new InvoiceParser.Item();it.claveProdServ=x.getString(0);it.noIdentificacion=x.getString(1);it.cantidad=x.getDouble(2);it.unidad=x.getString(3);it.descripcion=x.getString(4);it.valorUnitario=x.getDouble(5);it.importe=x.getDouble(6);it.descuento=x.getDouble(7);it.impuestos=x.getDouble(8);it.rawLine=x.getString(9);inv.items.add(it);}x.close(); return inv;
    }
    public JSONArray all()throws JSONException{JSONArray a=new JSONArray(); Cursor c=getReadableDatabase().rawQuery("SELECT id FROM documents ORDER BY id DESC",null); while(c.moveToNext())a.put(get(c.getLong(0)));c.close();return a;}
}

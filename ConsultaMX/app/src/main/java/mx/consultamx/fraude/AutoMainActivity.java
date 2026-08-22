package mx.consultamx.fraude;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class AutoMainActivity extends MainActivity {
    static final int REQ_CURP_RPA=2301, REQ_SEP_RPA=2402, REQ_EXPORT_JSON=2404;
    String pendingCurp="", pendingRfc="", pendingExportJson="", pendingExportFilename="consulta.json";
    long pendingQueryId=-1;

    @Override void showHome(){
        super.showHome();
        LinearLayout c=card();
        c.addView(tv("Base de verificaciones",18,NAVY,true));
        c.addView(tv("Consulta todas las respuestas oficiales almacenadas y exporta cada evidencia en JSON.",13,MUTED,false));
        Button records=btn("Ver registros oficiales guardados");records.setBackground(bg(BLUE,14));add(c,records,50);
        body.addView(c);
        records.setOnClickListener(v->showAllResponseRecords());
    }

    @Override void showPersonQuery(){
        base("Consulta de persona",true);
        LinearLayout c=card();
        c.addView(tv("CURP",18,NAVY,true));
        c.addView(tv("La consulta cuesta $1. Primero revisamos la base local; si no existe, el RPA consulta RENAPO automáticamente.",13,MUTED,false));
        EditText curp=input("18 caracteres"); curp.setAllCaps(true);
        EditText rfc=input("RFC opcional para listas SAT"); rfc.setAllCaps(true);
        add(c,curp,56); add(c,rfc,56);
        Button go=btn("Consultar por $1.00"); add(c,go,54);
        Button back=btn("Volver"); back.setBackground(bg(NAVY,14)); add(c,back,48);
        body.addView(c);
        back.setOnClickListener(v->showHome());
        go.setOnClickListener(v->{
            String x=curp.getText().toString().trim().toUpperCase(Locale.ROOT);
            String rr=rfc.getText().toString().trim().toUpperCase(Locale.ROOT);
            if(!validCurp(x)){toast("CURP inválida: deben ser 18 caracteres con estructura válida");return;}
            if(db.balance(user)<1){toast("Saldo insuficiente");showWallet();return;}
            Cursor cached=db.getCurp(x);
            boolean hit=cached.moveToFirst();
            String status=hit?cached.getString(1):"SIN_VERIFICAR";
            String checked=hit?cached.getString(2):"";
            cached.close();
            long q=db.chargePersonQuery(user,x,rr,hit?"CACHE":"RPA_RENAPO_AUTOMATICO");
            if(q<0){toast("No se pudo cobrar la consulta");return;}
            pendingCurp=x; pendingRfc=rr; pendingQueryId=q;
            if(hit){showPersonResult(x,rr,status,checked,true,q);return;}
            launchCurpRpa(x);
        });
    }

    void launchCurpRpa(String curp){
        base("Verificando CURP…",true);
        LinearLayout c=card();
        c.addView(tv("RPA RENAPO EN PROCESO",20,TEAL,true));
        c.addView(tv("Abriendo la fuente oficial, capturando la CURP, enviando el formulario y almacenando la respuesta completa como JSON.",14,NAVY,false));
        c.addView(tv("Folio Q-"+pendingQueryId+" · cargo $1.00 ya aplicado",12,MUTED,false));
        body.addView(c);
        Intent i=new Intent(this,HandsFreeCurpRpaActivity.class);
        i.putExtra("url",RENAPO);i.putExtra("curp",curp);
        startActivityForResult(i,REQ_CURP_RPA);
    }

    void launchSepRpa(String curp){Intent i=new Intent(this,SepCedulaRpaActivity.class);i.putExtra("curp",curp);startActivityForResult(i,REQ_SEP_RPA);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_EXPORT_JSON){if(resultCode==RESULT_OK && data!=null && data.getData()!=null)writeJson(data.getData());return;}
        if(requestCode==REQ_SEP_RPA){if(resultCode==RESULT_OK)toast("Respuesta SEP almacenada como registro JSON");if(pendingCurp.length()==18)showPersonResult(pendingCurp,pendingRfc,"VERIFICADA_RENAPO_RPA","",true,pendingQueryId);return;}
        if(requestCode!=REQ_CURP_RPA)return;
        Cursor c=db.getCurp(pendingCurp);boolean hit=c.moveToFirst();String status=hit?c.getString(1):"RPA_NO_COMPLETADO";String checked=hit?c.getString(2):"";c.close();
        if(hit)showPersonResult(pendingCurp,pendingRfc,status,checked,true,pendingQueryId);else showRpaFailure();
    }

    void showRpaFailure(){
        base("Verificación pendiente",true);LinearLayout c=card();c.addView(tv("EL RPA NO OBTUVO RESULTADO",19,AMBER,true));
        c.addView(tv("No se guardó la CURP como verificada. Puede ocurrir si RENAPO cambió su página, hubo problema de red o cerraste el navegador antes de terminar.",14,NAVY,false));
        c.addView(tv("No se realizará un segundo cobro al reintentar este folio.",13,GREEN,true));Button retry=btn("Reintentar RPA sin volver a cobrar");add(c,retry,52);Button home=btn("Volver al inicio");home.setBackground(bg(NAVY,14));add(c,home,48);body.addView(c);retry.setOnClickListener(v->launchCurpRpa(pendingCurp));home.setOnClickListener(v->showHome());
    }

    @Override void showPersonResult(String curp,String rfc,String status,String checked,boolean cacheHit,long qid){
        pendingCurp=curp;pendingRfc=rfc;pendingQueryId=qid;base("Resultado CURP",true);
        LinearLayout c=card();c.addView(tv("VERIFICACIÓN DISPONIBLE",19,GREEN,true));c.addView(tv("CURP: "+mask(curp),14,NAVY,true));c.addView(tv("Estado: "+status,13,NAVY,false));if(checked!=null&&!checked.isEmpty())c.addView(tv("Última verificación: "+checked,13,MUTED,false));c.addView(tv("Origen: "+(status.contains("RENAPO")?"RENAPO / RPA":"caché local"),13,MUTED,false));c.addView(tv("Folio Q-"+qid+" · cargo $1.00",12,MUTED,false));body.addView(c);

        SQLiteDatabase r=db.getReadableDatabase();Cursor d=null;try{d=r.rawQuery("SELECT names,first_surname,second_surname,birth_date,sex,state,nationality,source_url FROM curp_details WHERE curp=?",new String[]{curp});if(d.moveToFirst()){LinearLayout info=card();info.addView(tv("Datos extraídos de RENAPO",18,NAVY,true));addField(info,"Nombre(s)",d.getString(0));addField(info,"Primer apellido",d.getString(1));addField(info,"Segundo apellido",d.getString(2));addField(info,"Fecha de nacimiento",d.getString(3));addField(info,"Sexo",d.getString(4));addField(info,"Entidad de nacimiento",d.getString(5));addField(info,"Nacionalidad",d.getString(6));addField(info,"Fuente",d.getString(7));body.addView(info);}}catch(Exception ignored){}finally{if(d!=null)d.close();}

        LinearLayout evidence=card();evidence.addView(tv("Evidencia y datos completos",18,NAVY,true));int count=countRecords(curp);evidence.addView(tv(count+" respuesta(s) oficial(es) almacenada(s). Cada ejecución se conserva como registro independiente.",13,MUTED,false));Button latest=btn("Descargar último JSON RENAPO");add(evidence,latest,50);Button records=btn("Ver todos los registros de esta CURP");records.setBackground(bg(Color.rgb(51,65,85),14));add(evidence,records,50);body.addView(evidence);latest.setOnClickListener(v->exportLatest(curp,"RENAPO_CURP"));records.setOnClickListener(v->showRecordsForCurp(curp));

        LinearLayout s=card();s.addView(tv("Más verificaciones oficiales",18,NAVY,true));Button ced=btn("RPA cédula profesional SEP");ced.setBackground(bg(BLUE,14));add(s,ced,52);if(rfc!=null&&rfc.length()>=12){Button sat=btn("Consultar SAT 69 / 69-B");sat.setBackground(bg(AMBER,14));add(s,sat,52);sat.setOnClickListener(v->openOfficial(SAT69,""));}Button rsps=btn("Consultar servidores públicos sancionados");rsps.setBackground(bg(Color.rgb(127,29,29),14));add(s,rsps,52);Button home=btn("Inicio");home.setBackground(bg(NAVY,14));add(s,home,48);body.addView(s);ced.setOnClickListener(v->launchSepRpa(curp));rsps.setOnClickListener(v->openOfficial(RSPS,""));home.setOnClickListener(v->showHome());
    }

    int countRecords(String curp){SQLiteDatabase r=db.getReadableDatabase();ensureRecordsTable(r);Cursor c=r.rawQuery("SELECT COUNT(*) FROM source_response_records WHERE subject_type='CURP' AND subject_value=?",new String[]{curp});int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    void showRecordsForCurp(String curp){base("Registros de "+mask(curp),true);renderRecords("WHERE subject_type='CURP' AND subject_value=?",new String[]{curp});}
    void showAllResponseRecords(){base("Respuestas oficiales guardadas",true);renderRecords("",new String[]{});}

    void renderRecords(String where,String[] args){SQLiteDatabase r=db.getReadableDatabase();ensureRecordsTable(r);Cursor c=r.rawQuery("SELECT id,subject_value,source,status,source_url,captured_at,response_json FROM source_response_records "+where+" ORDER BY id DESC LIMIT 200",args);if(!c.moveToFirst()){LinearLayout empty=card();empty.addView(tv("Aún no hay respuestas oficiales almacenadas.",14,MUTED,false));body.addView(empty);}else do{long id=c.getLong(0);String subject=c.getString(1),source=c.getString(2),st=c.getString(3),url=c.getString(4),ts=c.getString(5),json=c.getString(6);LinearLayout x=card();x.addView(tv("#"+id+" · "+source,16,NAVY,true));x.addView(tv(mask(subject)+" · "+ts,13,MUTED,false));x.addView(tv(st==null?"":st,13,NAVY,false));Button view=btn("Ver JSON completo");view.setBackground(bg(Color.rgb(51,65,85),14));Button dl=btn("Descargar JSON");add(x,view,46);add(x,dl,46);body.addView(x);view.setOnClickListener(v->showJsonRecord(id,subject,source,ts,json,url));dl.setOnClickListener(v->exportJson(json,source+"-"+subject+"-"+id+".json"));}while(c.moveToNext());c.close();Button b=btn("Inicio");b.setBackground(bg(NAVY,14));add(body,b,48);b.setOnClickListener(v->showHome());}

    void showJsonRecord(long id,String subject,String source,String ts,String json,String url){base("Registro #"+id,true);LinearLayout c=card();c.addView(tv(source,18,NAVY,true));c.addView(tv(mask(subject)+" · "+ts,13,MUTED,false));if(url!=null)c.addView(tv("Fuente: "+url,12,MUTED,false));TextView raw=tv(prettyJson(json),12,NAVY,false);raw.setTextIsSelectable(true);c.addView(raw);Button dl=btn("Descargar este JSON");add(c,dl,50);Button back=btn("Volver a registros");back.setBackground(bg(NAVY,14));add(c,back,48);body.addView(c);dl.setOnClickListener(v->exportJson(json,source+"-"+subject+"-"+id+".json"));back.setOnClickListener(v->showAllResponseRecords());}

    void exportLatest(String curp,String source){SQLiteDatabase r=db.getReadableDatabase();ensureRecordsTable(r);Cursor c=r.rawQuery("SELECT id,response_json FROM source_response_records WHERE subject_value=? AND source=? ORDER BY id DESC LIMIT 1",new String[]{curp,source});if(c.moveToFirst()){long id=c.getLong(0);String json=c.getString(1);c.close();exportJson(json,source+"-"+curp+"-"+id+".json");}else{c.close();toast("No hay JSON de esa fuente todavía");}}
    void exportJson(String json,String filename){pendingExportJson=json==null?"{}":json;pendingExportFilename=filename.replaceAll("[^A-Za-z0-9._-]","_");Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,pendingExportFilename);startActivityForResult(i,REQ_EXPORT_JSON);}
    void writeJson(Uri uri){try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new Exception("sin stream");out.write(pendingExportJson.getBytes(StandardCharsets.UTF_8));out.flush();toast("JSON guardado correctamente");}catch(Exception e){toast("No se pudo guardar JSON: "+e.getMessage());}}
    String prettyJson(String json){try{return new org.json.JSONObject(json).toString(2);}catch(Exception e){try{return new org.json.JSONArray(json).toString(2);}catch(Exception x){return json==null?"":json;}}}
    void ensureRecordsTable(SQLiteDatabase r){r.execSQL("CREATE TABLE IF NOT EXISTS source_response_records(id INTEGER PRIMARY KEY AUTOINCREMENT,subject_type TEXT NOT NULL,subject_value TEXT NOT NULL,source TEXT NOT NULL,status TEXT,response_json TEXT NOT NULL,raw_text TEXT,source_url TEXT,captured_at TEXT NOT NULL)");}
    void addField(LinearLayout p,String label,String value){if(value==null||value.trim().isEmpty())return;TextView t=tv(label+": "+value.trim(),14,NAVY,false);t.setPadding(0,dp(5),0,dp(5));p.addView(t);}
}

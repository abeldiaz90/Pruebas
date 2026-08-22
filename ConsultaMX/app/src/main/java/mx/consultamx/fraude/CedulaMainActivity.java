package mx.consultamx.fraude;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.widget.Button;
import android.widget.LinearLayout;

public class CedulaMainActivity extends AutoMainActivity {
    boolean sepAttempted=false;

    @Override void launchSepRpa(String curp){
        Intent i=new Intent(this,SepCedulaRpaV2Activity.class);
        i.putExtra("curp",curp);
        startActivityForResult(i,REQ_SEP_RPA);
    }

    boolean hasSepRecord(String curp){
        SQLiteDatabase r=db.getReadableDatabase();
        ensureRecordsTable(r);
        Cursor c=r.rawQuery("SELECT COUNT(*) FROM source_response_records WHERE subject_type='CURP' AND subject_value=? AND source='SEP_CEDULA_PROFESIONAL'",new String[]{curp});
        boolean ok=c.moveToFirst() && c.getInt(0)>0;
        c.close();
        return ok;
    }

    @Override void showPersonResult(String curp,String rfc,String status,String checked,boolean cacheHit,long qid){
        pendingCurp=curp; pendingRfc=rfc; pendingQueryId=qid;
        if(!hasSepRecord(curp) && !sepAttempted){
            sepAttempted=true;
            base("Consultando cédulas…",true);
            LinearLayout wait=card();
            wait.addView(tv("CÉDULAS PROFESIONALES SEP",20,BLUE,true));
            wait.addView(tv("ConsultaMX está buscando automáticamente en el sistema oficial SIURP de la SEP usando la CURP.",14,NAVY,false));
            body.addView(wait);
            launchSepRpa(curp);
            return;
        }
        super.showPersonResult(curp,rfc,status,checked,cacheHit,qid);
        renderSepSummary(curp);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode==REQ_SEP_RPA) sepAttempted=true;
        super.onActivityResult(requestCode,resultCode,data);
    }

    void renderSepSummary(String curp){
        SQLiteDatabase r=db.getReadableDatabase(); ensureRecordsTable(r);
        Cursor c=r.rawQuery("SELECT id,status,response_json,captured_at FROM source_response_records WHERE subject_type='CURP' AND subject_value=? AND source='SEP_CEDULA_PROFESIONAL' ORDER BY id DESC LIMIT 1",new String[]{curp});
        LinearLayout box=card();
        box.addView(tv("Cédulas profesionales SEP",19,BLUE,true));
        if(c.moveToFirst()){
            long id=c.getLong(0); String st=c.getString(1); String json=c.getString(2); String ts=c.getString(3);
            box.addView(tv("Consulta oficial realizada: "+ts,13,MUTED,false));
            box.addView(tv("Estado: "+(st==null?"RESPUESTA_CAPTURADA":st),13,NAVY,false));
            String lower=(json==null?"":json).toLowerCase();
            boolean none=lower.contains("no se encontraron")||lower.contains("sin resultados")||lower.contains("no existe")||lower.contains("no encontrado");
            box.addView(tv(none?"Sin coincidencias en la respuesta SEP.":"Respuesta SEP almacenada. El JSON contiene todos los datos devueltos.",14,none?AMBER:GREEN,true));
            Button see=btn("Ver JSON de cédulas SEP"); add(box,see,48);
            Button dl=btn("Descargar JSON de cédulas SEP");dl.setBackground(bg(Color.rgb(51,65,85),14));add(box,dl,48);
            see.setOnClickListener(v->showJsonRecord(id,curp,"SEP_CEDULA_PROFESIONAL",ts,json,"https://siurp.sep.gob.mx/mvc/cedulaElectronica"));
            dl.setOnClickListener(v->exportJson(json,"SEP-CEDULAS-"+curp+"-"+id+".json"));
        }else{
            box.addView(tv("La SEP no devolvió un registro utilizable en esta ejecución.",14,AMBER,true));
            Button retry=btn("Reintentar cédulas SEP sin cobrar");add(box,retry,48);retry.setOnClickListener(v->launchSepRpa(curp));
        }
        c.close(); body.addView(box);
    }
}

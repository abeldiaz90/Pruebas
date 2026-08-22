package mx.consultamx.fraude;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

public class CedulaMainActivity extends AutoMainActivity {
    static final int REQ_SEP_FLEX=2601;
    boolean sepAttempted=false;

    @Override void showHome(){
        super.showHome();
        LinearLayout c=card();
        c.addView(tv("Cédulas profesionales SEP",19,BLUE,true));
        c.addView(tv("Busca directamente por CURP, por datos personales o valida usando el número de cédula.",13,MUTED,false));
        Button b=btn("Abrir buscador de cédulas");b.setBackground(bg(BLUE,14));add(c,b,52);body.addView(c);b.setOnClickListener(v->showCedulaSearch());
    }

    void showCedulaSearch(){
        base("Cédulas profesionales",true);
        LinearLayout a=card();a.addView(tv("Buscar por CURP",18,NAVY,true));EditText curp=input("CURP de 18 caracteres");curp.setAllCaps(true);add(a,curp,54);Button bc=btn("Consultar SEP por CURP");add(a,bc,50);body.addView(a);
        LinearLayout p=card();p.addView(tv("Buscar por datos personales",18,NAVY,true));EditText n=input("Nombre(s)");EditText ap=input("Primer apellido");EditText am=input("Segundo apellido (opcional)");EditText fn=input("Fecha de nacimiento (dd/mm/aaaa)");EditText sx=input("Sexo: Hombre / Mujer");EditText edo=input("Estado de nacimiento");add(p,n,50);add(p,ap,50);add(p,am,50);add(p,fn,50);add(p,sx,50);add(p,edo,50);Button bp=btn("Consultar SEP por datos personales");bp.setBackground(bg(Color.rgb(51,65,85),14));add(p,bp,50);body.addView(p);
        LinearLayout num=card();num.addView(tv("Validar por número de cédula",18,NAVY,true));EditText ce=input("Número de cédula profesional");add(num,ce,52);Button bn=btn("Consultar número de cédula");bn.setBackground(bg(GREEN,14));add(num,bn,50);body.addView(num);
        Button back=btn("Volver");back.setBackground(bg(NAVY,14));add(body,back,48);back.setOnClickListener(v->showHome());
        bc.setOnClickListener(v->{String x=curp.getText().toString().trim().toUpperCase();if(!validCurp(x)){toast("CURP inválida");return;}Intent i=new Intent(this,SepCedulaFallbackRpaActivity.class);i.putExtra("mode","CURP");i.putExtra("curp",x);startActivityForResult(i,REQ_SEP_FLEX);});
        bp.setOnClickListener(v->{if(n.getText().toString().trim().isEmpty()||ap.getText().toString().trim().isEmpty()||fn.getText().toString().trim().isEmpty()||sx.getText().toString().trim().isEmpty()||edo.getText().toString().trim().isEmpty()){toast("Completa nombre, primer apellido, fecha, sexo y estado");return;}Intent i=new Intent(this,SepCedulaFlexibleRpaActivity.class);i.putExtra("mode","PERSONAL");i.putExtra("names",n.getText().toString().trim());i.putExtra("first",ap.getText().toString().trim());i.putExtra("second",am.getText().toString().trim());i.putExtra("birth",fn.getText().toString().trim());i.putExtra("sex",sx.getText().toString().trim());i.putExtra("state",edo.getText().toString().trim());startActivityForResult(i,REQ_SEP_FLEX);});
        bn.setOnClickListener(v->{String x=ce.getText().toString().trim();if(x.isEmpty()){toast("Captura el número de cédula");return;}Intent i=new Intent(this,SepCedulaFlexibleRpaActivity.class);i.putExtra("mode","CEDULA");i.putExtra("cedula",x);startActivityForResult(i,REQ_SEP_FLEX);});
    }

    @Override void launchSepRpa(String curp){Intent i=new Intent(this,SepCedulaFallbackRpaActivity.class);i.putExtra("mode","CURP");i.putExtra("curp",curp);startActivityForResult(i,REQ_SEP_RPA);}

    boolean hasSepRecord(String curp){SQLiteDatabase r=db.getReadableDatabase();ensureRecordsTable(r);Cursor c=r.rawQuery("SELECT COUNT(*) FROM source_response_records WHERE subject_type='CURP' AND subject_value=? AND source='SEP_CEDULA_PROFESIONAL' AND status IN ('RESULTADO_REAL','SIN_COINCIDENCIAS')",new String[]{curp});boolean ok=c.moveToFirst()&&c.getInt(0)>0;c.close();return ok;}

    @Override void showPersonResult(String curp,String rfc,String status,String checked,boolean cacheHit,long qid){pendingCurp=curp;pendingRfc=rfc;pendingQueryId=qid;if(!hasSepRecord(curp)&&!sepAttempted){sepAttempted=true;base("Consultando cédulas…",true);LinearLayout wait=card();wait.addView(tv("CÉDULAS PROFESIONALES SEP",20,BLUE,true));wait.addView(tv("ConsultaMX intenta primero por CURP. Si SEP no responde, reintenta automáticamente con los datos personales que RENAPO ya devolvió.",14,NAVY,false));body.addView(wait);launchSepRpa(curp);return;}super.showPersonResult(curp,rfc,status,checked,cacheHit,qid);renderSepSummary(curp);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){if(requestCode==REQ_SEP_FLEX){toast(resultCode==RESULT_OK?"Consulta SEP guardada":"SEP no devolvió un resultado verificable");showAllResponseRecords();return;}if(requestCode==REQ_SEP_RPA)sepAttempted=true;super.onActivityResult(requestCode,resultCode,data);}

    void renderSepSummary(String curp){SQLiteDatabase r=db.getReadableDatabase();ensureRecordsTable(r);Cursor c=r.rawQuery("SELECT id,status,response_json,captured_at FROM source_response_records WHERE subject_type='CURP' AND subject_value=? AND source='SEP_CEDULA_PROFESIONAL' AND status IN ('RESULTADO_REAL','SIN_COINCIDENCIAS') ORDER BY id DESC LIMIT 1",new String[]{curp});LinearLayout box=card();box.addView(tv("Cédulas profesionales SEP",19,BLUE,true));if(c.moveToFirst()){long id=c.getLong(0);String st=c.getString(1);String json=c.getString(2);String ts=c.getString(3);box.addView(tv("Consulta oficial: "+ts,13,MUTED,false));box.addView(tv(st.equals("SIN_COINCIDENCIAS")?"SEP no encontró coincidencias.":"SEP devolvió un resultado real.",14,st.equals("SIN_COINCIDENCIAS")?AMBER:GREEN,true));Button see=btn("Ver JSON SEP");add(box,see,48);Button dl=btn("Descargar JSON SEP");dl.setBackground(bg(Color.rgb(51,65,85),14));add(box,dl,48);see.setOnClickListener(v->showJsonRecord(id,curp,"SEP_CEDULA_PROFESIONAL",ts,json,"https://siurp.sep.gob.mx/mvc/cedulaElectronica"));dl.setOnClickListener(v->exportJson(json,"SEP-CEDULAS-"+curp+"-"+id+".json"));}else{box.addView(tv("Todavía no hay un resultado SEP válido para esta CURP.",14,AMBER,true));Button retry=btn("Reintentar cédulas SEP");add(box,retry,48);retry.setOnClickListener(v->launchSepRpa(curp));}c.close();body.addView(box);}
}

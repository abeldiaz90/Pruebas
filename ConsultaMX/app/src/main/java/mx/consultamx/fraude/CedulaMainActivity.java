package mx.consultamx.fraude;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.Locale;

public class CedulaMainActivity extends AutoMainActivity {
    static final int REQ_SEP_FLEX=2601;
    static final int REQ_CEDULA_RENAPO=2701;
    boolean sepAttempted=false;
    String pendingCedulaCurp="";

    @Override void showHome(){
        super.showHome();
        LinearLayout c=card();
        c.addView(tv("Cédulas profesionales SEP",19,BLUE,true));
        c.addView(tv("Captura una CURP. ConsultaMX resuelve la identidad con RENAPO y usa esos datos para buscar cédulas profesionales en SEP.",13,MUTED,false));
        Button b=btn("Buscar cédulas por CURP");b.setBackground(bg(BLUE,14));add(c,b,52);body.addView(c);b.setOnClickListener(v->showCedulaSearch());
    }

    void showCedulaSearch(){
        base("Cédulas profesionales",true);
        LinearLayout a=card();
        a.addView(tv("Buscar por CURP",20,NAVY,true));
        a.addView(tv("Sólo captura la CURP. Si la identidad no está en la base local, primero se consulta RENAPO; después la búsqueda SEP se ejecuta automáticamente con los datos oficiales obtenidos.",13,MUTED,false));
        EditText curp=input("CURP de 18 caracteres");curp.setAllCaps(true);add(a,curp,54);
        Button bc=btn("Buscar cédulas por CURP");add(a,bc,52);body.addView(a);

        LinearLayout p=card();p.addView(tv("Búsqueda alternativa por datos personales",18,NAVY,true));
        EditText n=input("Nombre(s)");EditText ap=input("Primer apellido");EditText am=input("Segundo apellido (opcional)");EditText fn=input("Fecha de nacimiento (dd/mm/aaaa)");EditText sx=input("Sexo: Hombre / Mujer");EditText edo=input("Estado de nacimiento");
        add(p,n,50);add(p,ap,50);add(p,am,50);add(p,fn,50);add(p,sx,50);add(p,edo,50);
        Button bp=btn("Consultar SEP por datos personales");bp.setBackground(bg(Color.rgb(51,65,85),14));add(p,bp,50);body.addView(p);

        LinearLayout num=card();num.addView(tv("Validar por número de cédula",18,NAVY,true));EditText ce=input("Número de cédula profesional");add(num,ce,52);Button bn=btn("Consultar número de cédula");bn.setBackground(bg(GREEN,14));add(num,bn,50);body.addView(num);
        Button back=btn("Volver");back.setBackground(bg(NAVY,14));add(body,back,48);back.setOnClickListener(v->showHome());

        bc.setOnClickListener(v->{String x=curp.getText().toString().trim().toUpperCase(Locale.ROOT);if(!validCurp(x)){toast("CURP inválida");return;}startCedulaLookupByCurp(x,REQ_SEP_FLEX);});
        bp.setOnClickListener(v->{if(n.getText().toString().trim().isEmpty()||ap.getText().toString().trim().isEmpty()||fn.getText().toString().trim().isEmpty()||sx.getText().toString().trim().isEmpty()||edo.getText().toString().trim().isEmpty()){toast("Completa nombre, primer apellido, fecha, sexo y estado");return;}Intent i=new Intent(this,SepCedulaFlexibleRpaActivity.class);i.putExtra("mode","PERSONAL");i.putExtra("names",n.getText().toString().trim());i.putExtra("first",ap.getText().toString().trim());i.putExtra("second",am.getText().toString().trim());i.putExtra("birth",fn.getText().toString().trim());i.putExtra("sex",sx.getText().toString().trim());i.putExtra("state",edo.getText().toString().trim());startActivityForResult(i,REQ_SEP_FLEX);});
        bn.setOnClickListener(v->{String x=ce.getText().toString().trim();if(x.isEmpty()){toast("Captura el número de cédula");return;}Intent i=new Intent(this,SepCedulaFlexibleRpaActivity.class);i.putExtra("mode","CEDULA");i.putExtra("cedula",x);startActivityForResult(i,REQ_SEP_FLEX);});
    }

    void startCedulaLookupByCurp(String curp,int requestCode){
        pendingCedulaCurp=curp;
        String[] d=loadCurpIdentity(curp);
        if(d!=null){launchSepWithResolvedCurp(curp,d,requestCode);return;}
        base("Resolviendo CURP…",true);
        LinearLayout wait=card();wait.addView(tv("1 de 2 · RENAPO",20,TEAL,true));wait.addView(tv("No había datos de identidad en caché. ConsultaMX está resolviendo la CURP en RENAPO para después buscar automáticamente las cédulas en SEP.",14,NAVY,false));body.addView(wait);
        Intent i=new Intent(this,HandsFreeCurpRpaActivity.class);i.putExtra("url",RENAPO);i.putExtra("curp",curp);i.putExtra("cedulaRequestCode",requestCode);startActivityForResult(i,REQ_CEDULA_RENAPO);
    }

    String[] loadCurpIdentity(String curp){
        SQLiteDatabase r=db.getReadableDatabase();Cursor c=null;
        try{
            c=r.rawQuery("SELECT names,first_surname,second_surname,birth_date,sex,state FROM curp_details WHERE curp=? ORDER BY rowid DESC LIMIT 1",new String[]{curp});
            if(!c.moveToFirst())return null;
            String[] d=new String[6];for(int k=0;k<6;k++)d[k]=c.isNull(k)?"":c.getString(k).trim();
            if(d[0].isEmpty()||d[1].isEmpty()||d[3].isEmpty()||d[4].isEmpty()||d[5].isEmpty())return null;
            return d;
        }catch(Exception e){return null;}finally{if(c!=null)c.close();}
    }

    void launchSepWithResolvedCurp(String curp,String[] d,int requestCode){
        base("Buscando cédulas…",true);
        LinearLayout wait=card();wait.addView(tv("2 de 2 · SEP",20,BLUE,true));wait.addView(tv("Identidad resuelta por CURP. Buscando ahora coincidencias profesionales con nombre, apellidos, fecha, sexo y entidad obtenidos de RENAPO.",14,NAVY,false));body.addView(wait);
        Intent i=new Intent(this,SepCedulaByCurpResolvedActivity.class);i.putExtra("mode","PERSONAL");i.putExtra("subjectCurp",curp);i.putExtra("names",d[0]);i.putExtra("first",d[1]);i.putExtra("second",d[2]);i.putExtra("birth",d[3]);i.putExtra("sex",normalizeSex(d[4]));i.putExtra("state",d[5]);startActivityForResult(i,requestCode);
    }

    String normalizeSex(String s){String x=s==null?"":s.trim().toUpperCase(Locale.ROOT);if(x.equals("H")||x.startsWith("HOM"))return "Hombre";if(x.equals("M")||x.startsWith("MUJ"))return "Mujer";return s;}

    @Override void launchSepRpa(String curp){startCedulaLookupByCurp(curp,REQ_SEP_RPA);}

    boolean hasSepRecord(String curp){SQLiteDatabase r=db.getReadableDatabase();ensureRecordsTable(r);Cursor c=r.rawQuery("SELECT COUNT(*) FROM source_response_records WHERE subject_type='CURP' AND subject_value=? AND source='SEP_CEDULA_PROFESIONAL' AND status IN ('RESULTADO_REAL','SIN_COINCIDENCIAS')",new String[]{curp});boolean ok=c.moveToFirst()&&c.getInt(0)>0;c.close();return ok;}

    @Override void showPersonResult(String curp,String rfc,String status,String checked,boolean cacheHit,long qid){pendingCurp=curp;pendingRfc=rfc;pendingQueryId=qid;if(!hasSepRecord(curp)&&!sepAttempted){sepAttempted=true;startCedulaLookupByCurp(curp,REQ_SEP_RPA);return;}super.showPersonResult(curp,rfc,status,checked,cacheHit,qid);renderSepSummary(curp);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode==REQ_CEDULA_RENAPO){
            String[] d=loadCurpIdentity(pendingCedulaCurp);
            if(d==null){toast("RENAPO no devolvió datos suficientes para buscar cédulas");showCedulaSearch();return;}
            launchSepWithResolvedCurp(pendingCedulaCurp,d,REQ_SEP_FLEX);return;
        }
        if(requestCode==REQ_SEP_FLEX){toast(resultCode==RESULT_OK?"Consulta de cédulas terminada":"SEP no devolvió un resultado verificable");if(!pendingCedulaCurp.isEmpty())showRecordsForCurp(pendingCedulaCurp);else showAllResponseRecords();return;}
        if(requestCode==REQ_SEP_RPA)sepAttempted=true;
        super.onActivityResult(requestCode,resultCode,data);
    }

    void renderSepSummary(String curp){SQLiteDatabase r=db.getReadableDatabase();ensureRecordsTable(r);Cursor c=r.rawQuery("SELECT id,status,response_json,captured_at FROM source_response_records WHERE subject_type='CURP' AND subject_value=? AND source='SEP_CEDULA_PROFESIONAL' AND status IN ('RESULTADO_REAL','SIN_COINCIDENCIAS') ORDER BY id DESC LIMIT 1",new String[]{curp});LinearLayout box=card();box.addView(tv("Cédulas profesionales SEP",19,BLUE,true));if(c.moveToFirst()){long id=c.getLong(0);String st=c.getString(1);String json=c.getString(2);String ts=c.getString(3);box.addView(tv("Consulta oficial: "+ts,13,MUTED,false));box.addView(tv(st.equals("SIN_COINCIDENCIAS")?"SEP no encontró coincidencias.":"SEP devolvió uno o más registros profesionales.",14,st.equals("SIN_COINCIDENCIAS")?AMBER:GREEN,true));Button see=btn("Ver JSON SEP");add(box,see,48);Button dl=btn("Descargar JSON SEP");dl.setBackground(bg(Color.rgb(51,65,85),14));add(box,dl,48);see.setOnClickListener(v->showJsonRecord(id,curp,"SEP_CEDULA_PROFESIONAL",ts,json,"https://siurp.sep.gob.mx/mvc/cedulaElectronica"));dl.setOnClickListener(v->exportJson(json,"SEP-CEDULAS-"+curp+"-"+id+".json"));}else{box.addView(tv("Todavía no hay un resultado SEP válido para esta CURP.",14,AMBER,true));Button retry=btn("Buscar nuevamente por CURP");add(box,retry,48);retry.setOnClickListener(v->startCedulaLookupByCurp(curp,REQ_SEP_RPA));}c.close();body.addView(box);}
}

package mx.consultamx.fraude;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

public class AutoMainActivity extends MainActivity {
    static final int REQ_CURP_RPA=2301;
    String pendingCurp="", pendingRfc="";
    long pendingQueryId=-1;

    @Override void showPersonQuery(){
        base("Consulta de persona",true);
        LinearLayout c=card();
        c.addView(tv("CURP",18,NAVY,true));
        c.addView(tv("La consulta cuesta $1. Primero revisamos la base local; si no existe, ConsultaMX consulta RENAPO automáticamente.",13,MUTED,false));
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
            long q=db.chargePersonQuery(user,x,rr,hit?"CACHE":"RPA_RENAPO_HANDS_FREE");
            if(q<0){toast("No se pudo cobrar la consulta");return;}
            if(hit){showPersonResult(x,rr,status,checked,true,q);return;}
            pendingCurp=x; pendingRfc=rr; pendingQueryId=q;
            launchCurpRpa(x);
        });
    }

    void launchCurpRpa(String curp){
        base("Consultando fuente oficial…",true);
        LinearLayout c=card();
        c.addView(tv("RENAPO EN PROCESO",20,TEAL,true));
        c.addView(tv("ConsultaMX está capturando la CURP, enviando el formulario y esperando la respuesta automáticamente. No necesitas pulsar el botón Buscar de RENAPO.",14,NAVY,false));
        c.addView(tv("Folio Q-"+pendingQueryId+" · cargo $1.00 ya aplicado",12,MUTED,false));
        body.addView(c);
        Intent i=new Intent(this,HandsFreeCurpRpaActivity.class);
        i.putExtra("url",RENAPO);
        i.putExtra("curp",curp);
        startActivityForResult(i,REQ_CURP_RPA);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQ_CURP_RPA) return;
        Cursor c=db.getCurp(pendingCurp);
        boolean hit=c.moveToFirst();
        String status=hit?c.getString(1):"RPA_NO_COMPLETADO";
        String checked=hit?c.getString(2):"";
        c.close();
        if(hit){showPersonResult(pendingCurp,pendingRfc,status,checked,true,pendingQueryId);}else{showRpaFailure();}
    }

    void showRpaFailure(){
        base("Verificación pendiente",true);
        LinearLayout c=card();
        c.addView(tv("NO SE OBTUVO RESPUESTA OFICIAL",19,AMBER,true));
        c.addView(tv("No se guardó la CURP como verificada. Puede ocurrir si RENAPO cambió su formulario, no respondió o bloqueó la automatización.",14,NAVY,false));
        c.addView(tv("No se realizará un segundo cobro al reintentar este folio.",13,GREEN,true));
        Button retry=btn("Reintentar automáticamente sin cobrar"); add(c,retry,52);
        Button home=btn("Volver al inicio"); home.setBackground(bg(NAVY,14)); add(c,home,48);
        body.addView(c);
        retry.setOnClickListener(v->launchCurpRpa(pendingCurp));
        home.setOnClickListener(v->showHome());
    }

    @Override void showPersonResult(String curp,String rfc,String status,String checked,boolean cacheHit,long qid){
        base("Resultado CURP",true);
        LinearLayout c=card();
        c.addView(tv("VERIFICACIÓN DISPONIBLE",19,GREEN,true));
        c.addView(tv("CURP: "+mask(curp),14,NAVY,true));
        c.addView(tv("Estado: "+status,13,NAVY,false));
        c.addView(tv("Última verificación: "+checked,13,MUTED,false));
        c.addView(tv("Origen: "+(status.contains("RENAPO")?"RENAPO / RPA":"caché local"),13,MUTED,false));
        c.addView(tv("Folio Q-"+qid+" · cargo $1.00",12,MUTED,false));
        body.addView(c);

        SQLiteDatabase r=db.getReadableDatabase();
        Cursor d=r.rawQuery("SELECT names,first_surname,second_surname,birth_date,sex,state,nationality,source_url FROM curp_details WHERE curp=?",new String[]{curp});
        if(d.moveToFirst()){
            LinearLayout info=card();
            info.addView(tv("Datos extraídos de la fuente",18,NAVY,true));
            addField(info,"Nombre(s)",d.getString(0));
            addField(info,"Primer apellido",d.getString(1));
            addField(info,"Segundo apellido",d.getString(2));
            addField(info,"Fecha de nacimiento",d.getString(3));
            addField(info,"Sexo",d.getString(4));
            addField(info,"Entidad de nacimiento",d.getString(5));
            addField(info,"Nacionalidad",d.getString(6));
            addField(info,"Fuente",d.getString(7));
            body.addView(info);
        }
        d.close();

        LinearLayout s=card();
        s.addView(tv("Más verificaciones oficiales",18,NAVY,true));
        Button ced=btn("Consultar cédula profesional SEP"); ced.setBackground(bg(BLUE,14)); add(s,ced,52);
        if(rfc!=null && rfc.length()>=12){Button sat=btn("Consultar SAT 69 / 69-B");sat.setBackground(bg(AMBER,14));add(s,sat,52);sat.setOnClickListener(v->openOfficial(SAT69,""));}
        Button rsps=btn("Consultar servidores públicos sancionados"); rsps.setBackground(bg(Color.rgb(127,29,29),14)); add(s,rsps,52);
        Button home=btn("Inicio"); home.setBackground(bg(NAVY,14)); add(s,home,48);
        body.addView(s);
        ced.setOnClickListener(v->openOfficial(CEDULA,curp));
        rsps.setOnClickListener(v->openOfficial(RSPS,""));
        home.setOnClickListener(v->showHome());
    }

    void addField(LinearLayout p,String label,String value){
        if(value==null||value.trim().isEmpty()) return;
        TextView t=tv(label+": "+value.trim(),14,NAVY,false);
        t.setPadding(0,dp(5),0,dp(5));
        p.addView(t);
    }
}

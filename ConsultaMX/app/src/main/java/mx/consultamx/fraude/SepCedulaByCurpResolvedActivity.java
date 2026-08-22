package mx.consultamx.fraude;

import android.content.Intent;
import android.os.Bundle;
import org.json.JSONObject;

/**
 * Executes the SEP search with personal data previously resolved from RENAPO,
 * while keeping the official response linked to the original CURP.
 */
public class SepCedulaByCurpResolvedActivity extends SepCedulaFlexibleRpaActivity {
    String originalCurp="";

    @Override public void onCreate(Bundle b){
        Intent i=getIntent();
        originalCurp=i.getStringExtra("subjectCurp")==null?"":i.getStringExtra("subjectCurp").trim().toUpperCase();
        super.onCreate(b);
    }

    @Override void capture(JSONObject page,String body,String st){
        if(saved)return;
        String subject=originalCurp.length()==18?originalCurp:(names+" "+first+" "+second).trim();
        JSONObject out=new JSONObject();
        try{
            out.put("source","SEP_CEDULA_PROFESIONAL");
            out.put("mode","CURP_RESOLVED_TO_PERSONAL");
            out.put("requestedCurp",originalCurp);
            out.put("queryNames",names);
            out.put("queryFirstSurname",first);
            out.put("querySecondSurname",second);
            out.put("queryBirthDate",birth);
            out.put("querySex",sex);
            out.put("queryState",state);
            out.put("url",page.optString("url",web==null?"":web.getUrl()));
            out.put("title",page.optString("title",""));
            out.put("capturedAt",now());
            out.put("status",st);
            out.put("visibleText",body);
            out.put("rows",page.optJSONArray("rows"));
        }catch(Exception ignored){}
        String prevMode=mode;
        mode="CURP"; curp=subject;
        saveRecord("SEP_CEDULA_PROFESIONAL",subject,st,out.toString(),body,page.optString("url",web==null?"":web.getUrl()));
        mode=prevMode;
        saved=true; h.removeCallbacks(timeoutRunnable);
        Intent r=new Intent();r.putExtra("source","SEP_CEDULA_PROFESIONAL");r.putExtra("status",st);r.putExtra("curp",subject);setResult(RESULT_OK,r);
        h.postDelayed(this::finish,350);
    }

    @Override void finishWithDiagnostic(String st){
        if(saved)return;
        String subject=originalCurp.length()==18?originalCurp:(names+" "+first+" "+second).trim();
        JSONObject out=new JSONObject();
        try{
            out.put("source","SEP_CEDULA_PROFESIONAL");
            out.put("mode","CURP_RESOLVED_TO_PERSONAL");
            out.put("requestedCurp",originalCurp);
            out.put("capturedAt",now());
            out.put("status",st);
            out.put("diagnostic","SEP no devolvió una estructura verificable para la búsqueda por datos personales derivados de RENAPO.");
        }catch(Exception ignored){}
        String prevMode=mode;
        mode="CURP"; curp=subject;
        saveRecord("SEP_CEDULA_PROFESIONAL",subject,st,out.toString(),"",web==null?"":web.getUrl());
        mode=prevMode;
        saved=true;
        Intent r=new Intent();r.putExtra("source","SEP_CEDULA_PROFESIONAL");r.putExtra("status",st);r.putExtra("curp",subject);setResult(RESULT_CANCELED,r);
        finish();
    }
}

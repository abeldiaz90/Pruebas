package mx.consultamx.fraude;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

/**
 * Wrapper over the SEP RPA that supplies a second official-search path.
 * If the CURP flow times out, it reuses RENAPO data already cached locally
 * to launch the SEP personal-data search without asking the user to type it.
 */
public class SepCedulaFallbackRpaActivity extends SepCedulaFlexibleRpaActivity {
    boolean fallbackLaunched=false;
    String originalCurp="";

    @Override public void onCreate(Bundle b){
        Intent i=getIntent();
        originalCurp=i.getStringExtra("curp")==null?"":i.getStringExtra("curp").trim();
        super.onCreate(b);
    }

    /** Called by the base RPA when it wants to terminate after an unsuccessful wait. */
    void tryPersonalFallback(){
        if(fallbackLaunched || originalCurp.length()!=18){ finishWithTimeout(); return; }
        String[] p=loadCurpDetails(originalCurp);
        if(p==null || p[0].isEmpty() || p[1].isEmpty() || p[3].isEmpty() || p[4].isEmpty() || p[5].isEmpty()){
            finishWithTimeout();
            return;
        }
        fallbackLaunched=true;
        saved=false; submitted=false; attempts=0; polls=0;
        mode="PERSONAL";
        names=p[0]; first=p[1]; second=p[2]; birth=p[3]; sex=p[4]; state=p[5];
        status.setText("SEP no respondió por CURP. Reintentando automáticamente por datos personales obtenidos de RENAPO…");
        h.removeCallbacksAndMessages(null);
        for(android.webkit.WebView w:windows){ try{w.stopLoading();}catch(Exception ignored){} }
        web=windows.get(0);
        web.loadUrl(SEARCH_URL);
        h.postDelayed(timeoutRunnable,55000);
    }

    String[] loadCurpDetails(String c){
        SQLiteDatabase db=openOrCreateDatabase("consultamx.db",MODE_PRIVATE,null);
        Cursor q=null;
        try{
            q=db.rawQuery("SELECT names,first_surname,second_surname,birth_date,sex,state FROM curp_details WHERE curp=? ORDER BY rowid DESC LIMIT 1",new String[]{c});
            if(!q.moveToFirst()) return null;
            String[] r=new String[6];
            for(int x=0;x<6;x++) r[x]=q.isNull(x)?"":q.getString(x).trim();
            return r;
        }catch(Exception e){ return null; }
        finally{ if(q!=null)q.close(); db.close(); }
    }

    @Override void finishWithTimeout(){
        if("CURP".equals(mode) && !fallbackLaunched){
            tryPersonalFallback();
            return;
        }
        // Preserve the original CURP as subject even when fallback search used personal data.
        if(fallbackLaunched && originalCurp.length()==18){
            String prevMode=mode;
            mode="CURP";
            curp=originalCurp;
            super.finishWithTimeout();
            mode=prevMode;
            return;
        }
        super.finishWithTimeout();
    }

    @Override void capture(org.json.JSONObject page,String body,String st){
        if(fallbackLaunched && originalCurp.length()==18){
            String prevMode=mode;
            mode="CURP";
            curp=originalCurp;
            super.capture(page,body,st);
            mode=prevMode;
            return;
        }
        super.capture(page,body,st);
    }
}

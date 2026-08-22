package mx.consultamx.fraude;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONTokener;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SepCedulaRpaActivity extends Activity {
    static final String SEP_URL="https://msirepve.sep.gob.mx/validacionelectronica/publico/startCedulaElectronica!startWizard.action";
    WebView web; TextView status; Handler handler=new Handler(Looper.getMainLooper());
    String curp=""; int attempts=0,polls=0; boolean saved=false,revealed=false,submitted=false;

    @Override public void onCreate(Bundle b){super.onCreate(b);
        curp=clean(getIntent().getStringExtra("curp")).toUpperCase(Locale.ROOT);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);
        status=new TextView(this);status.setPadding(dp(14),dp(12),dp(14),dp(12));status.setTextSize(14);status.setTextColor(Color.rgb(11,19,36));status.setText("Consultando SEP automáticamente…");root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        web=new WebView(this);web.setVisibility(View.INVISIBLE);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setLoadWithOverviewMode(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){String u=r.getUrl().toString().toLowerCase(Locale.ROOT);return !(u.startsWith("https://msirepve.sep.gob.mx/")||u.startsWith("https://siurp.sep.gob.mx/")||u.startsWith("https://www.gob.mx/"));}
            @Override public void onPageFinished(WebView v,String u){handler.postDelayed(()->fillAndContinue(),700);handler.removeCallbacks(poller);handler.postDelayed(poller,1300);}
        });
        web.loadUrl(SEP_URL);
    }

    void fillAndContinue(){if(saved||attempts>24)return;attempts++;String safe=curp.replace("'","");
        String js="(function(){try{function n(s){return (s||'').toString().replace(/\\s+/g,' ').trim().toLowerCase();}function setv(e,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');if(d&&d.set)d.set.call(e,v);else e.value=v;['input','change','blur'].forEach(function(x){e.dispatchEvent(new Event(x,{bubbles:true}));});}var xs=[].slice.call(document.querySelectorAll('input'));var c=null;for(var i=0;i<xs.length;i++){var x=xs[i],p=n((x.name||'')+' '+(x.id||'')+' '+(x.placeholder||'')+' '+(x.getAttribute('aria-label')||''));if(p.indexOf('curp')>=0||x.maxLength==18){c=x;break;}}if(!c)return JSON.stringify({stage:'NO_INPUT'});setv(c,'"+safe+"');var bs=[].slice.call(document.querySelectorAll('button,input[type=submit],input[type=button],a,[role=button]'));for(var j=0;j<bs.length;j++){var b=bs[j],t=n((b.innerText||'')+' '+(b.value||'')+' '+(b.title||''));if(t.indexOf('continuar')===0||t==='buscar'||t.indexOf('consultar')===0){HTMLElement.prototype.click.call(b);return JSON.stringify({stage:'CLICKED',text:t});}}var f=c.form||c.closest('form');if(f){if(f.requestSubmit){f.requestSubmit();return JSON.stringify({stage:'REQUEST_SUBMIT'});}HTMLFormElement.prototype.submit.call(f);return JSON.stringify({stage:'FORM_SUBMIT'});}return JSON.stringify({stage:'NO_BUTTON'});}catch(e){return JSON.stringify({stage:'ERROR',error:e.message});}})();";
        web.evaluateJavascript(js,v->{submitted=true;status.setText("SEP: CURP enviada automáticamente. Esperando respuesta…");});
    }

    final Runnable poller=new Runnable(){public void run(){if(saved||isFinishing())return;polls++;
        String js="(function(){try{return JSON.stringify({url:location.href,title:document.title||'',body:(document.body&&document.body.innerText)||''});}catch(e){return JSON.stringify({error:e.message});}})();";
        web.evaluateJavascript(js,new ValueCallback<String>(){public void onReceiveValue(String value){inspect(value);if(!saved&&polls<180)handler.postDelayed(poller,1200);}});
    }};

    void inspect(String value){try{Object outer=new JSONTokener(value).nextValue();String json=outer instanceof String?(String)outer:String.valueOf(outer);JSONObject o=new JSONObject(json);String body=o.optString("body","");String low=body.toLowerCase(Locale.ROOT);
        boolean challenge=low.contains("captcha")||low.contains("recaptcha")||low.contains("no soy un robot")||low.contains("código de verificación")||low.contains("codigo de verificacion");
        boolean terminal=low.contains("carrera")||low.contains("grado")||low.contains("institución")||low.contains("institucion")||low.contains("profesión")||low.contains("profesion")||low.contains("no se encontraron")||low.contains("sin resultados")||low.contains("tu trámite")||low.contains("tu tramite");
        boolean moved=!o.optString("url",SEP_URL).contains("startWizard.action") || polls>5;
        if(challenge){if(!revealed){revealed=true;web.setVisibility(View.VISIBLE);status.setText("SEP requiere validación humana. Resuélvela; el RPA continuará automáticamente.");}return;}
        if(moved&&terminal){captureAndSave();return;}
        if(polls%3==0&&attempts<24)fillAndContinue();
    }catch(Exception ignored){if(polls%3==0)fillAndContinue();}}

    void captureAndSave(){if(saved)return;status.setText("SEP respondió. Guardando JSON completo…");
        String js="(function(){try{function clean(x){return (x||'').replace(/\\s+/g,' ').trim();}var pairs=[],seen={};var rows=document.querySelectorAll('tr');for(var i=0;i<rows.length;i++){var cs=rows[i].querySelectorAll('th,td');if(cs.length>=2){var k=clean(cs[0].innerText),v=clean(cs[1].innerText);if(k&&v&&!seen[k+'|'+v]){pairs.push({label:k,value:v});seen[k+'|'+v]=1;}}}return JSON.stringify({source:'SEP_CEDULA_PROFESIONAL',requestedCurp:'"+curp.replace("'","")+"',url:location.href,title:document.title||'',capturedAt:new Date().toISOString(),visibleText:(document.body&&document.body.innerText)||'',fields:pairs});}catch(e){return JSON.stringify({source:'SEP_CEDULA_PROFESIONAL',error:e.message,url:location.href,visibleText:(document.body&&document.body.innerText)||''});}})();";
        web.evaluateJavascript(js,v->{String response=unwrap(v);String raw="";String url=web.getUrl()==null?SEP_URL:web.getUrl();try{JSONObject o=new JSONObject(response);raw=o.optString("visibleText","");url=o.optString("url",url);}catch(Exception ignored){}saveRecord(response,raw,url);saved=true;status.setText("SEP completado ✓ Respuesta guardada.");Toast.makeText(this,"Respuesta SEP guardada",Toast.LENGTH_SHORT).show();Intent r=new Intent();r.putExtra("curp",curp);r.putExtra("source","SEP_CEDULA_PROFESIONAL");setResult(RESULT_OK,r);handler.postDelayed(this::finish,500);});
    }

    void saveRecord(String response,String raw,String url){SQLiteDatabase db=openOrCreateDatabase("consultamx.db",MODE_PRIVATE,null);db.execSQL("CREATE TABLE IF NOT EXISTS source_response_records(id INTEGER PRIMARY KEY AUTOINCREMENT,subject_type TEXT NOT NULL,subject_value TEXT NOT NULL,source TEXT NOT NULL,status TEXT,response_json TEXT NOT NULL,raw_text TEXT,source_url TEXT,captured_at TEXT NOT NULL)");ContentValues v=new ContentValues();v.put("subject_type","CURP");v.put("subject_value",curp);v.put("source","SEP_CEDULA_PROFESIONAL");v.put("status","RESPUESTA_CAPTURADA");v.put("response_json",response);v.put("raw_text",raw);v.put("source_url",url);v.put("captured_at",now());db.insert("source_response_records",null,v);db.close();}
    String unwrap(String v){try{Object o=new JSONTokener(v).nextValue();return o instanceof String?(String)o:String.valueOf(o);}catch(Exception e){return "{\"error\":\"snapshot\"}";}}
    String clean(String s){return s==null?"":s.trim();}int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}static String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date());}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}

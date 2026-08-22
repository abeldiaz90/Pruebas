package mx.consultamx.fraude;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONTokener;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SepCedulaFlexibleRpaActivity extends Activity {
    static final String SEARCH_URL="https://siurp.sep.gob.mx/mvc/cedulaElectronica";
    static final String NUMBER_URL="https://siurp.sep.gob.mx/mvc/profesionista/tramites/vinculacion-curp";
    static final int MAX_POLLS=42; // ~50 seconds; never wait forever
    WebView web;
    LinearLayout root;
    TextView status;
    Handler h=new Handler(Looper.getMainLooper());
    List<WebView> windows=new ArrayList<>();
    String mode,curp,names,first,second,birth,sex,state,cedula;
    int polls=0, attempts=0;
    boolean saved=false, submitted=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        Intent i=getIntent();
        mode=val(i,"mode","CURP"); curp=val(i,"curp",""); names=val(i,"names",""); first=val(i,"first",""); second=val(i,"second",""); birth=val(i,"birth",""); sex=val(i,"sex",""); state=val(i,"state",""); cedula=val(i,"cedula","");
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        status=new TextView(this); status.setPadding(dp(14),dp(12),dp(14),dp(12)); status.setTextSize(14); status.setTextColor(Color.rgb(11,19,36)); status.setText("Consultando Registro Nacional de Profesionistas…");
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        web=createManagedWebView(false);
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        windows.add(web);
        web.loadUrl("CEDULA".equals(mode)?NUMBER_URL:SEARCH_URL);
        h.postDelayed(timeoutRunnable,55000);
    }

    WebView createManagedWebView(boolean popup){
        WebView w=new WebView(this);
        w.setVisibility(View.INVISIBLE);
        WebSettings s=w.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true); s.setSupportMultipleWindows(true);
        w.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String u){
                web=v;
                if(!submitted) h.postDelayed(()->submitOn(v),700);
                h.removeCallbacks(poller); h.postDelayed(poller,900);
            }
        });
        w.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg){
                status.setText("SEP abrió la ventana de resultados. Analizando respuesta…");
                WebView child=createManagedWebView(true);
                child.setVisibility(View.INVISIBLE);
                root.addView(child,new LinearLayout.LayoutParams(1,1));
                windows.add(child);
                WebView.WebViewTransport transport=(WebView.WebViewTransport)resultMsg.obj;
                transport.setWebView(child); resultMsg.sendToTarget();
                web=child;
                h.removeCallbacks(poller); h.postDelayed(poller,700);
                return true;
            }
            @Override public void onCloseWindow(WebView window){
                if(window!=null && window!=web){ try{root.removeView(window);}catch(Exception ignored){} }
                super.onCloseWindow(window);
            }
        });
        return w;
    }

    void submit(){ submitOn(web); }
    void submitOn(WebView target){
        if(saved||submitted||target==null||attempts++>10)return;
        String js;
        if("CEDULA".equals(mode)){
            js="(function(){try{function n(s){return (s||'').toLowerCase();}function sv(e,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');if(d&&d.set)d.set.call(e,v);else e.value=v;['input','change','blur'].forEach(function(x){e.dispatchEvent(new Event(x,{bubbles:true}));});}var xs=[].slice.call(document.querySelectorAll('input'));var c=null;for(var i=0;i<xs.length;i++){var x=xs[i],p=n((x.name||'')+' '+(x.id||'')+' '+(x.placeholder||'')+' '+(x.getAttribute('aria-label')||''));if((p.indexOf('cedula')>=0||p.indexOf('cédula')>=0)&&x.type!='hidden'){c=x;break;}}if(!c)return 'NO_INPUT';sv(c,'"+esc(cedula)+"');var bs=document.querySelectorAll('button,input[type=submit],input[type=button],a,[role=button]');for(var j=0;j<bs.length;j++){var t=n((bs[j].innerText||'')+' '+(bs[j].value||'')+' '+(bs[j].title||''));if(t.indexOf('consultar')>=0||t.indexOf('buscar')>=0){bs[j].click();return 'CLICKED';}}var f=c.form||c.closest('form');if(f){if(f.requestSubmit)f.requestSubmit();else HTMLFormElement.prototype.submit.call(f);return 'SUBMITTED';}return 'NO_BUTTON';}catch(e){return 'ERR:'+e.message;}})();";
        } else if("PERSONAL".equals(mode)){
            js="(function(){try{function n(s){return (s||'').toLowerCase();}function sv(e,v){if(!e)return false;var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');if(d&&d.set)d.set.call(e,v);else e.value=v;['input','change','blur'].forEach(function(x){e.dispatchEvent(new Event(x,{bubbles:true}));});return true;}function find(hints){var xs=document.querySelectorAll('input');for(var i=0;i<xs.length;i++){var p=n((xs[i].name||'')+' '+(xs[i].id||'')+' '+(xs[i].placeholder||'')+' '+(xs[i].getAttribute('aria-label')||''));for(var h=0;h<hints.length;h++)if(p.indexOf(hints[h])>=0)return xs[i];}return null;}sv(find(['nombre']), '"+esc(names)+"');sv(find(['primer','paterno']), '"+esc(first)+"');if('"+esc(second)+"')sv(find(['segundo','materno']), '"+esc(second)+"');sv(find(['fecha','nacimiento']), '"+esc(birth)+"');var sels=document.querySelectorAll('select');function pick(hints,val){for(var i=0;i<sels.length;i++){var p=n((sels[i].name||'')+' '+(sels[i].id||'')+' '+(sels[i].getAttribute('aria-label')||''));var ok=false;for(var h=0;h<hints.length;h++)if(p.indexOf(hints[h])>=0)ok=true;if(!ok)continue;for(var k=0;k<sels[i].options.length;k++){var tx=n(sels[i].options[k].text),vv=n(sels[i].options[k].value);if(tx.indexOf(n(val))>=0||vv==n(val)){sels[i].selectedIndex=k;sels[i].dispatchEvent(new Event('change',{bubbles:true}));return true;}}}return false;}pick(['sexo'],'"+esc(sex)+"');pick(['estado','entidad'],'"+esc(state)+"');var bs=document.querySelectorAll('button,input[type=submit],input[type=button],a,[role=button]');for(var j=0;j<bs.length;j++){var t=n((bs[j].innerText||'')+' '+(bs[j].value||''));if(t.indexOf('continuar')>=0||t.indexOf('buscar')>=0||t.indexOf('consultar')>=0){bs[j].click();return 'CLICKED';}}return 'NO_BUTTON';}catch(e){return 'ERR:'+e.message;}})();";
        } else {
            js="(function(){try{function n(s){return (s||'').toLowerCase();}function sv(e,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');if(d&&d.set)d.set.call(e,v);else e.value=v;['input','change','blur'].forEach(function(x){e.dispatchEvent(new Event(x,{bubbles:true}));});}var xs=document.querySelectorAll('input');var c=null;for(var i=0;i<xs.length;i++){var p=n((xs[i].name||'')+' '+(xs[i].id||'')+' '+(xs[i].placeholder||'')+' '+(xs[i].getAttribute('aria-label')||''));if(p.indexOf('curp')>=0||xs[i].maxLength==18){c=xs[i];break;}}if(!c)return 'NO_INPUT';sv(c,'"+esc(curp)+"');var bs=document.querySelectorAll('button,input[type=submit],input[type=button],a,[role=button]');for(var j=0;j<bs.length;j++){var t=n((bs[j].innerText||'')+' '+(bs[j].value||''));if(t.indexOf('continuar')>=0||t.indexOf('buscar')>=0||t.indexOf('consultar')>=0){bs[j].click();return 'CLICKED';}}var f=c.form||c.closest('form');if(f){if(f.requestSubmit)f.requestSubmit();else HTMLFormElement.prototype.submit.call(f);return 'SUBMITTED';}return 'NO_BUTTON';}catch(e){return 'ERR:'+e.message;}})();";
        }
        target.evaluateJavascript(js,v->{
            String r=String.valueOf(v);
            if(r.contains("CLICKED")||r.contains("SUBMITTED")){ submitted=true; status.setText("Consulta enviada. Esperando la respuesta oficial de SEP…"); }
            else status.setText("Preparando formulario SEP…");
        });
    }

    final Runnable poller=new Runnable(){ public void run(){
        if(saved||isFinishing())return;
        polls++;
        List<WebView> copy=new ArrayList<>(windows);
        for(WebView w:copy){ if(w==null)continue; inspectWebView(w); }
        if(!saved && polls<MAX_POLLS) h.postDelayed(poller,1200);
        else if(!saved) finishWithDiagnostic("TIMEOUT_SIN_RESPUESTA");
    }};

    void inspectWebView(WebView w){
        w.evaluateJavascript("(function(){try{var rows=[];document.querySelectorAll('tr').forEach(function(r){var a=[];r.querySelectorAll('th,td').forEach(function(c){var t=(c.innerText||'').replace(/\\s+/g,' ').trim();if(t)a.push(t);});if(a.length)rows.push(a);});return JSON.stringify({url:location.href,title:document.title||'',body:(document.body&&document.body.innerText)||'',rows:rows,inputs:[].slice.call(document.querySelectorAll('input')).map(function(x){return {name:x.name||'',id:x.id||'',value:x.value||'',type:x.type||''};})});}catch(e){return JSON.stringify({error:e.message});}})();",new ValueCallback<String>(){public void onReceiveValue(String value){inspect(value,w);}});
    }

    void inspect(String value,WebView source){
        if(saved)return;
        try{
            Object outer=new JSONTokener(value).nextValue(); String js=outer instanceof String?(String)outer:String.valueOf(outer); JSONObject o=new JSONObject(js);
            String body=o.optString("body",""); String low=body.toLowerCase(Locale.ROOT);
            boolean challenge=low.contains("captcha")||low.contains("recaptcha")||low.contains("no soy un robot")||low.contains("código de verificación")||low.contains("codigo de verificacion");
            if(challenge){ source.setVisibility(View.VISIBLE); web=source; status.setText("SEP requiere validación humana. Al terminar, ConsultaMX continuará automáticamente."); return; }
            boolean landing=low.contains("la búsqueda de tus carreras o grados cursados") && low.contains("otras acciones");
            boolean noResults=low.contains("no se encontraron")||low.contains("sin resultados")||low.contains("no existe registro")||low.contains("no se encontró")||low.contains("ningún registro")||low.contains("ningun registro");
            boolean tableHeaders=(low.contains("núm. cédula")||low.contains("num. cédula")||low.contains("número de cédula")||low.contains("numero de cedula")) && (low.contains("institución")||low.contains("institucion")||low.contains("profesión")||low.contains("profesion")||low.contains("nombre completo"));
            boolean hasNumericCedula=body.matches("(?s).*\\b[0-9]{5,10}\\b.*");
            boolean actualResult=tableHeaders && hasNumericCedula;
            String url=o.optString("url","");
            boolean resultRoute=url.contains("vinculacion-curp") && low.contains("se encontraron los siguientes registros");
            if((actualResult||resultRoute||noResults) && !landing){ web=source; capture(o,body,noResults?"SIN_COINCIDENCIAS":"RESULTADO_REAL"); return; }
            if(!submitted && polls%3==0) submitOn(source);
        }catch(Exception ignored){}
    }

    final Runnable timeoutRunnable=new Runnable(){ public void run(){ if(!saved&&!isFinishing()) finishWithDiagnostic("TIMEOUT_SIN_RESPUESTA"); }};

    void finishWithDiagnostic(String st){
        if(saved)return;
        saved=true;
        String subject="CEDULA".equals(mode)?cedula:("PERSONAL".equals(mode)?(names+" "+first+" "+second).trim():curp);
        JSONObject out=new JSONObject();
        try{out.put("source","SEP_CEDULA_PROFESIONAL");out.put("mode",mode);out.put("subject",subject);out.put("capturedAt",now());out.put("status",st);out.put("diagnostic","SEP no devolvió una ventana o estructura de resultados reconocible dentro del tiempo límite. No se considera una consulta exitosa.");}catch(Exception ignored){}
        saveRecord("SEP_CEDULA_PROFESIONAL",subject,st,out.toString(),"",web==null?"":web.getUrl());
        Intent r=new Intent();r.putExtra("source","SEP_CEDULA_PROFESIONAL");r.putExtra("status",st);setResult(RESULT_CANCELED,r);
        Toast.makeText(this,"SEP no devolvió resultados; puedes reintentar",Toast.LENGTH_LONG).show();
        finish();
    }

    void capture(JSONObject page,String body,String st){
        if(saved)return;
        String source="SEP_CEDULA_PROFESIONAL";
        String subject="CEDULA".equals(mode)?cedula:("PERSONAL".equals(mode)?(names+" "+first+" "+second).trim():curp);
        JSONObject out=new JSONObject();
        try{out.put("source",source);out.put("mode",mode);out.put("subject",subject);out.put("url",page.optString("url",web==null?"":web.getUrl()));out.put("title",page.optString("title",""));out.put("capturedAt",now());out.put("status",st);out.put("visibleText",body);out.put("rows",page.optJSONArray("rows"));}catch(Exception ignored){}
        saveRecord(source,subject,st,out.toString(),body,page.optString("url",web==null?"":web.getUrl()));
        saved=true; h.removeCallbacks(timeoutRunnable);
        Intent r=new Intent();r.putExtra("source",source);r.putExtra("status",st);setResult(RESULT_OK,r);
        Toast.makeText(this,st.equals("SIN_COINCIDENCIAS")?"SEP: sin coincidencias":"Cédulas SEP encontradas y guardadas",Toast.LENGTH_SHORT).show();
        h.postDelayed(this::finish,350);
    }

    void saveRecord(String source,String subject,String st,String json,String raw,String url){
        SQLiteDatabase db=openOrCreateDatabase("consultamx.db",MODE_PRIVATE,null);
        db.execSQL("CREATE TABLE IF NOT EXISTS source_response_records(id INTEGER PRIMARY KEY AUTOINCREMENT,subject_type TEXT NOT NULL,subject_value TEXT NOT NULL,source TEXT NOT NULL,status TEXT,response_json TEXT NOT NULL,raw_text TEXT,source_url TEXT,captured_at TEXT NOT NULL)");
        ContentValues v=new ContentValues();v.put("subject_type","CEDULA".equals(mode)?"CEDULA":"CURP".equals(mode)?"CURP":"PERSONA");v.put("subject_value",subject);v.put("source",source);v.put("status",st);v.put("response_json",json);v.put("raw_text",raw);v.put("source_url",url);v.put("captured_at",now());db.insert("source_response_records",null,v);db.close();
    }

    String val(Intent i,String k,String d){String x=i.getStringExtra(k);return x==null?d:x.trim();}
    String esc(String s){return (s==null?"":s).replace("\\","\\\\").replace("'","\\'");}
    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    static String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date());}
    @Override protected void onDestroy(){h.removeCallbacksAndMessages(null);for(WebView w:new ArrayList<>(windows)){if(w!=null){try{w.stopLoading();w.destroy();}catch(Exception ignored){}}}super.onDestroy();}
}

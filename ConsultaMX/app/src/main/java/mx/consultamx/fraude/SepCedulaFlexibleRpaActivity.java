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

public class SepCedulaFlexibleRpaActivity extends Activity {
    static final String SEARCH_URL="https://siurp.sep.gob.mx/mvc/cedulaElectronica";
    static final String NUMBER_URL="https://siurp.sep.gob.mx/mvc/profesionista/tramites/vinculacion-curp";
    WebView web; TextView status; Handler h=new Handler(Looper.getMainLooper());
    String mode,curp,names,first,second,birth,sex,state,cedula; int polls=0, attempts=0; boolean saved=false;

    @Override public void onCreate(Bundle b){super.onCreate(b);
        Intent i=getIntent(); mode=val(i,"mode","CURP");curp=val(i,"curp","");names=val(i,"names","");first=val(i,"first","");second=val(i,"second","");birth=val(i,"birth","");sex=val(i,"sex","");state=val(i,"state","");cedula=val(i,"cedula","");
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);status=new TextView(this);status.setPadding(dp(14),dp(12),dp(14),dp(12));status.setTextSize(14);status.setTextColor(Color.rgb(11,19,36));status.setText("Consultando Registro Nacional de Profesionistas…");root.addView(status,new LinearLayout.LayoutParams(-1,-2));web=new WebView(this);web.setVisibility(View.INVISIBLE);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){h.postDelayed(()->submit(),800);h.removeCallbacks(poller);h.postDelayed(poller,1400);}});
        web.loadUrl("CEDULA".equals(mode)?NUMBER_URL:SEARCH_URL);
    }

    void submit(){if(saved||attempts++>30)return;String js;
        if("CEDULA".equals(mode)){
            js="(function(){try{function n(s){return (s||'').toLowerCase();}var xs=[].slice.call(document.querySelectorAll('input'));var c=null;for(var i=0;i<xs.length;i++){var x=xs[i],p=n((x.name||'')+' '+(x.id||'')+' '+(x.placeholder||'')+' '+(x.getAttribute('aria-label')||''));if(p.indexOf('cedula')>=0||p.indexOf('cédula')>=0){c=x;break;}}if(!c)return 'NO_INPUT';c.value='"+esc(cedula)+"';c.dispatchEvent(new Event('input',{bubbles:true}));c.dispatchEvent(new Event('change',{bubbles:true}));var bs=document.querySelectorAll('button,input[type=submit],input[type=button]');for(var j=0;j<bs.length;j++){var t=n((bs[j].innerText||'')+' '+(bs[j].value||''));if(t.indexOf('consultar')>=0||t.indexOf('buscar')>=0){bs[j].click();return 'CLICKED';}}return 'NO_BUTTON';}catch(e){return 'ERR:'+e.message;}})();";
        } else if("PERSONAL".equals(mode)){
            js="(function(){try{function n(s){return (s||'').toLowerCase();}function setBy(key,v){var xs=document.querySelectorAll('input');for(var i=0;i<xs.length;i++){var p=n((xs[i].name||'')+' '+(xs[i].id||'')+' '+(xs[i].placeholder||'')+' '+(xs[i].getAttribute('aria-label')||''));if(p.indexOf(key)>=0){xs[i].value=v;xs[i].dispatchEvent(new Event('input',{bubbles:true}));xs[i].dispatchEvent(new Event('change',{bubbles:true}));return true;}}return false;}setBy('nombre','"+esc(names)+"');setBy('primer','"+esc(first)+"')||setBy('paterno','"+esc(first)+"');setBy('segundo','"+esc(second)+"')||setBy('materno','"+esc(second)+"');setBy('fecha','"+esc(birth)+"')||setBy('nacimiento','"+esc(birth)+"');var sels=document.querySelectorAll('select');function pick(hint,val){for(var i=0;i<sels.length;i++){var p=n((sels[i].name||'')+' '+(sels[i].id||''));if(p.indexOf(hint)>=0){for(var k=0;k<sels[i].options.length;k++){var tx=n(sels[i].options[k].text);if(tx.indexOf(n(val))>=0||n(sels[i].options[k].value)==n(val)){sels[i].selectedIndex=k;sels[i].dispatchEvent(new Event('change',{bubbles:true}));return true;}}}}return false;}pick('sexo','"+esc(sex)+"');pick('estado','"+esc(state)+"')||pick('entidad','"+esc(state)+"');var bs=document.querySelectorAll('button,input[type=submit],input[type=button]');for(var j=0;j<bs.length;j++){var t=n((bs[j].innerText||'')+' '+(bs[j].value||''));if(t.indexOf('continuar')>=0||t.indexOf('buscar')>=0){bs[j].click();return 'CLICKED';}}return 'NO_BUTTON';}catch(e){return 'ERR:'+e.message;}})();";
        } else {
            js="(function(){try{function n(s){return (s||'').toLowerCase();}var xs=document.querySelectorAll('input');var c=null;for(var i=0;i<xs.length;i++){var p=n((xs[i].name||'')+' '+(xs[i].id||'')+' '+(xs[i].placeholder||''));if(p.indexOf('curp')>=0||xs[i].maxLength==18){c=xs[i];break;}}if(!c)return 'NO_INPUT';c.value='"+esc(curp)+"';c.dispatchEvent(new Event('input',{bubbles:true}));c.dispatchEvent(new Event('change',{bubbles:true}));var bs=document.querySelectorAll('button,input[type=submit],input[type=button]');for(var j=0;j<bs.length;j++){var t=n((bs[j].innerText||'')+' '+(bs[j].value||''));if(t.indexOf('continuar')>=0||t.indexOf('buscar')>=0){bs[j].click();return 'CLICKED';}}return 'NO_BUTTON';}catch(e){return 'ERR:'+e.message;}})();";
        }
        web.evaluateJavascript(js,v->status.setText("Consulta enviada. Esperando resultados reales de SEP…"));
    }

    final Runnable poller=new Runnable(){public void run(){if(saved||isFinishing())return;polls++;web.evaluateJavascript("(function(){return JSON.stringify({url:location.href,title:document.title||'',body:(document.body&&document.body.innerText)||''});})();",new ValueCallback<String>(){public void onReceiveValue(String value){inspect(value);if(!saved&&polls<180)h.postDelayed(poller,1200);}});}};

    void inspect(String value){try{Object outer=new JSONTokener(value).nextValue();String js=outer instanceof String?(String)outer:String.valueOf(outer);JSONObject o=new JSONObject(js);String body=o.optString("body","");String low=body.toLowerCase(Locale.ROOT);boolean challenge=low.contains("captcha")||low.contains("recaptcha")||low.contains("no soy un robot")||low.contains("código de verificación")||low.contains("codigo de verificacion");if(challenge){web.setVisibility(View.VISIBLE);status.setText("SEP requiere validación humana. Después el robot continúa solo.");return;}
        boolean landing=low.contains("la búsqueda de tus carreras o grados cursados")&&low.contains("otras acciones");
        boolean noResults=(low.contains("no se encontraron")||low.contains("sin resultados")||low.contains("no existe registro")||low.contains("no se encontró"));
        boolean realCedula=low.contains("núm. cédula")||low.contains("num. cédula")||low.contains("número de cédula")||low.contains("numero de cedula");
        boolean realResult=(realCedula&&(low.contains("institución")||low.contains("institucion")||low.contains("profesión")||low.contains("profesion")||low.contains("nombre completo")));
        if(!landing&&(realResult||noResults)){capture(o,body,noResults?"SIN_COINCIDENCIAS":"RESULTADO_REAL");return;}
        if(polls%3==0)submit();
    }catch(Exception e){if(polls%3==0)submit();}}

    void capture(JSONObject page,String body,String st){if(saved)return;String source="SEP_CEDULA_PROFESIONAL";String subject="CEDULA".equals(mode)?cedula:("PERSONAL".equals(mode)?(names+" "+first+" "+second).trim():curp);JSONObject out=new JSONObject();try{out.put("source",source);out.put("mode",mode);out.put("subject",subject);out.put("url",page.optString("url",web.getUrl()));out.put("title",page.optString("title",""));out.put("capturedAt",now());out.put("status",st);out.put("visibleText",body);}catch(Exception ignored){}saveRecord(source,subject,st,out.toString(),body,page.optString("url",web.getUrl()));saved=true;Intent r=new Intent();r.putExtra("source",source);r.putExtra("status",st);setResult(RESULT_OK,r);Toast.makeText(this,"Consulta SEP guardada",Toast.LENGTH_SHORT).show();h.postDelayed(this::finish,500);}

    void saveRecord(String source,String subject,String st,String json,String raw,String url){SQLiteDatabase db=openOrCreateDatabase("consultamx.db",MODE_PRIVATE,null);db.execSQL("CREATE TABLE IF NOT EXISTS source_response_records(id INTEGER PRIMARY KEY AUTOINCREMENT,subject_type TEXT NOT NULL,subject_value TEXT NOT NULL,source TEXT NOT NULL,status TEXT,response_json TEXT NOT NULL,raw_text TEXT,source_url TEXT,captured_at TEXT NOT NULL)");ContentValues v=new ContentValues();v.put("subject_type","CEDULA".equals(mode)?"CEDULA":"CURP".equals(mode)?"CURP":"PERSONA");v.put("subject_value",subject);v.put("source",source);v.put("status",st);v.put("response_json",json);v.put("raw_text",raw);v.put("source_url",url);v.put("captured_at",now());db.insert("source_response_records",null,v);db.close();}
    String val(Intent i,String k,String d){String x=i.getStringExtra(k);return x==null?d:x.trim();}String esc(String s){return (s==null?"":s).replace("\\","\\\\").replace("'","\\'");}int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}static String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date());}
    @Override protected void onDestroy(){h.removeCallbacksAndMessages(null);if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}

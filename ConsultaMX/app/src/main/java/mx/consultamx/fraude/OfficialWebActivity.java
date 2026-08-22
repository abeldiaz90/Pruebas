package mx.consultamx.fraude;

import android.app.Activity;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import org.json.JSONTokener;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OfficialWebActivity extends Activity {
    WebView web;
    TextView status;
    Handler handler=new Handler(Looper.getMainLooper());
    String initialUrl="", curp="";
    boolean isCurpRpa=false, saved=false, submitted=false;
    int polls=0;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        initialUrl=clean(getIntent().getStringExtra("url"));
        curp=clean(getIntent().getStringExtra("curp")).toUpperCase(Locale.ROOT);
        if(initialUrl.length()==0) initialUrl="https://www.gob.mx/curp/";
        isCurpRpa=curp.matches("^[A-Z][AEIOUX][A-Z]{2}\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d$") && initialUrl.contains("gob.mx/curp");

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        status=new TextView(this);
        status.setPadding(dp(14),dp(10),dp(14),dp(10));
        status.setTextColor(Color.rgb(11,19,36));
        status.setTextSize(14);
        status.setText(isCurpRpa?"RPA: abriendo RENAPO…":"Fuente oficial");
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button retry=new Button(this); retry.setText("Reintentar RPA");
        Button close=new Button(this); close.setText("Cerrar");
        actions.addView(retry,new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(close,new LinearLayout.LayoutParams(0,-2,1));
        if(isCurpRpa) root.addView(actions,new LinearLayout.LayoutParams(-1,-2));

        web=new WebView(this);
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);

        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request){
                String u=request.getUrl().toString();
                return !allowed(u);
            }
            @Override public void onPageFinished(WebView view,String u){
                if(isCurpRpa){
                    status.setText("RPA: página cargada. Capturando CURP…");
                    handler.postDelayed(()->fillAndSubmit(),700);
                    handler.postDelayed(poller,1300);
                }
            }
        });
        retry.setOnClickListener(v->{saved=false;submitted=false;polls=0;status.setText("RPA: reintentando…");fillAndSubmit();handler.removeCallbacks(poller);handler.postDelayed(poller,1000);});
        close.setOnClickListener(v->finish());
        web.loadUrl(initialUrl);
    }

    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    String clean(String s){return s==null?"":s.trim();}
    boolean allowed(String u){
        String x=u.toLowerCase(Locale.ROOT);
        return x.startsWith("https://www.gob.mx/") || x.startsWith("https://gob.mx/") ||
               x.startsWith("https://consultas.curp.gob.mx/") || x.startsWith("https://www.consultas.curp.gob.mx/") ||
               x.startsWith("https://www.sat.gob.mx/") || x.startsWith("https://wwwmat.sat.gob.mx/") || x.startsWith("https://portalsat.plataforma.sat.gob.mx/") ||
               x.startsWith("https://rsps.gob.mx/");
    }

    void fillAndSubmit(){
        if(!isCurpRpa || saved) return;
        String safe=curp.replace("'","");
        String js="(function(){try{"+
          "function setv(el,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');if(d&&d.set)d.set.call(el,v);else el.value=v;el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));}"+
          "var ins=[].slice.call(document.querySelectorAll('input'));var c=null;for(var i=0;i<ins.length;i++){var x=ins[i],p=((x.placeholder||'')+' '+(x.name||'')+' '+(x.id||'')+' '+(x.getAttribute('aria-label')||'')).toLowerCase();if(p.indexOf('curp')>=0||x.maxLength==18){c=x;break;}}"+
          "if(!c)return 'NO_CURP_INPUT';setv(c,'"+safe+"');"+
          "var txt=(document.body.innerText||'').toLowerCase();var cap=txt.indexOf('código de verificación')>=0||txt.indexOf('codigo de verificacion')>=0||document.querySelector('iframe[src*=recaptcha],.g-recaptcha,[id*=captcha],[class*=captcha]');"+
          "if(cap)return 'HUMAN_REQUIRED';"+
          "var bs=[].slice.call(document.querySelectorAll('button,input[type=submit],input[type=button],a'));for(var j=0;j<bs.length;j++){var b=bs[j],t=((b.innerText||b.value||b.title||'')+'').trim().toLowerCase();if(t==='buscar'||t.indexOf('buscar')===0||t==='consultar'){b.click();return 'SUBMITTED';}}return 'NO_BUTTON';"+
          "}catch(e){return 'ERROR:'+e.message;}})();";
        web.evaluateJavascript(js,v->{
            String r=unwrap(v);
            if(r.contains("HUMAN_REQUIRED")) status.setText("RPA: CURP capturada. Resuelve el código/CAPTCHA que muestre RENAPO y pulsa Buscar; el robot seguirá automáticamente.");
            else if(r.contains("SUBMITTED")){submitted=true;status.setText("RPA: consulta enviada. Esperando respuesta de RENAPO…");}
            else if(r.contains("NO_CURP_INPUT")) status.setText("RPA: aún no aparece el campo CURP; volveré a intentarlo.");
            else status.setText("RPA: CURP capturada. Esperando la respuesta del portal…");
        });
    }

    final Runnable poller=new Runnable(){public void run(){
        if(!isCurpRpa || saved || isFinishing()) return;
        polls++;
        String js="(function(){try{var b=(document.body&&document.body.innerText)||'';return JSON.stringify({url:location.href,title:document.title||'',body:b.substring(0,24000)});}catch(e){return JSON.stringify({error:e.message});}})();";
        web.evaluateJavascript(js,new ValueCallback<String>(){public void onReceiveValue(String value){
            inspect(value);
            if(!saved && polls<180) handler.postDelayed(poller,1200);
        }});
    }};

    void inspect(String value){
        try{
            Object outer=new JSONTokener(value).nextValue();
            String json=outer instanceof String?(String)outer:String.valueOf(outer);
            JSONObject o=new JSONObject(json);
            String body=o.optString("body","");
            String url=o.optString("url",web.getUrl()==null?initialUrl:web.getUrl());
            String low=body.toLowerCase(Locale.ROOT);
            if(low.contains("código de verificación")||low.contains("codigo de verificacion")||low.contains("captcha")){
                status.setText("RPA: esperando validación humana del portal; después continuaré sin que captures nuevamente la CURP.");
            }
            boolean hasCurp=body.toUpperCase(Locale.ROOT).contains(curp);
            boolean looksResult=hasCurp && (low.contains("nombre")||low.contains("apellido")) && (low.contains("fecha de nacimiento")||low.contains("sexo")||low.contains("entidad"));
            if(looksResult){
                CurpData d=parse(body);
                d.sourceUrl=url;
                save(d);
                saved=true;
                status.setText("RPA COMPLETADO ✓ Resultado extraído de RENAPO y guardado automáticamente en la base local.");
                Toast.makeText(this,"CURP verificada y guardada automáticamente",Toast.LENGTH_LONG).show();
            } else if(polls%8==0 && !submitted){
                fillAndSubmit();
            }
        }catch(Exception ignored){}
    }

    static class CurpData{
        String curp="",names="",firstSurname="",secondSurname="",birthDate="",sex="",state="",nationality="",status="VERIFICADA_RENAPO_RPA",sourceUrl="",raw="";
    }

    CurpData parse(String body){
        CurpData d=new CurpData(); d.curp=curp; d.raw=body;
        d.names=field(body,"Nombre\\(s\\)","Nombres?","Nombre");
        d.firstSurname=field(body,"Primer apellido","Apellido paterno","Primer Apellido");
        d.secondSurname=field(body,"Segundo apellido","Apellido materno","Segundo Apellido");
        d.birthDate=field(body,"Fecha de nacimiento","Fecha Nacimiento");
        d.sex=field(body,"Sexo","Género","Genero");
        d.state=field(body,"Entidad de nacimiento","Estado de nacimiento","Entidad Federativa de nacimiento");
        d.nationality=field(body,"Nacionalidad");
        return d;
    }

    String field(String body,String... labels){
        for(String label:labels){
            Pattern p=Pattern.compile("(?im)^\\s*(?:"+label+")\\s*[:\\-]?\\s*(?:\\r?\\n\\s*)?([^\\r\\n]{1,160})");
            Matcher m=p.matcher(body);
            if(m.find()){
                String x=m.group(1).trim();
                if(!x.equalsIgnoreCase(label) && !x.toUpperCase(Locale.ROOT).contains(curp)) return x;
            }
        }
        return "";
    }

    void save(CurpData d){
        SQLiteDatabase db=openOrCreateDatabase("consultamx.db",MODE_PRIVATE,null);
        db.execSQL("CREATE TABLE IF NOT EXISTS curp_cache(curp TEXT PRIMARY KEY,status TEXT,checked TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS curp_details(curp TEXT PRIMARY KEY,names TEXT,first_surname TEXT,second_surname TEXT,birth_date TEXT,sex TEXT,state TEXT,nationality TEXT,status TEXT,source_url TEXT,raw_text TEXT,checked TEXT)");
        String ts=now();
        ContentValues c=new ContentValues();c.put("curp",d.curp);c.put("status",d.status);c.put("checked",ts);db.insertWithOnConflict("curp_cache",null,c,SQLiteDatabase.CONFLICT_REPLACE);
        ContentValues v=new ContentValues();v.put("curp",d.curp);v.put("names",d.names);v.put("first_surname",d.firstSurname);v.put("second_surname",d.secondSurname);v.put("birth_date",d.birthDate);v.put("sex",d.sex);v.put("state",d.state);v.put("nationality",d.nationality);v.put("status",d.status);v.put("source_url",d.sourceUrl);v.put("raw_text",d.raw);v.put("checked",ts);db.insertWithOnConflict("curp_details",null,v,SQLiteDatabase.CONFLICT_REPLACE);db.close();
    }

    String unwrap(String v){try{Object o=new JSONTokener(v).nextValue();return String.valueOf(o);}catch(Exception e){return String.valueOf(v);}}
    static String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date());}

    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}

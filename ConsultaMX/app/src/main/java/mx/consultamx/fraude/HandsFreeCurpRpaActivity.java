package mx.consultamx.fraude;

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.ValueCallback;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Hands-free CURP RPA. CAPTCHA/human challenges are never bypassed. */
public class HandsFreeCurpRpaActivity extends OfficialWebActivity {
    boolean revealed=false;
    int submitAttempts=0;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(isCurpRpa){
            web.setVisibility(View.INVISIBLE);
            status.setText("Consultando RENAPO automáticamente…");
        }
    }

    @Override void fillAndSubmit(){
        if(!isCurpRpa || saved) return;
        submitAttempts++;
        String safe=curp.replace("'","");
        String js="(function(){try{"+
          "function nativeSet(el,v){var proto=el instanceof HTMLTextAreaElement?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;var d=Object.getOwnPropertyDescriptor(proto,'value');if(d&&d.set)d.set.call(el,v);else el.value=v;['input','change','blur'].forEach(function(n){el.dispatchEvent(new Event(n,{bubbles:true}));});}"+
          "function norm(s){return (s||'').toString().replace(/\\s+/g,' ').trim().toLowerCase();}"+
          "var ins=[].slice.call(document.querySelectorAll('input,textarea'));var c=null;"+
          "for(var i=0;i<ins.length;i++){var x=ins[i],p=norm((x.placeholder||'')+' '+(x.name||'')+' '+(x.id||'')+' '+(x.getAttribute('aria-label')||''));if(p.indexOf('curp')>=0||x.maxLength==18){c=x;break;}}"+
          "if(!c)return JSON.stringify({stage:'NO_INPUT',url:location.href});nativeSet(c,'"+safe+"');c.focus();"+
          "var form=c.form||c.closest('form');var all=[].slice.call(document.querySelectorAll('button,input[type=submit],input[type=button],a,[role=button]'));var btn=null;"+
          "for(var j=0;j<all.length;j++){var e=all[j],t=norm((e.innerText||'')+' '+(e.value||'')+' '+(e.title||'')+' '+(e.getAttribute('aria-label')||''));if(t==='buscar'||t.indexOf('buscar ')===0||t==='consultar'||t.indexOf('consultar ')===0||t.indexOf('continuar')===0){btn=e;break;}}"+
          "if(btn){try{btn.disabled=false;}catch(_e){};btn.dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));btn.dispatchEvent(new MouseEvent('mouseup',{bubbles:true}));HTMLElement.prototype.click.call(btn);return JSON.stringify({stage:'CLICKED',text:norm(btn.innerText||btn.value),url:location.href});}"+
          "if(form){if(typeof form.requestSubmit==='function'){form.requestSubmit();return JSON.stringify({stage:'REQUEST_SUBMIT',url:location.href});}form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));try{HTMLFormElement.prototype.submit.call(form);return JSON.stringify({stage:'FORM_SUBMIT',url:location.href});}catch(_x){}}"+
          "c.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));c.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));return JSON.stringify({stage:'ENTER',url:location.href});"+
          "}catch(e){return JSON.stringify({stage:'ERROR',error:e.message,url:location.href});}})();";
        web.evaluateJavascript(js,new ValueCallback<String>(){public void onReceiveValue(String value){
            String stage="";
            try{Object outer=new JSONTokener(value).nextValue();String json=outer instanceof String?(String)outer:String.valueOf(outer);stage=new JSONObject(json).optString("stage","");}catch(Exception ignored){}
            if(stage.equals("NO_INPUT")) status.setText("RENAPO está cargando el formulario…");
            else if(stage.equals("ERROR")) status.setText("Reintentando automatización de RENAPO…");
            else {submitted=true;status.setText("Consulta enviada automáticamente. Esperando respuesta oficial…");}
        }});
    }

    @Override void inspect(String value){
        if(saved) return;
        try{
            Object outer=new JSONTokener(value).nextValue();
            String json=outer instanceof String?(String)outer:String.valueOf(outer);
            JSONObject o=new JSONObject(json);
            String body=o.optString("body","");
            String low=body.toLowerCase(Locale.ROOT);
            boolean hasCurp=body.toUpperCase(Locale.ROOT).contains(curp);
            boolean looksResult=hasCurp && (low.contains("nombre")||low.contains("apellido")) && (low.contains("fecha de nacimiento")||low.contains("sexo")||low.contains("entidad"));
            if(looksResult){
                CurpData d=parse(body); d.sourceUrl=o.optString("url",web.getUrl()==null?initialUrl:web.getUrl());
                saved=true;
                persistCompleteResponseThenFinish(d);
                return;
            }
            boolean human=low.contains("recaptcha")||low.contains("captcha")||low.contains("código de verificación")||low.contains("codigo de verificacion")||low.contains("no soy un robot");
            if(human && polls>3) revealForHumanVerification();
            else if(polls%3==0 && submitAttempts<20) fillAndSubmit();
        }catch(Exception ignored){
            if(polls%3==0 && submitAttempts<20) fillAndSubmit();
        }
    }

    void revealForHumanVerification(){
        if(revealed) return;
        revealed=true;
        web.setVisibility(View.VISIBLE);
        status.setText("RENAPO requiere una validación humana. Resuélvela; el robot volverá a enviar el formulario automáticamente.");
    }

    void persistCompleteResponseThenFinish(CurpData d){
        status.setText("Resultado recibido. Guardando respuesta completa…");
        String js="(function(){try{"+
          "function clean(x){return (x||'').replace(/\\s+/g,' ').trim();}"+
          "var pairs=[];var seen={};"+
          "var rows=document.querySelectorAll('tr');for(var i=0;i<rows.length;i++){var cs=rows[i].querySelectorAll('th,td');if(cs.length>=2){var k=clean(cs[0].innerText),v=clean(cs[1].innerText);if(k&&v&&!seen[k+'|'+v]){pairs.push({label:k,value:v});seen[k+'|'+v]=1;}}}"+
          "var dts=document.querySelectorAll('dt');for(var j=0;j<dts.length;j++){var dd=dts[j].nextElementSibling;if(dd){var k2=clean(dts[j].innerText),v2=clean(dd.innerText);if(k2&&v2&&!seen[k2+'|'+v2]){pairs.push({label:k2,value:v2});seen[k2+'|'+v2]=1;}}}"+
          "var labs=document.querySelectorAll('label');for(var z=0;z<labs.length;z++){var l=labs[z],forId=l.getAttribute('for'),el=forId?document.getElementById(forId):null;if(el&&el.type!=='password'&&el.type!=='hidden'){var k3=clean(l.innerText),v3=clean(el.value||el.innerText);if(k3&&v3&&!seen[k3+'|'+v3]){pairs.push({label:k3,value:v3});seen[k3+'|'+v3]=1;}}}"+
          "return JSON.stringify({url:location.href,title:document.title||'',capturedAt:new Date().toISOString(),visibleText:(document.body&&document.body.innerText)||'',fields:pairs});"+
          "}catch(e){return JSON.stringify({url:location.href,error:e.message,visibleText:(document.body&&document.body.innerText)||''});}})();";
        web.evaluateJavascript(js,new ValueCallback<String>(){public void onReceiveValue(String value){
            String responseJson=unwrapJson(value);
            try{
                JSONObject root=new JSONObject(responseJson);
                root.put("source","RENAPO_CURP");
                root.put("requestedCurp",d.curp);
                JSONObject parsed=new JSONObject();
                parsed.put("curp",d.curp);parsed.put("names",d.names);parsed.put("firstSurname",d.firstSurname);parsed.put("secondSurname",d.secondSurname);
                parsed.put("birthDate",d.birthDate);parsed.put("sex",d.sex);parsed.put("birthState",d.state);parsed.put("nationality",d.nationality);parsed.put("status",d.status);
                root.put("parsed",parsed);
                responseJson=root.toString();
            }catch(Exception ignored){}
            d.raw=extractRawText(responseJson,d.raw);
            superSaveAndAppend(d,responseJson,"RENAPO_CURP","VERIFICADA_RENAPO_RPA");
            status.setText("Verificación oficial completada ✓ Respuesta JSON guardada.");
            Toast.makeText(HandsFreeCurpRpaActivity.this,"CURP y respuesta completa guardadas",Toast.LENGTH_SHORT).show();
            Intent result=new Intent();result.putExtra("curp",d.curp);result.putExtra("status",d.status);result.putExtra("sourceUrl",d.sourceUrl);setResult(RESULT_OK,result);
            new Handler(Looper.getMainLooper()).postDelayed(HandsFreeCurpRpaActivity.this::finish,500);
        }});
    }

    void superSaveAndAppend(CurpData d,String responseJson,String source,String recordStatus){
        super.save(d);
        SQLiteDatabase db=openOrCreateDatabase("consultamx.db",MODE_PRIVATE,null);
        db.execSQL("CREATE TABLE IF NOT EXISTS source_response_records(id INTEGER PRIMARY KEY AUTOINCREMENT,subject_type TEXT NOT NULL,subject_value TEXT NOT NULL,source TEXT NOT NULL,status TEXT,response_json TEXT NOT NULL,raw_text TEXT,source_url TEXT,captured_at TEXT NOT NULL)");
        ContentValues v=new ContentValues();v.put("subject_type","CURP");v.put("subject_value",d.curp);v.put("source",source);v.put("status",recordStatus);v.put("response_json",responseJson);v.put("raw_text",d.raw);v.put("source_url",d.sourceUrl);v.put("captured_at",nowFull());
        db.insert("source_response_records",null,v);db.close();
    }

    String unwrapJson(String v){
        try{Object o=new JSONTokener(v).nextValue();return o instanceof String?(String)o:String.valueOf(o);}catch(Exception e){return "{\"error\":\"No se pudo decodificar snapshot\"}";}
    }
    String extractRawText(String json,String fallback){try{return new JSONObject(json).optString("visibleText",fallback);}catch(Exception e){return fallback;}}
    static String nowFull(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date());}
}

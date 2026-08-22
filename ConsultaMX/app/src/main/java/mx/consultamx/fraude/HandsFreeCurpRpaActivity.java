package mx.consultamx.fraude;

import android.content.Intent;
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
import java.util.Locale;

/**
 * Hands-free CURP RPA. It intentionally does not bypass CAPTCHA or other human
 * verification. The browser is hidden while the flow can be automated and is
 * shown only when RENAPO requires unavoidable human interaction.
 */
public class HandsFreeCurpRpaActivity extends OfficialWebActivity {
    boolean revealed=false;
    int submitAttempts=0;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(isCurpRpa){
            // Keep the government page out of the user's way while the normal
            // form can be automated. INVISIBLE still allows WebView layout/JS.
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
          "for(var i=0;i<ins.length;i++){var x=ins[i],p=norm((x.placeholder||'')+' '+(x.name||'')+' '+(x.id||'')+' '+(x.getAttribute('aria-label')||'')+' '+(x.getAttribute('autocomplete')||''));if(p.indexOf('curp')>=0||x.maxLength==18){c=x;break;}}"+
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
                save(d); saved=true;
                status.setText("Verificación oficial completada ✓");
                Toast.makeText(this,"CURP consultada y guardada automáticamente",Toast.LENGTH_SHORT).show();
                return;
            }

            // Always attempt normal submit first. Only reveal the page when an
            // anti-bot/human challenge is actually blocking completion.
            boolean human=low.contains("recaptcha")||low.contains("captcha")||low.contains("código de verificación")||low.contains("codigo de verificacion")||low.contains("no soy un robot");
            if(human && polls>3){
                revealForHumanVerification();
            } else if(polls%3==0 && submitAttempts<20){
                fillAndSubmit();
            }
        }catch(Exception ignored){
            if(polls%3==0 && submitAttempts<20) fillAndSubmit();
        }
    }

    void revealForHumanVerification(){
        if(revealed) return;
        revealed=true;
        web.setVisibility(View.VISIBLE);
        status.setText("RENAPO requiere una validación humana. Resuélvela; no necesitas pulsar Buscar después: el robot seguirá reintentando automáticamente.");
    }

    @Override void save(CurpData d){
        super.save(d);
        Intent result=new Intent();
        result.putExtra("curp",d.curp);
        result.putExtra("status",d.status);
        result.putExtra("sourceUrl",d.sourceUrl);
        setResult(RESULT_OK,result);
        new Handler(Looper.getMainLooper()).postDelayed(this::finish,450);
    }
}

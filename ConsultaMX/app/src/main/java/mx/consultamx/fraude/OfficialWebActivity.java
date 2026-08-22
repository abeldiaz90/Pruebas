package mx.consultamx.fraude;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class OfficialWebActivity extends Activity {
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        String url=getIntent().getStringExtra("url");
        String curp=getIntent().getStringExtra("curp");
        WebView w=new WebView(this);
        setContentView(w);
        w.getSettings().setJavaScriptEnabled(true);
        w.getSettings().setDomStorageEnabled(true);
        w.setWebChromeClient(new WebChromeClient());
        w.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String u){
                if(curp!=null && curp.length()==18){
                    String safe=curp.replace("'","");
                    String js="(function(){var xs=document.querySelectorAll('input');for(var i=0;i<xs.length;i++){var x=xs[i];var p=((x.placeholder||'')+' '+(x.name||'')+' '+(x.id||'')).toLowerCase();if(p.indexOf('curp')>=0 || x.maxLength==18){x.focus();x.value='"+safe+"';x.dispatchEvent(new Event('input',{bubbles:true}));x.dispatchEvent(new Event('change',{bubbles:true}));break;}}})();";
                    view.evaluateJavascript(js,null);
                }
            }
        });
        w.loadUrl(url==null?"https://www.gob.mx/curp/":url);
    }
}

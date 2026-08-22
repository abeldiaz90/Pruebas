package mx.consultamx.fraude;

import android.os.Bundle;

/** Uses the current official SIURP professional-license endpoint. */
public class SepCedulaRpaV2Activity extends SepCedulaRpaActivity {
    static final String CURRENT_SEP_URL="https://siurp.sep.gob.mx/mvc/cedulaElectronica";

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        status.setText("Consultando cédulas profesionales SEP / SIURP automáticamente…");
        attempts=0; polls=0; submitted=false; saved=false; revealed=false;
        handler.removeCallbacksAndMessages(null);
        web.loadUrl(CURRENT_SEP_URL);
    }
}
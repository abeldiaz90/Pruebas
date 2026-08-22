package mx.consultamx.fraude;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class AutoOfficialWebActivity extends OfficialWebActivity {
    @Override void save(CurpData d){
        super.save(d);
        Intent result=new Intent();
        result.putExtra("curp",d.curp);
        result.putExtra("status",d.status);
        result.putExtra("sourceUrl",d.sourceUrl);
        setResult(RESULT_OK,result);
        new Handler(Looper.getMainLooper()).postDelayed(this::finish,700);
    }
}

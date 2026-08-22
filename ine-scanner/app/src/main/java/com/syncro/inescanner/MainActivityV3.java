package com.syncro.inescanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivityV3 extends Activity {
    private static final int REQ_CAMERA = 1001;
    private static final int REQ_GALLERY = 1002;

    private Uri currentImageUri;
    private File currentCameraFile;
    private ImageView preview;
    private TextView status;
    private TextView records;
    private EditText searchBox;
    private Button ocrButton;
    private final Map<String, EditText> fields = new HashMap<>();
    private PeopleDbV3 db;

    private final String[] fieldOrder = new String[]{
            "nombre", "apellido_paterno", "apellido_materno", "curp", "clave_elector",
            "fecha_nacimiento", "sexo", "anio_registro", "emision",
            "domicilio", "colonia", "codigo_postal", "municipio", "estado",
            "seccion", "vigencia"
    };

    private final String[] fieldLabels = new String[]{
            "Nombre(s)", "Apellido paterno", "Apellido materno", "CURP", "Clave de elector",
            "Fecha de nacimiento", "Sexo", "Año de registro", "Emisión",
            "Domicilio", "Colonia", "Código postal", "Municipio", "Estado",
            "Sección electoral", "Vigencia"
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = new PeopleDbV3(this);
        setContentView(buildUi());
        loadRecords("");
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(36));
        root.setBackgroundColor(Color.rgb(245,247,251));
        scroll.addView(root, new ScrollView.LayoutParams(-1,-2));

        TextView title = text("INE Scanner v0.3", 28, true); title.setTextColor(Color.rgb(17,24,39)); root.addView(title);
        TextView sub = text("OCR mejorado, campos adicionales y búsqueda local cifrada.", 15, false);
        sub.setTextColor(Color.DKGRAY); sub.setPadding(0,dp(4),0,dp(14)); root.addView(sub);

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button camera = button("Tomar foto"); Button gallery = button("Galería");
        actions.addView(camera, new LinearLayout.LayoutParams(0,dp(52),1));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0,dp(52),1); gp.setMarginStart(dp(8)); actions.addView(gallery,gp); root.addView(actions);

        preview = new ImageView(this); preview.setAdjustViewBounds(true); preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        preview.setBackgroundColor(Color.rgb(229,231,235)); LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1,dp(250)); ip.topMargin=dp(12); root.addView(preview,ip);

        ocrButton = button("Leer INE con OCR"); ocrButton.setEnabled(false);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1,dp(54)); op.topMargin=dp(10); root.addView(ocrButton,op);

        status = text("Toma una foto nítida de la INE.",13,false); status.setTextColor(Color.rgb(71,85,105)); status.setPadding(0,dp(10),0,dp(10)); root.addView(status);

        TextView warning = text("La app extrae datos visibles; no certifica autenticidad.",12,false);
        warning.setTextColor(Color.rgb(146,64,14)); warning.setBackgroundColor(Color.rgb(255,247,237)); warning.setPadding(dp(12),dp(10),dp(12),dp(10)); root.addView(warning);

        TextView formTitle = text("Datos detectados",20,true); formTitle.setPadding(0,dp(18),0,dp(4)); root.addView(formTitle);
        for (int i=0;i<fieldOrder.length;i++) {
            TextView l=text(fieldLabels[i],13,true); l.setPadding(0,dp(8),0,dp(4)); root.addView(l);
            EditText e=new EditText(this); e.setTextSize(16); e.setSingleLine(!fieldOrder[i].equals("domicilio"));
            e.setPadding(dp(12),dp(8),dp(12),dp(8)); e.setBackgroundColor(Color.WHITE); fields.put(fieldOrder[i],e);
            root.addView(e,new LinearLayout.LayoutParams(-1,fieldOrder[i].equals("domicilio")?dp(84):dp(50)));
        }

        Button save=button("Guardar persona cifrada"); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(56)); sp.topMargin=dp(18); root.addView(save,sp);
        Button clear=button("Limpiar formulario"); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(50)); cp.topMargin=dp(8); root.addView(clear,cp);

        TextView searchTitle=text("Buscar expedientes",20,true); searchTitle.setPadding(0,dp(24),0,dp(6)); root.addView(searchTitle);
        searchBox=new EditText(this); searchBox.setHint("Nombre, CURP o clave de elector"); searchBox.setSingleLine(true); searchBox.setTextSize(16);
        searchBox.setPadding(dp(12),dp(8),dp(12),dp(8)); searchBox.setBackgroundColor(Color.WHITE); root.addView(searchBox,new LinearLayout.LayoutParams(-1,dp(52)));

        LinearLayout sa=new LinearLayout(this); sa.setOrientation(LinearLayout.HORIZONTAL); Button search=button("Buscar"); Button all=button("Ver todos");
        sa.addView(search,new LinearLayout.LayoutParams(0,dp(50),1)); LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(50),1); ap.setMarginStart(dp(8)); sa.addView(all,ap);
        LinearLayout.LayoutParams sap=new LinearLayout.LayoutParams(-1,dp(50)); sap.topMargin=dp(8); root.addView(sa,sap);

        TextView dbTitle=text("Registros locales",20,true); dbTitle.setPadding(0,dp(20),0,dp(8)); root.addView(dbTitle);
        records=text("",14,false); records.setTextColor(Color.rgb(30,41,59)); records.setPadding(dp(12),dp(10),dp(12),dp(10)); records.setBackgroundColor(Color.WHITE); root.addView(records);

        camera.setOnClickListener(v->capturePhoto()); gallery.setOnClickListener(v->chooseImage()); ocrButton.setOnClickListener(v->runOcr());
        save.setOnClickListener(v->savePerson()); clear.setOnClickListener(v->clearForm());
        search.setOnClickListener(v->loadRecords(searchBox.getText().toString()));
        all.setOnClickListener(v->{searchBox.setText("");loadRecords("");});
        return scroll;
    }

    private void capturePhoto() {
        try {
            cleanupCameraFile(); File dir=new File(getCacheDir(),"camera"); if(!dir.exists()&&!dir.mkdirs()) throw new IOException("No se pudo crear almacenamiento temporal.");
            currentCameraFile=File.createTempFile("INE_",".jpg",dir); currentImageUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",currentCameraFile);
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE); if(i.resolveActivity(getPackageManager())==null) throw new IOException("No encontré una aplicación de cámara.");
            i.putExtra(MediaStore.EXTRA_OUTPUT,currentImageUri); i.setClipData(ClipData.newRawUri("INE",currentImageUri));
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(i,REQ_CAMERA);
        } catch(Exception e){cleanupCameraFile();showError("No se pudo abrir la cámara: "+e.getMessage());}
    }

    private void chooseImage(){cleanupCameraFile();Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,REQ_GALLERY);}

    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);
        if(req==REQ_CAMERA){if(result==RESULT_OK&&currentImageUri!=null&&currentCameraFile!=null&&currentCameraFile.exists()&&currentCameraFile.length()>0){showImage(currentImageUri);status.setText("Foto cargada. Pulsa Leer INE con OCR.");}else{cleanupCameraFile();status.setText("La cámara no devolvió una foto válida.");}}
        else if(req==REQ_GALLERY&&result==RESULT_OK&&data!=null&&data.getData()!=null){currentImageUri=data.getData();currentCameraFile=null;try{getContentResolver().takePersistableUriPermission(currentImageUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}showImage(currentImageUri);status.setText("Imagen cargada desde galería.");}}

    private void showImage(Uri uri){try{preview.setImageDrawable(null);try(InputStream in=getContentResolver().openInputStream(uri)){Bitmap bmp=BitmapFactory.decodeStream(in);if(bmp==null)throw new IOException("La imagen no pudo decodificarse.");preview.setImageBitmap(bmp);}ocrButton.setEnabled(true);}catch(Exception e){preview.setImageDrawable(null);ocrButton.setEnabled(false);status.setText("No pude cargar la imagen: "+e.getMessage());}}

    private void runOcr(){if(currentImageUri==null)return;ocrButton.setEnabled(false);status.setText("Leyendo la credencial…");
        try{InputImage image=InputImage.fromFilePath(this,currentImageUri);TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image).addOnSuccessListener(result->{String raw=result.getText()==null?"":result.getText();Map<String,String> parsed=IneParserV3.parse(raw);clearDetectedFields();for(String k:fieldOrder)setField(k,parsed.get(k));int populated=0;for(String k:fieldOrder)if(!value(k).isEmpty())populated++;status.setText(raw.trim().isEmpty()?"No pude leer texto. Intenta otra foto.":"OCR completado: "+populated+" campos. "+(value("curp").isEmpty()?"CURP no detectada.":"CURP detectada."));recognizer.close();ocrButton.setEnabled(true);})
                    .addOnFailureListener(e->{recognizer.close();status.setText("No se pudo procesar: "+e.getMessage());ocrButton.setEnabled(true);});
        }catch(IOException e){status.setText("No se pudo abrir la imagen: "+e.getMessage());ocrButton.setEnabled(true);}}

    private void savePerson(){if(value("nombre").isEmpty()&&value("curp").isEmpty()&&value("clave_elector").isEmpty()){Toast.makeText(this,"Captura al menos nombre, CURP o clave.",Toast.LENGTH_LONG).show();return;}try{Map<String,String> p=new HashMap<>();for(String k:fieldOrder)p.put(k,value(k));long id=db.insert(p);Toast.makeText(this,"Guardado cifrado. ID "+id,Toast.LENGTH_LONG).show();clearForm();loadRecords("");}catch(Exception e){showError("No se pudo guardar: "+e.getMessage());}}

    private void loadRecords(String q){try{List<Map<String,String>> list=db.search(q,100);if(list.isEmpty()){records.setText(q==null||q.trim().isEmpty()?"Aún no hay personas guardadas.":"No encontré registros para: "+q.trim());return;}StringBuilder out=new StringBuilder();for(Map<String,String> r:list){out.append("#").append(r.get("id")).append("  ").append(r.get("nombre")).append(" ").append(r.get("apellido_paterno")).append(" ").append(r.get("apellido_materno")).append("\nCURP: ").append(mask(r.get("curp"))).append("\nClave: ").append(mask(r.get("clave_elector"))).append("   Vigencia: ").append(r.get("vigencia")).append("\nSección: ").append(r.get("seccion")).append("   Sexo: ").append(r.get("sexo")).append("\nCaptura: ").append(r.get("created_at")).append("\n\n");}records.setText(out.toString().trim());}catch(Exception e){records.setText("No se pudo abrir la base local: "+e.getMessage());}}

    private String mask(String s){if(s==null||s.length()<8)return s==null?"":s;return s.substring(0,4)+"••••••"+s.substring(s.length()-4);}
    private void clearDetectedFields(){for(EditText e:fields.values())e.setText("");}
    private void cleanupCameraFile(){if(currentCameraFile!=null)try{if(currentCameraFile.exists())currentCameraFile.delete();}catch(Exception ignored){}currentCameraFile=null;currentImageUri=null;}
    private void clearForm(){clearDetectedFields();preview.setImageDrawable(null);cleanupCameraFile();ocrButton.setEnabled(false);status.setText("Toma una foto nítida de la INE.");}
    private String value(String k){EditText e=fields.get(k);return e==null?"":e.getText().toString().trim();}
    private void setField(String k,String v){EditText e=fields.get(k);if(e!=null&&v!=null&&!v.trim().isEmpty())e.setText(v.trim());}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setAllCaps(false);return b;}
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);return t;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void showError(String m){new AlertDialog.Builder(this).setTitle("INE Scanner").setMessage(m).setPositiveButton("Aceptar",null).show();}
    @Override protected void onDestroy(){super.onDestroy();if(db!=null)db.close();cleanupCameraFile();}
}

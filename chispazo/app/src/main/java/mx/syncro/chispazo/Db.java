package mx.syncro.chispazo;
import android.content.*; import android.database.*; import android.database.sqlite.*; import java.util.*;
public class Db extends SQLiteOpenHelper {
    public Db(Context c){ super(c,"chispazo.db",null,1); }
    public void onCreate(SQLiteDatabase db){ db.execSQL("CREATE TABLE draws(contest INTEGER PRIMARY KEY,date TEXT,n1 INTEGER,n2 INTEGER,n3 INTEGER,n4 INTEGER,n5 INTEGER)"); }
    public void onUpgrade(SQLiteDatabase db,int o,int n){}
    public void replaceAll(List<Draw> ds){ SQLiteDatabase db=getWritableDatabase(); db.beginTransaction(); try{ for(Draw d:ds){ ContentValues v=new ContentValues(); v.put("contest",d.contest);v.put("date",d.date); for(int i=0;i<5;i++)v.put("n"+(i+1),d.n[i]); db.insertWithOnConflict("draws",null,v,SQLiteDatabase.CONFLICT_REPLACE);} db.setTransactionSuccessful(); } finally{db.endTransaction();} }
    public List<Draw> all(){ ArrayList<Draw> out=new ArrayList<>(); Cursor c=getReadableDatabase().rawQuery("SELECT contest,date,n1,n2,n3,n4,n5 FROM draws ORDER BY contest DESC",null); while(c.moveToNext()){out.add(new Draw(c.getInt(0),c.getString(1),new int[]{c.getInt(2),c.getInt(3),c.getInt(4),c.getInt(5),c.getInt(6)}));} c.close(); return out; }
}

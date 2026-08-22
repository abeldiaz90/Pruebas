package mx.syncro.chispazo;
import java.util.*;
public class Draw {
    public final int contest; public final String date; public final int[] n;
    public Draw(int contest, String date, int[] n){ this.contest=contest; this.date=date; this.n=n; Arrays.sort(this.n); }
    public String numbers(){ return String.format(Locale.US,"%02d %02d %02d %02d %02d",n[0],n[1],n[2],n[3],n[4]); }
}

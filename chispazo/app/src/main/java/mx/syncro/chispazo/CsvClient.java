package mx.syncro.chispazo;
import java.io.*; import java.net.*; import java.nio.charset.StandardCharsets; import java.util.*; import java.util.regex.*;
public class CsvClient {
    public static final String URL="https://www.loterianacional.gob.mx/Home/Historicos?ARHP=QwBoAGkAcwBwAGEAegBvAA%3D%3D";
    public static List<Draw> fetch() throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(URL).openConnection(); c.setConnectTimeout(15000); c.setReadTimeout(25000); c.setRequestProperty("User-Agent","Mozilla/5.0 ChispazoAnalitica/1.0");
        int code=c.getResponseCode(); if(code<200||code>=300) throw new IOException("HTTP "+code);
        BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8)); ArrayList<String> lines=new ArrayList<>(); String s; while((s=r.readLine())!=null) lines.add(s); r.close(); return parse(lines);
    }
    static List<Draw> parse(List<String> lines){ ArrayList<Draw> out=new ArrayList<>(); if(lines.isEmpty())return out; String[] h=split(lines.get(0)); Map<String,Integer> m=new HashMap<>(); for(int i=0;i<h.length;i++)m.put(norm(h[i]),i);
        for(int li=1;li<lines.size();li++){ String[] p=split(lines.get(li)); try{
            Integer contest=getInt(p,m,"CONCURSO","SORTEO","CONCURSONO"); String date=getStr(p,m,"FECHA","FECHASORTEO"); int[] nums=new int[5]; boolean ok=true;
            for(int k=0;k<5;k++){ Integer v=getInt(p,m,"R"+(k+1),"N"+(k+1),"NUMERO"+(k+1)); if(v==null||v<1||v>28){ok=false;break;} nums[k]=v; }
            if(!ok){ ArrayList<Integer> vals=new ArrayList<>(); for(String q:p){ try{int v=Integer.parseInt(q.trim()); if(v>=1&&v<=28) vals.add(v);}catch(Exception ignored){}} if(vals.size()>=5){ for(int k=0;k<5;k++)nums[k]=vals.get(vals.size()-5+k); ok=true; } }
            if(contest==null){ for(String q:p){try{int v=Integer.parseInt(q.trim());if(v>1000){contest=v;break;}}catch(Exception ignored){}} }
            if(date==null||date.isEmpty()){ Pattern dp=Pattern.compile("\\d{1,2}/\\d{1,2}/\\d{4}"); for(String q:p)if(dp.matcher(q).find()){date=q.trim();break;} }
            if(contest!=null&&ok) out.add(new Draw(contest,date==null?"":date,nums));
        }catch(Exception ignored){} }
        out.sort((a,b)->Integer.compare(b.contest,a.contest)); return out;
    }
    static String[] split(String s){ return s.replace("\uFEFF","").split("[,;\\t]",-1); }
    static String norm(String s){return s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");}
    static Integer getInt(String[] p,Map<String,Integer>m,String...ks){String s=getStr(p,m,ks); if(s==null)return null; try{return Integer.parseInt(s.replaceAll("[^0-9-]",""));}catch(Exception e){return null;}}
    static String getStr(String[]p,Map<String,Integer>m,String...ks){for(String k:ks){Integer i=m.get(norm(k)); if(i!=null&&i<p.length)return p[i].trim();}return null;}
}

package mx.syncro.chispazo;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    Db db; LinearLayout body, nav; TextView status, sectionTitle; List<Draw> draws=new ArrayList<>(); Analyzer an;
    final int navy=Color.rgb(15,23,42), navy2=Color.rgb(30,41,59), gold=Color.rgb(245,166,35), ink=Color.rgb(17,24,39), muted=Color.rgb(100,116,139), bg=Color.rgb(244,247,251), white=Color.WHITE, green=Color.rgb(22,163,74), blue=Color.rgb(37,99,235);
    String current="Inicio";

    @Override public void onCreate(Bundle b){super.onCreate(b); db=new Db(this); build(); loadLocal(); if(draws.isEmpty())refresh();}

    int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    GradientDrawable box(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    GradientDrawable strokeBox(int color,int radius,int strokeColor){GradientDrawable g=box(color,radius);g.setStroke(dp(1),strokeColor);return g;}
    TextView tv(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(ink);v.setIncludeFontPadding(false);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    Space space(int h){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return s;}
    void pad(View v,int l,int t,int r,int b){v.setPadding(dp(l),dp(t),dp(r),dp(b));}

    Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(white);b.setBackground(box(navy,14));pad(b,16,10,16,10);return b;}
    Button ghost(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(navy);b.setBackground(strokeBox(white,14,Color.rgb(226,232,240)));pad(b,14,8,14,8);return b;}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setBackground(box(navy,0));pad(hero,22,22,22,18);
        TextView brand=tv("CHISPAZO",12,true);brand.setTextColor(gold);brand.setLetterSpacing(.16f);hero.addView(brand);
        TextView title=tv("Analítica inteligente",29,true);title.setTextColor(white);hero.addView(title);
        TextView sub=tv("Histórico oficial · patrones · combinaciones sugeridas",14,false);sub.setTextColor(Color.rgb(203,213,225));pad(sub,0,5,0,0);hero.addView(sub);
        status=tv("Preparando histórico…",12,false);status.setTextColor(Color.rgb(148,163,184));pad(status,0,12,0,0);hero.addView(status);
        root.addView(hero);

        HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);hsv.setBackgroundColor(white);
        nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);pad(nav,14,10,14,10);
        String[] labs={"Inicio","Sugerencias","Frecuencias","Pares","Tríos","Historial"};
        for(String l:labs){Button b=ghost(l);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(42));p.setMargins(0,0,dp(8),0);b.setLayoutParams(p);b.setOnClickListener(v->{current=l;show(l);});nav.addView(b);}
        hsv.addView(nav);root.addView(hsv);

        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);pad(body,18,18,18,50);sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    void loadLocal(){draws=db.all();if(!draws.isEmpty()){an=new Analyzer(draws);Draw d=draws.get(0);status.setText(draws.size()+" sorteos · último #"+d.contest+" · "+d.date);show(current);}}
    void refresh(){status.setText("Actualizando histórico oficial…");new Thread(()->{try{List<Draw>d=CsvClient.fetch();if(d.isEmpty())throw new Exception("CSV sin sorteos reconocibles");db.replaceAll(d);runOnUiThread(()->{loadLocal();Toast.makeText(this,"Histórico actualizado",Toast.LENGTH_SHORT).show();});}catch(Exception e){runOnUiThread(()->{status.setText("Sin conexión · usando histórico local");if(!draws.isEmpty())show(current);else{body.removeAllViews();TextView x=tv("No pude descargar el histórico. Conéctate a Internet y pulsa Actualizar.",16,true);body.addView(x);Button u=btn("Actualizar ahora");u.setOnClickListener(v->refresh());body.addView(space(12));body.addView(u);}});}}).start();}
    void clear(){body.removeAllViews();}
    void show(String tab){if(an==null)return;clear();switch(tab){case"Sugerencias":picks(true);break;case"Frecuencias":freq();break;case"Pares":pairs();break;case"Tríos":trios();break;case"Historial":history();break;default:home();}}

    LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setBackground(strokeBox(white,18,Color.rgb(226,232,240)));pad(c,16,15,16,15);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(12));c.setLayoutParams(p);return c;}
    void heading(String a,String b){TextView x=tv(a,22,true);body.addView(x);if(b!=null){TextView y=tv(b,13,false);y.setTextColor(muted);pad(y,0,5,0,14);body.addView(y);}}
    void small(LinearLayout c,String s){TextView t=tv(s,13,false);t.setTextColor(muted);pad(t,0,5,0,0);c.addView(t);}

    TextView ball(int n){TextView t=tv(String.format(Locale.US,"%02d",n),17,true);t.setTextColor(white);t.setGravity(Gravity.CENTER);t.setBackground(box(navy,999));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(48),dp(48));p.setMargins(0,0,dp(8),0);t.setLayoutParams(p);return t;}

    void comboCard(Analyzer.Pick p,int rank,double max){
        LinearLayout c=card();
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView tag=tv(rank==1?"MEJOR AJUSTE":"OPCIÓN "+rank,11,true);tag.setTextColor(rank==1?white:navy);tag.setBackground(box(rank==1?gold:Color.rgb(241,245,249),999));pad(tag,10,5,10,5);top.addView(tag);
        TextView score=tv(String.format(Locale.US,"  Afinidad %.0f/100",Math.max(1,Math.min(100,(p.score/max)*100))),12,true);score.setTextColor(green);top.addView(score);c.addView(top);c.addView(space(12));
        LinearLayout balls=new LinearLayout(this);balls.setOrientation(LinearLayout.HORIZONTAL);for(int n:p.n)balls.addView(ball(n));c.addView(balls);
        TextView why=tv(p.why,13,false);why.setTextColor(muted);pad(why,0,12,0,0);c.addView(why);body.addView(c);
    }

    void home(){
        heading("Jugadas sugeridas para el próximo sorteo","Se recalculan con el histórico descargado y combinan frecuencia, recencia, pares, tríos, equilibrio y dispersión.");
        List<Analyzer.Pick> ps=an.generate(100);if(!ps.isEmpty()){double max=ps.get(0).score;for(int i=0;i<Math.min(5,ps.size());i++)comboCard(ps.get(i),i+1,max);}else{LinearLayout c=card();c.addView(tv("Aún no hay suficiente histórico para generar sugerencias.",15,true));body.addView(c);}
        Button more=btn("Ver 10 combinaciones sugeridas");more.setOnClickListener(v->{current="Sugerencias";show("Sugerencias");});body.addView(more);body.addView(space(16));

        LinearLayout latest=card();TextView h=tv("Último resultado disponible",15,true);h.setTextColor(navy);latest.addView(h);Draw d=draws.get(0);TextView nums=tv(d.numbers(),25,true);nums.setTextColor(blue);pad(nums,0,9,0,3);latest.addView(nums);small(latest,"Sorteo #"+d.contest+" · "+d.date);body.addView(latest);

        LinearLayout pulse=card();pulse.addView(tv("Pulso estadístico",15,true));List<Analyzer.Stat> ns=an.numbers();StringBuilder sb=new StringBuilder();for(int i=0;i<Math.min(7,ns.size());i++){if(i>0)sb.append("  ·  ");sb.append(ns.get(i).key);}TextView hot=tv(sb.toString(),20,true);hot.setTextColor(gold);pad(hot,0,10,0,4);pulse.addView(hot);small(pulse,"Números con mayor índice ponderado en el histórico reciente + total.");body.addView(pulse);

        Button upd=btn("Actualizar datos de Lotería Nacional");upd.setOnClickListener(v->refresh());body.addView(upd);body.addView(space(18));
        TextView note=tv("Importante: el puntaje es una afinidad heurística con patrones históricos; no cambia la probabilidad matemática de una combinación en un sorteo independiente.",12,false);note.setTextColor(muted);body.addView(note);
    }

    void picks(boolean all){heading("Combinaciones sugeridas","Ranking calculado sobre las combinaciones posibles de 5 números entre 1 y 28.");List<Analyzer.Pick> ps=an.generate(120);if(ps.isEmpty())return;double max=ps.get(0).score;for(int i=0;i<Math.min(10,ps.size());i++)comboCard(ps.get(i),i+1,max);LinearLayout explain=card();explain.addView(tv("¿Qué considera el puntaje?",15,true));small(explain,"• frecuencia histórica total");small(explain,"• frecuencia en los últimos 100 y 30 sorteos");small(explain,"• atraso/recencia de cada número");small(explain,"• pares y tríos que suelen aparecer juntos");small(explain,"• balance par/impar, suma y dispersión");small(explain,"• penalización de jugadas casi idénticas a resultados muy recientes");body.addView(explain);}

    void freq(){heading("Frecuencias","Índice ponderado: histórico + últimos 100 + últimos 30 sorteos.");int i=0;for(Analyzer.Stat s:an.numbers()){LinearLayout c=card();LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.addView(ball(Integer.parseInt(s.key)));TextView t=tv("#"+(++i)+"   "+s.count+" apariciones",16,true);r.addView(t);c.addView(r);small(c,String.format(Locale.US,"Índice ponderado %.2f",s.score));body.addView(c);}}
    void pairs(){heading("Pares frecuentes","Parejas que han coincidido más veces dentro del mismo sorteo.");int i=0;for(Analyzer.Stat s:an.topPairs()){LinearLayout c=card();c.addView(tv((++i)+".  "+s.key.replace("-","  +  "),20,true));small(c,s.count+" coincidencias históricas");body.addView(c);if(i==30)break;}}
    void trios(){heading("Tríos frecuentes","Tríos con mayor coocurrencia histórica.");int i=0;for(Analyzer.Stat s:an.topTrios()){LinearLayout c=card();c.addView(tv((++i)+".  "+s.key.replace("-","  +  "),20,true));small(c,s.count+" coincidencias históricas");body.addView(c);if(i==30)break;}}

    void history(){heading("Historial","Busca por número de sorteo, fecha o cualquier número ganador.");EditText q=new EditText(this);q.setHint("Ej. 12204, 21/08/2026 o 15");q.setSingleLine(true);q.setBackground(strokeBox(white,14,Color.rgb(203,213,225)));pad(q,14,10,14,10);body.addView(q);body.addView(space(12));LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);Runnable render=()->{list.removeAllViews();String z=q.getText().toString().trim();int c=0;for(Draw d:draws){String row="#"+d.contest+" · "+d.date+" · "+d.numbers();if(z.isEmpty()||row.contains(z)){LinearLayout card=card();card.addView(tv(d.numbers(),20,true));small(card,"Sorteo #"+d.contest+" · "+d.date);list.addView(card);if(++c>=200)break;}}};q.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){render.run();}public void afterTextChanged(android.text.Editable e){}});render.run();}
}

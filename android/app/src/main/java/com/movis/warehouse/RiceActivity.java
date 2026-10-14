package com.movis.warehouse;

import android.app.*;
import android.os.*;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Staff companion: new in-app images, server checked totals, no direct balance editing. */
public class RiceActivity extends Activity {

    private static final int INK=0xff152c36, TEAL=0xff087f70, PAPER=0xfff5f7fa, MUTED=0xff61727e, GOLD=0xffd3a84c;
    private String expectedModel="";
    private ProgressBar progress;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final HashMap<String,String> keys=new HashMap<>();
    private final ArrayList<Button> buttons=new ArrayList<>();
    private LinearLayout root,body;
    private TextView status;
    private String token="",username="",role="",page="Inventory",actionId="";
    private JSONObject state=new JSONObject(),action=new JSONObject(),pendingImage;
    private boolean busy=false;
    private final Handler loadingHandler=new Handler(Looper.getMainLooper());
    private Runnable loadingIndicator;
    private String updatedAt="";
    interface Work { JSONObject run() throws Exception; }
    interface Reply { void done(JSONObject r) throws Exception; }
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getPreferences(MODE_PRIVATE).edit().remove("server").apply();
        try(InputStream in=getAssets().open("scanner_model.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);expectedModel=new JSONObject(out.toString("UTF-8")).optString("sha256");}catch(Exception ignored){}
        getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);login();}
    @Override protected void onDestroy(){super.onDestroy();loadingHandler.removeCallbacksAndMessages(null);worker.shutdownNow();}
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    JSONObject obj(Object... parts){JSONObject r=new JSONObject();try{for(int i=0;i<parts.length;i+=2)r.put((String)parts[i],parts[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return r;}
    JSONArray arr(JSONObject o,String key){JSONArray a=o.optJSONArray(key);return a==null?new JSONArray():a;}
    LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    TextView txt(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextColor(INK);v.setTextSize(size);v.setFontFeatureSettings("kern");v.setLineSpacing(dp(3),1);v.setPadding(0,dp(6),0,dp(6));return v;}
    TextView muted(LinearLayout parent,String s){TextView v=txt(s,14);v.setTextColor(MUTED);parent.addView(v);return v;}
    TextView tag(LinearLayout parent,String s,boolean good){TextView v=txt(s,12);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setTextColor(good?TEAL:0xff946123);v.setPadding(dp(12),dp(6),dp(12),dp(6));v.setBackground(bg(good?0xffe2f1e8:0xfffbefd8));parent.addView(v,new LinearLayout.LayoutParams(-2,-2));return v;}
    String label(String kind){switch(kind){case "stock-in":return "Stock in";case "stock-out":return "Stock out";case "count":return "Inventory count";case "return":return "Return";case "damage":return "Damage";default:return kind;}}
    String date(String value){try{return java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d · h:mm a",Locale.getDefault()));}catch(Exception e){return value;}}
    String units(int quantity){return quantity==1?"sack":"sacks";}
    boolean supported(int id){for(int i=0;i<arr(state,"products").length();i++){JSONObject r=arr(state,"products").optJSONObject(i);if(r.optInt("id")==id)return r.optBoolean("scan_supported");}return false;}
    JSONObject scanner(){JSONObject r=state.optJSONObject("scanner");return r==null?new JSONObject():r;}
    boolean connectedModel(){return scanner().optBoolean("ready")&&!expectedModel.isEmpty()&&expectedModel.equals(scanner().optString("sha256"));}
    void heading(LinearLayout p,String s){TextView t=txt(s,23);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);p.addView(t);}
    GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    LinearLayout card(LinearLayout parent){LinearLayout c=col();c.setPadding(dp(18),dp(16),dp(18),dp(16));GradientDrawable d=bg(Color.WHITE);d.setStroke(dp(1),0xffe4eaf0);c.setBackground(d);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);parent.addView(c,lp);return c;}
    Button button(LinearLayout parent,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(TEAL);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(14),dp(10),dp(14),dp(10));GradientDrawable d=bg(0xffe8f2eb);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22087f70),d,null));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);parent.addView(b,lp);buttons.add(b);b.setEnabled(!busy);b.setOnClickListener(v->{if(!busy)try{action.run();}catch(Exception e){error(e.getMessage());}});return b;}
    Button primary(LinearLayout parent,String title,Runnable action){Button b=button(parent,title,action);b.setTextColor(Color.WHITE);b.setBackground(bg(TEAL));return b;}
    EditText field(LinearLayout parent,String title,boolean number){TextView label=txt(title,13);label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);parent.addView(label);EditText e=new EditText(this);e.setSingleLine();e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(number?10:500)});e.setTextSize(16);e.setTextColor(INK);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setMinHeight(dp(50));GradientDrawable d=bg(0xfff5f7fa);d.setStroke(dp(1),0xffd9e2e8);e.setBackground(d);e.setInputType(number?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT);e.setContentDescription(title);parent.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    CheckBox check(LinearLayout p,String s,boolean checked){CheckBox c=new CheckBox(this);c.setText(s);c.setTextColor(INK);c.setChecked(checked);p.addView(c);return c;}
    void error(String s){status.setVisibility(View.VISIBLE);status.setText(s==null?"Request failed":s);status.setTextColor(0xff9c3228);status.setBackgroundColor(0xffffeee9);if(progress!=null)progress.setVisibility(View.INVISIBLE);}
    void shell(String title){
        buttons.clear();root=col();root.setBackgroundColor(PAPER);
        root.setOnApplyWindowInsetsListener((v,in)->{if(Build.VERSION.SDK_INT>=30){Insets bars=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());Insets ime=in.getInsets(WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));}else v.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in;});
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(24),dp(12),dp(24),dp(12));
        TextView logo=txt("M",22);logo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);logo.setGravity(Gravity.CENTER);logo.setTextColor(Color.WHITE);logo.setPadding(0,0,0,0);logo.setBackground(bg(TEAL));header.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout brand=col();brand.setPadding(dp(10),0,0,0);TextView name=txt("MOVIS",19);name.setLetterSpacing(.08f);name.setPadding(0,0,0,0);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);brand.addView(name);TextView sub=txt("ELDER JAY RICE MILL",9);sub.setLetterSpacing(.08f);sub.setPadding(0,dp(3),0,0);sub.setTextColor(MUTED);brand.addView(sub);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        if(!token.isEmpty()){
            TextView menu=txt(username.isEmpty()?"A":username.substring(0,1).toUpperCase(Locale.US),16);menu.setGravity(Gravity.CENTER);menu.setPadding(0,0,0,0);menu.setTypeface(Typeface.DEFAULT,Typeface.BOLD);menu.setBackground(bg(0xffe5edf2));menu.setContentDescription("Account menu");menu.setFocusable(true);header.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
            menu.setOnClickListener(v->{if(busy)return;PopupMenu pm=new PopupMenu(this,menu);pm.getMenu().add("Refresh");pm.getMenu().add("Sign out");pm.setOnMenuItemClickListener(item->{if(item.getTitle().toString().equals("Refresh")){if(actionId.isEmpty())load();else open(actionId);}else new AlertDialog.Builder(this).setTitle("Sign out?").setMessage("Your saved tasks will be available when you sign in again.").setNegativeButton("Stay",null).setPositiveButton("Sign out",(d,w)->signOut()).show();return true;});pm.show();});
        }
        root.addView(header);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.INVISIBLE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        status=txt("",13);status.setPadding(dp(24),dp(12),dp(24),dp(12));status.setVisibility(View.GONE);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);body=col();body.setPadding(dp(24),dp(12),dp(24),dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);root.requestApplyInsets();
    }
    void signOut(){
        final String previousToken=token;
        token="";username="";role="";actionId="";pendingImage=null;state=new JSONObject();action=new JSONObject();keys.clear();
        new File(getCacheDir(),"capture.jpg").delete();login();
        worker.execute(()->{try{ApiClient.request("/logout",obj(),previousToken);}catch(Exception ignored){}});
    }
    @Override public void onBackPressed(){
        if(busy){Toast.makeText(this,"Please wait for this request to finish",Toast.LENGTH_SHORT).show();return;}
        if(!actionId.isEmpty()){
            if(pendingImage!=null){new AlertDialog.Builder(this).setTitle("Photo not uploaded").setMessage("Retry the scan or discard this photo before leaving.").setNegativeButton("Stay",null).setPositiveButton("Discard photo",(d,w)->{pendingImage=null;new File(getCacheDir(),"capture.jpg").delete();page="Tasks";actionId="";load();}).show();}
            else{page="Tasks";actionId="";load();}
        }else if(!token.isEmpty()&&!page.equals("Inventory")){page="Inventory";home(page);}else super.onBackPressed();
    }
    void bottomNav(){
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(12),dp(6),dp(12),dp(8));nav.setBackgroundColor(Color.WHITE);
        for(String n:new String[]{"Inventory","Tasks","Activity"}){
            LinearLayout item=col();item.setGravity(Gravity.CENTER);item.setPadding(0,dp(8),0,dp(6));item.setBackground(bg(page.equals(n)?0xffe5f3ef:Color.WHITE));item.setContentDescription(n);item.setSelected(page.equals(n));item.setFocusable(true);
            Icon icon=new Icon(n,page.equals(n)?TEAL:MUTED);item.addView(icon,new LinearLayout.LayoutParams(dp(23),dp(23)));
            TextView label=txt(n,11);label.setPadding(0,dp(4),0,0);label.setTextColor(page.equals(n)?TEAL:MUTED);label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);item.addView(label);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(4),0,dp(4),0);nav.addView(item,lp);
            item.setOnClickListener(v->{if(!busy&&!page.equals(n))home(n);});
        }root.addView(nav);
    }
    class Icon extends View{
        String kind;int color;Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(String kind,int color){super(RiceActivity.this);this.kind=kind;this.color=color;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            if(kind.equals("Inventory")){canvas.drawRoundRect(3,4,21,21,3,3,paint);canvas.drawLine(3,10,21,10,paint);canvas.drawLine(9,4,9,10,paint);canvas.drawLine(15,4,15,10,paint);canvas.drawLine(10,15,14,15,paint);}
            else if(kind.equals("Tasks")){canvas.drawRoundRect(5,4,20,22,2,2,paint);canvas.drawRoundRect(9,2,16,6,1,1,paint);canvas.drawLine(9,12,16,12,paint);canvas.drawLine(9,17,14,17,paint);}
            else{canvas.drawCircle(12,12,9,paint);canvas.drawLine(12,6,12,12,paint);canvas.drawLine(12,12,16,15,paint);}canvas.restore();
        }
    }
    void actionRow(LinearLayout parent,String symbol,String title,String detail,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(12),dp(14),dp(12));row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22087f70),bg(Color.WHITE),null));
        TextView sign=txt(symbol,23);sign.setTextColor(TEAL);sign.setGravity(Gravity.CENTER);sign.setPadding(0,0,0,0);sign.setBackground(bg(0xffe8f4f0));row.addView(sign,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout words=col();words.setPadding(dp(14),0,dp(8),0);TextView t=txt(title,16);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(0,0,0,dp(3));words.addView(t);TextView d=txt(detail,12);d.setTextColor(MUTED);d.setPadding(0,0,0,0);words.addView(d);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=txt("›",24);arrow.setTextColor(MUTED);row.addView(arrow);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);parent.addView(row,lp);row.setFocusable(true);row.setContentDescription(title+". "+detail);row.setOnClickListener(v->{if(!busy)action.run();});
    }
    void scannerCard(){
        boolean ready=connectedModel();LinearLayout c=card(body);c.setPadding(dp(16),dp(10),dp(16),dp(10));
        TextView label=txt(ready?"●  Smart counting ready":"●  Smart counting unavailable",13);label.setTextColor(ready?TEAL:0xff9a6220);label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);c.addView(label);
        muted(c,ready?"Magnolia rice · Verify each count before submitting.":"Contact your administrator. You can still enter verified quantities manually.");
    }
    void finishLoading(){
        if(loadingIndicator!=null)loadingHandler.removeCallbacks(loadingIndicator);
        loadingIndicator=null;busy=false;
        for(Button b:buttons)b.setEnabled(true);
        progress.setVisibility(View.INVISIBLE);
    }
    void task(Work work,Reply reply){
        if(busy||isDestroyed())return;
        busy=true;for(Button b:buttons)b.setEnabled(false);
        status.setVisibility(View.GONE);
        // Short requests complete without flashing a banner or moving the layout.
        loadingIndicator=()->{if(busy&&!isDestroyed())progress.setVisibility(View.VISIBLE);};
        loadingHandler.postDelayed(loadingIndicator,350);
        worker.execute(()->{
            try{
                JSONObject r=work.run();
                runOnUiThread(()->{
                    if(isDestroyed())return;
                    finishLoading();
                    try{reply.done(r);}catch(Exception e){error(e.getMessage());}
                });
            }catch(Exception e){runOnUiThread(()->{
                if(isDestroyed())return;
                finishLoading();
                if(e instanceof ApiClient.ApiError&&((ApiClient.ApiError)e).code==401){
                    token="";actionId="";pendingImage=null;state=new JSONObject();action=new JSONObject();keys.clear();login();
                }
                error(e.getMessage());
            });}
        });
    }
    JSONObject api(String path,JSONObject data)throws Exception{ArrayList<String> known=new ArrayList<>();
        for(int i=0;i<arr(action,"images").length();i++){
            JSONObject image=arr(action,"images").optJSONObject(i);
            if(image.optInt("deleted")==0&&!image.optString("image").isEmpty())known.add(image.optString("id"));
        }
        return ApiClient.request(path,data,token,String.join(",",known));}
    JSONObject request(String kind,JSONObject d){String sig=kind+d.toString();String key=keys.get(sig);if(key==null){key=UUID.randomUUID().toString();keys.put(sig,key);}try{d.put("request_id",key);}catch(JSONException ignored){}return d;}
    void login(){
        actionId="";shell("Sign in");
        TextView welcome=txt("Your warehouse.\nIn clear view.",34);welcome.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));welcome.setPadding(0,dp(38),0,dp(10));body.addView(welcome);
        muted(body,"Keep stock accurate, one verified movement at a time.");
        LinearLayout f=card(body);LinearLayout.LayoutParams fl=(LinearLayout.LayoutParams)f.getLayoutParams();fl.topMargin=dp(26);f.setLayoutParams(fl);
        heading(f,"Sign in");muted(f,"Use your warehouse account.");
        EditText u=field(f,"Username",false),p=field(f,"Password",false);p.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);p.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});
        u.setAutofillHints(View.AUTOFILL_HINT_USERNAME);p.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);u.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);p.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);u.setText(getPreferences(MODE_PRIVATE).getString("username",""));
        Button submit=primary(f,"Sign in",()->{
            final String user=u.getText().toString().trim(),pass=p.getText().toString();
            if(user.isEmpty()||pass.isEmpty()){error("Enter your username and password.");return;}
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(p.getWindowToken(),0);
            task(()->api("/login",obj("username",user,"password",pass)),r->{p.setText("");token=r.getString("token");username=r.getString("username");role=r.getString("role");getPreferences(MODE_PRIVATE).edit().putString("username",username).apply();page="Inventory";shell("Inventory");heading(body,"Opening your workspace");button(body,"Try again",()->load());load();});
        });
        p.setOnEditorActionListener((v,id,event)->{if(id==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){submit.performClick();return true;}return false;});
        TextView help=muted(body,"Need access? Contact your warehouse administrator.");help.setGravity(Gravity.CENTER);
        TextView version=muted(body,"MOVIS 0.8.2 · Secure warehouse operations");version.setTextSize(11);version.setGravity(Gravity.CENTER);version.setPadding(0,dp(24),0,0);
    }
    void load(){task(()->api("/v2/state",null),r->{state=r;updatedAt=java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"));home(page);});}
    String name(int id){JSONArray p=arr(state,"products");for(int i=0;i<p.length();i++)if(p.optJSONObject(i).optInt("id")==id)return p.optJSONObject(i).optString("name");return "Rice "+id;}
    boolean write(){return role.equals("operator")||role.equals("admin");}
    void home(String tab){
        page=tab;actionId="";shell(tab);
        if(tab.equals("Inventory")){
            muted(body,"WORKSPACE");heading(body,"Inventory");muted(body,"Welcome back, "+username+".");
            long total=0;JSONArray products=arr(state,"products");for(int i=0;i<products.length();i++)total+=products.optJSONObject(i).optInt("quantity");
            LinearLayout hero=card(body);hero.setBackground(bg(INK));LinearLayout.LayoutParams hp=(LinearLayout.LayoutParams)hero.getLayoutParams();hp.topMargin=dp(12);hero.setLayoutParams(hp);
            TextView small=txt("TOTAL AVAILABLE",11);small.setLetterSpacing(.12f);small.setTextColor(0xffa9c6c6);hero.addView(small);TextView big=txt(String.format(Locale.US,"%,d",total),46);big.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));big.setTextColor(Color.WHITE);hero.addView(big);TextView detail=txt("sacks  ·  "+products.length()+" products",14);detail.setTextColor(0xffd1e3e3);hero.addView(detail);
            if(write()){
                LinearLayout quick=new LinearLayout(this);hero.addView(quick);Button receive=primary(quick,"+  Stock in",()->start("stock-in"));Button dispatch=primary(quick,"−  Stock out",()->start("stock-out"));LinearLayout.LayoutParams q1=new LinearLayout.LayoutParams(0,dp(50),1);q1.setMargins(0,dp(12),dp(5),0);receive.setLayoutParams(q1);LinearLayout.LayoutParams q2=new LinearLayout.LayoutParams(0,dp(50),1);q2.setMargins(dp(5),dp(12),0,0);dispatch.setLayoutParams(q2);dispatch.setBackground(bg(0xff2b4651));receive.setTextSize(14);dispatch.setTextSize(14);receive.setPadding(dp(6),dp(8),dp(6),dp(8));dispatch.setPadding(dp(6),dp(8),dp(6),dp(8));
            }
            scannerCard();heading(body,"Products");
            for(int i=0;i<products.length();i++){
                JSONObject item=products.optJSONObject(i);LinearLayout c=card(body);c.setPadding(dp(16),dp(12),dp(16),dp(12));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout info=col();TextView title=txt(item.optString("name"),17);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);info.addView(title);TextView sku=muted(info,item.optString("sku"));sku.setTextSize(11);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));LinearLayout amounts=col();amounts.setGravity(Gravity.END);TextView qty=txt(String.format(Locale.US,"%,d",item.optInt("quantity")),27);qty.setTypeface(Typeface.DEFAULT,Typeface.BOLD);amounts.addView(qty);TextView sack=muted(amounts,"sacks");sack.setTextSize(11);row.addView(amounts);c.addView(row);
                if(item.optBoolean("scan_supported")){TextView support=muted(c,"Smart counting");support.setTextColor(TEAL);support.setTextSize(11);}
            }
            muted(body,"Last refreshed "+updatedAt+" · Account menu → Refresh");
        }else if(tab.equals("Tasks")){
            muted(body,"OPERATIONS");heading(body,"Tasks");muted(body,"Record a movement or continue a saved task.");
            if(write()){
                actionRow(body,"+","Stock in","Receive sacks into inventory",()->start("stock-in"));
                actionRow(body,"−","Stock out","Release sacks from inventory",()->start("stock-out"));
                actionRow(body,"=","Inventory count","Compare physical and recorded stock",()->start("count"));
                actionRow(body,"↩","Return","Send returned sacks for inspection",()->start("return"));
                actionRow(body,"!","Report damage","Send damaged sacks for review",()->start("damage"));
            }
            actionRow(body,"◎","Inspections","Track returns and damage reports",()->home("Inspections"));
            heading(body,"In progress");JSONArray acts=arr(state,"actions");int drafts=0;
            for(int i=0;i<acts.length();i++){JSONObject a=acts.optJSONObject(i);if(!a.optString("state").equals("open"))continue;drafts++;actionRow(body,"·",label(a.optString("kind")),date(a.optString("created_at")),()->open(a.optString("id")));}
            if(drafts==0){LinearLayout c=card(body);heading(c,"All caught up");muted(c,"Your unfinished tasks will appear here.");}
        }else if(tab.equals("Inspections")){
            button(body,"‹  Back to tasks",()->home("Tasks"));heading(body,"Inspections");muted(body,"Returns and damage reports reviewed by your administrator.");JSONArray cases=arr(state,"cases");
            if(cases.length()==0){LinearLayout c=card(body);heading(c,"No inspection reports");muted(c,"Submitted returns and damage reports appear here.");}
            for(int i=0;i<cases.length();i++){JSONObject r=cases.optJSONObject(i);LinearLayout c=card(body);tag(c,r.optString("status").replace('_',' '),r.optInt("remaining")==0);heading(c,r.optString("name"));c.addView(txt(r.optInt("quantity")+" sacks · "+label(r.optString("kind")),17));muted(c,r.optInt("remaining")+" awaiting review");muted(c,r.optString("condition")+"\n"+r.optString("reason"));}
        }else{
            muted(body,"AUDIT TRAIL");heading(body,"Activity");muted(body,"Verified stock movements and completed tasks.");JSONArray history=arr(state,"history");
            if(history.length()==0){LinearLayout c=card(body);heading(c,"No movements yet");muted(c,"Completed stock changes will appear here.");}
            for(int i=0;i<Math.min(history.length(),100);i++){JSONObject r=history.optJSONObject(i);LinearLayout c=card(body);TextView product=txt(r.optString("name"),16);product.setTypeface(Typeface.DEFAULT,Typeface.BOLD);c.addView(product);int diff=r.optInt("difference");TextView change=txt((diff>0?"+":"")+diff+" sacks",25);change.setTypeface(Typeface.DEFAULT,Typeface.BOLD);change.setTextColor(diff>=0?TEAL:0xffb44738);c.addView(change);muted(c,label(r.optString("kind"))+" · "+date(r.optString("created_at")));muted(c,r.optString("username")+" · "+r.optInt("previous_quantity")+" → "+r.optInt("quantity"));if(!r.optString("reason").isEmpty())muted(c,r.optString("reason"));}
            heading(body,"Completed tasks");JSONArray acts=arr(state,"actions");for(int i=0;i<acts.length();i++){JSONObject a=acts.optJSONObject(i);if(!a.optString("state").equals("open"))actionRow(body,"✓",label(a.optString("kind")),date(a.optString("created_at")),()->open(a.optString("id")));}
        }
        bottomNav();
    }
    void start(String kind){LinearLayout f=col();f.setPadding(dp(18),dp(8),dp(18),dp(8));f.addView(txt("Select the rice products included in this action.",15));ArrayList<CheckBox> picks=new ArrayList<>();JSONArray ps=arr(state,"products");for(int i=0;i<ps.length();i++)picks.add(check(f,ps.optJSONObject(i).optString("name")+(ps.optJSONObject(i).optBoolean("scan_supported")?" · smart count":" · manual count"),ps.optJSONObject(i).optBoolean("scan_supported")));AlertDialog d=new AlertDialog.Builder(this).setTitle(label(kind)).setView(f).setNegativeButton("Cancel",null).setPositiveButton("Start",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{JSONArray ids=new JSONArray();for(int i=0;i<picks.size();i++)if(picks.get(i).isChecked())ids.put(ps.optJSONObject(i).optInt("id"));if(ids.length()==0){error("Choose at least one rice product");return;}d.dismiss();task(()->api("/v2/open",request("open",obj("kind",kind,"products",ids))),r->{keys.entrySet().removeIf(e->e.getKey().startsWith("open"));afterMutation(r,r.optString("action_id"));});}));d.show();}
    void showAction(JSONObject response)throws Exception{
        JSONObject next=response.getJSONObject("action");
        // Reuse only exact immutable image IDs from this same task; metadata is always fresh.
        HashMap<String,String> photos=new HashMap<>();
        if(next.optString("id").equals(action.optString("id"))){
            for(int i=0;i<arr(action,"images").length();i++){
                JSONObject im=arr(action,"images").getJSONObject(i);
                if(im.optInt("deleted")==0)photos.put(im.optString("id"),im.optString("image"));
            }
        }
        for(int i=0;i<arr(next,"images").length();i++){
            JSONObject im=arr(next,"images").getJSONObject(i);
            if(!im.has("image")&&im.optInt("deleted")==0)im.put("image",photos.getOrDefault(im.optString("id"),""));
        }
        for(String key:new String[]{"products","user","scanner"})if(response.has(key))state.put(key,response.get(key));
        action=next;actionId=next.getString("id");actionScreen();
    }
    void afterMutation(JSONObject receipt,String id)throws Exception{
        if(receipt.optJSONObject("action_state")!=null)showAction(receipt.getJSONObject("action_state"));
        else open(id); // Compatible with servers that do not yet return combined receipts.
    }
    void open(String id){actionId=id;task(()->api("/v2/state?action_id="+URLEncoder.encode(id,"UTF-8")+"&images=1",null),r->showAction(r));}
    JSONObject base(){return obj("action_id",actionId,"revision",action.optInt("revision"));}
    void actionScreen(){shell(label(action.optString("kind")));heading(body,label(action.optString("kind")));button(body,"‹  Back to tasks",()->onBackPressed());boolean editable=action.optString("state").equals("open");body.addView(txt("Count each sack once. Review quantities before submitting.",14));
        if(pendingImage!=null&&pendingImage.optString("action_id").equals(actionId)&&editable)button(body,"Retry pending image scan",()->sendImage());
        JSONArray images=arr(action,"images");HashMap<Integer,Integer> totals=new HashMap<>();LinearLayout cameraRow=col();body.addView(cameraRow);if(editable)primary(cameraRow,"Take a new photo",()->{if(!connectedModel()){error("Smart counting is unavailable. Contact your administrator or enter verified quantities manually.");return;}if(pendingImage!=null){error("Retry or discard your pending photo first.");return;}startActivityForResult(new Intent(this,CameraActivity.class),101);});LinearLayout gallery=col();cameraRow.addView(gallery,new LinearLayout.LayoutParams(-1,-2));gallery.addView(txt("Photos",16));int kept=0;
        for(int i=0;i<images.length();i++){JSONObject im=images.optJSONObject(i);if(im.optInt("deleted")==1)continue;kept++;byte[] bytes=Base64.decode(im.optString("image"),Base64.DEFAULT);BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=4;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);LinearLayout c=card(gallery);if(bitmap!=null){ImageView preview=new ImageView(this);preview.setImageBitmap(bitmap);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);preview.setAdjustViewBounds(true);preview.setContentDescription("Enlarge captured image "+kept);c.addView(preview,new LinearLayout.LayoutParams(-1,dp(200)));preview.setOnClickListener(v->enlarge(im));}c.addView(txt((im.optInt("reviewed")==1?"Reviewed":"Review needed"),13));if(editable){button(c,"Review detections",()->review(im));button(c,"Delete photo",()->new AlertDialog.Builder(this).setTitle("Delete this photo?").setMessage("Its reviewed quantities will be removed from this task.").setNegativeButton("Keep photo",null).setPositiveButton("Delete",(dialog,which)->{JSONObject d=base();try{d.put("image_id",im.optString("id"));}catch(JSONException ignored){}task(()->api("/v2/delete",request("delete",d)),r->{pendingImage=null;afterMutation(r,actionId);});}).show());}JSONArray lines=arr(im,"checked");if(im.optInt("reviewed")==1)for(int j=0;j<lines.length();j++){JSONObject l=lines.optJSONObject(j);int pid=l.optInt("product_id");totals.put(pid,totals.getOrDefault(pid,0)+l.optInt("quantity"));}}
        if(kept==0)gallery.addView(txt("New images for this action appear here.",14));heading(body,"Reviewed quantities");JSONArray snapshot=arr(action,"snapshot");for(int i=0;i<snapshot.length();i++){int pid=snapshot.optJSONObject(i).optInt("product_id");body.addView(txt(name(pid)+": "+totals.getOrDefault(pid,0)+" sacks",16));}
        if(editable){button(body,"Refresh action",()->open(actionId));if(action.optString("kind").equals("count"))button(body,"Review changed stock baseline",()->new AlertDialog.Builder(this).setTitle("Recheck affected sacks").setMessage("This reads current stock and resets image verification. Physically recheck the affected sacks and check every retained image again before finishing.").setNegativeButton("Cancel",null).setPositiveButton("I rechecked affected sacks",(d,w)->{JSONObject p=base();try{p.put("confirmed_review",true);}catch(JSONException ignored){}task(()->api("/v2/refresh-count",request("refresh-count",p)),r->afterMutation(r,actionId));}).show());button(body,action.optString("kind").equals("count")?"Finish count":"Submit transaction",()->completeDialog());}
        else if(action.optString("kind").equals("count")){JSONArray comp=arr(action,"comparison");for(int i=0;i<comp.length();i++){JSONObject r=comp.optJSONObject(i);body.addView(txt(name(r.optInt("product_id"))+"  |  recorded "+r.optInt("quantity")+"  |  count "+r.optInt("count")+"  |  difference "+r.optInt("difference")+(r.optBoolean("adjusted")?"  |  corrected":""),15));}if(state.optJSONObject("user").optBoolean("can_adjust"))button(body,"Confirm adjustment",()->adjust());else body.addView(txt("Saved count leaves stock unchanged. An authorized user must confirm any correction.",14));}
    }
    void enlarge(JSONObject im){byte[] bytes=Base64.decode(im.optString("image"),Base64.DEFAULT);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);if(b==null)return;ScrollView scroll=new ScrollView(this);LinearLayout f=col();scroll.addView(f);f.addView(new Boxes(b,arr(im,"raw")),new LinearLayout.LayoutParams(-1,dp(400)));f.addView(txt("Detected sacks. Your verified quantities are saved separately.",14));for(int i=0;i<arr(im,"raw").length();i++){JSONObject r=arr(im,"raw").optJSONObject(i);f.addView(txt(r.optString("name")+"  |  "+Math.round(r.optDouble("confidence")*100)+"%",14));}new AlertDialog.Builder(this).setTitle("Captured image").setView(scroll).setPositiveButton("Close",null).show();}
    void review(JSONObject im){LinearLayout f=col();f.setPadding(dp(16),dp(8),dp(16),dp(8));JSONArray snap=arr(action,"snapshot");ArrayList<EditText> qs=new ArrayList<>();HashMap<Integer,Integer> raw=new HashMap<>();for(int i=0;i<arr(im,"raw").length();i++){int pid=arr(im,"raw").optJSONObject(i).optInt("product_id");if(pid<=0)continue;raw.put(pid,raw.getOrDefault(pid,0)+1);}HashMap<Integer,Integer> checked=new HashMap<>();for(int i=0;i<arr(im,"checked").length();i++){JSONObject l=arr(im,"checked").optJSONObject(i);checked.put(l.optInt("product_id"),l.optInt("quantity"));}byte[] imageBytes=Base64.decode(im.optString("image"),Base64.DEFAULT);BitmapFactory.Options reviewOpts=new BitmapFactory.Options();reviewOpts.inSampleSize=2;Bitmap reviewBitmap=BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length,reviewOpts);if(reviewBitmap!=null)f.addView(new Boxes(reviewBitmap,arr(im,"raw")),new LinearLayout.LayoutParams(-1,dp(240)));for(int i=0;i<arr(im,"raw").length();i++){JSONObject detected=arr(im,"raw").optJSONObject(i);if(detected.isNull("product_id"))muted(f,"An item was not matched to a product. Enter its verified quantity below.");}
        f.addView(txt("Check each highlighted sack. Smart counting supports Magnolia rice; verify other products manually.",14));for(int i=0;i<snap.length();i++){int pid=snap.optJSONObject(i).optInt("product_id");EditText q=field(f,name(pid)+" · detected "+raw.getOrDefault(pid,0),true);q.setText(String.valueOf(checked.getOrDefault(pid,raw.getOrDefault(pid,0))));qs.add(q);}CheckBox confirmed=check(f,"I checked this image and corrected product names and quantities",false);TextView err=txt("",14);f.addView(err);ScrollView reviewScroll=new ScrollView(this);reviewScroll.addView(f);AlertDialog d=new AlertDialog.Builder(this).setTitle("Review rice quantities").setView(reviewScroll).setNegativeButton("Cancel",null).setPositiveButton("Save quantities",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{if(!confirmed.isChecked())throw new Exception("Confirm you checked the image");JSONArray lines=new JSONArray();for(int i=0;i<qs.size();i++){int qty=Integer.parseInt(qs.get(i).getText().toString());if(qty<0)throw new Exception("Use nonnegative whole sacks");lines.put(obj("product_id",snap.optJSONObject(i).optInt("product_id"),"quantity",qty));}JSONObject p=base();p.put("image_id",im.optString("id"));p.put("checked",lines);p.put("confirmed",true);d.dismiss();task(()->api("/v2/review",request("review",p)),r->afterMutation(r,actionId));}catch(Exception e){err.setText(e.getMessage());}}));d.show();}
    void completeDialog(){ScrollView scroll=new ScrollView(this);LinearLayout f=col();f.setPadding(dp(18),dp(8),dp(18),dp(8));scroll.addView(f);boolean count=action.optString("kind").equals("count"),ret=action.optString("kind").equals("return")||action.optString("kind").equals("damage");EditText reason=field(f,"Transaction / count reason",false);CheckBox unique=check(f,"Every sack is included only once across retained images and checked entries",false);JSONArray snap=arr(action,"snapshot");ArrayList<EditText> manual=new ArrayList<>(),why=new ArrayList<>();ArrayList<CheckBox> full=new ArrayList<>(),zero=new ArrayList<>();for(int i=0;i<snap.length();i++){int pid=snap.optJSONObject(i).optInt("product_id");heading(f,name(pid));EditText q=field(f,"Additional separately checked sacks (not in images)",true);q.setText("0");manual.add(q);why.add(field(f,"Reason for any additional checked quantity",false));if(count){full.add(check(f,"All sacks of this product were physically checked",false));zero.add(check(f,"If total is zero, I physically confirmed no sacks remain",false));}}CheckBox stocked=ret?check(f,"These sacks are already included in available stock",action.optString("kind").equals("damage")):null;EditText condition=ret?field(f,"Rice and packaging condition",false):null;TextView err=txt("",14);f.addView(err);AlertDialog d=new AlertDialog.Builder(this).setTitle(count?"Finish count - no stock change":"Review final transaction").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton(count?"Finish count":"Submit transaction",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{if(!unique.isChecked()||reason.getText().toString().trim().isEmpty())throw new Exception("Enter a reason and confirm sacks are counted once");JSONArray entries=new JSONArray();JSONObject fulls=new JSONObject(),zeros=new JSONObject();for(int i=0;i<snap.length();i++){int pid=snap.optJSONObject(i).optInt("product_id"),qty=Integer.parseInt(manual.get(i).getText().toString());if(qty<0)throw new Exception("Use nonnegative whole sacks");if(qty>0){if(why.get(i).getText().toString().trim().isEmpty())throw new Exception("Explain separately checked quantities");entries.put(obj("product_id",pid,"quantity",qty,"reason",why.get(i).getText().toString()));}if(count){if(!full.get(i).isChecked())throw new Exception("Confirm every chosen product was fully counted");fulls.put(String.valueOf(pid),full.get(i).isChecked());zeros.put(String.valueOf(pid),zero.get(i).isChecked());}}JSONObject p=base();p.put("reason",reason.getText().toString());p.put("unique_sacks",true);p.put("manual",entries);if(count){p.put("full_counts",fulls);p.put("zero_confirmed",zeros);}if(ret){p.put("already_stocked",stocked.isChecked());p.put("condition",condition.getText().toString());}String endpoint=count?"finish":"submit";JSONObject payload=request(endpoint,p);d.dismiss();task(()->api("/v2/"+endpoint,payload),r->{pendingImage=null;afterMutation(r,actionId);});}catch(Exception e){err.setText(e.getMessage());}}));d.show();}
    void adjust(){LinearLayout f=col();f.setPadding(dp(18),dp(8),dp(18),dp(8));JSONArray comp=arr(action,"comparison");ArrayList<Integer> ids=new ArrayList<>();ArrayList<CheckBox> picks=new ArrayList<>();for(int i=0;i<comp.length();i++){JSONObject r=comp.optJSONObject(i);if(r.optInt("difference")!=0&&!r.optBoolean("adjusted")){ids.add(r.optInt("product_id"));picks.add(check(f,name(r.optInt("product_id"))+": "+r.optInt("quantity")+"  ->  "+r.optInt("count"),true));}}EditText reason=field(f,"Stock correction reason",false);TextView err=txt("",14);f.addView(err);AlertDialog d=new AlertDialog.Builder(this).setTitle("Confirm separate adjustment").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Confirm adjustment",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{JSONArray chosen=new JSONArray();for(int i=0;i<picks.size();i++)if(picks.get(i).isChecked())chosen.put(ids.get(i));if(chosen.length()==0||reason.getText().toString().trim().isEmpty())throw new Exception("Select a difference and enter a reason");JSONObject p=base();p.put("products",chosen);p.put("reason",reason.getText().toString());p.put("confirmed",true);d.dismiss();task(()->api("/v2/adjust",request("adjust",p)),r->afterMutation(r,actionId));}catch(Exception e){err.setText(e.getMessage());}}));d.show();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=101||result!=RESULT_OK||actionId.isEmpty())return;
        final String capturedAction=actionId;
        task(()->{
            File file=new File(getCacheDir(),"capture.jpg");BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),opts);opts.inSampleSize=1;while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>2400)opts.inSampleSize*=2;opts.inJustDecodeBounds=false;
            Bitmap b=BitmapFactory.decodeFile(file.getAbsolutePath(),opts);if(b==null)throw new IOException("Unable to read photo. Please take it again.");
            int orient=new androidx.exifinterface.media.ExifInterface(file.getAbsolutePath()).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();
            switch(orient){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(-90);m.postScale(-1,1);break;case 8:m.setRotate(270);break;}
            Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);int max=Math.max(rotated.getWidth(),rotated.getHeight());Bitmap resized=max>1600?Bitmap.createScaledBitmap(rotated,Math.max(1,rotated.getWidth()*1600/max),Math.max(1,rotated.getHeight()*1600/max),true):rotated;
            ByteArrayOutputStream out=new ByteArrayOutputStream();resized.compress(Bitmap.CompressFormat.JPEG,85,out);String encoded=Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);if(resized!=rotated)resized.recycle();if(rotated!=b)rotated.recycle();b.recycle();file.delete();
            return obj("action_id",capturedAction,"image_id",UUID.randomUUID().toString(),"source","camera","zoom",1,"image",encoded);
        },r->{pendingImage=r;actionScreen();sendImage();});
    }
    void sendImage(){final JSONObject p=pendingImage;if(p==null)return;task(()->api("/v2/image",p),r->{pendingImage=null;afterMutation(r,actionId);});}
    class Boxes extends View{Bitmap bitmap;JSONArray boxes;Paint p=new Paint(3);Boxes(Bitmap b,JSONArray boxes){super(RiceActivity.this);bitmap=b;this.boxes=boxes;}@Override protected void onDraw(Canvas c){float s=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight()),w=bitmap.getWidth()*s,h=bitmap.getHeight()*s,x=(getWidth()-w)/2,y=(getHeight()-h)/2;p.setStyle(Paint.Style.FILL);c.drawBitmap(bitmap,null,new RectF(x,y,x+w,y+h),p);for(int i=0;i<boxes.length();i++){JSONObject r=boxes.optJSONObject(i);JSONArray b=r.optJSONArray("box");if(b==null)continue;p.setColor(TEAL);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));c.drawRect(x+(float)b.optDouble(0)*w,y+(float)b.optDouble(1)*h,x+(float)b.optDouble(2)*w,y+(float)b.optDouble(3)*h,p);p.setStyle(Paint.Style.FILL);p.setTextSize(dp(12));String caption=r.optString("name")+" "+Math.round(r.optDouble("confidence")*100)+"%";float tx=x+(float)b.optDouble(0)*w,ty=Math.max(y+dp(18),y+(float)b.optDouble(1)*h);float textWidth=p.measureText(caption);c.drawRoundRect(tx,ty-dp(18),Math.min(x+w,tx+textWidth+dp(10)),ty+dp(3),dp(4),dp(4),p);p.setColor(Color.WHITE);c.drawText(caption,tx+dp(5),ty-dp(3),p);}}}
}

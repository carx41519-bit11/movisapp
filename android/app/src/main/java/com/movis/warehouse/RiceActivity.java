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

    private static final int INK=0xff171717, ACCENT=INK, PAPER=0xfff5f5f5, MUTED=0xff6b6b6b;
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
    TextView txt(String s,int size){
        TextView v=new TextView(this);v.setText(s);v.setTextColor(INK);v.setTextSize(size);
        v.setFontFeatureSettings("kern");v.setLineSpacing(dp(3),1);v.setIncludeFontPadding(false);
        v.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));v.setPadding(0,dp(5),0,dp(5));return v;
    }
    TextView muted(LinearLayout parent,String s){TextView v=txt(s,13);v.setTextColor(MUTED);parent.addView(v);return v;}
    TextView eyebrow(LinearLayout parent,String s){TextView t=txt(s.toUpperCase(Locale.US),10);t.setLetterSpacing(.16f);t.setTypeface(Typeface.create("sans-serif-medium",0));t.setTextColor(MUTED);t.setPadding(0,dp(18),0,dp(12));parent.addView(t);return t;}
    TextView tag(LinearLayout parent,String s,boolean good){TextView v=txt(s,11);v.setTypeface(Typeface.create("sans-serif-medium",0));v.setTextColor(good?INK:0xff8c4d0e);v.setPadding(dp(10),dp(6),dp(10),dp(6));v.setBackground(shape(good?0xffefefef:0xfffff3e0,8,0));parent.addView(v,new LinearLayout.LayoutParams(-2,-2));return v;}
    String label(String kind){switch(kind){case "stock-in":return "Stock in";case "stock-out":return "Stock out";case "count":return "Inventory count";case "return":return "Return";case "damage":return "Damage";default:return kind;}}
    String date(String value){try{return java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d Â· h:mm a",Locale.getDefault()));}catch(Exception e){return value;}}
    String units(int quantity){return quantity==1?"sack":"sacks";}
    boolean supported(int id){for(int i=0;i<arr(state,"products").length();i++){JSONObject r=arr(state,"products").optJSONObject(i);if(r.optInt("id")==id)return r.optBoolean("scan_supported");}return false;}
    JSONObject scanner(){JSONObject r=state.optJSONObject("scanner");return r==null?new JSONObject():r;}
    boolean connectedModel(){return scanner().optBoolean("ready")&&!expectedModel.isEmpty()&&expectedModel.equals(scanner().optString("sha256"));}
    void heading(LinearLayout p,String s){TextView t=txt(s,27);t.setTypeface(Typeface.create("sans-serif-medium",0));t.setLetterSpacing(-.035f);t.setPadding(0,dp(6),0,dp(10));if(Build.VERSION.SDK_INT>=28)t.setAccessibilityHeading(true);p.addView(t);}
    GradientDrawable shape(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    GradientDrawable bg(int color){return shape(color,20,0);}
    android.graphics.drawable.Drawable ripple(int color,int radius){return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x18000000),shape(color,radius,0),null);}
    LinearLayout card(LinearLayout parent){LinearLayout c=col();c.setPadding(dp(20),dp(18),dp(20),dp(18));c.setBackground(shape(Color.WHITE,22,0xffe8e8e8));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);parent.addView(c,lp);return c;}
    Button button(LinearLayout parent,String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setTextColor(INK);b.setMinHeight(dp(52));b.setMinimumHeight(dp(52));b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setStateListAnimator(null);b.setBackground(ripple(0xffeeeeee,14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);parent.addView(b,lp);buttons.add(b);b.setEnabled(!busy);b.setOnClickListener(v->{if(!busy)try{action.run();}catch(Exception e){error(e.getMessage());}});return b;
    }
    Button primary(LinearLayout parent,String title,Runnable action){Button b=button(parent,title,action);b.setTextColor(Color.WHITE);b.setBackground(ripple(INK,14));return b;}
    EditText field(LinearLayout parent,String title,boolean number){
        TextView label=txt(title,12);label.setTypeface(Typeface.create("sans-serif-medium",0));label.setPadding(0,dp(16),0,dp(8));parent.addView(label);
        EditText e=new EditText(this);e.setSingleLine();e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(number?10:500)});e.setTextSize(number?22:16);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setPadding(dp(16),dp(14),dp(16),dp(14));e.setMinHeight(dp(56));e.setBackground(shape(0xfff7f7f7,12,0xffe4e4e4));e.setInputType(number?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT);e.setContentDescription(title);parent.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }
    CheckBox check(LinearLayout p,String s,boolean checked){CheckBox c=new CheckBox(this);c.setText(s);c.setTextSize(13);c.setTextColor(INK);c.setButtonTintList(android.content.res.ColorStateList.valueOf(INK));c.setMinHeight(dp(52));c.setPadding(0,dp(8),0,dp(8));c.setChecked(checked);p.addView(c);return c;}
    void error(String s){status.setVisibility(View.VISIBLE);status.setText(s==null?"Request failed. Please try again.":s);status.setTextColor(0xffa32929);status.setBackgroundColor(0xffffeeee);if(progress!=null)progress.setVisibility(View.INVISIBLE);status.announceForAccessibility(status.getText());}
    void divider(LinearLayout p){View line=new View(this);line.setBackgroundColor(0xffebebeb);p.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
    void showSheet(AlertDialog dialog){dialog.show();Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(shape(Color.WHITE,26,0));window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);window.getDecorView().post(()->{int max=(int)(getResources().getDisplayMetrics().heightPixels*.9);if(window.getDecorView().getHeight()>max)window.setLayout(-1,max);});}}
    void shell(String title){
        buttons.clear();root=col();root.setBackgroundColor(PAPER);
        root.setOnApplyWindowInsetsListener((v,in)->{if(Build.VERSION.SDK_INT>=30){Insets bars=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());Insets ime=in.getInsets(WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));}else v.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in;});
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(24),dp(16),dp(24),dp(12));
        MovisMark mark=new MovisMark(this,INK);header.addView(mark,new LinearLayout.LayoutParams(dp(27),dp(32)));
        LinearLayout brand=col();brand.setPadding(dp(10),0,0,0);TextView name=txt("movis",23);name.setLetterSpacing(-.05f);name.setPadding(0,0,0,0);name.setTypeface(Typeface.create("sans-serif-medium",0));brand.addView(name);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        if(!token.isEmpty()){
            TextView menu=txt(username.isEmpty()?"A":username.substring(0,1).toUpperCase(Locale.US),14);menu.setGravity(Gravity.CENTER);menu.setPadding(0,0,0,0);menu.setTypeface(Typeface.create("sans-serif-medium",0));menu.setBackground(shape(Color.WHITE,24,0xffdedede));menu.setContentDescription("Account and refresh menu");menu.setFocusable(true);header.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
            menu.setOnClickListener(v->{if(busy)return;PopupMenu pm=new PopupMenu(this,menu);pm.getMenu().add("Refresh");pm.getMenu().add("Sign out");pm.setOnMenuItemClickListener(item->{if(item.getTitle().toString().equals("Refresh")){if(actionId.isEmpty())load();else open(actionId);}else new AlertDialog.Builder(this).setTitle("Sign out?").setMessage("Your saved tasks will be available when you sign in again.").setNegativeButton("Stay",null).setPositiveButton("Sign out",(d,w)->signOut()).show();return true;});pm.show();});
        }else{TextView label=txt("ELDER JAY RICE MILL",9);label.setLetterSpacing(.11f);label.setTextColor(MUTED);header.addView(label);}
        root.addView(header);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(INK));progress.setIndeterminate(true);progress.setVisibility(View.INVISIBLE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));
        status=txt("",13);status.setPadding(dp(24),dp(12),dp(24),dp(12));status.setVisibility(View.GONE);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);body=col();body.setPadding(dp(24),dp(8),dp(24),dp(28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);root.requestApplyInsets();
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
        divider(root);LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(16),dp(8),dp(16),dp(10));nav.setBackgroundColor(Color.WHITE);
        for(String n:new String[]{"Inventory","Tasks","Activity"}){
            boolean selected=page.equals(n)||(page.equals("Inspections")&&n.equals("Tasks"));LinearLayout item=col();item.setGravity(Gravity.CENTER);item.setMinimumHeight(dp(58));item.setPadding(0,dp(6),0,dp(6));item.setBackground(ripple(selected?0xffeeeeee:Color.WHITE,16));item.setContentDescription(n+", "+(selected?"selected":"tab"));item.setSelected(selected);item.setFocusable(true);
            Icon icon=new Icon(n,selected?INK:0xff8c8c8c);item.addView(icon,new LinearLayout.LayoutParams(dp(23),dp(23)));TextView label=txt(n,11);label.setPadding(0,dp(5),0,0);label.setTextColor(selected?INK:MUTED);label.setTypeface(Typeface.create(selected?"sans-serif-medium":"sans-serif",0));item.addView(label);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(4),0,dp(4),0);nav.addView(item,lp);item.setOnClickListener(v->{if(!busy&&!page.equals(n))home(n);});
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
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(84));row.setPadding(dp(16),dp(14),dp(16),dp(14));row.setBackground(ripple(Color.WHITE,18));
        TextView sign=txt(symbol,22);sign.setTextColor(INK);sign.setGravity(Gravity.CENTER);sign.setPadding(0,0,0,0);sign.setBackground(shape(0xfff1f1f1,13,0));row.addView(sign,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout words=col();words.setPadding(dp(14),0,dp(8),0);TextView t=txt(title,15);t.setTypeface(Typeface.create("sans-serif-medium",0));t.setPadding(0,0,0,dp(5));words.addView(t);TextView d=txt(detail,12);d.setTextColor(MUTED);d.setPadding(0,0,0,0);words.addView(d);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=txt("â€º",24);arrow.setTextColor(0xff999999);row.addView(arrow);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);parent.addView(row,lp);row.setFocusable(true);row.setContentDescription(title+". "+detail);row.setOnClickListener(v->{if(!busy)action.run();});
    }
    void scannerCard(){
        boolean ready=connectedModel();LinearLayout c=card(body);c.setPadding(dp(16),dp(14),dp(16),dp(14));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);Icon scan=new Icon("Tasks",INK);row.addView(scan,new LinearLayout.LayoutParams(dp(20),dp(20)));LinearLayout text=col();text.setPadding(dp(12),0,0,0);TextView title=txt(ready?"Smart count is ready":"Smart count unavailable",13);title.setTypeface(Typeface.create("sans-serif-medium",0));text.addView(title);TextView hint=txt(ready?"Magnolia rice Â· Review every count":"Manual quantities are still available",11);hint.setTextColor(MUTED);text.addView(hint);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));c.addView(row);
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
        actionId="";shell("Sign in");eyebrow(body,"Warehouse, in order");
        TextView welcome=txt("A clear view.\nA better workday.",39);welcome.setTypeface(Typeface.create("sans-serif-medium",0));welcome.setLetterSpacing(-.055f);welcome.setPadding(0,dp(20),0,dp(16));body.addView(welcome);
        TextView intro=muted(body,"Your stock, movements, and daily counts.\nTogether in one place.");intro.setLineSpacing(dp(5),1);
        LinearLayout f=card(body);LinearLayout.LayoutParams fl=(LinearLayout.LayoutParams)f.getLayoutParams();fl.topMargin=dp(32);f.setLayoutParams(fl);
        TextView title=txt("Welcome back",21);title.setTypeface(Typeface.create("sans-serif-medium",0));f.addView(title);muted(f,"Sign in to your warehouse.");
        EditText u=field(f,"Username",false),p=field(f,"Password",false);u.setHint("Your username");p.setHint("Your password");p.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);p.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});u.setAutofillHints(View.AUTOFILL_HINT_USERNAME);p.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);u.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);p.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);u.setText(getPreferences(MODE_PRIVATE).getString("username",""));
        Button submit=primary(f,"Sign in  â†’",()->{
            final String user=u.getText().toString().trim(),pass=p.getText().toString();if(user.isEmpty()||pass.isEmpty()){error("Enter your username and password.");return;}
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(p.getWindowToken(),0);
            task(()->api("/login",obj("username",user,"password",pass)),r->{p.setText("");token=r.getString("token");username=r.getString("username");role=r.getString("role");getPreferences(MODE_PRIVATE).edit().putString("username",username).apply();page="Inventory";shell("Inventory");heading(body,"Opening your workspace");button(body,"Try again",()->load());load();});
        });
        p.setOnEditorActionListener((v,id,event)->{if(id==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){submit.performClick();return true;}return false;});
        TextView help=muted(body,"Need an account? Ask your administrator.");help.setGravity(Gravity.CENTER);help.setPadding(0,dp(10),0,dp(10));TextView version=muted(body,"MOVIS 0.9.0  /  ELDER JAY RICE MILL");version.setTextSize(9);version.setLetterSpacing(.1f);version.setGravity(Gravity.CENTER);version.setPadding(0,dp(26),0,0);
    }
    void load(){task(()->api("/v2/state",null),r->{state=r;updatedAt=java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"));home(page);});}
    String name(int id){JSONArray p=arr(state,"products");for(int i=0;i<p.length();i++)if(p.optJSONObject(i).optInt("id")==id)return p.optJSONObject(i).optString("name");return "Rice "+id;}
    boolean write(){return role.equals("operator")||role.equals("admin");}
    void home(String tab){
        page=tab;actionId="";shell(tab);
        if(tab.equals("Inventory")){
            eyebrow(body,java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d",Locale.getDefault())));heading(body,"Inventory");muted(body,"Everything accounted for.");
            long total=0;JSONArray products=arr(state,"products");for(int i=0;i<products.length();i++)total+=products.optJSONObject(i).optInt("quantity");
            LinearLayout hero=card(body);hero.setPadding(dp(24),dp(22),dp(24),dp(22));hero.setBackground(shape(INK,26,0));LinearLayout.LayoutParams hp=(LinearLayout.LayoutParams)hero.getLayoutParams();hp.topMargin=dp(20);hero.setLayoutParams(hp);
            TextView small=txt("TOTAL AVAILABLE",10);small.setLetterSpacing(.15f);small.setTextColor(0xffbcbcbc);hero.addView(small);TextView big=txt(String.format(Locale.US,"%,d",total),56);big.setTypeface(Typeface.create("sans-serif-light",0));big.setLetterSpacing(-.05f);big.setTextColor(Color.WHITE);big.setMaxLines(1);big.setAutoSizeTextTypeUniformWithConfiguration(28,56,1,android.util.TypedValue.COMPLEX_UNIT_SP);big.setPadding(0,dp(12),0,dp(6));hero.addView(big);TextView detail=txt("sacks across "+products.length()+" products",13);detail.setTextColor(0xffbcbcbc);hero.addView(detail);
            if(write()){
                LinearLayout quick=new LinearLayout(this);hero.addView(quick);Button receive=button(quick,"+  Stock in",()->start("stock-in"));Button dispatch=button(quick,"âˆ’  Stock out",()->start("stock-out"));receive.setBackground(ripple(Color.WHITE,12));dispatch.setTextColor(Color.WHITE);dispatch.setBackground(ripple(0xff343434,12));LinearLayout.LayoutParams q1=new LinearLayout.LayoutParams(0,-2,1);q1.setMargins(0,dp(20),dp(5),0);receive.setLayoutParams(q1);LinearLayout.LayoutParams q2=new LinearLayout.LayoutParams(0,-2,1);q2.setMargins(dp(5),dp(20),0,0);dispatch.setLayoutParams(q2);receive.setPadding(dp(4),0,dp(4),0);dispatch.setPadding(dp(4),0,dp(4),0);
            }
            scannerCard();eyebrow(body,"Your products");
            EditText search=new EditText(this);search.setSingleLine();search.setTextSize(14);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("Search products");search.setContentDescription("Search inventory products");search.setPadding(dp(16),dp(12),dp(16),dp(12));search.setMinHeight(dp(50));search.setBackground(shape(0xffeaeaea,14,0));body.addView(search,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout list=col();LinearLayout.LayoutParams listParams=new LinearLayout.LayoutParams(-1,-2);listParams.topMargin=dp(12);body.addView(list,listParams);productRows(list,products,"");search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){productRows(list,products,s.toString());}public void afterTextChanged(android.text.Editable e){}});
            TextView refreshed=muted(body,"Updated "+updatedAt+" Â· Refresh from your account menu");refreshed.setTextSize(11);refreshed.setPadding(0,dp(12),0,0);
        }else if(tab.equals("Tasks")){
            eyebrow(body,"Make a move");heading(body,"Tasks");muted(body,"Keep the day moving, one action at a time.");eyebrow(body,"New transaction");
            if(write()){
                actionRow(body,"+","Receive stock","Add incoming sacks",()->start("stock-in"));actionRow(body,"âˆ’","Dispatch stock","Record outgoing sacks",()->start("stock-out"));actionRow(body,"=","Count inventory","Compare your physical stock",()->start("count"));actionRow(body,"â†©","Record a return","Send returned sacks for inspection",()->start("return"));actionRow(body,"!","Report damage","Flag sacks for review",()->start("damage"));
            }
            actionRow(body,"â—Ž","Inspections","Track returns and damage reports",()->home("Inspections"));eyebrow(body,"In progress");JSONArray acts=arr(state,"actions");int drafts=0;
            for(int i=0;i<acts.length();i++){JSONObject a=acts.optJSONObject(i);if(!a.optString("state").equals("open"))continue;drafts++;actionRow(body,"Â·",label(a.optString("kind")),date(a.optString("created_at")),()->open(a.optString("id")));}
            if(drafts==0)emptyState(body,"All caught up","Your unfinished tasks will appear here.");
        }else if(tab.equals("Inspections")){
            button(body,"â€¹  Back to tasks",()->home("Tasks"));eyebrow(body,"Quality control");heading(body,"Inspections");muted(body,"Returns and damage, ready for review.");JSONArray cases=arr(state,"cases");
            if(cases.length()==0)emptyState(body,"Nothing to review","Submitted returns and damage reports appear here.");
            for(int i=0;i<cases.length();i++){JSONObject r=cases.optJSONObject(i);LinearLayout c=card(body);tag(c,r.optString("status").replace('_',' '),r.optInt("remaining")==0);heading(c,r.optString("name"));c.addView(txt(r.optInt("quantity")+" sacks Â· "+label(r.optString("kind")),17));muted(c,r.optInt("remaining")+" awaiting review");muted(c,r.optString("condition")+"\n"+r.optString("reason"));}
        }else{
            eyebrow(body,"Every movement, recorded");heading(body,"Activity");muted(body,"A clear record of what changed and when.");eyebrow(body,"Recent movements");JSONArray history=arr(state,"history");
            if(history.length()==0)emptyState(body,"A fresh start","Completed stock movements will appear here.");
            LinearLayout historyGroup=history.length()>0?card(body):null;
            for(int i=0;i<Math.min(history.length(),100);i++){
                JSONObject r=history.optJSONObject(i);if(i>0)divider(historyGroup);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(14),0,dp(14));LinearLayout words=col();TextView product=txt(r.optString("name"),15);product.setTypeface(Typeface.create("sans-serif-medium",0));words.addView(product);muted(words,label(r.optString("kind"))+" Â· "+date(r.optString("created_at")));TextView by=muted(words,r.optString("username")+" Â· "+r.optInt("previous_quantity")+" â†’ "+r.optInt("quantity"));by.setTextSize(11);if(!r.optString("reason").isEmpty())muted(words,r.optString("reason"));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));int diff=r.optInt("difference");TextView change=txt((diff>0?"+":"")+diff,25);change.setTypeface(Typeface.create("sans-serif-medium",0));change.setContentDescription((diff>0?"Added ":"Removed ")+Math.abs(diff)+" sacks");row.addView(change);historyGroup.addView(row);
            }
            eyebrow(body,"Completed tasks");JSONArray acts=arr(state,"actions");for(int i=0;i<acts.length();i++){JSONObject a=acts.optJSONObject(i);if(!a.optString("state").equals("open"))actionRow(body,"âœ“",label(a.optString("kind")),date(a.optString("created_at")),()->open(a.optString("id")));}
        }
        bottomNav();
    }
    void emptyState(LinearLayout parent,String title,String detail){LinearLayout c=card(parent);c.setPadding(dp(24),dp(28),dp(24),dp(28));TextView mark=txt("â€”",28);mark.setTextColor(0xffa0a0a0);c.addView(mark);TextView name=txt(title,19);name.setTypeface(Typeface.create("sans-serif-medium",0));c.addView(name);muted(c,detail);}
    void productRows(LinearLayout parent,JSONArray products,String query){
        parent.removeAllViews();String filter=query.trim().toLowerCase(Locale.ROOT);LinearLayout group=null;int shown=0;
        for(int i=0;i<products.length();i++){
            JSONObject item=products.optJSONObject(i);if(!(item.optString("name")+" "+item.optString("sku")).toLowerCase(Locale.ROOT).contains(filter))continue;
            if(group==null){group=card(parent);group.setPadding(dp(16),dp(2),dp(16),dp(2));}if(shown++>0)divider(group);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(18),0,dp(18));SackIcon sack=new SackIcon(this);row.addView(sack,new LinearLayout.LayoutParams(dp(40),dp(44)));LinearLayout info=col();info.setPadding(dp(12),0,dp(8),0);TextView name=txt(item.optString("name"),15);name.setTypeface(Typeface.create("sans-serif-medium",0));info.addView(name);TextView sku=muted(info,item.optBoolean("scan_supported")?"Smart count available":item.optString("sku"));sku.setTextSize(11);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));LinearLayout amounts=col();amounts.setGravity(Gravity.END);TextView qty=txt(String.format(Locale.US,"%,d",item.optInt("quantity")),25);qty.setTypeface(Typeface.create("sans-serif-medium",0));qty.setLetterSpacing(-.03f);amounts.addView(qty);TextView unit=muted(amounts,"sacks");unit.setTextSize(11);row.addView(amounts);group.addView(row);
        }
        if(shown==0)emptyState(parent,"No matching products","Try a product name or SKU.");
    }
    void start(String kind){LinearLayout f=col();f.setPadding(dp(18),dp(8),dp(18),dp(8));f.addView(txt("Select the rice products included in this action.",15));ArrayList<CheckBox> picks=new ArrayList<>();JSONArray ps=arr(state,"products");for(int i=0;i<ps.length();i++)picks.add(check(f,ps.optJSONObject(i).optString("name")+(ps.optJSONObject(i).optBoolean("scan_supported")?" Â· smart count":" Â· manual count"),ps.optJSONObject(i).optBoolean("scan_supported")));AlertDialog d=new AlertDialog.Builder(this).setTitle(label(kind)).setView(f).setNegativeButton("Cancel",null).setPositiveButton("Start",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{JSONArray ids=new JSONArray();for(int i=0;i<picks.size();i++)if(picks.get(i).isChecked())ids.put(ps.optJSONObject(i).optInt("id"));if(ids.length()==0){error("Choose at least one rice product");return;}d.dismiss();task(()->api("/v2/open",request("open",obj("kind",kind,"products",ids))),r->{keys.entrySet().removeIf(e->e.getKey().startsWith("open"));afterMutation(r,r.optString("action_id"));});}));showSheet(d);}
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
    void actionScreen(){
        shell(label(action.optString("kind")));button(body,"â€¹  Back to tasks",()->onBackPressed());eyebrow(body,"Stock movement");heading(body,label(action.optString("kind")));boolean editable=action.optString("state").equals("open");tag(body,editable?"In progress":"Completed",true);muted(body,"Photograph. Review. Confirm.");
        if(pendingImage!=null&&pendingImage.optString("action_id").equals(actionId)&&editable)button(body,"Retry photo upload",()->sendImage());
        JSONArray images=arr(action,"images");HashMap<Integer,Integer> totals=new HashMap<>();
        if(editable)primary(body,"+  Take a photo",()->{if(!connectedModel()){error("Smart counting is unavailable. Contact your administrator or enter verified quantities manually.");return;}if(pendingImage!=null){error("Retry or discard your pending photo first.");return;}startActivityForResult(new Intent(this,CameraActivity.class),101);});
        eyebrow(body,"Photos");int kept=0;
        for(int i=0;i<images.length();i++){
            JSONObject im=images.optJSONObject(i);if(im.optInt("deleted")==1)continue;kept++;byte[] bytes=Base64.decode(im.optString("image"),Base64.DEFAULT);BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=4;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);LinearLayout c=card(body);c.setPadding(dp(12),dp(12),dp(12),dp(12));
            if(bitmap!=null){ImageView preview=new ImageView(this);preview.setImageBitmap(bitmap);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);preview.setBackground(shape(0xffeeeeee,14,0));preview.setClipToOutline(true);preview.setContentDescription("Open photo "+kept);c.addView(preview,new LinearLayout.LayoutParams(-1,dp(200)));preview.setOnClickListener(v->enlarge(im));}
            TextView caption=txt("Photo "+String.format(Locale.US,"%02d",kept)+"  Â·  "+(im.optInt("reviewed")==1?"Reviewed":"Needs your review"),12);caption.setPadding(dp(4),dp(14),dp(4),dp(4));c.addView(caption);
            if(editable){primary(c,im.optInt("reviewed")==1?"Edit reviewed quantities":"Review this photo",()->review(im));Button remove=button(c,"Remove photo",()->new AlertDialog.Builder(this).setTitle("Remove this photo?").setMessage("Its reviewed quantities will be removed from this task.").setNegativeButton("Keep photo",null).setPositiveButton("Remove",(dialog,which)->{JSONObject d=base();try{d.put("image_id",im.optString("id"));}catch(JSONException ignored){}task(()->api("/v2/delete",request("delete",d)),r->{pendingImage=null;afterMutation(r,actionId);});}).show());remove.setBackgroundColor(Color.TRANSPARENT);remove.setTextColor(MUTED);}
            JSONArray lines=arr(im,"checked");if(im.optInt("reviewed")==1)for(int j=0;j<lines.length();j++){JSONObject l=lines.optJSONObject(j);int pid=l.optInt("product_id");totals.put(pid,totals.getOrDefault(pid,0)+l.optInt("quantity"));}
        }
        if(kept==0)emptyState(body,"Start with a clear photo","Keep every sack visible and count each one only once.");
        eyebrow(body,"Reviewed quantities");LinearLayout summary=card(body);JSONArray snapshot=arr(action,"snapshot");for(int i=0;i<snapshot.length();i++){int pid=snapshot.optJSONObject(i).optInt("product_id");if(i>0)divider(summary);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView product=txt(name(pid),15);row.addView(product,new LinearLayout.LayoutParams(0,-2,1));TextView count=txt(totals.getOrDefault(pid,0)+" sacks",20);count.setTypeface(Typeface.create("sans-serif-medium",0));row.addView(count);summary.addView(row);}
        if(editable){primary(body,action.optString("kind").equals("count")?"Review & finish count":"Review & submit",()->completeDialog());button(body,"Refresh task",()->open(actionId));if(action.optString("kind").equals("count"))button(body,"Review changed stock baseline",()->new AlertDialog.Builder(this).setTitle("Recheck affected sacks").setMessage("This reads current stock and resets image verification. Physically recheck the affected sacks and every retained image before finishing.").setNegativeButton("Cancel",null).setPositiveButton("I rechecked the sacks",(d,w)->{JSONObject p=base();try{p.put("confirmed_review",true);}catch(JSONException ignored){}task(()->api("/v2/refresh-count",request("refresh-count",p)),r->afterMutation(r,actionId));}).show());}
        else if(action.optString("kind").equals("count")){JSONArray comp=arr(action,"comparison");for(int i=0;i<comp.length();i++){JSONObject r=comp.optJSONObject(i);LinearLayout c=card(body);heading(c,name(r.optInt("product_id")));muted(c,"Recorded "+r.optInt("quantity")+"  Â·  Counted "+r.optInt("count"));c.addView(txt("Difference "+r.optInt("difference")+(r.optBoolean("adjusted")?" Â· Corrected":""),18));}if(state.optJSONObject("user").optBoolean("can_adjust"))primary(body,"Confirm adjustment",()->adjust());else muted(body,"An authorized user must confirm stock corrections.");}
    }
    Bitmap reviewBitmap(JSONObject im){byte[] bytes=Base64.decode(im.optString("image"),Base64.DEFAULT);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);}
    Dialog photoScreen(String title,LinearLayout content,LinearLayout footer){
        Dialog dialog=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);LinearLayout layout=col();layout.setBackgroundColor(Color.WHITE);
        layout.setOnApplyWindowInsetsListener((v,in)->{if(Build.VERSION.SDK_INT>=30){Insets bars=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());Insets ime=in.getInsets(WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));}else v.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in;});
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(20),dp(10),dp(12),dp(10));TextView name=txt(title,18);name.setTypeface(Typeface.create("sans-serif-medium",0));bar.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView close=txt("Ã—",30);close.setGravity(Gravity.CENTER);close.setPadding(0,0,0,0);close.setBackground(ripple(0xfff0f0f0,24));close.setContentDescription("Close photo review");close.setFocusable(true);bar.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));close.setOnClickListener(v->dialog.dismiss());layout.addView(bar);divider(layout);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.addView(content);layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));if(footer!=null){divider(layout);footer.setPadding(dp(24),dp(10),dp(24),dp(14));layout.addView(footer);}
        dialog.setContentView(layout);Window window=dialog.getWindow();if(window!=null){window.setStatusBarColor(Color.WHITE);window.setNavigationBarColor(Color.WHITE);window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}dialog.show();if(window!=null)window.setLayout(-1,-1);layout.requestApplyInsets();return dialog;
    }
    void detectionLegend(LinearLayout content,JSONArray detections){
        if(detections.length()==0){muted(content,"No sacks detected. Enter the physical count below.");return;}eyebrow(content,"Model suggestions");
        for(int i=0;i<detections.length();i++){JSONObject r=detections.optJSONObject(i);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView number=txt(String.valueOf(i+1),11);number.setGravity(Gravity.CENTER);number.setTextColor(Color.WHITE);number.setPadding(0,0,0,0);number.setBackground(shape(INK,8,0));row.addView(number,new LinearLayout.LayoutParams(dp(28),dp(28)));TextView label=txt(r.optString("name"),13);label.setPadding(dp(10),dp(10),dp(6),dp(10));row.addView(label,new LinearLayout.LayoutParams(0,-2,1));TextView score=txt(Math.round(r.optDouble("confidence")*100)+"%",12);score.setTextColor(MUTED);row.addView(score);content.addView(row);}
    }
    void enlarge(JSONObject im){Bitmap b=reviewBitmap(im);if(b==null){error("Unable to open this photo.");return;}LinearLayout f=col();f.setPadding(dp(20),dp(16),dp(20),dp(24));Boxes boxes=new Boxes(b,arr(im,"raw"));f.addView(boxes,new LinearLayout.LayoutParams(-1,dp(400)));detectionLegend(f,arr(im,"raw"));Dialog dialog=photoScreen("Photo details",f,null);dialog.setOnDismissListener(d->b.recycle());}
    void review(JSONObject im){
        Bitmap bitmap=reviewBitmap(im);LinearLayout f=col();f.setPadding(dp(24),dp(16),dp(24),dp(24));JSONArray snap=arr(action,"snapshot");ArrayList<EditText> quantities=new ArrayList<>();HashMap<Integer,Integer> raw=new HashMap<>(),checked=new HashMap<>();
        for(int i=0;i<arr(im,"raw").length();i++){JSONObject detection=arr(im,"raw").optJSONObject(i);int pid=detection.optInt("product_id");if(pid>0)raw.put(pid,raw.getOrDefault(pid,0)+1);}for(int i=0;i<arr(im,"checked").length();i++){JSONObject line=arr(im,"checked").optJSONObject(i);checked.put(line.optInt("product_id"),line.optInt("quantity"));}
        if(bitmap!=null){Boxes boxes=new Boxes(bitmap,arr(im,"raw"));f.addView(boxes,new LinearLayout.LayoutParams(-1,dp(300)));}detectionLegend(f,arr(im,"raw"));
        eyebrow(f,"Confirm the physical count");TextView instruction=muted(f,"Check each numbered sack. Correct duplicates, missed sacks, and other brands before saving.");instruction.setPadding(0,0,0,dp(12));
        for(int i=0;i<arr(im,"raw").length();i++)if(arr(im,"raw").optJSONObject(i).isNull("product_id")){tag(f,"Unmatched product â€” verify manually",false);break;}
        for(int i=0;i<snap.length();i++){
            int pid=snap.optJSONObject(i).optInt("product_id");LinearLayout c=card(f);TextView product=txt(name(pid),16);product.setTypeface(Typeface.create("sans-serif-medium",0));c.addView(product);muted(c,raw.getOrDefault(pid,0)+" model detections Â· Verify the actual sacks");
            LinearLayout stepper=new LinearLayout(this);stepper.setGravity(Gravity.CENTER_VERTICAL);Button minus=button(stepper,"âˆ’",()->{});EditText q=new EditText(this);q.setInputType(InputType.TYPE_CLASS_NUMBER);q.setSingleLine();q.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10)});q.setText(String.valueOf(checked.getOrDefault(pid,raw.getOrDefault(pid,0))));q.setTextColor(INK);q.setTextSize(30);q.setGravity(Gravity.CENTER);q.setBackgroundColor(Color.TRANSPARENT);q.setSelectAllOnFocus(true);q.setContentDescription(name(pid)+" verified quantity in sacks");stepper.addView(q,new LinearLayout.LayoutParams(0,dp(60),1));Button plus=button(stepper,"+",()->{});minus.setLayoutParams(new LinearLayout.LayoutParams(dp(52),dp(52)));plus.setLayoutParams(new LinearLayout.LayoutParams(dp(52),dp(52)));minus.setContentDescription("Decrease "+name(pid)+" quantity");plus.setContentDescription("Increase "+name(pid)+" quantity");
            minus.setOnClickListener(v->{try{long value=Long.parseLong(q.getText().toString());q.setText(String.valueOf(Math.max(0,value-1)));}catch(NumberFormatException e){q.setText("0");}});plus.setOnClickListener(v->{try{long value=Long.parseLong(q.getText().toString());if(value<Integer.MAX_VALUE)q.setText(String.valueOf(value+1));}catch(NumberFormatException e){q.setText("1");}});c.addView(stepper);quantities.add(q);
        }
        CheckBox confirmed=check(f,"I checked the photo and verified every quantity.",false);TextView err=txt("",13);err.setTextColor(0xffa32929);err.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);f.addView(err);
        LinearLayout footer=col();final Dialog[] holder=new Dialog[1];Button save=primary(footer,"Save verified quantities",()->{
            try{if(!confirmed.isChecked())throw new Exception("Confirm that you checked the photo first.");JSONArray lines=new JSONArray();for(int i=0;i<quantities.size();i++){int qty=Integer.parseInt(quantities.get(i).getText().toString());if(qty<0)throw new Exception("Use nonnegative whole sacks.");lines.put(obj("product_id",snap.optJSONObject(i).optInt("product_id"),"quantity",qty));}JSONObject p=base();p.put("image_id",im.optString("id"));p.put("checked",lines);p.put("confirmed",true);holder[0].dismiss();task(()->api("/v2/review",request("review",p)),r->afterMutation(r,actionId));}catch(NumberFormatException e){err.setText("Enter a valid whole sack count.");}catch(Exception e){err.setText(e.getMessage());}
        });holder[0]=photoScreen("Review quantities",f,footer);holder[0].setOnDismissListener(d->{if(bitmap!=null)bitmap.recycle();});
    }
    void completeDialog(){ScrollView scroll=new ScrollView(this);LinearLayout f=col();f.setPadding(dp(18),dp(8),dp(18),dp(8));scroll.addView(f);boolean count=action.optString("kind").equals("count"),ret=action.optString("kind").equals("return")||action.optString("kind").equals("damage");EditText reason=field(f,"Transaction / count reason",false);CheckBox unique=check(f,"Every sack is included only once across retained images and checked entries",false);JSONArray snap=arr(action,"snapshot");ArrayList<EditText> manual=new ArrayList<>(),why=new ArrayList<>();ArrayList<CheckBox> full=new ArrayList<>(),zero=new ArrayList<>();for(int i=0;i<snap.length();i++){int pid=snap.optJSONObject(i).optInt("product_id");heading(f,name(pid));EditText q=field(f,"Additional separately checked sacks (not in images)",true);q.setText("0");manual.add(q);why.add(field(f,"Reason for any additional checked quantity",false));if(count){full.add(check(f,"All sacks of this product were physically checked",false));zero.add(check(f,"If total is zero, I physically confirmed no sacks remain",false));}}CheckBox stocked=ret?check(f,"These sacks are already included in available stock",action.optString("kind").equals("damage")):null;EditText condition=ret?field(f,"Rice and packaging condition",false):null;TextView err=txt("",14);f.addView(err);AlertDialog d=new AlertDialog.Builder(this).setTitle(count?"Finish count - no stock change":"Review final transaction").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton(count?"Finish count":"Submit transaction",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{if(!unique.isChecked()||reason.getText().toString().trim().isEmpty())throw new Exception("Enter a reason and confirm sacks are counted once");JSONArray entries=new JSONArray();JSONObject fulls=new JSONObject(),zeros=new JSONObject();for(int i=0;i<snap.length();i++){int pid=snap.optJSONObject(i).optInt("product_id"),qty=Integer.parseInt(manual.get(i).getText().toString());if(qty<0)throw new Exception("Use nonnegative whole sacks");if(qty>0){if(why.get(i).getText().toString().trim().isEmpty())throw new Exception("Explain separately checked quantities");entries.put(obj("product_id",pid,"quantity",qty,"reason",why.get(i).getText().toString()));}if(count){if(!full.get(i).isChecked())throw new Exception("Confirm every chosen product was fully counted");fulls.put(String.valueOf(pid),full.get(i).isChecked());zeros.put(String.valueOf(pid),zero.get(i).isChecked());}}JSONObject p=base();p.put("reason",reason.getText().toString());p.put("unique_sacks",true);p.put("manual",entries);if(count){p.put("full_counts",fulls);p.put("zero_confirmed",zeros);}if(ret){p.put("already_stocked",stocked.isChecked());p.put("condition",condition.getText().toString());}String endpoint=count?"finish":"submit";JSONObject payload=request(endpoint,p);d.dismiss();task(()->api("/v2/"+endpoint,payload),r->{pendingImage=null;afterMutation(r,actionId);});}catch(Exception e){err.setText(e.getMessage());}}));showSheet(d);}
    void adjust(){LinearLayout f=col();f.setPadding(dp(18),dp(8),dp(18),dp(8));JSONArray comp=arr(action,"comparison");ArrayList<Integer> ids=new ArrayList<>();ArrayList<CheckBox> picks=new ArrayList<>();for(int i=0;i<comp.length();i++){JSONObject r=comp.optJSONObject(i);if(r.optInt("difference")!=0&&!r.optBoolean("adjusted")){ids.add(r.optInt("product_id"));picks.add(check(f,name(r.optInt("product_id"))+": "+r.optInt("quantity")+"  ->  "+r.optInt("count"),true));}}EditText reason=field(f,"Stock correction reason",false);TextView err=txt("",14);f.addView(err);AlertDialog d=new AlertDialog.Builder(this).setTitle("Confirm separate adjustment").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Confirm adjustment",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{JSONArray chosen=new JSONArray();for(int i=0;i<picks.size();i++)if(picks.get(i).isChecked())chosen.put(ids.get(i));if(chosen.length()==0||reason.getText().toString().trim().isEmpty())throw new Exception("Select a difference and enter a reason");JSONObject p=base();p.put("products",chosen);p.put("reason",reason.getText().toString());p.put("confirmed",true);d.dismiss();task(()->api("/v2/adjust",request("adjust",p)),r->afterMutation(r,actionId));}catch(Exception e){err.setText(e.getMessage());}}));showSheet(d);}
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
    class Boxes extends View{
        final Bitmap bitmap;final JSONArray boxes;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF imageRect=new RectF();
        Boxes(Bitmap bitmap,JSONArray boxes){super(RiceActivity.this);this.bitmap=bitmap;this.boxes=boxes;setContentDescription("Photo with "+boxes.length()+" numbered detection boxes; details listed below");}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);if(bitmap.isRecycled())return;canvas.drawColor(0xffeeeeee);float scale=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());float width=bitmap.getWidth()*scale,height=bitmap.getHeight()*scale,left=(getWidth()-width)/2,top=(getHeight()-height)/2;imageRect.set(left,top,left+width,top+height);paint.setStyle(Paint.Style.FILL);canvas.drawBitmap(bitmap,null,imageRect,paint);
            for(int i=0;i<boxes.length();i++){JSONObject detection=boxes.optJSONObject(i);JSONArray box=detection.optJSONArray("box");if(box==null||box.length()!=4)continue;float x1=left+(float)box.optDouble(0)*width,y1=top+(float)box.optDouble(1)*height,x2=left+(float)box.optDouble(2)*width,y2=top+(float)box.optDouble(3)*height;paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(4));canvas.drawRect(x1,y1,x2,y2,paint);paint.setColor(INK);paint.setStrokeWidth(dp(2));canvas.drawRect(x1,y1,x2,y2,paint);float size=dp(25),x=Math.max(left,Math.min(left+width-size,x1)),y=Math.max(top,Math.min(top+height-size,y1));paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(x,y,x+size,y+size,dp(5),dp(5),paint);paint.setColor(Color.WHITE);paint.setTextSize(dp(12));paint.setTypeface(Typeface.create("sans-serif-medium",0));paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(String.valueOf(i+1),x+size/2,y+size/2-(paint.ascent()+paint.descent())/2,paint);paint.setTextAlign(Paint.Align.LEFT);}
        }
    }

}


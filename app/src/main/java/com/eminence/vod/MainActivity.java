package com.eminence.vod;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** TV launcher for the publicly accessible site, using its own player. */
public final class MainActivity extends Activity {
    private static final String HOME = "https://dulo.mov/";
    private static final int GOLD = Color.rgb(230,185,93);
    private FrameLayout root, contentLayer;
    private LinearLayout bar;
    private WebView web;
    private TextView cursor;
    private View fullScreenView;
    private WebChromeClient.CustomViewCallback fullScreenCallback;
    private boolean cursorMode = true, barVisible = true;
    private float cursorX=300, cursorY=220;
    private final Handler handler=new Handler();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        TextView splash=new TextView(this);
        splash.setText("Ⓔ\nEMINENCE VOD");splash.setGravity(Gravity.CENTER);
        splash.setTextColor(GOLD);splash.setTextSize(45);splash.setBackgroundColor(Color.BLACK);
        root.addView(splash,new FrameLayout.LayoutParams(-1,-1));
        handler.postDelayed(()->{root.removeView(splash);createUi();},850);
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable panel(int color,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
    }
    private void addButton(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setTextColor(GOLD);b.setTextSize(13);
        b.setAllCaps(false);b.setBackground(panel(0xff202020,7));
        b.setOnClickListener(v->action.run());b.setFocusable(true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(46));
        lp.setMargins(dp(5),dp(5),dp(5),dp(5));bar.addView(b,lp);
    }
    private void createUi(){
        contentLayer=new FrameLayout(this);root.addView(contentLayer,new FrameLayout.LayoutParams(-1,-1));
        web=new WebView(this);web.setBackgroundColor(Color.BLACK);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);s.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();if("https".equalsIgnoreCase(u.getScheme()))return false;
                Toast.makeText(MainActivity.this,"Unsupported link",Toast.LENGTH_SHORT).show();return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View v,CustomViewCallback cb){
                if(fullScreenView!=null){cb.onCustomViewHidden();return;}
                fullScreenView=v;fullScreenCallback=cb;contentLayer.setVisibility(View.GONE);bar.setVisibility(View.GONE);
                root.addView(v,new FrameLayout.LayoutParams(-1,-1));
            }
            @Override public void onHideCustomView(){exitVideo();}
        });
        contentLayer.addView(web,new FrameLayout.LayoutParams(-1,-1));
        cursor=new TextView(this);cursor.setText("+");cursor.setTextColor(Color.BLACK);cursor.setTextSize(18);
        cursor.setGravity(Gravity.CENTER);cursor.setBackground(panel(GOLD,6));
        contentLayer.addView(cursor,new FrameLayout.LayoutParams(dp(22),dp(22)));
        bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBackgroundColor(0xed080808);
        root.addView(bar,new FrameLayout.LayoutParams(-1,dp(58),Gravity.TOP));
        TextView title=new TextView(this);title.setText("EMINENCE VOD");title.setTextColor(GOLD);
        title.setTextSize(18);title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title,new LinearLayout.LayoutParams(0,dp(50),1));
        addButton("Home",()->web.loadUrl(HOME));
        addButton("Back",()->{if(web.canGoBack())web.goBack();});
        addButton("Reload",()->web.reload());
        addButton("Cursor",()->{cursorMode=!cursorMode;cursor.setVisibility(cursorMode?View.VISIBLE:View.GONE);});
        addButton("Browser",this::openExternal);
        contentLayer.post(this::updateCursor);web.loadUrl(HOME);
        Toast.makeText(this,"Arrows move pointer • OK selects • Menu toggles bar",Toast.LENGTH_LONG).show();
    }
    private void openExternal(){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(web.getUrl()==null?HOME:web.getUrl())));}
        catch(ActivityNotFoundException e){Toast.makeText(this,"No TV browser installed",Toast.LENGTH_SHORT).show();}
    }
    private void updateCursor(){
        if(cursor==null||contentLayer==null)return;
        int w=contentLayer.getWidth(),h=contentLayer.getHeight();
        if(w<1||h<1)return;
        cursorX=Math.max(12,Math.min(w-12,cursorX));cursorY=Math.max(72,Math.min(h-12,cursorY));
        cursor.setX(cursorX-dp(11));cursor.setY(cursorY-dp(11));
    }
    private void clickAtCursor(){
        long t=android.os.SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,cursorX,cursorY,0);
        MotionEvent up=MotionEvent.obtain(t,t+70,MotionEvent.ACTION_UP,cursorX,cursorY,0);
        web.dispatchTouchEvent(down);web.dispatchTouchEvent(up);down.recycle();up.recycle();
    }
    private void exitVideo(){
        if(fullScreenView==null)return;
        root.removeView(fullScreenView);fullScreenView=null;contentLayer.setVisibility(View.VISIBLE);
        bar.setVisibility(barVisible?View.VISIBLE:View.GONE);
        if(fullScreenCallback!=null){fullScreenCallback.onCustomViewHidden();fullScreenCallback=null;}
    }
    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(fullScreenView!=null){if(code==KeyEvent.KEYCODE_BACK){exitVideo();return true;}return super.onKeyDown(code,event);}
        if(web==null)return super.onKeyDown(code,event);
        if(code==KeyEvent.KEYCODE_MENU){barVisible=!barVisible;bar.setVisibility(barVisible?View.VISIBLE:View.GONE);return true;}
        if(code==KeyEvent.KEYCODE_BACK){if(web.canGoBack())web.goBack();else web.loadUrl(HOME);return true;}
        if(code==KeyEvent.KEYCODE_CHANNEL_DOWN){web.pageDown(false);return true;}
        if(code==KeyEvent.KEYCODE_CHANNEL_UP){web.pageUp(false);return true;}
        if(cursorMode){
            int step=dp(38);
            switch(code){
                case KeyEvent.KEYCODE_DPAD_LEFT:cursorX-=step;updateCursor();return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:cursorX+=step;updateCursor();return true;
                case KeyEvent.KEYCODE_DPAD_UP:cursorY-=step;updateCursor();return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:cursorY+=step;updateCursor();return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:clickAtCursor();return true;
            }
        }
        return super.onKeyDown(code,event);
    }
    @Override public void onBackPressed(){
        if(fullScreenView!=null)exitVideo();
        else if(web!=null&&web.canGoBack())web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}

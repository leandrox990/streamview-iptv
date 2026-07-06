package com.streamview.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends Activity {

    private WebView web;
    private FrameLayout root;

    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout playerContainer;
    private TextView banner;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable hideBanner;

    private boolean fsActive = false;     // pantalla completa
    private boolean previewing = false;   // preview en el panel derecho
    private int pX, pY, pW, pH;           // rect del preview (px)
    private String currentTitle = "";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        setContentView(root);

        // --- WebView ---
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setTextZoom(100); // ignora la escala de fuente del sistema (evita texto gigante)
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new NativeBridge(), "AndroidTV");

        // --- Reproductor nativo (encima, oculto). No roba el foco del mando. ---
        playerContainer = new FrameLayout(this);
        playerContainer.setBackgroundColor(Color.BLACK);
        playerContainer.setVisibility(View.GONE);
        playerContainer.setFocusable(false);
        playerContainer.setFocusableInTouchMode(false);

        // PlayerView con TextureView (se superpone bien en sub-región sobre el WebView)
        playerView = (PlayerView) LayoutInflater.from(this).inflate(R.layout.player, playerContainer, false);
        playerView.setFocusable(false);
        playerContainer.addView(playerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        banner = new TextView(this);
        banner.setTextColor(Color.WHITE);
        banner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        banner.setPadding(dp(20), dp(12), dp(20), dp(12));
        banner.setBackgroundColor(Color.parseColor("#B3000000"));
        banner.setVisibility(View.GONE);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.gravity = Gravity.BOTTOM | Gravity.START;
        bl.setMargins(dp(28), 0, 0, dp(36));
        playerContainer.addView(banner, bl);

        root.addView(playerContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setPlayWhenReady(true);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) { showBanner("Canal no disponible"); }
        });

        web.loadUrl("file:///android_asset/index.html");
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // ---------- Puente JavaScript ----------
    private class NativeBridge {
        @JavascriptInterface
        public void setSource(final String url, final String title) {
            ui.post(() -> { currentTitle = title == null ? "" : title;
                if (url == null || url.isEmpty()) return;
                player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.play();
                if (fsActive) showBanner(currentTitle);
            });
        }
        @JavascriptInterface
        public void showPreview(final int x, final int y, final int w, final int h) {
            ui.post(() -> { pX = x; pY = y; pW = w; pH = h; previewing = true; fsActive = false;
                applyBounds(x, y, w, h); playerContainer.setVisibility(View.VISIBLE); });
        }
        @JavascriptInterface
        public void enterFs() {
            ui.post(() -> { fsActive = true; applyFull(); enterImmersive();
                playerContainer.setVisibility(View.VISIBLE); showBanner(currentTitle); });
        }
        @JavascriptInterface
        public void exitFs() {
            ui.post(() -> backToPreview());
        }
        @JavascriptInterface
        public void hide() {
            ui.post(() -> { previewing = false; fsActive = false; player.stop();
                playerContainer.setVisibility(View.GONE); exitImmersive(); });
        }
    }

    private void applyBounds(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x; lp.topMargin = y; lp.gravity = Gravity.TOP | Gravity.START;
        playerContainer.setLayoutParams(lp);
    }
    private void applyFull() {
        playerContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void backToPreview() {
        fsActive = false;
        exitImmersive();
        if (previewing) { applyBounds(pX, pY, pW, pH); }
        else { playerContainer.setVisibility(View.GONE); }
        banner.setVisibility(View.GONE);
        web.evaluateJavascript("window.tvExit && window.tvExit();", null);
    }

    private void showBanner(final String text) {
        if (text != null && text.length() > 0) banner.setText(text);
        banner.setVisibility(View.VISIBLE);
        if (hideBanner != null) ui.removeCallbacks(hideBanner);
        hideBanner = () -> banner.setVisibility(View.GONE);
        ui.postDelayed(hideBanner, 3500);
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    private void exitImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    // ---------- Mando ----------
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (fsActive && event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    web.evaluateJavascript("window.tvNav && window.tvNav(1);", null); showBanner(null); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    web.evaluateJavascript("window.tvNav && window.tvNav(-1);", null); showBanner(null); return true;
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    backToPreview(); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    showBanner(null); return true; // en vivo: sin pausa
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (fsActive) { backToPreview(); return true; }
            if (previewing) { // salir del preview y volver solo a la lista
                previewing = false; player.stop(); playerContainer.setVisibility(View.GONE);
                web.evaluateJavascript("window.tvHidden && window.tvHidden();", null); return true;
            }
            if (web.canGoBack()) { web.goBack(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onPause() { super.onPause(); if (player != null) player.pause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); if (player != null && (fsActive || previewing)) player.play(); }
    @Override protected void onDestroy() { if (player != null) { player.release(); player = null; } super.onDestroy(); }
}

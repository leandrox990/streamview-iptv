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
import android.widget.Toast;

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

    // Reproductor nativo para pantalla completa
    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout playerContainer;
    private TextView banner;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable hideBanner;

    private boolean fsActive = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        setContentView(root);

        // --- WebView (interfaz) ---
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
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new NativeBridge(), "AndroidTV");

        // --- Contenedor del reproductor nativo (encima, oculto) ---
        playerContainer = new FrameLayout(this);
        playerContainer.setBackgroundColor(Color.BLACK);
        playerContainer.setVisibility(View.GONE);

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
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
            @Override
            public void onPlayerError(PlaybackException error) {
                showBanner("Canal no disponible");
            }
        });

        web.loadUrl("file:///android_asset/index.html");
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // ---------- Puente JavaScript ----------
    private class NativeBridge {
        @JavascriptInterface
        public void fsPlay(final String url, final String title) {
            ui.post(() -> startFullscreen(url, title));
        }
        @JavascriptInterface
        public void fsStop() {
            ui.post(() -> stopFullscreen());
        }
    }

    private void startFullscreen(String url, String title) {
        if (url == null || url.isEmpty()) return;
        fsActive = true;
        playerContainer.setVisibility(View.VISIBLE);
        playerContainer.requestFocus();
        enterImmersive();
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.play();
        showBanner(title);
    }

    private void stopFullscreen() {
        fsActive = false;
        player.stop();
        player.clearMediaItems();
        playerContainer.setVisibility(View.GONE);
        exitImmersive();
        // Devolver el foco a la lista de canales en la web
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
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void exitImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    // ---------- Mando ----------
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (fsActive && event.getAction() == KeyEvent.ACTION_DOWN) {
            int k = event.getKeyCode();
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    web.evaluateJavascript("window.tvNav && window.tvNav(1);", null);
                    showBanner(null);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    web.evaluateJavascript("window.tvNav && window.tvNav(-1);", null);
                    showBanner(null);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    stopFullscreen();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    showBanner(null); // mostrar info; sin pausa (es en vivo)
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (fsActive) { stopFullscreen(); return true; }
            if (web.canGoBack()) { web.goBack(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        if (player != null && fsActive) player.play();
    }

    @Override
    protected void onDestroy() {
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}

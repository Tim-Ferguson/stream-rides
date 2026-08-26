package com.pelotonhack.ridestarter;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public final class GameActivity extends Activity implements GameView.Host {
    private static volatile boolean foreground;
    private GameView gameView;

    static boolean isForeground() {
        return foreground;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        gameView = new GameView(this, this);
        setContentView(gameView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        gameView.onHostResume();
        RideStarterAccessibilityService.refreshOverlay();
    }

    @Override
    protected void onPause() {
        foreground = false;
        gameView.onHostPause();
        RideStarterAccessibilityService.refreshOverlay();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (!gameView.handleBack()) {
            super.onBackPressed();
        }
    }

    @Override
    public void exitGames() {
        finish();
    }
}

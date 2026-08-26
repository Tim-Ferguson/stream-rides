package com.pelotonhack.ridestarter;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public final class OverlayCurrentActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!RideStarterAccessibilityService.isRuntimeReady()) {
            Toast.makeText(this, "SARO overlay or bike sensors are not ready",
                    Toast.LENGTH_LONG).show();
            MediaLauncher.openHub(this);
            finish();
            return;
        }
        RideEngine.get(this).startLocalRide();
        RideStarterAccessibilityService.refreshOverlay();
        if (!MediaLauncher.launchLast(this)) {
            Toast.makeText(this, "Choose a streaming app", Toast.LENGTH_SHORT).show();
            MediaLauncher.openHub(this);
        }
        finish();
    }
}

package com.pelotonhack.ridestarter;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public final class ConfigTransferActivity extends Activity {
    public static final String ACTION_EXPORT = "com.pelotonhack.ridestarter.EXPORT_CONFIG";
    public static final String ACTION_IMPORT = "com.pelotonhack.ridestarter.IMPORT_CONFIG";
    public static final String EXTRA_REQUEST_ID = "request_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String action = getIntent().getAction();
        String requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        if (requestId == null || requestId.length() > 128) {
            requestId = "";
        }
        String message;
        boolean success = false;
        try {
            if (ACTION_IMPORT.equals(action)) {
                ConfigStore.importFromDefaultFile(this);
                message = "Configuration restored";
            } else if (ACTION_EXPORT.equals(action)) {
                ConfigStore.exportToDefaultFile(this);
                message = "Configuration exported";
            } else {
                throw new IllegalArgumentException("Unsupported transfer action");
            }
            success = true;
        } catch (Exception exception) {
            message = "Configuration failed: " + exception.getMessage();
        }
        try {
            ConfigStore.writeTransferStatus(this, requestId, action, success, message);
        } catch (Exception exception) {
            success = false;
            message = "Configuration status failed: " + exception.getMessage();
        }
        setResult(success ? RESULT_OK : RESULT_CANCELED);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }
}

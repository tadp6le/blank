package com.example.summon;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Setup screen:
 *  1. Enable the AccessibilityService (trigger for Home long-press + edge
 *     swipe on API 34+).
 *  2. Grant "Draw over other apps" so FloatingWindowService edge strips can
 *     work on API 29-33.
 *  3. Save the AI provider + API key in EncryptedSharedPreferences.
 *
 * NOTE: this app deliberately does NOT register as the Default Assistant.
 */
public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_REQUEST = 1001;

    private TextView status;
    private EditText apiKeyInput;
    private Spinner providerSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        apiKeyInput = findViewById(R.id.apiKeyInput);
        providerSpinner = findViewById(R.id.providerSpinner);
        Button saveBtn = findViewById(R.id.saveBtn);

        providerSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"OpenAI", "Gemini", "OpenRouter"}));

        apiKeyInput.setText(SettingsStore.getApiKey(this));
        String saved = SettingsStore.getProvider(this);
        if ("Gemini".equals(saved)) providerSpinner.setSelection(1);
        else if ("OpenRouter".equals(saved)) providerSpinner.setSelection(2);

        status.setOnClickListener(v -> {
            // Deep-link straight into the accessibility settings page.
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        saveBtn.setOnClickListener(v -> {
            SettingsStore.save(this,
                    apiKeyInput.getText().toString().trim(),
                    (String) providerSpinner.getSelectedItem());
            Toast.makeText(this, R.string.save, Toast.LENGTH_SHORT).show();
            ensureOverlayPermission();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        ensureOverlayPermission();
        // Start the fallback edge-strip service when we have overlay rights.
        if (Settings.canDrawOverlays(this)) {
            startService(new Intent(this, FloatingWindowService.class));
        }
    }

    private void refreshStatus() {
        boolean on = SettingsStore.isAccessibilityEnabled(this);
        status.setText(on ? R.string.status_on : R.string.status_off);
    }

    private void ensureOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, OVERLAY_REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                startService(new Intent(this, FloatingWindowService.class));
            }
        }
    }
}

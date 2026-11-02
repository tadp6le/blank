package com.example.summon;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

/**
 * Setup screen — three steps:
 *
 * 1. Default assistant (RECOMMENDED, this is the fix for Home long-press):
 *    requests the system assistant role via RoleManager. Once granted,
 *    long-pressing Home (3-button nav) or the corner-swipe (gesture nav)
 *    summons the panel — exactly how Gemini is invoked. This needs NO
 *    accessibility service.
 *
 * 2. Background edge-swipe triggers (optional): accessibility service
 *    (Android 14+ touch observation) + "display over other apps" for the
 *    persistent edge-strip service (Android 10-13). Both run in the
 *    foreground service that restarts at boot.
 *
 * 3. AI provider — ANY provider: pick a preset or "Custom", then set
 *    base URL + model + API key. Anything OpenAI-compatible (or Gemini)
 *    works. Stored encrypted on-device.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_ASSISTANT = 2001;
    private static final int REQ_OVERLAY = 1001;
    private static final int REQ_NOTIF = 3001;

    /** label, base URL, default model — all editable afterwards. */
    static final String[][] PRESETS = {
            {"OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"},
            {"Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash"},
            {"OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"},
            {"Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"},
            {"DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"},
            {"Mistral", "https://api.mistral.ai/v1", "mistral-large-latest"},
            {"xAI", "https://api.x.ai/v1", "grok-2-latest"},
            {"Ollama (local)", "http://127.0.0.1:11434/v1", "llama3.2"},
            {"Custom", "", ""}
    };

    private TextView statusView;
    private TextView testResult;
    private Spinner presetSpinner;
    private EditText baseUrlInput;
    private EditText modelInput;
    private EditText apiKeyInput;
    private boolean spinnerReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.statusView);
        testResult = findViewById(R.id.testResult);
        presetSpinner = findViewById(R.id.presetSpinner);
        baseUrlInput = findViewById(R.id.baseUrlInput);
        modelInput = findViewById(R.id.modelInput);
        apiKeyInput = findViewById(R.id.apiKeyInput);
        Button btnAssistant = findViewById(R.id.btnAssistant);
        Button btnAccessibility = findViewById(R.id.btnAccessibility);
        Button btnOverlay = findViewById(R.id.btnOverlay);
        SwitchCompat edgeToggle = findViewById(R.id.edgeToggle);
        Button saveBtn = findViewById(R.id.saveBtn);
        Button testBtn = findViewById(R.id.testBtn);

        edgeToggle.setChecked(SettingsStore.isEdgeTriggerEnabled(this));
        edgeToggle.setOnCheckedChangeListener((button, checked) -> {
            SettingsStore.setEdgeTriggerEnabled(this, checked);
            if (checked) {
                startTriggerServiceIfPossible();
            } else {
                stopService(new Intent(this, FloatingWindowService.class));
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, presetLabels());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);

        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (!spinnerReady) return;
                String[] preset = PRESETS[pos];
                baseUrlInput.setText(preset[1]);
                modelInput.setText(preset[2]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        String provider = SettingsStore.getProvider(this);
        int idx = PRESETS.length - 1;
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i][0].equals(provider)) {
                idx = i;
                break;
            }
        }
        presetSpinner.setSelection(idx, false);
        baseUrlInput.setText(SettingsStore.getBaseUrl(this));
        modelInput.setText(SettingsStore.getModel(this));
        apiKeyInput.setText(SettingsStore.getApiKey(this));
        spinnerReady = true;

        btnAssistant.setOnClickListener(v -> requestAssistantRole());
        btnAccessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        btnOverlay.setOnClickListener(v ->
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())), REQ_OVERLAY));

        saveBtn.setOnClickListener(v -> {
            String label = PRESETS[Math.max(0, presetSpinner.getSelectedItemPosition())][0];
            SettingsStore.save(this, label,
                    baseUrlInput.getText().toString().trim(),
                    modelInput.getText().toString().trim(),
                    apiKeyInput.getText().toString().trim());
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        });

        testBtn.setOnClickListener(v -> {
            testResult.setText(R.string.testing);
            ApiClient.ask(
                    baseUrlInput.getText().toString().trim(),
                    modelInput.getText().toString().trim(),
                    apiKeyInput.getText().toString().trim(),
                    "Reply with exactly: OK",
                    new ApiClient.AskCallback() {
                        @Override
                        public void onSuccess(String answer) {
                            testResult.setText(answer);
                        }

                        @Override
                        public void onError(String message) {
                            testResult.setText(message);
                        }
                    });
        });

        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        startTriggerServiceIfPossible();
    }

    private boolean assistantHeld() {
        try {
            RoleManager rm = getSystemService(RoleManager.class);
            return rm != null && rm.isRoleHeld(RoleManager.ROLE_ASSISTANT);
        } catch (Exception e) {
            return false;
        }
    }

    private void requestAssistantRole() {
        try {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm == null) throw new IllegalStateException("no RoleManager");
            if (rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                Toast.makeText(this, R.string.role_already, Toast.LENGTH_SHORT).show();
                return;
            }
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),
                    REQ_ASSISTANT);
        } catch (Exception e) {
            Toast.makeText(this, R.string.role_error, Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        }
    }

    private void startTriggerServiceIfPossible() {
        if (SettingsStore.isEdgeTriggerEnabled(this)
                && Settings.canDrawOverlays(this)) {
            startForegroundService(new Intent(this, FloatingWindowService.class));
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private void refreshStatus() {
        statusView.setText(
                "Assistant role: " + (assistantHeld() ? "ON" : "OFF") + "\n"
                        + "Accessibility: "
                        + (SettingsStore.isAccessibilityEnabled(this) ? "ON" : "OFF") + "\n"
                        + "Overlay: " + (Settings.canDrawOverlays(this) ? "ON" : "OFF"));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ASSISTANT || requestCode == REQ_OVERLAY) {
            refreshStatus();
            startTriggerServiceIfPossible();
        }
    }

    private String[] presetLabels() {
        String[] labels = new String[PRESETS.length];
        for (int i = 0; i < PRESETS.length; i++) labels[i] = PRESETS[i][0];
        return labels;
    }
}

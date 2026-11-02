package com.example.summon;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The summoned overlay panel. It is ALSO the Default Assistant target:
 * long-press Home (3-button nav) or corner-swipe (gesture nav) launches this
 * activity with ACTION_ASSIST, and "Ask Summon AI" in the text-selection
 * menu launches it with ACTION_PROCESS_TEXT (selection prefilled).
 */
public class SummonActivity extends Activity {

    private static volatile boolean showing = false;

    /** Services check this to avoid summoning the panel twice. */
    public static boolean isShowing() {
        return showing;
    }

    private View scrim;
    private LinearLayout bottomSheet;
    private EditText inputBox;
    private ImageButton sendBtn;
    private TextView responseText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        setContentView(R.layout.activity_summon);

        scrim = findViewById(R.id.scrim);
        bottomSheet = findViewById(R.id.bottomSheet);
        inputBox = findViewById(R.id.inputBox);
        sendBtn = findViewById(R.id.sendBtn);
        responseText = findViewById(R.id.responseText);

        scrim.setOnClickListener(v -> close());
        sendBtn.setOnClickListener(v -> onSend());
        inputBox.setOnClickListener(v -> showKeyboard());

        bottomSheet.setTranslationY(dp(220));
        bottomSheet.setAlpha(0f);
        bottomSheet.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        inputBox.requestFocus();
        inputBox.post(this::showKeyboard);
        maybePrefill(getIntent());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            inputBox.post(this::showKeyboard);
        }
    }

    private void showKeyboard() {
        if (inputBox == null) return;
        inputBox.requestFocus();
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(inputBox, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        maybePrefill(intent);
    }

    private void maybePrefill(Intent intent) {
        if (intent != null && Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            CharSequence t = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if (!TextUtils.isEmpty(t) && inputBox != null) {
                inputBox.setText(t);
                inputBox.setSelection(inputBox.getText().length());
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        showing = true;
    }

    @Override
    protected void onStop() {
        showing = false;
        super.onStop();
    }

    private void onSend() {
        String text = inputBox.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        String baseUrl = SettingsStore.getBaseUrl(this);
        String model = SettingsStore.getModel(this);
        String apiKey = SettingsStore.getApiKey(this);

        if (TextUtils.isEmpty(baseUrl) || TextUtils.isEmpty(model)
                || TextUtils.isEmpty(apiKey)) {
            copyToClipboard(text);
            Toast.makeText(this, R.string.no_key, Toast.LENGTH_LONG).show();
            close();
            return;
        }

        sendBtn.setEnabled(false);
        responseText.setVisibility(View.VISIBLE);
        responseText.setText("...");

        ApiClient.ask(baseUrl, model, apiKey, text, new ApiClient.AskCallback() {
            @Override
            public void onSuccess(String answer) {
                runOnUiThread(() -> {
                    responseText.setText(answer);
                    sendBtn.setEnabled(true);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    responseText.setText(message);
                    sendBtn.setEnabled(true);
                });
            }
        });
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("summon", text));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void close() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(inputBox.getWindowToken(), 0);
        }
        bottomSheet.animate()
                .translationY(dp(220))
                .alpha(0f)
                .setDuration(160)
                .withEndAction(this::finish)
                .start();
    }

    @Override
    public void onBackPressed() {
        close();
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}

package com.example.summon;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The summoned overlay UI.
 *
 * Window design:
 *  - Theme.Translucent.NoTitleBar -> the activity background is fully
 *    transparent, so the previous app stays visible behind the scrim.
 *  - The window starts NOT focusable and NOT touch modal outside the sheet
 *    so it can appear without stealing state; when the user taps the input
 *    box we clear FLAG_NOT_FOCUSABLE so the soft keyboard opens and
 *    adjustResize pushes the bottom sheet up smoothly.
 *  - Tapping the dark scrim closes everything.
 *
 * Behaviour:
 *  - With a saved API key: the text is sent to the AI and the answer is
 *    shown inside the sheet (still copyable).
 *  - Without an API key: the text is copied to the clipboard and the
 *    overlay closes.
 */
public class SummonActivity extends Activity {

    private View scrim;
    private LinearLayout bottomSheet;
    private EditText inputBox;
    private ImageButton sendBtn;
    private TextView responseText;

    private boolean keyboardMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        setContentView(R.layout.activity_summon);

        scrim = findViewById(R.id.scrim);
        bottomSheet = findViewById(R.id.bottomSheet);
        inputBox = findViewById(R.id.inputBox);
        sendBtn = findViewById(R.id.sendBtn);
        responseText = findViewById(R.id.responseText);

        scrim.setOnClickListener(v -> close());
        sendBtn.setOnClickListener(v -> onSend());

        // Tapping the input box = enter keyboard mode: make the window
        // focusable so the IME shows and adjustResize pushes the sheet up.
        inputBox.setOnClickListener(v -> enterKeyboardMode());

        // Slide the sheet in from the bottom.
        bottomSheet.setTranslationY(dp(220));
        bottomSheet.setAlpha(0f);
        bottomSheet.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // Open keyboard immediately for a Gemini-like flow.
        inputBox.postDelayed(this::enterKeyboardMode, 150);
    }

    private void enterKeyboardMode() {
        if (keyboardMode) return;
        keyboardMode = true;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        inputBox.requestFocus();
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(inputBox, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void exitKeyboardModeAndClose() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(inputBox.getWindowToken(), 0);
        }
        close();
    }

    private void onSend() {
        String text = inputBox.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        String apiKey = SettingsStore.getApiKey(this);
        if (TextUtils.isEmpty(apiKey)) {
            copyToClipboard(text);
            Toast.makeText(this, R.string.no_key, Toast.LENGTH_LONG).show();
            exitKeyboardModeAndClose();
            return;
        }

        sendBtn.setEnabled(false);
        responseText.setVisibility(View.VISIBLE);
        responseText.setText("…");

        String provider = SettingsStore.getProvider(this);
        ApiClient.ask(this, provider, apiKey, text, new ApiClient.Callback() {
            @Override
            public void onSuccess(final String answer) {
                runOnUiThread(() -> {
                    responseText.setText(answer);
                    sendBtn.setEnabled(true);
                });
            }

            @Override
            public void onError(final String message) {
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
        exitKeyboardModeAndClose();
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}

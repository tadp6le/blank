package com.example.summon;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Encrypted storage for the API key + provider choice, plus a helper to
 * check whether our AccessibilityService is currently enabled.
 */
public final class SettingsStore {

    private static final String FILE = "summon_secure_prefs";
    private static final String KEY_API = "api_key";
    private static final String KEY_PROVIDER = "provider";

    private SettingsStore() {}

    private static SharedPreferences prefs(Context ctx) {
        try {
            MasterKey master = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    ctx, FILE, master,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            // Extremely rare (keystore failure); fall back to plain prefs
            // rather than crash — still functional, just unencrypted.
            return ctx.getSharedPreferences(FILE + "_fallback", Context.MODE_PRIVATE);
        }
    }

    public static void save(Context ctx, String apiKey, String provider) {
        prefs(ctx).edit()
                .putString(KEY_API, apiKey == null ? "" : apiKey)
                .putString(KEY_PROVIDER, provider)
                .apply();
    }

    public static String getApiKey(Context ctx) {
        return prefs(ctx).getString(KEY_API, "");
    }

    public static String getProvider(Context ctx) {
        return prefs(ctx).getString(KEY_PROVIDER, "OpenAI");
    }

    public static boolean isAccessibilityEnabled(Context ctx) {
        String enabled = Settings.Secure.getString(
                ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        String me = ctx.getPackageName() + "/" +
                MyAccessibilityService.class.getCanonicalName();
        for (String s : enabled.split(":")) {
            if (s.equalsIgnoreCase(me)) return true;
        }
        return false;
    }
}

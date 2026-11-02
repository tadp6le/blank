package com.example.summon;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Encrypted storage for the AI provider configuration:
 * provider label + base URL + model name + API key.
 */
public final class SettingsStore {

    private static final String FILE = "summon_secure_prefs";
    private static final String KEY_API = "api_key";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_MODEL = "model";
    private static final String KEY_EDGE_ON = "edge_trigger_on";

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

    public static void save(Context ctx, String provider, String baseUrl,
                            String model, String apiKey) {
        prefs(ctx).edit()
                .putString(KEY_PROVIDER, provider == null ? "" : provider)
                .putString(KEY_BASE_URL, baseUrl == null ? "" : baseUrl)
                .putString(KEY_MODEL, model == null ? "" : model)
                .putString(KEY_API, apiKey == null ? "" : apiKey)
                .apply();
    }

    public static String getProvider(Context ctx) {
        return prefs(ctx).getString(KEY_PROVIDER, "");
    }

    public static String getBaseUrl(Context ctx) {
        return prefs(ctx).getString(KEY_BASE_URL, "");
    }

    public static String getModel(Context ctx) {
        return prefs(ctx).getString(KEY_MODEL, "");
    }

    public static String getApiKey(Context ctx) {
        return prefs(ctx).getString(KEY_API, "");
    }

    /** Edge-swipe overlay strips on/off (default on). */
    public static boolean isEdgeTriggerEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_EDGE_ON, true);
    }

    public static void setEdgeTriggerEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_EDGE_ON, on).apply();
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

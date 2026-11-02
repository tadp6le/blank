package com.example.summon;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Provider-agnostic API client.
 *
 * Works with ANY provider:
 *  - Everything OpenAI-compatible (OpenAI, OpenRouter, Groq, DeepSeek,
 *    Mistral, xAI, Together, Cerebras, ... plus local Ollama / LM Studio):
 *    POST {baseUrl}/chat/completions with "Authorization: Bearer {key}".
 *  - Google Gemini: POST {baseUrl}/models/{model}:generateContent with the
 *    "x-goog-api-key" header.
 *
 * The style is picked automatically from the base URL, so a "Custom" preset
 * with any OpenAI-compatible URL just works with no code changes.
 */
public final class ApiClient {

    public interface AskCallback {
        void onSuccess(String answer);
        void onError(String message);
    }

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ApiClient() {}

    public static void ask(String baseUrl, String model, String apiKey,
                           String prompt, AskCallback cb) {
        if (TextUtils.isEmpty(baseUrl) || TextUtils.isEmpty(model)
                || TextUtils.isEmpty(apiKey)) {
            cb.onError("Provider not configured. Open the Summon app and set base URL, model and API key.");
            return;
        }

        final boolean geminiStyle = baseUrl.contains("generativelanguage.googleapis.com");
        Request request;
        try {
            request = build(baseUrl, model, apiKey, prompt, geminiStyle);
        } catch (Exception e) {
            cb.onError("Could not build request: " + e.getMessage());
            return;
        }

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                MAIN.post(() -> cb.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    String body = r.body() != null ? r.body().string() : "";
                    if (!r.isSuccessful()) {
                        String msg = "HTTP " + r.code() + ": "
                                + body.substring(0, Math.min(body.length(), 300));
                        MAIN.post(() -> cb.onError(msg));
                        return;
                    }
                    String answer = parse(body, geminiStyle);
                    MAIN.post(() -> cb.onSuccess(answer));
                } catch (Exception e) {
                    MAIN.post(() -> cb.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    private static Request build(String baseUrl, String model, String apiKey,
                                 String prompt, boolean geminiStyle) throws Exception {
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        if (geminiStyle) {
            JSONObject body = new JSONObject();
            JSONArray parts = new JSONArray().put(new JSONObject().put("text", prompt));
            JSONObject content = new JSONObject().put("parts", parts);
            body.put("contents", new JSONArray().put(content));

            return new Request.Builder()
                    .url(base + "/models/" + model + ":generateContent")
                    .header("x-goog-api-key", apiKey)
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();
        }

        JSONObject body = new JSONObject();
        body.put("model", model);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        body.put("messages", messages);

        // Accept both ".../v1" and full ".../chat/completions" base URLs.
        String url = base.endsWith("/chat/completions")
                ? base : base + "/chat/completions";

        return new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
    }

    private static String parse(String body, boolean geminiStyle) throws Exception {
        JSONObject root = new JSONObject(body);
        if (geminiStyle) {
            return root.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim();
        }
        return root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim();
    }
}

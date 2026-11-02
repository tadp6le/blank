package com.example.summon;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

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
 * Minimal OkHttp client for the three supported providers.
 * All requests are simple synchronous-shaped JSON POSTs executed async
 * with the answer delivered on the main thread.
 */
public final class ApiClient {

    public interface Callback {
        void onSuccess(String answer);
        void onError(String message);
    }

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ApiClient() {}

    public static void ask(Context ctx, String provider, String apiKey,
                           String prompt, Callback cb) {
        try {
            Request request;
            switch (provider) {
                case "Gemini":
                    request = geminiRequest(apiKey, prompt);
                    break;
                case "OpenRouter":
                    request = openAiStyleRequest(
                            "https://openrouter.ai/api/v1/chat/completions",
                            apiKey, "openai/gpt-4o-mini", prompt);
                    break;
                default:
                    request = openAiStyleRequest(
                            "https://api.openai.com/v1/chat/completions",
                            apiKey, "gpt-4o-mini", prompt);
            }
            CLIENT.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    MAIN.post(() -> cb.onError("Network error: " + e.getMessage()));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response)
                        throws IOException {
                    try (Response r = response) {
                        String body = r.body() != null ? r.body().string() : "";
                        if (!r.isSuccessful()) {
                            String msg = "HTTP " + r.code() + ": "
                                    + body.substring(0, Math.min(body.length(), 300));
                            MAIN.post(() -> cb.onError(msg));
                            return;
                        }
                        String answer = parse(provider, body);
                        MAIN.post(() -> cb.onSuccess(answer));
                    } catch (Exception e) {
                        MAIN.post(() -> cb.onError("Parse error: " + e.getMessage()));
                    }
                }
            });
        } catch (Exception e) {
            cb.onError("Request error: " + e.getMessage());
        }
    }

    private static Request openAiStyleRequest(String url, String apiKey,
                                              String model, String prompt)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        JSONArray messages = new JSONArray();
        JSONObject m = new JSONObject();
        m.put("role", "user");
        m.put("content", prompt);
        messages.put(m);
        body.put("messages", messages);

        return new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
    }

    private static Request geminiRequest(String apiKey, String prompt) throws Exception {
        JSONObject body = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject p = new JSONObject();
        p.put("text", prompt);
        parts.put(p);
        JSONObject content = new JSONObject();
        content.put("parts", parts);
        JSONArray contents = new JSONArray();
        contents.put(content);
        body.put("contents", contents);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + "gemini-1.5-flash:generateContent?key=" + apiKey;

        return new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
    }

    private static String parse(String provider, String body) throws Exception {
        JSONObject root = new JSONObject(body);
        if ("Gemini".equals(provider)) {
            return root.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");
        }
        // OpenAI / OpenRouter share the same shape
        return root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim();
    }
}

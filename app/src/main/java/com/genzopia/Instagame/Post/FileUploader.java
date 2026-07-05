package com.genzopia.Instagame.Post;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;


public class FileUploader {

    private static final String TAG = "FileUploader";

    /**
     * Fetches the current Firebase user's ID token synchronously.
     * MUST be called from a background thread (upload runs off the main thread).
     * The gateway's authGuard rejects requests without a valid Bearer token,
     * so this token is required for /upload/* endpoints to succeed.
     */
    private static String getIdTokenBlocking() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return null;
            GetTokenResult result = Tasks.await(user.getIdToken(false));
            return result != null ? result.getToken() : null;
        } catch (Exception e) {
            Log.e(TAG, "getIdTokenBlocking failed: " + e.getMessage());
            return null;
        }
    }

    /** Routes a profile photo through the backend Gateway (POST /upload/profile-photo).
     *  Requirements: 10.1 */
    public static void uploadProfilePhoto(File file, BiConsumer<Boolean, String> callback) {
        uploadProfilePhoto(file, callback, null);
    }

    public static void uploadProfilePhoto(File file, BiConsumer<Boolean, String> callback,
                                          Consumer<Integer> progressCallback) {
        OkHttpClient client = new OkHttpClient();

        RequestBody fileBody = new ProgressRequestBody(file, MediaType.parse("image/jpeg"), progressCallback);
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        // Build the gateway URL for profile photo upload
        String gatewayBase = com.genzopia.Instagame.BuildConfig.GATEWAY_BASE_URL;
        if (!gatewayBase.endsWith("/")) gatewayBase += "/";
        HttpUrl url = HttpUrl.parse(gatewayBase + "upload/profile-photo");
        if (url == null) {
            callback.accept(false, "Invalid gateway URL");
            return;
        }

        Request.Builder profileRequestBuilder = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", com.genzopia.Instagame.BuildConfig.GATEWAY_API_KEY)
                .post(requestBody);
        String profileIdToken = getIdTokenBlocking();
        if (profileIdToken != null) {
            profileRequestBuilder.addHeader("Authorization", "Bearer " + profileIdToken);
        }
        Request request = profileRequestBuilder.build();

        client.newCall(request).enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "uploadProfilePhoto failed: " + e.getMessage());
                callback.accept(false, e.getMessage());
            }

            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "uploadProfilePhoto response code=" + response.code());
                callback.accept(response.isSuccessful(), body);
            }
        });
    }

    /** Uploads video files through the backend Gateway (POST /upload/video).
     *  Requirements: 10.2 */
    public static void uploadFileToWorker(File file, String fileType, Map<String, String> idMap,
                                          BiConsumer<Boolean, String> callback) {
        uploadFileToWorker(file, fileType, idMap, callback, null);
    }

    public static void uploadFileToWorker(File file, String fileType, Map<String, String> idMap,
                                          BiConsumer<Boolean, String> callback,
                                          Consumer<Integer> progressCallback) {
        // Large videos routed through the gateway can take a while to upload and the
        // gateway only responds once the whole file is received, so disable the overall
        // call timeout and give generous read/write windows.
        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();

        String contentType = switch (fileType) {
            case "zip" -> "application/zip";
            case "video" -> "video/mp4";
            case "photo" -> "image/png";
            default -> "application/octet-stream";
        };

        RequestBody fileBody = new ProgressRequestBody(file, MediaType.parse(contentType), progressCallback);

        // Build multipart body — include video_title and game_id from idMap as form fields
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody);
        if (idMap != null) {
            // Accept both "video_title" and legacy "title" key
            String videoTitle = idMap.getOrDefault("video_title", idMap.getOrDefault("title", ""));
            String gameId = idMap.getOrDefault("game_id", idMap.getOrDefault("gameid", ""));
            if (!videoTitle.isEmpty()) bodyBuilder.addFormDataPart("video_title", videoTitle);
            if (!gameId.isEmpty()) bodyBuilder.addFormDataPart("game_id", gameId);
        }

        // Route through the Gateway — CF Worker key never leaves the server
        String gatewayBase = com.genzopia.Instagame.BuildConfig.GATEWAY_BASE_URL;
        if (!gatewayBase.endsWith("/")) gatewayBase += "/";
        HttpUrl url = HttpUrl.parse(gatewayBase + "upload/video");
        if (url == null) {
            callback.accept(false, "Invalid gateway URL");
            return;
        }

        Request.Builder videoRequestBuilder = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", com.genzopia.Instagame.BuildConfig.GATEWAY_API_KEY)
                .post(bodyBuilder.build());
        String videoIdToken = getIdTokenBlocking();
        if (videoIdToken != null) {
            videoRequestBuilder.addHeader("Authorization", "Bearer " + videoIdToken);
        } else {
            // Without a Firebase Bearer token the gateway's authGuard returns 401,
            // so fail fast with a clear reason instead of a silent "Upload failed!".
            Log.e(TAG, "uploadFileToWorker: no Firebase ID token (user not signed in?) — gateway will reject with 401");
            callback.accept(false, "Not authenticated: unable to obtain Firebase ID token");
            return;
        }
        Request request = videoRequestBuilder.build();

        client.newCall(request).enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "uploadFileToWorker network failure: " + e.getMessage());
                callback.accept(false, e.getMessage());
            }

            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "uploadFileToWorker response code=" + response.code() + " body=" + body);
                callback.accept(response.isSuccessful(), body);
            }
        });
    }

    /**
     * Uploads a video file DIRECTLY to the Cloudflare file-upload Worker, exactly
     * like the legacy app did. This bypasses the gateway on purpose: Cloud Run
     * rejects any request body larger than ~32 MiB with HTTP 413, so routing real
     * videos through the gateway always failed. The worker itself imposes no such
     * limit and needs no auth key (only the query params). Firebase writes still
     * go through the secured gateway via {@link #registerVideoViaGateway}.
     *
     * @param queryParams e.g. video_id, title, description, game_id — appended to the
     *                    worker URL. The object is stored under the video_id key.
     */
    public static void uploadVideoToWorkerDirect(File file, Map<String, String> queryParams,
                                                 BiConsumer<Boolean, String> callback,
                                                 Consumer<Integer> progressCallback) {
        // Videos can be large and the worker only responds once fully received, so
        // disable the overall call timeout and give generous read/write windows.
        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();

        RequestBody fileBody = new ProgressRequestBody(file, MediaType.parse("video/mp4"), progressCallback);
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        HttpUrl base = HttpUrl.parse(com.genzopia.Instagame.BuildConfig.WORKER_UPLOAD_URL);
        if (base == null) {
            callback.accept(false, "Invalid worker upload URL");
            return;
        }
        HttpUrl.Builder urlBuilder = base.newBuilder();
        if (queryParams != null) {
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    urlBuilder.addQueryParameter(e.getKey(), e.getValue());
                }
            }
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "uploadVideoToWorkerDirect network failure: " + e.getMessage());
                callback.accept(false, e.getMessage());
            }

            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "uploadVideoToWorkerDirect response code=" + response.code() + " body=" + body);
                callback.accept(response.isSuccessful(), body);
            }
        });
    }

    /**
     * Registers a video's metadata with the secured gateway (POST /videos/register)
     * after its bytes were uploaded directly to the worker. This request is a small
     * JSON payload — well under Cloud Run's 32 MiB limit — and carries the Firebase
     * Bearer token so the gateway (admin SDK) can write the entry the locked-down
     * database rules forbid the client from writing directly.
     *
     * @param meta must contain video_id, video_title and game_id; key is optional.
     */
    public static void registerVideoViaGateway(Map<String, String> meta,
                                               BiConsumer<Boolean, String> callback) {
        OkHttpClient client = new OkHttpClient();

        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("video_id", meta.get("video_id"));
            String key = meta.get("key");
            if (key != null && !key.isEmpty()) json.put("key", key);
            String videoTitle = meta.getOrDefault("video_title", meta.getOrDefault("title", ""));
            String gameId = meta.getOrDefault("game_id", meta.getOrDefault("gameid", ""));
            json.put("video_title", videoTitle);
            json.put("game_id", gameId);
        } catch (org.json.JSONException e) {
            callback.accept(false, "Failed to build register payload: " + e.getMessage());
            return;
        }

        RequestBody body = RequestBody.create(
                json.toString(), MediaType.parse("application/json; charset=utf-8"));

        String gatewayBase = com.genzopia.Instagame.BuildConfig.GATEWAY_BASE_URL;
        if (!gatewayBase.endsWith("/")) gatewayBase += "/";
        HttpUrl url = HttpUrl.parse(gatewayBase + "videos/register");
        if (url == null) {
            callback.accept(false, "Invalid gateway URL");
            return;
        }

        Request.Builder registerBuilder = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", com.genzopia.Instagame.BuildConfig.GATEWAY_API_KEY)
                .post(body);
        String idToken = getIdTokenBlocking();
        if (idToken == null) {
            Log.e(TAG, "registerVideoViaGateway: no Firebase ID token — gateway will reject with 401");
            callback.accept(false, "Not authenticated: unable to obtain Firebase ID token");
            return;
        }
        registerBuilder.addHeader("Authorization", "Bearer " + idToken);

        client.newCall(registerBuilder.build()).enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "registerVideoViaGateway network failure: " + e.getMessage());
                callback.accept(false, e.getMessage());
            }

            public void onResponse(Call call, Response response) throws IOException {
                String respBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "registerVideoViaGateway response code=" + response.code() + " body=" + respBody);
                callback.accept(response.isSuccessful(), respBody);
            }
        });
    }

    /**
     * Parses the "key" field from the worker's JSON response, if present.
     * Returns null when the body isn't JSON or has no key (the gateway falls back
     * to video_id in that case).
     */
    public static String parseWorkerKey(String workerResponse) {
        if (workerResponse == null || workerResponse.isEmpty()) return null;
        try {
            org.json.JSONObject obj = new org.json.JSONObject(workerResponse);
            String key = obj.optString("key", null);
            return (key != null && !key.isEmpty()) ? key : null;
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    // ProgressRequestBody for real upload progress
    public static class ProgressRequestBody extends RequestBody {
        private final File file;
        private final MediaType contentType;
        private final Consumer<Integer> progressCallback;
        private static final int DEFAULT_BUFFER_SIZE = 2048;

        public ProgressRequestBody(File file, MediaType contentType, Consumer<Integer> progressCallback) {
            this.file = file;
            this.contentType = contentType;
            this.progressCallback = progressCallback;
        }

        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public long contentLength() throws IOException {
            return file.length();
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            long fileLength = file.length();
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            try (FileInputStream in = new FileInputStream(file)) {
                long uploaded = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    sink.write(buffer, 0, read);
                    uploaded += read;
                    if (progressCallback != null) {
                        int progress = (int) (100 * uploaded / fileLength);
                        progressCallback.accept(progress);
                    }
                }
            }
        }
    }
}


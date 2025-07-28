package com.genzopia.Instagame.Post;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
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
import okio.Okio;

public class FileUploader {
    public static void uploadFileToWorker(File file, String fileType, Map<String, String> idMap, BiConsumer<Boolean, String> callback) {
        uploadFileToWorker(file, fileType, idMap, callback, null);
    }

    public static void uploadFileToWorker(File file, String fileType, Map<String, String> idMap, BiConsumer<Boolean, String> callback, Consumer<Integer> progressCallback) {
        OkHttpClient client = new OkHttpClient();

        String contentType = switch (fileType) {
            case "zip" -> "application/zip";
            case "video" -> "video/mp4";
            case "photo" -> "image/png";
            default -> "application/octet-stream";
        };

        RequestBody fileBody = new ProgressRequestBody(file, MediaType.parse(contentType), progressCallback);
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        HttpUrl.Builder urlBuilder = HttpUrl.parse("https://file-uploader.genzopia.workers.dev").newBuilder();
        idMap.forEach(urlBuilder::addQueryParameter);

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                callback.accept(false, e.getMessage());
            }

            public void onResponse(Call call, Response response) throws IOException {
                callback.accept(response.isSuccessful(), response.body().string());
            }
        });
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


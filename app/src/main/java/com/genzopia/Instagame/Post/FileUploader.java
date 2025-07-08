package com.genzopia.Instagame.Post;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.BiConsumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FileUploader {
    public static void uploadFileToWorker(File file, String fileType, Map<String, String> idMap, BiConsumer<Boolean, String> callback) {
        OkHttpClient client = new OkHttpClient();

        String contentType = switch (fileType) {
            case "zip" -> "application/zip";
            case "video" -> "video/mp4";
            case "photo" -> "image/png";
            default -> "application/octet-stream";
        };

        RequestBody fileBody = RequestBody.create(file, MediaType.parse(contentType));
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
}


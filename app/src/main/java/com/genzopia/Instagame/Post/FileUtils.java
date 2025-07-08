package com.genzopia.Instagame.Post;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {
    public static File getFileFromUri(Context context, Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            File tempFile = File.createTempFile("upload", ".mp4", context.getCacheDir());
            tempFile.deleteOnExit();

            try (OutputStream output = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    output.write(buffer, 0, length);
                }
            }

            return tempFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

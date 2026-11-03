package org.endercore.android.operator.instance;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.endercore.android.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class InstanceDownloader {
    private static final String TAG = "InstanceDownloader";

    public interface DownloadListener {
        void onProgress(int percent);
        void onSuccess(File downloadedFile);
        void onError(Exception e);
    }

    public static class DownloadTask {
        private final Thread thread;
        public DownloadTask(Thread thread) {
            this.thread = thread;
        }
        public void cancel() {
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

    public DownloadTask downloadApk(Context context, String downloadUrl, File destinationFile, DownloadListener listener) {
        if (!isNetworkAvailable(context)) {
            if (listener != null) {
                listener.onError(new java.net.UnknownHostException("No internet connection."));
            }
            return new DownloadTask(null);
        }

        Thread thread = new Thread(() -> download(downloadUrl, destinationFile, listener));
        thread.start();
        return new DownloadTask(thread);
    }

    void download(String downloadUrl, File destinationFile, DownloadListener listener) {
        File partFile = new File(destinationFile.getPath() + ".part");
        HttpURLConnection connection = null;
        try {
            Log.d(TAG, "Starting download from: " + downloadUrl);
            URL url = new URL(downloadUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new Exception("Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage());
            }

            long fileLength = connection.getContentLengthLong();
            Log.d(TAG, "File size: " + fileLength + " bytes");

            if (!destinationFile.getParentFile().exists()) {
                destinationFile.getParentFile().mkdirs();
            }

            long total = 0;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(partFile)) {
                byte[] data = new byte[4096];
                int count;
                int lastProgress = -1;

                while ((count = input.read(data)) != -1) {
                    // Check if thread is interrupted to allow cancellation
                    if (Thread.currentThread().isInterrupted()) {
                        throw new Exception("Download cancelled by user.");
                    }
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            if (listener != null) {
                                listener.onProgress(progress);
                            }
                        }
                    }
                    output.write(data, 0, count);
                }
                output.getFD().sync();
            }

            if (fileLength > 0 && total != fileLength) {
                throw new IOException("Download is incomplete: received " + total + " of " + fileLength + " bytes.");
            }
            FileUtils.moveReplacing(partFile, destinationFile);
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            partFile.delete();
            if (listener != null) {
                listener.onError(e);
            }
            return;
        } finally {
            if (connection != null) connection.disconnect();
        }

        Log.d(TAG, "Download finished: " + destinationFile.getAbsolutePath());
        if (listener != null) {
            listener.onSuccess(destinationFile);
        }
    }
}

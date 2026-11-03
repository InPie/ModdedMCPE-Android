package org.endercore.android.operator.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.endercore.android.TestFiles;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class InstanceDownloaderTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private ServerSocket server;
    private File destination;
    private File partFile;

    private static final class RecordingListener implements InstanceDownloader.DownloadListener {
        File downloaded;
        Exception error;
        int lastProgress = -1;

        @Override
        public void onProgress(int percent) {
            lastProgress = percent;
        }

        @Override
        public void onSuccess(File downloadedFile) {
            downloaded = downloadedFile;
        }

        @Override
        public void onError(Exception e) {
            error = e;
        }
    }

    @Before
    public void setUp() throws Exception {
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        destination = new File(folder.getRoot(), "apk/game.apk");
        partFile = new File(destination.getPath() + ".part");
    }

    @After
    public void tearDown() throws Exception {
        server.close();
    }

    private void serveOnce(String response) {
        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    // Skip the request
                }
                OutputStream output = socket.getOutputStream();
                output.write(response.getBytes(StandardCharsets.ISO_8859_1));
                output.flush();
            } catch (IOException ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private RecordingListener download() {
        RecordingListener listener = new RecordingListener();
        String url = "http://" + server.getInetAddress().getHostAddress() + ":" + server.getLocalPort() + "/game.apk";
        new InstanceDownloader().download(url, destination, listener);
        return listener;
    }

    @Test
    public void completeDownloadAppearsAtDestination() throws Exception {
        serveOnce("HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello");

        RecordingListener listener = download();

        assertNull(listener.error);
        assertEquals(destination, listener.downloaded);
        assertEquals("hello", TestFiles.read(destination));
        assertEquals(100, listener.lastProgress);
        assertFalse(partFile.exists());
    }

    @Test
    public void downloadWithoutContentLengthSucceeds() throws Exception {
        serveOnce("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\nhello");

        RecordingListener listener = download();

        assertNull(listener.error);
        assertEquals("hello", TestFiles.read(destination));
    }

    @Test
    public void truncatedDownloadFailsAndLeavesNoFiles() {
        serveOnce("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\nhello");

        RecordingListener listener = download();

        assertNotNull(listener.error);
        assertNull(listener.downloaded);
        assertFalse(destination.exists());
        assertFalse(partFile.exists());
    }

    @Test
    public void httpErrorFailsAndLeavesNoFiles() {
        serveOnce("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");

        RecordingListener listener = download();

        assertNotNull(listener.error);
        assertTrue(listener.error.getMessage(), listener.error.getMessage().contains("404"));
        assertFalse(destination.exists());
        assertFalse(partFile.exists());
    }
}

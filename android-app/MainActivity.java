package com.savevid.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.widget.Toast;
import com.getcapacitor.BridgeActivity;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends BridgeActivity {

    private void js(String code) {
        try {
            getBridge().getWebView().post(() -> getBridge().getWebView().evaluateJavascript(code, null));
        } catch (Exception ignored) { }
    }

    private void toast(String m) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show());
    }

    public class NativeApp {
        @JavascriptInterface
        public void play(String uri) {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(Uri.parse(uri), "video/*");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent ch = Intent.createChooser(i, "تشغيل الفيديو");
                    ch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(ch);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "ما قدرنا نفتح الفيديو. افتحه من المعرض.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void download(final String url, final String name, final String mime) {
        new Thread(() -> {
            Uri result = null;
            ContentResolver cr = getContentResolver();
            File legacyFile = null;
            boolean audio = mime.startsWith("audio");
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(60000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SaveVid");
                if (c.getResponseCode() != 200) throw new Exception("http " + c.getResponseCode());
                String ct = c.getContentType();
                if (ct == null || !(ct.startsWith("video/") || ct.startsWith("audio/") || ct.contains("octet-stream"))) throw new Exception("not media");
                OutputStream out;
                Uri item = null;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, (audio ? Environment.DIRECTORY_MUSIC : Environment.DIRECTORY_MOVIES) + "/SaveVid");
                    v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    item = cr.insert(audio ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            : MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
                    if (item == null) throw new Exception("insert failed");
                    out = cr.openOutputStream(item);
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(audio ? Environment.DIRECTORY_MUSIC : Environment.DIRECTORY_MOVIES), "SaveVid");
                    dir.mkdirs();
                    legacyFile = new File(dir, name);
                    out = new FileOutputStream(legacyFile);
                }
                long total = 0;
                byte[] head = new byte[12];
                int headLen = 0;
                try (InputStream in = c.getInputStream(); OutputStream o = out) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (headLen < 12) {
                            int k = Math.min(12 - headLen, n);
                            System.arraycopy(buf, 0, head, headLen, k);
                            headLen += k;
                        }
                        o.write(buf, 0, n);
                        total += n;
                    }
                }
                boolean okMp4 = audio || (headLen >= 8 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p');
                if (total < 20000 || !okMp4) {
                    if (item != null) cr.delete(item, null, null);
                    if (legacyFile != null) legacyFile.delete();
                    throw new Exception("bad file");
                }
                if (item != null) {
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    cr.update(item, done, null, null);
                    result = item;
                    onDone(name, result);
                } else {
                    final String nm = name;
                    MediaScannerConnection.scanFile(this, new String[]{legacyFile.getAbsolutePath()}, new String[]{mime},
                            (path, uri) -> onDone(nm, uri != null ? uri : Uri.fromFile(new File(path))));
                }
            } catch (Exception e) {
                toast("فشل التحميل. جرب مرة ثانية.");
                js("window.onDownloadFailed && window.onDownloadFailed(" + JSONObject.quote(name) + ")");
            }
        }).start();
    }

    private void onDone(String name, Uri u) {
        toast("تم التحميل. تلاقيه في المعرض (مجلد SaveVid).");
        js("window.onDownloaded && window.onDownloaded(" + JSONObject.quote(name) + "," + JSONObject.quote(String.valueOf(u)) + ")");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
        }
        getBridge().getWebView().addJavascriptInterface(new NativeApp(), "NativeApp");
        getBridge().getWebView().setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            String name = URLUtil.guessFileName(url, contentDisposition, "video/mp4");
            if (!name.toLowerCase().matches(".*\\.(mp4|mp3|webm)$")) name = name.replaceAll("\\.[^.]*$", "") + ".mp4";
            String mime = name.endsWith(".mp3") ? "audio/mpeg" : name.endsWith(".webm") ? "video/webm" : "video/mp4";
            Toast.makeText(this, "بدأ التحميل...", Toast.LENGTH_SHORT).show();
            download(url, name, mime);
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            sendClip();
            getBridge().getWebView().postDelayed(this::sendClip, 1500);
        }
    }

    private void sendClip() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return;
            ClipData d = cm.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return;
            CharSequence t = d.getItemAt(0).coerceToText(this);
            if (t == null || t.length() == 0 || t.length() > 2000) return;
            js("window.onClip && window.onClip(" + JSONObject.quote(t.toString()) + ")");
        } catch (Exception ignored) { }
    }
}

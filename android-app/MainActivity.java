package com.savevid.app;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import org.json.JSONObject;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.URLUtil;
import android.widget.Toast;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getBridge().getWebView().setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
                r.setMimeType(mimeType);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(this, "بدأ التحميل...", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "تعذر التحميل", Toast.LENGTH_LONG).show();
            }
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
            final String js = "window.onClip && window.onClip(" + JSONObject.quote(t.toString()) + ")";
            getBridge().getWebView().post(() -> getBridge().getWebView().evaluateJavascript(js, null));
        } catch (Exception ignored) { }
    }
}

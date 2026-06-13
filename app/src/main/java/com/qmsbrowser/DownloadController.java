package com.qmsbrowser;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.widget.Toast;

public final class DownloadController {
    private final Activity activity;
    private PendingDownload pendingDownload;

    public static final class PendingDownload {
        final String url;
        final String userAgent;
        final String contentDisposition;
        final String mimeType;

        PendingDownload(String url, String userAgent, String contentDisposition, String mimeType) {
            this.url = url;
            this.userAgent = userAgent;
            this.contentDisposition = contentDisposition;
            this.mimeType = mimeType;
        }
    }

    public DownloadController(Activity activity) {
        this.activity = activity;
    }

    public DownloadListener createDownloadListener() {
        return (url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (!SecurityPolicy.isAllowedWebUrl(url)) {
                Toast.makeText(activity, "Blocked an unsafe download URL", Toast.LENGTH_LONG).show();
                return;
            }
            PendingDownload download = new PendingDownload(url, userAgent, contentDisposition, mimeType);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                    && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingDownload = download;
                activity.requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        104 // REQUEST_DOWNLOAD
                );
            } else {
                enqueueDownload(download);
            }
        };
    }

    public void handlePermissionResult(int[] grantResults) {
        if (pendingDownload != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enqueueDownload(pendingDownload);
            } else {
                Toast.makeText(activity, "Storage permission is needed to download", Toast.LENGTH_LONG).show();
            }
            pendingDownload = null;
        }
    }

    private void enqueueDownload(PendingDownload download) {
        try {
            String fileName = URLUtil.guessFileName(
                    download.url,
                    download.contentDisposition,
                    download.mimeType
            );
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(download.url));
            request.setTitle(fileName);
            request.setDescription("Downloading from " + Uri.parse(download.url).getHost());
            request.setMimeType(download.mimeType);
            request.addRequestHeader("User-Agent", download.userAgent);
            String cookies = CookieManager.getInstance().getCookie(download.url);
            if (cookies != null) {
                request.addRequestHeader("Cookie", cookies);
            }
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(activity, "Downloading " + fileName, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(activity, "Download Manager not available", Toast.LENGTH_LONG).show();
            }
        } catch (Exception error) {
            Toast.makeText(activity, "Download failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

package com.qmsbrowser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.List;

public final class PermissionController {
    private final Activity activity;
    
    private PermissionRequest webPermissionRequest;
    private String pendingWebPermissionOrigin;
    private String[] pendingWebPermissionResources;

    private GeolocationPermissions.Callback geolocationCallback;
    private String geolocationOrigin;

    private static final int REQUEST_WEB_PERMISSIONS = 102;
    private static final int REQUEST_LOCATION = 103;

    public PermissionController(Activity activity) {
        this.activity = activity;
    }

    public void handlePermissionRequest(PermissionRequest request, WebView webView, boolean restrictToStartHost, String configuredStartUrl) {
        Uri origin = request.getOrigin();
        if (!isAllowedPermissionOrigin(origin, webView, restrictToStartHost, configuredStartUrl)) {
            request.deny();
            return;
        }
        List<String> missing = new ArrayList<>();
        List<String> supportedResources = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                supportedResources.add(resource);
                if (activity.checkSelfPermission(Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    missing.add(Manifest.permission.CAMERA);
                }
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                supportedResources.add(resource);
                if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    missing.add(Manifest.permission.RECORD_AUDIO);
                }
            }
        }

        if (supportedResources.isEmpty()) {
            request.deny();
            return;
        }
        String originLabel = origin.getScheme() + "://" + origin.getAuthority();
        new AlertDialog.Builder(activity)
                .setTitle("Allow website access?")
                .setMessage(originLabel + " wants to use "
                        + describeWebResources(supportedResources) + ".")
                .setNegativeButton("Block", (dialog, which) -> request.deny())
                .setPositiveButton("Allow", (dialog, which) -> {
                    if (missing.isEmpty()) {
                        request.grant(supportedResources.toArray(new String[0]));
                    } else {
                        webPermissionRequest = request;
                        pendingWebPermissionOrigin = originLabel;
                        pendingWebPermissionResources = supportedResources.toArray(new String[0]);
                        activity.requestPermissions(
                                missing.toArray(new String[0]),
                                REQUEST_WEB_PERMISSIONS
                        );
                    }
                })
                .setOnCancelListener(dialog -> request.deny())
                .show();
    }

    public void handleGeolocationPermissionsShowPrompt(
            String origin,
            GeolocationPermissions.Callback callback,
            WebView webView,
            boolean restrictToStartHost,
            String configuredStartUrl
    ) {
        Uri originUri = Uri.parse(origin);
        if (!isAllowedPermissionOrigin(originUri, webView, restrictToStartHost, configuredStartUrl)) {
            callback.invoke(origin, false, false);
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Allow location access?")
                .setMessage(origin + " wants to use this device's location.")
                .setNegativeButton("Block", (dialog, which) ->
                        callback.invoke(origin, false, false))
                .setPositiveButton("Allow", (dialog, which) -> {
                    if (hasLocationPermission()) {
                        callback.invoke(origin, true, false);
                    } else {
                        geolocationOrigin = origin;
                        geolocationCallback = callback;
                        activity.requestPermissions(
                                new String[]{
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                },
                                REQUEST_LOCATION
                        );
                    }
                })
                .setOnCancelListener(dialog -> callback.invoke(origin, false, false))
                .show();
    }

    public void handlePermissionResult(int requestCode, int[] grantResults, WebView webView, boolean restrictToStartHost, String configuredStartUrl) {
        if (requestCode == REQUEST_WEB_PERMISSIONS && webPermissionRequest != null) {
            if (allPermissionsGranted(grantResults)
                    && pendingWebPermissionOrigin != null
                    && isAllowedPermissionOrigin(Uri.parse(pendingWebPermissionOrigin), webView, restrictToStartHost, configuredStartUrl)) {
                webPermissionRequest.grant(pendingWebPermissionResources);
            } else {
                webPermissionRequest.deny();
            }
            webPermissionRequest = null;
            pendingWebPermissionOrigin = null;
            pendingWebPermissionResources = null;
        } else if (requestCode == REQUEST_LOCATION && geolocationCallback != null) {
            geolocationCallback.invoke(
                    geolocationOrigin,
                    hasLocationPermission()
                            && isAllowedPermissionOrigin(Uri.parse(geolocationOrigin), webView, restrictToStartHost, configuredStartUrl),
                    false
            );
            geolocationCallback = null;
            geolocationOrigin = null;
        }
    }

    private boolean isAllowedPermissionOrigin(Uri origin, WebView webView, boolean restrictToStartHost, String configuredStartUrl) {
        if (origin == null || origin.getHost() == null) {
            return false;
        }
        String scheme = origin.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return false;
        }
        Uri current = Uri.parse(webView.getUrl() == null ? "" : webView.getUrl());
        return current.getAuthority() != null
                && current.getAuthority().equalsIgnoreCase(origin.getAuthority())
                && NavigationPolicy.isAllowedKioskHost(activity, origin, configuredStartUrl, restrictToStartHost);
    }

    private String describeWebResources(List<String> resources) {
        boolean camera = resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        boolean microphone = resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
        return camera && microphone ? "your camera and microphone"
                : camera ? "your camera" : "your microphone";
    }

    private boolean allPermissionsGranted(int[] grantResults) {
        if (grantResults.length == 0) {
            return false;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean hasLocationPermission() {
        return activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}

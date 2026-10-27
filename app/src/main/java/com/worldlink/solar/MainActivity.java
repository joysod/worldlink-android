
package com.worldlink.solar;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.ValueCallback;
import android.webkit.GeolocationPermissions;
import android.net.Uri;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> fileUploadCallback;

    private static final int GALLERY_REQUEST = 1001;
    private static final int CAMERA_REQUEST = 1002;

    private Uri cameraPhotoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);

        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {

                Uri uri = request.getUrl();

                if ("78.159.103.149".equals(uri.getHost())) {
                    return false;
                }

                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {

                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }

                fileUploadCallback = filePathCallback;

                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Select Consumer Photo")
                    .setItems(
                        new String[]{"Take Photo", "Choose from Gallery"},
                        (dialog, which) -> {
                            if (which == 0) {
                                openCamera();
                            } else {
                                openGallery(fileChooserParams);
                            }
                        })
                    .setOnCancelListener(dialog -> finishFileSelection(null))
                    .show();

                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin,
                    GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }
        });

        webView.loadUrl("http://78.159.103.149:8081/");
    }

    private void openGallery(FileChooserParams params) {
        try {
            Intent intent = params.createIntent();
            startActivityForResult(intent, GALLERY_REQUEST);
        } catch (ActivityNotFoundException e) {
            finishFileSelection(null);
            Toast.makeText(this,
                "No gallery or file picker is available",
                Toast.LENGTH_LONG).show();
        }
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (intent.resolveActivity(getPackageManager()) == null) {
            finishFileSelection(null);
            Toast.makeText(this, "No camera app is available",
                Toast.LENGTH_LONG).show();
            return;
        }

        try {
            File photoFile = File.createTempFile(
                "worldlink_photo_", ".jpg", getCacheDir());

            cameraPhotoUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                photoFile);

            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            List<ResolveInfo> cameraApps =
                getPackageManager().queryIntentActivities(
                    intent, PackageManager.MATCH_DEFAULT_ONLY);

            for (ResolveInfo app : cameraApps) {
                grantUriPermission(
                    app.activityInfo.packageName,
                    cameraPhotoUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }

            startActivityForResult(intent, CAMERA_REQUEST);

        } catch (IOException | ActivityNotFoundException e) {
            cameraPhotoUri = null;
            finishFileSelection(null);
            Toast.makeText(this, "Could not open camera",
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST) {
            if (resultCode == RESULT_OK && cameraPhotoUri != null) {
                finishFileSelection(new Uri[]{cameraPhotoUri});
            } else {
                finishFileSelection(null);
            }
            cameraPhotoUri = null;

        } else if (requestCode == GALLERY_REQUEST) {
            Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {
                results = WebChromeClient.FileChooserParams
                    .parseResult(resultCode, data);
            }

            finishFileSelection(results);
        }
    }

    private void finishFileSelection(Uri[] results) {
        if (fileUploadCallback != null) {
            fileUploadCallback.onReceiveValue(results);
            fileUploadCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (fileUploadCallback != null) {
            fileUploadCallback.onReceiveValue(null);
            fileUploadCallback = null;
        }

        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}

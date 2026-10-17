package cool.starlight.skin;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupDownloadListener();
        setupBackNavigation();
    }

    /**
     * 返回键行为：
     * 1) 网页还能回退一步（站内子页面 / SPA 历史）→ 回退到上一页；
     * 2) 已在站点首页（canGoBack() == false）→ 退出 App，回到桌面。
     *
     * 为什么需要接管：Capacitor 自带 App 插件的默认实现是
     * 「有 JS 监听就发事件，没有监听且能回退就 goBack()，否则什么都不做」，
     * 因此停在首页时按返回键会毫无反应。
     * 配置文件里已加 plugins.App.disableBackButtonHandler=true，关掉它那条回调，
     * 由这里的 OnBackPressedCallback 统一处理。
     */
    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView webView = getBridge() != null ? getBridge().getWebView() : null;
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish(); // 回到桌面
                }
            }
        });
    }

    /**
     * 让 WebView 内的下载（皮肤/文件）保存到系统「下载」目录。
     */
    private void setupDownloadListener() {
        WebView webView = this.bridge.getWebView();
        if (webView == null) {
            return;
        }
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype, long contentLength) {
                try {
                    String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    req.setMimeType(mimetype);
                    String ua = userAgent;
                    if (ua == null || ua.isEmpty()) {
                        ua = webView.getSettings().getUserAgentString();
                    }
                    req.addRequestHeader("User-Agent", ua);
                    String cookie = CookieManager.getInstance().getCookie(url);
                    if (cookie != null && !cookie.isEmpty()) {
                        req.addRequestHeader("Cookie", cookie);
                    }
                    req.setTitle(filename);
                    req.setDescription("StarLight 皮肤站");
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    dm.enqueue(req);
                    Toast.makeText(MainActivity.this, "开始下载：" + filename, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "下载失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}

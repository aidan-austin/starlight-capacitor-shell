package cool.starlight.skin;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    // 与网站主题一致：black-dark ≈ #09090b，autumn-light = #f5fafc
    private static final int COLOR_DARK = 0xFF09090B;
    private static final int COLOR_LIGHT = 0xFFF5FAFC;

    private static final String PREFS = "starlight";
    private static final String PREF_THEME = "theme"; // "dark" / "light"

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable themePollTask;
    private long lastBackPressedAt = 0L;

    /** 开屏动画是否还在显示：期间状态栏锁定跟随开屏背景，避免被网站主题中途改掉 */
    private volatile boolean splashVisible = true;
    /** 开屏期间收到的网站主题，等动画结束后再应用 */
    private Boolean pendingDark;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupDownloadListener();
        setupBackNavigation();
        installThemeBridge();

        boolean dark = resolveInitialDarkMode();
        applySystemBarColors(dark);
        showIntroAnimation(dark);
        startThemeWatcher();

        // 部分 ROM（如 EMUI/Android 10）会在启动主题切换到 postSplashScreenTheme 时
        // 把状态栏颜色重置回主题默认值，这里补两次，确保最终与开屏背景一致。
        final boolean darkFinal = dark;
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                applySystemBarColors(darkFinal);
            }
        }, 250);
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                applySystemBarColors(darkFinal);
            }
        }, 900);
    }

    // ---------------------------------------------------------------------
    // 主题：深色 / 浅色
    //   · 记忆模式：从网站读到的 data-theme 存进 SharedPreferences，
    //               下次启动沿用（"autumn-light" = 浅色，"black-dark" = 深色）
    //   · 首次启动（无记忆）：一律深色。网站本身默认就是深色，这样开屏和网页一致，
    //               也不会出现「浅色开屏 → 深色网页」的跳变。
    // ---------------------------------------------------------------------
    private boolean resolveInitialDarkMode() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        return !"light".equals(sp.getString(PREF_THEME, null));
    }

    /** 状态栏颜色 + WebView 底色都跟着主题走（浅色模式下避免页面加载前露黑底） */
    private void applySystemBarColors(boolean dark) {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        window.setStatusBarColor(dark ? COLOR_DARK : COLOR_LIGHT);

        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView != null) {
            webView.setBackgroundColor(dark ? COLOR_DARK : COLOR_LIGHT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+：SYSTEM_UI_FLAG_LIGHT_STATUS_BAR 已废弃（Android 15/16 上会被忽略），
            // 必须用 WindowInsetsController 才能让浅色背景下的图标变深色。
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                controller.setSystemBarsAppearance(dark ? 0 : mask, mask);
            }
        } else {
            View decor = window.getDecorView();
            int vis = decor.getSystemUiVisibility();
            if (dark) {
                vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                vis |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(vis);
        }
    }

    /** 把网页里的主题传回原生 */
    private class ThemeBridge {
        @JavascriptInterface
        public void set(String theme) {
            final String value = "light".equalsIgnoreCase(theme) ? "light" : "dark";
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            String current = sp.getString(PREF_THEME, null);
            if (value.equals(current)) {
                return;
            }
            sp.edit().putString(PREF_THEME, value).apply();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (splashVisible) {
                        // 开屏还没结束：先记下来，等动画收尾后再改状态栏
                        pendingDark = "dark".equals(value);
                    } else {
                        applySystemBarColors("dark".equals(value));
                    }
                }
            });
        }
    }

    private void installThemeBridge() {
        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView != null) {
            webView.addJavascriptInterface(new ThemeBridge(), "SlTheme");
        }
    }

    /**
     * 页面内安装 MutationObserver：站点切换主题只会改 <html data-theme>，
     * 不刷新页面。用观察器可以「一变就上报」，避免状态栏等 3 秒轮询。
     * 脚本幂等（window.__slThemeObserver 已存在就不重复装），
     * 页面跳转/刷新后由下面的定时器重新注入。
     */
    private static final String THEME_JS =
            "(function(){try{" +
            "function report(){var t=document.documentElement.getAttribute('data-theme')||'';" +
            "if(window.SlTheme){window.SlTheme.set(/light/i.test(t)?'light':'dark');}}" +
            "if(!window.__slThemeObserver){" +
            "window.__slThemeObserver=new MutationObserver(report);" +
            "window.__slThemeObserver.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme','class']});" +
            "if(document.body){window.__slThemeObserver.observe(document.body,{attributes:true,attributeFilter:['data-theme','class']});}" +
            "}" +
            "report();}catch(e){}})();";

    /**
     * 定时注入观察器（幂等），只在页面刷新后补装；正常切换靠 MutationObserver 即时上报。
     */
    private void startThemeWatcher() {
        themePollTask = new Runnable() {
            @Override
            public void run() {
                WebView webView = getBridge() != null ? getBridge().getWebView() : null;
                if (webView != null) {
                    webView.evaluateJavascript(THEME_JS, null);
                }
                uiHandler.postDelayed(this, 4000);
            }
        };
        uiHandler.postDelayed(themePollTask, 1200);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 某些系统/插件在恢复时会重置状态栏样式，这里按记忆的主题再应用一次
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = sp.getString(PREF_THEME, null);
        if (saved != null) {
            applySystemBarColors("dark".equals(saved));
        }
    }

    @Override
    public void onDestroy() {
        if (themePollTask != null) {
            uiHandler.removeCallbacks(themePollTask);
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------------
    // 开场动画
    //   1) 星星从下往上渐入（alpha 0→1，Y 位移 +120dp→0，轻微放大）
    //   2) 星星向左滑动，同时 "StarLight" 沿同一方向滑入到星星右侧
    //   3) 停留片刻后整体淡出，露出后面已经开始加载的网页
    //   背景 / 状态栏跟随当前主题（深色 #09090b / 浅色 #f5fafc）
    // ---------------------------------------------------------------------
    private void showIntroAnimation(boolean dark) {
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        final int background = dark ? COLOR_DARK : COLOR_LIGHT;

        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(background);
        overlay.setClickable(true); // 动画期间吃掉触摸，避免误点网页

        final ImageView star = new ImageView(this);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.logo_star);
        BitmapDrawable starDrawable = new BitmapDrawable(getResources(), bitmap);
        starDrawable.setFilterBitmap(false); // 像素风：关闭双线性插值，保持锐利
        star.setImageDrawable(starDrawable);
        star.setScaleType(ImageView.ScaleType.FIT_CENTER);
        star.setAlpha(0f);
        star.setTranslationY(dp(120));
        star.setScaleX(0.88f);
        star.setScaleY(0.88f);
        overlay.addView(star, new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER));

        final GradientWordView word = new GradientWordView(this, TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 40, getResources().getDisplayMetrics()));
        word.setAlpha(0f);
        overlay.addView(word, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        content.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        overlay.post(new Runnable() {
            @Override
            public void run() {
                final float starSize = star.getWidth();
                final float textWidth = word.getWidth();
                final float gap = dp(16);
                // 把「星星 + 间距 + 文字」当作一个整体，让其水平居中
                final float groupWidth = starSize + gap + textWidth;
                final float starShift = -groupWidth / 2f + starSize / 2f;
                final float wordTarget = starShift + starSize / 2f + gap + textWidth / 2f;

                // 阶段 1：星星自下而上渐入
                star.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(500)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                // 阶段 2：星星左移；文字沿同方向滑入并淡入（放慢，让文字出现更从容）
                overlay.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        star.animate()
                                .translationX(starShift)
                                .setDuration(900)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        word.setTranslationX(wordTarget + dp(56));
                        word.animate()
                                .alpha(1f)
                                .translationX(wordTarget)
                                .setDuration(900)
                                .setInterpolator(new DecelerateInterpolator())
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        word.setAlpha(1f); // 确保满不透明，避免停在中间值
                                    }
                                })
                                .start();
                    }
                }, 500);

                // 阶段 3：动画全部结束后再停留约 1 秒，然后整体淡出
                overlay.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        overlay.animate()
                                .alpha(0f)
                                .setDuration(300)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        ViewGroup parent = (ViewGroup) overlay.getParent();
                                        if (parent != null) {
                                            parent.removeView(overlay);
                                        }
                                        // 开屏结束：这时才允许状态栏跟随网站主题
                                        splashVisible = false;
                                        if (pendingDark != null) {
                                            applySystemBarColors(pendingDark);
                                            pendingDark = null;
                                        }
                                    }
                                })
                                .start();
                    }
                }, 2400);
            }
        });
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------------
    // 返回键：能回退就回退；已在首页则「再按一次」才退出
    // ---------------------------------------------------------------------
    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView webView = getBridge() != null ? getBridge().getWebView() : null;
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return;
                }
                long now = SystemClock.elapsedRealtime();
                if (now - lastBackPressedAt <= 2000L) {
                    finish();
                } else {
                    lastBackPressedAt = now;
                    Toast.makeText(MainActivity.this, "再按一次退出应用", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // ---------------------------------------------------------------------
    // 下载：交给系统 DownloadManager
    // ---------------------------------------------------------------------
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

    /**
     * 自绘的渐变文字：直接用 Canvas.drawText 画，渐变范围精确对齐字形墨迹
     * （主页 logo 的 from-[#FF1CF7] to-[#b249f8] bg-clip-text bg-gradient-to-b）。
     * 用 TextView 时 shader 在这台设备上不生效，所以自己画。
     */
    private static final class GradientWordView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final String text = "StarLight";
        private LinearGradient shader;
        private float baseline;
        private float textX;

        GradientWordView(Context context, float textSizePx) {
            super(context);
            paint.setTextSize(textSizePx);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            android.graphics.Rect ink = new android.graphics.Rect();
            paint.getTextBounds(text, 0, text.length(), ink);
            int width = (int) Math.ceil(paint.measureText(text)) + 4;
            int height = ink.height() + 8;
            setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            android.graphics.Rect ink = new android.graphics.Rect();
            paint.getTextBounds(text, 0, text.length(), ink);
            float inkCenter = (ink.top + ink.bottom) / 2f;
            baseline = h / 2f - inkCenter;
            textX = (w - paint.measureText(text)) / 2f;
            shader = new LinearGradient(
                    0f, baseline + ink.top,
                    0f, baseline + ink.bottom,
                    new int[]{0xFFFF1CF7, 0xFFB249F8},
                    null, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (shader != null) {
                paint.setShader(shader);
            }
            canvas.drawText(text, textX, baseline, paint);
        }
    }
}

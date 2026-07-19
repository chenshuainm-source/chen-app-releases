package com.videoflow.control;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final String PREFS = "video_flow_client";
    static final String KEY_BASE_URL = "base_url";
    static final String KEY_TOKEN = "access_token";
    static final String KEY_PAIRING_URL = "pairing_url";

    private static final int BG = Color.rgb(7, 16, 29);
    private static final int SURFACE = Color.rgb(12, 23, 39);
    private static final int SURFACE_2 = Color.rgb(16, 30, 49);
    private static final int LINE = Color.rgb(32, 49, 73);
    private static final int TEXT = Color.rgb(233, 240, 250);
    private static final int MUTED = Color.rgb(128, 145, 169);
    private static final int CYAN = Color.rgb(65, 216, 201);
    private static final int GREEN = Color.rgb(84, 214, 139);
    private static final int AMBER = Color.rgb(246, 191, 99);
    private static final int RED = Color.rgb(255, 111, 120);
    private static final int BLUE = Color.rgb(91, 140, 255);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout accountList;
    private LinearLayout notificationList;
    private LinearLayout pipelineContainer;
    private LinearLayout taskControlList;
    private TextView connectionText;
    private TextView workerTitle;
    private TextView workerDetail;
    private TextView workerBadge;
    private TextView statAccounts;
    private TextView statQueue;
    private TextView statCompleted;
    private Button startButton;
    private Button stopButton;
    private boolean refreshing;
    private boolean visible;
    private String baseUrl = "";
    private String token = "";
    private WebView fullDashboard;

    private final Runnable periodicRefresh = new Runnable() {
        @Override public void run() {
            if (visible && fullDashboard == null) refreshState(false);
            handler.postDelayed(this, 15_000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        NotificationHelper.ensureChannels(this);
        requestNotificationPermission();
        restoreConfig();
        if (baseUrl.isEmpty() || token.isEmpty()) showSetupScreen();
        else {
            showFullDashboard();
            startMonitorService();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        handler.removeCallbacks(periodicRefresh);
        handler.post(periodicRefresh);
    }

    @Override protected void onPause() {
        visible = false;
        handler.removeCallbacks(periodicRefresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (fullDashboard != null) {
            fullDashboard.stopLoading();
            fullDashboard.destroy();
            fullDashboard = null;
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private void restoreConfig() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        baseUrl = prefs.getString(KEY_BASE_URL, "");
        token = prefs.getString(KEY_TOKEN, "");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3001);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.14f);
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value.toUpperCase(Locale.ROOT), 10, CYAN);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLetterSpacing(0.12f);
        return view;
    }

    private Button button(String caption, int fill, int color, int stroke) {
        Button view = new Button(this);
        view.setText(caption);
        view.setTextColor(color);
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setAllCaps(false);
        view.setMinHeight(0);
        view.setMinWidth(0);
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setBackground(shape(fill, 12, stroke));
        return view;
    }

    private LinearLayout vertical(int padding) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        return layout;
    }

    private LinearLayout card(int padding) {
        LinearLayout view = vertical(padding);
        view.setBackground(shape(SURFACE, 19, LINE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        view.setLayoutParams(params);
        return view;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView chip(String value, int foreground, int background) {
        TextView view = text(value, 10, foreground);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(6), dp(10), dp(6));
        view.setBackground(shape(background, 99, Color.TRANSPARENT));
        return view;
    }

    private ImageView brandIcon() {
        ImageView view = new ImageView(this);
        view.setImageResource(com.videoflow.control.R.drawable.app_icon_portrait);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setBackground(shape(SURFACE_2, 14, LINE));
        view.setClipToOutline(true);
        return view;
    }

    private void showSetupScreen() {
        LinearLayout page = vertical(20);
        page.setGravity(Gravity.CENTER);
        page.setBackgroundColor(BG);

        LinearLayout brand = row();
        ImageView mark = brandIcon();
        brand.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout brandText = vertical(0);
        brandText.setPadding(dp(12), 0, 0, 0);
        TextView name = text("陳", 18, TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brandText.addView(name);
        brandText.addView(text("原生移动控制端", 11, MUTED));
        brand.addView(brandText);
        page.addView(brand);

        LinearLayout panel = card(22);
        panel.addView(label("SECURE PAIRING"));
        TextView title = text("连接你的自动化中心", 28, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(9);
        panel.addView(title, titleParams);
        TextView help = text("粘贴电脑端生成的固定云服务配对链接。正式客户版使用移动数据即可连接，不需要安装或开启 VPN。", 14, MUTED);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        helpParams.topMargin = dp(10);
        panel.addView(help, helpParams);

        LinearLayout guarantee = row();
        guarantee.setPadding(dp(12), dp(10), dp(12), dp(10));
        guarantee.setBackground(shape(SURFACE_2, 12, LINE));
        guarantee.addView(chip("无需 VPN", GREEN, Color.rgb(17, 49, 40)));
        TextView guaranteeText = text("  固定 HTTPS · 设备独立凭证", 11, MUTED);
        guarantee.addView(guaranteeText);
        LinearLayout.LayoutParams guaranteeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        guaranteeParams.topMargin = dp(16);
        panel.addView(guarantee, guaranteeParams);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("https://你的云服务/?access=...");
        input.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PAIRING_URL, ""));
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(13);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(shape(Color.rgb(9, 21, 34), 12, LINE));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        inputParams.topMargin = dp(18);
        panel.addView(input, inputParams);

        Button connect = button("保存并连接", CYAN, BG, Color.TRANSPARENT);
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        connectParams.topMargin = dp(12);
        panel.addView(connect, connectParams);
        connect.setOnClickListener(view -> {
            ApiClient.Pairing pairing = ApiClient.parsePairingUrl(input.getText().toString());
            if (pairing == null) {
                input.setError("请粘贴包含 access 令牌的完整 HTTPS 配对链接");
                return;
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_BASE_URL, pairing.baseUrl)
                    .putString(KEY_TOKEN, pairing.token)
                    .putString(KEY_PAIRING_URL, pairing.originalUrl)
                    .apply();
            baseUrl = pairing.baseUrl;
            token = pairing.token;
            showFullDashboard();
            startMonitorService();
        });
        page.addView(panel);

        TextView footer = text("临时联调隧道仅供开发测试，不作为客户正式入口。", 11, MUTED);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = dp(18);
        page.addView(footer, footerParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.addView(page);
        setContentView(scroll);
    }

    private void showDashboard() {
        LinearLayout page = vertical(16);
        page.setBackgroundColor(BG);

        LinearLayout header = row();
        ImageView mark = brandIcon();
        header.addView(mark, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout titles = vertical(0);
        titles.setPadding(dp(11), 0, 0, 0);
        TextView title = text("陳", 20, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        connectionText = text("正在连接…", 10, MUTED);
        titles.addView(title);
        titles.addView(connectionText);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button refresh = button("刷新", Color.TRANSPARENT, CYAN, LINE);
        Button settings = button("设置", Color.TRANSPARENT, MUTED, LINE);
        LinearLayout.LayoutParams headerButton = new LinearLayout.LayoutParams(dp(62), dp(40));
        headerButton.leftMargin = dp(7);
        header.addView(refresh, headerButton);
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(62), dp(40));
        settingsParams.leftMargin = dp(7);
        header.addView(settings, settingsParams);
        page.addView(header);
        refresh.setOnClickListener(view -> refreshState(true));
        settings.setOnClickListener(view -> showSetupScreen());

        LinearLayout statusCard = card(20);
        LinearLayout statusTop = row();
        statusTop.addView(label("CURRENT RUN"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        workerBadge = chip("等待", MUTED, SURFACE_2);
        statusTop.addView(workerBadge);
        statusCard.addView(statusTop);
        workerTitle = text("等待状态数据", 21, TEXT);
        workerTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams workerTitleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        workerTitleParams.topMargin = dp(17);
        statusCard.addView(workerTitle, workerTitleParams);
        workerDetail = text("正在连接电脑端…", 12, MUTED);
        LinearLayout.LayoutParams workerDetailParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        workerDetailParams.topMargin = dp(7);
        statusCard.addView(workerDetail, workerDetailParams);
        pipelineContainer = row();
        LinearLayout.LayoutParams pipelineParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pipelineParams.topMargin = dp(22);
        statusCard.addView(pipelineContainer, pipelineParams);
        renderPipeline("idle", "");
        page.addView(statusCard);

        LinearLayout metrics = row();
        metrics.setPadding(0, dp(12), 0, 0);
        statAccounts = addMetric(metrics, "账号", "0", 0);
        statQueue = addMetric(metrics, "等待", "0", 1);
        statCompleted = addMetric(metrics, "已完成", "0", 2);
        page.addView(metrics);

        LinearLayout master = card(18);
        LinearLayout masterHead = row();
        LinearLayout masterText = vertical(0);
        TextView masterTitle = text("全部账号轮转", 17, TEXT);
        masterTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        masterText.addView(masterTitle);
        masterText.addView(text("按各账号时间段自动调度", 10, MUTED));
        masterHead.addView(masterText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        masterHead.addView(chip("24H", CYAN, Color.rgb(18, 48, 68)));
        master.addView(masterHead);
        LinearLayout controls = row();
        controls.setPadding(0, dp(16), 0, 0);
        startButton = button("启动全部", CYAN, BG, Color.TRANSPARENT);
        stopButton = button("停止全部", Color.rgb(50, 24, 32), RED, Color.rgb(105, 49, 58));
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        startParams.rightMargin = dp(6);
        controls.addView(startButton, startParams);
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        stopParams.leftMargin = dp(6);
        controls.addView(stopButton, stopParams);
        master.addView(controls);
        page.addView(master);
        startButton.setOnClickListener(view -> postControl("/api/automation/start", "已启动全部轮转"));
        stopButton.setOnClickListener(view -> postControl("/api/automation/stop", "已停止全部任务"));

        TextView accountsTitle = sectionTitle("账号分区", "每个账号独立状态与计划");
        page.addView(accountsTitle);
        accountList = vertical(0);
        page.addView(accountList);

        TextView tasksTitle = sectionTitle("任务控制", "手机端可直接暂停、继续、重试、取消和批准发布");
        page.addView(tasksTitle);
        taskControlList = vertical(0);
        page.addView(taskControlList);

        TextView alertsTitle = sectionTitle("需要关注", "仅显示需要人工处理的事项");
        page.addView(alertsTitle);
        notificationList = vertical(0);
        page.addView(notificationList);

        Button testNotification = button("测试本机通知", SURFACE_2, CYAN, LINE);
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        testParams.topMargin = dp(18);
        page.addView(testNotification, testParams);
        testNotification.setOnClickListener(view -> {
            requestNotificationPermission();
            NotificationHelper.showTest(this);
        });

        TextView footer = text("前台每 15 秒同步 · 后台每 60 秒检查 · 登录、验证、风控、实名、封禁和额度异常才通知", 10, MUTED);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = dp(20);
        footerParams.bottomMargin = dp(32);
        page.addView(footer, footerParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.addView(page);
        setContentView(scroll);
        refreshState(true);
    }

    private void showFullDashboard() {
        if (token.isEmpty()) {
            showDashboard();
            return;
        }
        fullDashboard = new WebView(this);
        fullDashboard.setBackgroundColor(BG);
        WebSettings settings = fullDashboard.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        fullDashboard.setWebChromeClient(new WebChromeClient());
        fullDashboard.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });
        // Keep the WebView on a permanent GitHub Pages origin. The page reads a
        // token-free endpoint document and swaps the embedded tunnel whenever
        // Cloudflare rotates its temporary hostname.
        fullDashboard.loadUrl(ApiClient.DISCOVERY_SHELL + "#access=" + Uri.encode(token));
        setContentView(fullDashboard);
    }

    @Override public void onBackPressed() {
        if (fullDashboard != null && fullDashboard.canGoBack()) {
            fullDashboard.goBack();
            return;
        }
        super.onBackPressed();
    }

    private TextView addMetric(LinearLayout parent, String label, String value, int index) {
        LinearLayout card = vertical(13);
        card.setBackground(shape(SURFACE, 15, LINE));
        TextView number = text(value, 22, TEXT);
        number.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(number);
        card.addView(text(label, 10, MUTED));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        if (index > 0) params.leftMargin = dp(6);
        if (index < 2) params.rightMargin = dp(6);
        parent.addView(card, params);
        return number;
    }

    private TextView sectionTitle(String title, String subtitle) {
        TextView view = text(title + "\n" + subtitle, 18, TEXT);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(24);
        view.setLayoutParams(params);
        return view;
    }

    private void refreshState(boolean userInitiated) {
        if (refreshing || baseUrl.isEmpty()) return;
        refreshing = true;
        if (connectionText != null) connectionText.setText("正在同步…");
        executor.execute(() -> {
            try {
                JSONObject state = ApiClient.getState(baseUrl, token);
                runOnUiThread(() -> renderState(state));
                NotificationHelper.dispatchHumanAlerts(this, state);
            } catch (Exception error) {
                try {
                    String recovered = ApiClient.discoverBaseUrl();
                    JSONObject state = ApiClient.getState(recovered, token);
                    baseUrl = recovered;
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_BASE_URL, recovered).apply();
                    runOnUiThread(() -> renderState(state));
                    NotificationHelper.dispatchHumanAlerts(this, state);
                    return;
                } catch (Exception ignored) {}
                runOnUiThread(() -> {
                    if (connectionText != null) {
                        connectionText.setText("连接失败 · 点刷新重试");
                        connectionText.setTextColor(RED);
                    }
                    if (userInitiated) Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                refreshing = false;
            }
        });
    }

    private void renderState(JSONObject state) {
        connectionText.setText("固定云服务已连接");
        connectionText.setTextColor(GREEN);
        JSONObject worker = state.optJSONObject("workerActivity");
        String workerStatus = worker == null ? "idle" : worker.optString("status", "idle");
        String step = worker == null ? "等待下一条" : worker.optString("step", "等待下一条");
        String detail = worker == null ? "" : worker.optString("detail", "");
        boolean idle = "idle".equals(workerStatus);
        workerTitle.setText(idle ? "当前没有执行中任务" : step);
        workerDetail.setText(idle ? "后台执行器正在等待计划时间" : (detail.isEmpty() ? "流水线正在后台推进" : detail));
        workerBadge.setText(idle ? "等待" : "运行中");
        workerBadge.setTextColor(idle ? MUTED : GREEN);
        workerBadge.setBackground(shape(idle ? SURFACE_2 : Color.rgb(17, 49, 40), 99, Color.TRANSPARENT));
        renderPipeline(workerStatus, step);

        JSONArray queue = state.optJSONArray("queue");
        if (queue == null) queue = state.optJSONArray("tasks");
        JSONArray runs = state.optJSONArray("completedRuns");
        JSONArray accounts = state.optJSONArray("accounts");
        int waiting = 0;
        if (queue != null) for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.optJSONObject(i);
            if (item == null) continue;
            String status = item.optString("status", "");
            if (!"cancelled".equals(status) && !"published".equals(status) && !"completed".equals(status)) waiting++;
        }
        statAccounts.setText(String.valueOf(accounts == null ? 0 : accounts.length()));
        statQueue.setText(String.valueOf(waiting));
        statCompleted.setText(String.valueOf(runs == null ? 0 : runs.length()));
        renderAccounts(accounts, queue);
        renderTaskControls(queue);
        renderNotifications(state.optJSONArray("notifications"));

        JSONObject settings = state.optJSONObject("settings");
        boolean enabled = settings != null && settings.optBoolean("masterAutomationEnabled", false);
        startButton.setEnabled(!enabled);
        stopButton.setEnabled(enabled);
        startButton.setAlpha(enabled ? 0.38f : 1f);
        stopButton.setAlpha(enabled ? 1f : 0.38f);
    }

    private void renderPipeline(String status, String step) {
        pipelineContainer.removeAllViews();
        String[] names = {"文案", "豆包", "下载", "字幕", "发布", "核验"};
        int current = pipelineIndex(status, step);
        for (int i = 0; i < names.length; i++) {
            LinearLayout item = vertical(0);
            item.setGravity(Gravity.CENTER);
            int color = i < current ? GREEN : (i == current && !"idle".equals(status) ? CYAN : Color.rgb(63, 81, 106));
            TextView dot = text(i < current ? "✓" : String.valueOf(i + 1), 10, i <= current && !"idle".equals(status) ? BG : MUTED);
            dot.setGravity(Gravity.CENTER);
            dot.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            dot.setBackground(shape(color, 99, Color.TRANSPARENT));
            item.addView(dot, new LinearLayout.LayoutParams(dp(25), dp(25)));
            TextView caption = text(names[i], 9, i == current && !"idle".equals(status) ? TEXT : MUTED);
            caption.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            captionParams.topMargin = dp(6);
            item.addView(caption, captionParams);
            pipelineContainer.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
    }

    private int pipelineIndex(String status, String step) {
        if ("idle".equals(status)) return -1;
        if (step.contains("核验") || step.contains("成功") || step.contains("清理")) return 5;
        if (step.contains("发布") || step.contains("视频号") || step.contains("上传") || step.contains("转码") || step.contains("原创")) return 4;
        if (step.contains("字幕") || step.contains("剪映") || step.contains("导出")) return 3;
        if (step.contains("下载") || step.contains("成片")) return 2;
        if (step.contains("豆包") || step.contains("生成") || step.contains("对话")) return 1;
        return 0;
    }

    private void renderAccounts(JSONArray accounts, JSONArray queue) {
        accountList.removeAllViews();
        if (accounts == null || accounts.length() == 0) {
            accountList.addView(text("暂无账号", 13, MUTED));
            return;
        }
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.optJSONObject(i);
            if (account == null) continue;
            String accountId = account.optString("id", "");
            LinearLayout item = card(17);
            LinearLayout head = row();
            LinearLayout identity = vertical(0);
            TextView name = text(account.optString("name", "账号"), 17, TEXT);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            identity.addView(name);
            identity.addView(text(countAccountQueue(queue, accountId) + " 条等待", 10, MUTED));
            head.addView(identity, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            boolean enabled = account.optBoolean("runEnabled", account.optBoolean("enabled", true));
            head.addView(chip(enabled ? "已接入轮转" : "已暂停", enabled ? GREEN : AMBER, enabled ? Color.rgb(17, 49, 40) : Color.rgb(58, 43, 22)));
            item.addView(head);

            boolean wechat = "online".equals(account.optString("loginStatus"));
            boolean doubao = "online".equals(account.optString("doubaoLoginStatus"));
            LinearLayout services = row();
            services.setPadding(0, dp(14), 0, dp(12));
            TextView doubaoChip = chip("● 豆包 " + (doubao ? "在线" : "需登录"), doubao ? GREEN : RED, doubao ? Color.rgb(17, 49, 40) : Color.rgb(50, 24, 32));
            TextView wechatChip = chip("● 视频号 " + (wechat ? "在线" : "需登录"), wechat ? GREEN : RED, wechat ? Color.rgb(17, 49, 40) : Color.rgb(50, 24, 32));
            services.addView(doubaoChip);
            LinearLayout.LayoutParams wechatParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wechatParams.leftMargin = dp(8);
            services.addView(wechatChip, wechatParams);
            item.addView(services);

            JSONObject schedule = account.optJSONObject("schedule");
            boolean scheduleEnabled = schedule != null && schedule.optBoolean("enabled", false);
            String scheduleText = scheduleEnabled ? summarizeWindows(schedule.optJSONArray("windows")) : "发布时间计划已关闭";
            LinearLayout scheduleRow = row();
            scheduleRow.setPadding(dp(12), dp(11), dp(12), dp(11));
            scheduleRow.setBackground(shape(Color.rgb(9, 21, 36), 12, LINE));
            TextView scheduleLabel = text("发布时间", 10, MUTED);
            scheduleRow.addView(scheduleLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView scheduleValue = text(scheduleText, 10, scheduleEnabled ? CYAN : MUTED);
            scheduleValue.setGravity(Gravity.END);
            scheduleRow.addView(scheduleValue);
            item.addView(scheduleRow);
            accountList.addView(item);
        }
    }

    private void renderTaskControls(JSONArray queue) {
        if (taskControlList == null) return;
        taskControlList.removeAllViews();
        int shown = 0;
        if (queue != null) for (int i = 0; i < queue.length() && shown < 12; i++) {
            JSONObject task = queue.optJSONObject(i);
            if (task == null) continue;
            String status = task.optString("status", "queued");
            if ("published".equals(status) || "completed".equals(status) || "cancelled".equals(status)) continue;
            String account = task.optString("accountName", task.optString("accountId", "账号"));
            String copy = task.optString("copy", "").replace('\n', ' ');
            if (copy.length() > 34) copy = copy.substring(0, 34) + "…";

            LinearLayout item = card(15);
            LinearLayout head = row();
            TextView title = text("任务 #" + task.optInt("id", 0) + " · " + account, 14, TEXT);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            head.addView(chip(statusLabel(status), statusColor(status), statusBg(status)));
            item.addView(head);
            if (!copy.isEmpty()) {
                TextView copyText = text(copy, 11, MUTED);
                LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                copyParams.topMargin = dp(8);
                item.addView(copyText, copyParams);
            }

            LinearLayout actions = row();
            actions.setPadding(0, dp(12), 0, 0);
            addTaskAction(actions, task, "paused".equals(status) ? "继续" : "暂停", "paused".equals(status) ? "resume" : "pause");
            addTaskAction(actions, task, "重试", "retry");
            addTaskAction(actions, task, "取消", "cancel");
            if (task.optBoolean("manualReviewRequired", false)) addTaskAction(actions, task, "批准", "approve");
            item.addView(actions);
            taskControlList.addView(item);
            shown++;
        }
        if (shown == 0) {
            LinearLayout clear = card(15);
            clear.addView(text("当前没有可控制的进行中任务", 12, MUTED));
            taskControlList.addView(clear);
        }
    }

    private void addTaskAction(LinearLayout parent, JSONObject task, String caption, String action) {
        Button actionButton = button(caption, SURFACE_2, CYAN, LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1);
        params.rightMargin = dp(6);
        parent.addView(actionButton, params);
        actionButton.setOnClickListener(view -> postControl("/api/tasks/" + task.optInt("id", 0) + "/" + action, "已发送：" + caption));
    }

    private String statusLabel(String status) {
        if ("generating".equals(status)) return "生成中";
        if ("captioning".equals(status)) return "字幕中";
        if ("publishing".equals(status)) return "发布中";
        if ("failed".equals(status)) return "失败";
        if ("paused".equals(status)) return "已暂停";
        return "等待中";
    }

    private int statusColor(String status) {
        if ("failed".equals(status)) return RED;
        if ("paused".equals(status)) return AMBER;
        if ("publishing".equals(status) || "captioning".equals(status) || "generating".equals(status)) return CYAN;
        return MUTED;
    }

    private int statusBg(String status) {
        if ("failed".equals(status)) return Color.rgb(50, 24, 32);
        if ("paused".equals(status)) return Color.rgb(58, 43, 22);
        return SURFACE_2;
    }

    private int countAccountQueue(JSONArray queue, String accountId) {
        if (queue == null) return 0;
        int count = 0;
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.optJSONObject(i);
            if (item == null || !accountId.equals(item.optString("accountId"))) continue;
            String status = item.optString("status", "");
            if (!"cancelled".equals(status) && !"published".equals(status) && !"completed".equals(status)) count++;
        }
        return count;
    }

    private String summarizeWindows(JSONArray windows) {
        if (windows == null || windows.length() == 0) return "已开启随机发布";
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < windows.length() && i < 3; i++) {
            JSONObject window = windows.optJSONObject(i);
            if (window == null) continue;
            if (value.length() > 0) value.append(" · ");
            value.append(window.optString("start", "")).append("–").append(window.optString("end", ""));
        }
        if (windows.length() > 3) value.append(" +").append(windows.length() - 3);
        return value.toString();
    }

    private void renderNotifications(JSONArray notes) {
        notificationList.removeAllViews();
        int shown = 0;
        if (notes != null) for (int i = 0; i < notes.length() && shown < 4; i++) {
            JSONObject note = notes.optJSONObject(i);
            if (note == null || note.optBoolean("read", false)) continue;
            LinearLayout item = card(16);
            LinearLayout head = row();
            TextView dot = text("!", 11, BG);
            dot.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            dot.setGravity(Gravity.CENTER);
            dot.setBackground(shape(AMBER, 99, Color.TRANSPARENT));
            head.addView(dot, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView title = text(note.optString("title", "状态提醒"), 14, TEXT);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            titleParams.leftMargin = dp(10);
            head.addView(title, titleParams);
            item.addView(head);
            TextView body = text(note.optString("body", ""), 11, MUTED);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bodyParams.topMargin = dp(9);
            item.addView(body, bodyParams);
            notificationList.addView(item);
            shown++;
        }
        if (shown == 0) {
            LinearLayout clear = card(16);
            TextView clearText = text("✓ 目前没有需要你处理的事项", 13, GREEN);
            clearText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            clear.addView(clearText);
            notificationList.addView(clear);
        }
    }

    private void postControl(String path, String successMessage) {
        startButton.setEnabled(false);
        stopButton.setEnabled(false);
        executor.execute(() -> {
            try {
                ApiClient.post(baseUrl, token, path);
                runOnUiThread(() -> Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show());
                refreshState(false);
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void startMonitorService() {
        Intent service = new Intent(this, MonitoringService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
    }
}

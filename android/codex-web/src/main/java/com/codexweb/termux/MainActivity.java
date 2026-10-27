package com.codexweb.termux;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.InputType;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String TERMUX_SERVICE = "com.termux.app.RunCommandService";
    private static final String OPENCODE_REPOSITORY_URL =
            "https://github.com/jhonathamlegal-sudo/codex-web-opencode.git";
    private static final String OPENCODE_REPOSITORY_COMMIT =
            "ab25de89be501a62945d017d57b57eae8a0470a1";
    private static final String BOOTSTRAP_ASSET = "codex-web-termux-start.sh";
    private static final String WATCHDOG_ASSET = "codex-web-termux-watchdog.sh";
    private static final String SERVER_URL = "http://127.0.0.1:4096";
    private static final String SERVER_INFO_URL = SERVER_URL + "/api/info";
    private static final String SERVER_SESSION_URL = SERVER_URL + "/api/session?limit=1";
    private static final String APINEX_MODELS_URL = "https://apinex.bond/v1/models";
    private static final String APINEX_SITE_URL = "https://apinex.bond/";
    private static final String STATUS_ACTION = "com.codexweb.termux.STATUS_RESULT";
    private static final String LOG_ACTION = "com.codexweb.termux.LOG_RESULT";
    private static final int MAX_PROBES = 60;
    private static final String PREFS = "codex_web";
    private static final String PASSWORD_KEY = "server_password";
    private static final String SETUP_COMMAND =
            "mkdir -p ~/.termux && " +
            "(grep -qxF 'allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || " +
            "echo 'allow-external-apps=true' >> ~/.termux/termux.properties)";

    private static final int BG = Color.rgb(10, 12, 16);
    private static final int SURFACE = Color.rgb(21, 25, 32);
    private static final int SURFACE_2 = Color.rgb(28, 33, 42);
    private static final int TEXT = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(159, 169, 184);
    private static final int GREEN = Color.rgb(107, 231, 157);
    private static final int BLUE = Color.rgb(111, 174, 255);
    private static final int AMBER = Color.rgb(255, 194, 92);
    private static final int RED = Color.rgb(255, 118, 118);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ScrollView setupView;
    private WebView webView;
    private TextView stateChip;
    private TextView connectionTitle;
    private TextView connectionDetail;
    private TextView setupHint;
    private TextView codeBlock;
    private ProgressBar progress;
    private Button primaryAction;
    private Button openTermuxAction;
    private Button copyAction;
    private Button installComponentsAction;
    private Button batteryAction;
    private Button chatAction;
    private Button viewLogAction;
    private Button copyLogAction;
    private LinearLayout keyCard;
    private EditText apiKeyInput;
    private Button saveKeyAction;
    private Button openApiKeyAction;
    private Button toggleKeyAction;
    private boolean keyVisible;
    private TextView componentsStatus;
    private FdroidInstaller fdroidInstaller;
    private BroadcastReceiver installReceiver;
    private BroadcastReceiver commandOutputReceiver;
    private int commandRequestCode;
    private int probeCount;
    private int commandAttempts;
    private String serverPassword;
    private boolean repairRequested;
    private boolean connectionRequested;
    private boolean batteryPromptShown;
    private boolean installFlowStarted;
    private boolean waitingForBatteryReturn;
    private boolean termuxLaunchStarted;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        serverPassword = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PASSWORD_KEY, null);
        if (serverPassword == null) {
            serverPassword = randomPassword();
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PASSWORD_KEY, serverPassword).apply();
        }
        fdroidInstaller = new FdroidInstaller(this);
        installReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (fdroidInstaller != null) fdroidInstaller.onInstallResult(intent);
            }
        };
        IntentFilter filter = new IntentFilter(FdroidInstaller.INSTALL_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(installReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(installReceiver, filter);
        commandOutputReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String output = intent.getStringExtra("com.termux.RUN_COMMAND_RESULT_STDOUT");
                if (output == null) output = "";
                if (STATUS_ACTION.equals(intent.getAction())) applyBootstrapStatus(output);
                if (LOG_ACTION.equals(intent.getAction())) showLog(output, intent.getBooleanExtra("copy_log", false));
            }
        };
        IntentFilter outputFilter = new IntentFilter();
        outputFilter.addAction(STATUS_ACTION);
        outputFilter.addAction(LOG_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(commandOutputReceiver, outputFilter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(commandOutputReceiver, outputFilter);
        buildUi();
        if (!fdroidInstaller.resume(this, installerCallback())) {
            refreshInstallerState();
            if (FdroidInstaller.missing(this).isEmpty()) {
                mainHandler.postDelayed(this::startTermuxAndServer, 250);
            } else {
                mainHandler.postDelayed(this::autoInstallIfNeeded, 350);
            }
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        setupView = new ScrollView(this);
        setupView.setFillViewport(true);
        setupView.setClipToPadding(false);
        LinearLayout content = column(20, 24, 20, 28);
        setupView.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(setupView, new FrameLayout.LayoutParams(-1, -1));

        webView = new WebView(this);
        configureWebView(webView);
        webView.setVisibility(View.GONE);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("C", 22, TEXT, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(circle(GREEN));
        brandRow.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout brandText = column(0, 0, 0, 0);
        TextView title = text("Codex Web", 28, TEXT, true);
        TextView subtitle = text("IA local, com o poder do seu Termux", 14, MUTED, false);
        brandText.addView(title);
        brandText.addView(subtitle, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams brandTextParams = new LinearLayout.LayoutParams(0, -2, 1);
        brandTextParams.leftMargin = dp(14);
        brandRow.addView(brandText, brandTextParams);
        content.addView(brandRow);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        stateChip = text("VERIFICANDO…", 11, BG, true);
        stateChip.setGravity(Gravity.CENTER);
        stateChip.setPadding(dp(12), dp(7), dp(12), dp(7));
        stateChip.setBackground(round(AMBER, 24));
        statusRow.addView(stateChip, new LinearLayout.LayoutParams(-2, -2));
        TextView privacy = text("  •  conexão somente neste aparelho", 12, MUTED, false);
        statusRow.addView(privacy, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(18);
        content.addView(statusRow, statusParams);

        LinearLayout connectionCard = card();
        connectionCard.addView(text("CONEXÃO", 11, BLUE, true));
        connectionTitle = text("Preparando seu workspace…", 20, TEXT, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(9);
        connectionCard.addView(connectionTitle, titleParams);
        connectionDetail = text("O serviço será iniciado no Termux em segundo plano; você não precisa digitar comandos.", 14, MUTED, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(7);
        connectionCard.addView(connectionDetail, detailParams);
        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        progressParams.topMargin = dp(16);
        connectionCard.addView(progress, progressParams);
        content.addView(connectionCard, marginTop(dp(22)));

        LinearLayout installerCard = card();
        installerCard.addView(text("Instalador do ambiente", 17, TEXT, true));
        TextView installerHint = text("Verifica o Termux e as extensões oficiais antes de iniciar o servidor.", 14, MUTED, false);
        LinearLayout.LayoutParams installerHintParams = new LinearLayout.LayoutParams(-1, -2);
        installerHintParams.topMargin = dp(7);
        installerCard.addView(installerHint, installerHintParams);
        componentsStatus = text("Verificando componentes…", 14, AMBER, true);
        LinearLayout.LayoutParams componentsParams = new LinearLayout.LayoutParams(-1, -2);
        componentsParams.topMargin = dp(14);
        installerCard.addView(componentsStatus, componentsParams);
        installComponentsAction = actionButton("Instalar e configurar", true);
        installComponentsAction.setOnClickListener(v -> installComponents());
        LinearLayout.LayoutParams installButtonParams = new LinearLayout.LayoutParams(-1, dp(48));
        installButtonParams.topMargin = dp(14);
        installerCard.addView(installComponentsAction, installButtonParams);
        batteryAction = actionButton("Desativar otimização de bateria do Termux", false);
        batteryAction.setOnClickListener(v -> configureBatteryOptimization());
        LinearLayout.LayoutParams batteryParams = new LinearLayout.LayoutParams(-1, dp(48));
        batteryParams.topMargin = dp(10);
        installerCard.addView(batteryAction, batteryParams);
        content.addView(installerCard, marginTop(dp(14)));

        keyCard = buildApiKeyCard();
        content.addView(keyCard, marginTop(dp(14)));

        primaryAction = actionButton("Iniciar conexão", true);
        primaryAction.setOnClickListener(v -> startTermuxAndServer());
        content.addView(primaryAction, marginTop(dp(16)));

        chatAction = actionButton("Abrir chat (aguardando servidor)", false);
        chatAction.setEnabled(false);
        chatAction.setOnClickListener(v -> showWebView());
        content.addView(chatAction, marginTop(dp(10)));

        LinearLayout secondaryRow = new LinearLayout(this);
        secondaryRow.setGravity(Gravity.CENTER);
        openTermuxAction = actionButton("Abrir Termux", false);
        openTermuxAction.setOnClickListener(v -> openTermux());
        copyAction = actionButton("Copiar comando", false);
        copyAction.setOnClickListener(v -> copySetupCommand());
        secondaryRow.addView(openTermuxAction, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        copyParams.leftMargin = dp(10);
        secondaryRow.addView(copyAction, copyParams);
        content.addView(secondaryRow, marginTop(dp(10)));

        LinearLayout logRow = new LinearLayout(this);
        logRow.setGravity(Gravity.CENTER);
        viewLogAction = actionButton("Ver log", false);
        viewLogAction.setOnClickListener(v -> requestLog(false));
        copyLogAction = actionButton("Copiar log", false);
        copyLogAction.setOnClickListener(v -> requestLog(true));
        logRow.addView(viewLogAction, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams copyLogParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        copyLogParams.leftMargin = dp(10);
        logRow.addView(copyLogAction, copyLogParams);
        content.addView(logRow, marginTop(dp(8)));

        LinearLayout setupCard = card();
        setupCard.addView(text("Configuração única", 17, TEXT, true));
        setupHint = text("Se o Termux bloquear o comando externo, copie este comando, cole no Termux e reinicie o aplicativo.", 14, MUTED, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(7);
        setupCard.addView(setupHint, hintParams);
        codeBlock = text(SETUP_COMMAND, 12, Color.rgb(204, 218, 232), false);
        codeBlock.setTypeface(Typeface.MONOSPACE);
        codeBlock.setTextIsSelectable(true);
        codeBlock.setPadding(dp(13), dp(13), dp(13), dp(13));
        codeBlock.setBackground(round(Color.rgb(12, 15, 20), 12));
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(-1, -2);
        codeParams.topMargin = dp(14);
        setupCard.addView(codeBlock, codeParams);
        content.addView(setupCard, marginTop(dp(22)));

        LinearLayout stepsCard = card();
        stepsCard.addView(text("Como funciona", 17, TEXT, true));
        addStep(stepsCard, "1", "Termux instalado", "O backend permanece no armazenamento privado do Termux.", GREEN);
        addStep(stepsCard, "2", "Permissão externa", "Permite que este APK peça ao Termux para iniciar o servidor.", AMBER);
        addStep(stepsCard, "3", "Chat conectado", "O chat, sessões, modelos e ferramentas são carregados do servidor real.", BLUE);
        content.addView(stepsCard, marginTop(dp(14)));

        TextView footer = text("Codex Web  •  servidor local  •  sem chave dentro do APK", 12, Color.rgb(112, 123, 139), false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(24);
        content.addView(footer, footerParams);
        setContentView(root);
    }

    private LinearLayout buildApiKeyCard() {
        LinearLayout card = card();
        card.addView(text("Conectar à APInex", 17, TEXT, true));
        TextView explanation = text("A chave é usada somente pelo servidor local para liberar os modelos do provedor.", 14, MUTED, false);
        LinearLayout.LayoutParams explanationParams = new LinearLayout.LayoutParams(-1, -2);
        explanationParams.topMargin = dp(7);
        card.addView(explanation, explanationParams);

        apiKeyInput = new EditText(this);
        apiKeyInput.setSingleLine(true);
        apiKeyInput.setHint("Cole sua API key");
        apiKeyInput.setTextColor(TEXT);
        apiKeyInput.setHintTextColor(MUTED);
        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKeyInput.setPadding(dp(14), 0, dp(14), 0);
        apiKeyInput.setBackground(round(SURFACE_2, 12));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(52));
        inputParams.topMargin = dp(14);
        card.addView(apiKeyInput, inputParams);

        toggleKeyAction = actionButton("Mostrar key", false);
        toggleKeyAction.setOnClickListener(v -> {
            keyVisible = !keyVisible;
            apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT
                    | (keyVisible ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            toggleKeyAction.setText(keyVisible ? "Ocultar key" : "Mostrar key");
            apiKeyInput.setSelection(apiKeyInput.length());
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(-1, dp(42));
        toggleParams.topMargin = dp(6);
        card.addView(toggleKeyAction, toggleParams);

        saveKeyAction = actionButton("Validar e salvar a key", true);
        saveKeyAction.setOnClickListener(v -> validateAndSaveApiKey());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(48));
        saveParams.topMargin = dp(10);
        card.addView(saveKeyAction, saveParams);

        openApiKeyAction = actionButton("Ainda não tem a key? Clique aqui para obter", false);
        openApiKeyAction.setTextColor(BLUE);
        openApiKeyAction.setBackgroundColor(Color.TRANSPARENT);
        openApiKeyAction.setOnClickListener(v -> openUrl(APINEX_SITE_URL));
        LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(-1, dp(48));
        linkParams.topMargin = dp(4);
        card.addView(openApiKeyAction, linkParams);

        card.setVisibility(View.GONE);
        return card;
    }

    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        view.setWebChromeClient(new WebChromeClient());
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView current, WebResourceRequest request) {
                Uri uri = request == null ? null : request.getUrl();
                if (uri == null) return true;
                String host = uri.getHost();
                boolean local = ("http".equalsIgnoreCase(uri.getScheme())
                        && ("127.0.0.1".equals(host) || "localhost".equals(host)));
                if (local) return false;
                openUrl(uri.toString());
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView current, String url) {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                boolean local = ("http".equalsIgnoreCase(uri.getScheme())
                        && ("127.0.0.1".equals(host) || "localhost".equals(host)));
                if (local) return false;
                openUrl(url);
                return true;
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView current, HttpAuthHandler handler, String host, String realm) {
                if ("127.0.0.1".equals(host) || "localhost".equals(host)) handler.proceed("opencode", serverPassword);
                else handler.cancel();
            }

            @Override
            public void onReceivedError(WebView current, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showServerFailure();
            }
        });
    }

    private void addStep(LinearLayout parent, String number, String title, String detail, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        TextView badge = text(number, 13, BG, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(circle(color));
        row.addView(badge, new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout body = column(0, 0, 0, 0);
        body.addView(text(title, 15, TEXT, true));
        TextView copy = text(detail, 13, MUTED, false);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-1, -2);
        copyParams.topMargin = dp(3);
        body.addView(copy, copyParams);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0, -2, 1);
        bodyParams.leftMargin = dp(12);
        row.addView(body, bodyParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(18);
        parent.addView(row, rowParams);
    }

    private boolean isTermuxInstalled() {
        return FdroidInstaller.installed(this, TERMUX_PACKAGE);
    }

    private void refreshInstallerState() {
        String summary = FdroidInstaller.summary(this);
        componentsStatus.setText(summary + "\n" + FdroidInstaller.details(this));
        refreshBatteryState();
        if (!FdroidInstaller.missing(this).isEmpty()) {
            setState("PREPARAÇÃO", AMBER, "Ambiente incompleto", "Instale os componentes oficiais para liberar a conexão automática.", false);
            primaryAction.setText("Instalar e configurar");
            primaryAction.setEnabled(true);
            primaryAction.setOnClickListener(v -> installComponents());
            openTermuxAction.setVisibility(View.GONE);
            progress.setVisibility(View.GONE);
            return;
        }
        installComponentsAction.setText("Componentes prontos");
        installComponentsAction.setEnabled(false);
        primaryAction.setText("Iniciar conexão");
        primaryAction.setEnabled(true);
        primaryAction.setOnClickListener(v -> startTermuxAndServer());
    }

    private boolean canRunTermuxCommand() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission("com.termux.permission.RUN_COMMAND") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean needsBatteryOptimization() {
        if (Build.VERSION.SDK_INT < 23) return false;
        try {
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            return power == null || !power.isIgnoringBatteryOptimizations(TERMUX_PACKAGE);
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private void showInstallPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permissão necessária")
                .setMessage("Para instalar Termux, API, Boot e Widget, o Android precisa permitir que o Codex Web instale APKs oficiais. Na tela seguinte, ative 'Permitir desta fonte'.")
                .setPositiveButton("Abrir configurações", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName())));
                    } catch (ActivityNotFoundException ignored) {
                        startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                    }
                })
                .setNegativeButton("Agora não", null)
                .show();
    }

    private void showBatteryPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permissão de bateria")
                .setMessage("Para o servidor não ser encerrado pelo Android, permita que o Termux funcione sem restrições de bateria. Isso é opcional, mas recomendado.")
                .setPositiveButton("Abrir configurações", (dialog, which) -> {
                    waitingForBatteryReturn = true;
                    configureBatteryOptimization();
                })
                .setNegativeButton("Continuar mesmo assim", (dialog, which) -> startTermuxAndServer())
                .show();
    }

    private void refreshBatteryState() {
        if (batteryAction == null) return;
        boolean ignored = false;
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
                ignored = power != null && power.isIgnoringBatteryOptimizations(TERMUX_PACKAGE);
            } catch (RuntimeException ignoredError) {
                // Some Android builds hide this status; keep the manual action visible.
            }
        }
        batteryAction.setText(ignored ? "Bateria do Termux configurada" : "Desativar otimização de bateria do Termux");
        batteryAction.setEnabled(!ignored);
    }

    private void configureBatteryOptimization() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + TERMUX_PACKAGE));
            startActivity(request);
        } catch (ActivityNotFoundException | SecurityException error) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, "Abra as configurações de bateria e permita o Termux em 'Sem restrições'.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void installComponents() {
        if (fdroidInstaller == null || fdroidInstaller.isRunning()) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            setState("PERMISSÃO", AMBER, "Permita instalações pelo Codex Web", "O Android precisa autorizar a instalação dos componentes oficiais.", false);
            componentsStatus.setText("Abra a permissão de instalação e toque novamente em Instalar e configurar.");
            showInstallPermissionDialog();
            return;
        }
        primaryAction.setEnabled(false);
        installComponentsAction.setEnabled(false);
        setState("INSTALANDO", AMBER, "Preparando componentes…", "O Android exibirá uma confirmação para cada APK.", true);
        installFlowStarted = true;
        fdroidInstaller.start(this, installerCallback());
    }

    private void autoInstallIfNeeded() {
        if (fdroidInstaller == null || fdroidInstaller.isRunning() || installFlowStarted) return;
        if (FdroidInstaller.missing(this).isEmpty()) return;
        installComponents();
    }

    private FdroidInstaller.Callback installerCallback() {
        return new FdroidInstaller.Callback() {
            @Override
            public void status(String message) {
                componentsStatus.setText(message);
            }

            @Override
            public void permissionRequired() {
                installFlowStarted = false;
                installComponentsAction.setEnabled(true);
                setState("PERMISSÃO", AMBER, "Permita instalações pelo Codex Web", "O Android precisa autorizar a instalação dos componentes oficiais.", false);
                showInstallPermissionDialog();
            }

            @Override
            public void finished(boolean success) {
                installFlowStarted = false;
                refreshInstallerState();
                if (!success) {
                    installComponentsAction.setEnabled(true);
                    installComponentsAction.setText("Tentar instalação novamente");
                    return;
                }
                componentsStatus.setText(FdroidInstaller.details(MainActivity.this));
                startTermuxAndServer();
            }
        };
    }

    private void startTermuxAndServer() {
        if (!FdroidInstaller.missing(this).isEmpty()) {
            refreshInstallerState();
            return;
        }
        if (!isTermuxInstalled()) {
            showTermuxMissing();
            return;
        }
        if (!canRunTermuxCommand()) {
            showExternalAppsInstructions();
            return;
        }
        if (needsBatteryOptimization() && !batteryPromptShown) {
            batteryPromptShown = true;
            showBatteryPermissionDialog();
            return;
        }
        connectionRequested = true;
        setState("CONECTANDO", AMBER, "Iniciando o serviço do Termux…", "Executando o servidor em segundo plano; esta tela permanecerá aberta.", true);
        openTermuxAction.setVisibility(View.VISIBLE);
        primaryAction.setEnabled(false);
        repairRequested = false;
        commandAttempts = 0;
        launchTermuxThenCommand();
    }

    private void launchTermuxThenCommand() {
        connectionDetail.setText("Abrindo o Termux uma vez para concluir a inicialização…");
        if (!termuxLaunchStarted) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(TERMUX_PACKAGE);
            if (launch != null) {
                termuxLaunchStarted = true;
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
                try {
                    startActivity(launch);
                } catch (RuntimeException ignored) {
                    // RUN_COMMAND may still work if Termux is already initialized.
                }
            }
        }
        mainHandler.postDelayed(this::attemptExternalCommand, 1800);
    }

    private void attemptExternalCommand() {
        try {
            sendRunCommand(repairRequested);
            probeCount = 0;
            requestStatus();
            probeServer();
        } catch (SecurityException error) {
            commandAttempts++;
            if (commandAttempts < 8) {
                connectionDetail.setText("Aguardando o Termux… tentativa " + commandAttempts + "/7");
                mainHandler.postDelayed(this::attemptExternalCommand, 2500);
            } else {
                showExternalAppsInstructions();
            }
        }
    }

    private void sendRunCommand(boolean repair) {
        final String bootstrap;
        final String watchdog;
        try {
            bootstrap = readAsset(BOOTSTRAP_ASSET);
            watchdog = readAsset(WATCHDOG_ASSET);
        } catch (IOException error) {
            showBootstrapError("Scripts de bootstrap ausentes no APK: " + error.getMessage());
            return;
        }
        String bootstrapEncoded = Base64.getEncoder().encodeToString(bootstrap.getBytes(StandardCharsets.UTF_8));
        String watchdogEncoded = Base64.getEncoder().encodeToString(watchdog.getBytes(StandardCharsets.UTF_8));
        String command = "mkdir -p \"$HOME/.cache/codex-web\"; "
                + "printf '%s' '" + shellQuote(bootstrapEncoded) + "' | base64 -d > \"$HOME/.cache/codex-web/termux-start.sh.new\" || exit 1; "
                + "printf '%s' '" + shellQuote(watchdogEncoded) + "' | base64 -d > \"$HOME/.cache/codex-web/termux-watchdog.sh.new\" || exit 1; "
                + "mv \"$HOME/.cache/codex-web/termux-start.sh.new\" \"$HOME/.cache/codex-web/termux-start.sh\"; "
                + "mv \"$HOME/.cache/codex-web/termux-watchdog.sh.new\" \"$HOME/.cache/codex-web/termux-watchdog.sh\"; "
                + "chmod 700 \"$HOME/.cache/codex-web/termux-start.sh\" \"$HOME/.cache/codex-web/termux-watchdog.sh\"; "
                + "export OPENCODE_REPOSITORY_COMMIT='" + shellQuote(OPENCODE_REPOSITORY_COMMIT)
                + "'; export OPENCODE_REPOSITORY_URL='" + shellQuote(OPENCODE_REPOSITORY_URL)
                + "'; export OPENCODE_SERVER_PASSWORD='" + shellQuote(serverPassword)
                + "'; export OPENCODE_REPAIR='" + (repair ? "1" : "0")
                + "'; exec bash \"$HOME/.cache/codex-web/termux-start.sh\"";
        Intent intent = new Intent("com.termux.RUN_COMMAND");
        intent.setClassName(TERMUX_PACKAGE, TERMUX_SERVICE);
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        try {
            startService(intent);
            returnToLauncher();
        } catch (RuntimeException error) {
            showExternalAppsInstructions();
        }
    }

    private void sendRunCommandForRepair() {
        repairRequested = true;
        commandAttempts = 0;
        try {
            launchTermuxThenCommand();
        } catch (SecurityException error) {
            showExternalAppsInstructions();
        }
    }

    private void probeServer() {
        executor.execute(() -> {
            boolean ready = false;
            try {
                String credentials = "opencode:" + serverPassword;
                String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                ready = probeEndpoint(SERVER_INFO_URL, encoded) && probeEndpoint(SERVER_SESSION_URL, encoded);
            } catch (IOException ignored) {
                // The server may still be booting.
            }
            boolean finalReady = ready;
            mainHandler.post(() -> {
                requestStatus();
                if (finalReady) showWebView();
                else if (++probeCount < MAX_PROBES) {
                    connectionDetail.setText("Servidor local iniciando… " + probeCount + "/" + MAX_PROBES);
                    mainHandler.postDelayed(this::probeServer, 1000);
                } else showServerFailure();
            });
        });
    }

    private boolean probeEndpoint(String endpoint, String encodedCredentials) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(700);
        connection.setReadTimeout(1200);
        connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);
        boolean ready = connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        connection.disconnect();
        return ready;
    }

    private void showWebView() {
        chatAction.setText("Abrir chat");
        chatAction.setEnabled(true);
        setupView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        String credentials = "opencode:" + serverPassword;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String authToken = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String url = SERVER_URL + "/?auth_token=" + Uri.encode(authToken);
        webView.loadUrl(url, Collections.singletonMap("Authorization", "Basic " + encoded));
    }

    private void showTermuxMissing() {
        setState("TERMUX AUSENTE", RED, "Termux não encontrado", "Use o instalador guiado para baixar o Termux e suas extensões do F-Droid.", false);
        primaryAction.setText("Instalar e configurar");
        primaryAction.setEnabled(true);
        primaryAction.setOnClickListener(v -> installComponents());
        openTermuxAction.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
    }

    private void requestStatus() {
        if (!isTermuxInstalled()) return;
        sendOutputCommand("cat \"$HOME/.cache/codex-web/status\" 2>/dev/null || true", STATUS_ACTION, false);
    }

    private void applyBootstrapStatus(String output) {
        String line = output == null ? "" : output.trim();
        if (line.isEmpty() || webView.getVisibility() == View.VISIBLE) return;
        String[] parts = line.split("\\|", 3);
        if (parts.length < 2) return;
        String phase = parts[0];
        String message = parts[1];
        String elapsed = parts.length > 2 ? parts[2] : "?";
        if ("cloning".equals(phase)) setState("CLONANDO", AMBER, "Clonando o OpenCode…", message, true);
        else if ("installing_dependencies".equals(phase) || "installing".equals(phase)) setState("INSTALANDO", AMBER, "Instalando componentes…", message, true);
        else if ("compiling".equals(phase)) setState("COMPILANDO", BLUE, "Compilando a interface…", message, true);
        else if ("starting".equals(phase) || "checking".equals(phase) || "restarting".equals(phase)) setState("PREPARAÇÃO", AMBER, "Preparando servidor…", message, true);
        else if ("online".equals(phase)) setState("ONLINE", GREEN, "Servidor online", message + " Tempo: " + elapsed + "s", false);
        else if ("zombie_restart".equals(phase)) setState("RECUPERANDO", AMBER, "Servidor zumbi detectado", message, true);
        else if ("repair_required".equals(phase) || "repository_conflict".equals(phase)) showRepairRequired();
        else if ("key_missing".equals(phase)) showKeyMissing();
        else if (phase.endsWith("_error") || "config_missing".equals(phase)) showBootstrapError(message);
    }

    private void showRepairRequired() {
        setState("REPARO", AMBER, "Ambiente incompleto", "~/opencode existe, mas não parece um checkout funcional. O reparo cria um backup e não apaga seu código.", false);
        primaryAction.setText("Reparar com backup seguro");
        primaryAction.setEnabled(true);
        primaryAction.setOnClickListener(v -> sendRunCommandForRepair());
        setupHint.setText("O diretório atual será renomeado para ~/opencode-user-backup-AAAAmmdd-HHmmss antes de um novo clone. Nada será apagado.");
        progress.setVisibility(View.GONE);
    }

    private void showKeyMissing() {
        setState("CONFIGURAÇÃO", AMBER, "Chave APINEX ausente", "Configure a chave no Termux para liberar os modelos gratuitos.", false);
        keyCard.setVisibility(View.VISIBLE);
        saveKeyAction.setEnabled(true);
        setupHint.setText("Você pode inserir a key no cartão acima. Como alternativa, configure ~/.config/opencode/apinex.env no Termux.");
        codeBlock.setText("mkdir -p ~/.config/opencode && printf '%s\\n' 'APINEX_API_KEY=sua-chave-aqui' > ~/.config/opencode/apinex.env && chmod 600 ~/.config/opencode/apinex.env");
        primaryAction.setText("Tentar novamente");
        primaryAction.setEnabled(true);
        primaryAction.setOnClickListener(v -> startTermuxAndServer());
        progress.setVisibility(View.GONE);
    }

    private void validateAndSaveApiKey() {
        final String key = apiKeyInput.getText().toString().trim();
        if (key.isEmpty()) {
            apiKeyInput.setError("Insira uma API key.");
            return;
        }
        saveKeyAction.setEnabled(false);
        setState("VALIDANDO", BLUE, "Validando a key APInex…", "A chave não será gravada se a API rejeitá-la.", true);
        executor.execute(() -> {
            boolean valid = validateApiKey(key);
            mainHandler.post(() -> {
                if (!valid) {
                    saveKeyAction.setEnabled(true);
                    setState("ERRO", RED, "A API key foi rejeitada", "Confira a chave no site oficial da APInex e tente novamente.", false);
                    return;
                }
                sendApiKeyToTermux(key);
            });
        });
    }

    private boolean validateApiKey(String key) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(APINEX_MODELS_URL).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Authorization", "Bearer " + key);
            connection.setRequestProperty("Accept", "application/json");
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void sendApiKeyToTermux(String key) {
        String command = "umask 077; mkdir -p \"$HOME/.config/opencode\"; "
                + "IFS= read -r APINEX_API_KEY; "
                + "case \"$APINEX_API_KEY\" in *[[:cntrl:]]*) exit 2;; esac; "
                + "printf 'APINEX_API_KEY=%s\\n' \"$APINEX_API_KEY\" > \"$HOME/.config/opencode/apinex.env\"; "
                + "chmod 600 \"$HOME/.config/opencode/apinex.env\"; unset APINEX_API_KEY";
        try {
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName(TERMUX_PACKAGE, TERMUX_SERVICE);
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
            intent.putExtra("com.termux.RUN_COMMAND_STDIN", key + "\n");
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            startService(intent);
            apiKeyInput.setText("");
            keyCard.setVisibility(View.GONE);
            setState("CONFIGURAÇÃO", GREEN, "Key salva no Termux", "O servidor será reiniciado para carregar a configuração.", true);
            mainHandler.postDelayed(this::startTermuxAndServer, 1800);
        } catch (SecurityException error) {
            saveKeyAction.setEnabled(true);
            showExternalAppsInstructions();
        }
    }

    private void showBootstrapError(String message) {
        setState("ERRO", RED, "Erro no bootstrap", message + " Veja o log para detalhes.", false);
        primaryAction.setText("Tentar novamente");
        primaryAction.setEnabled(true);
        primaryAction.setOnClickListener(v -> startTermuxAndServer());
        progress.setVisibility(View.GONE);
    }

    private void openTermuxPermissions() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + TERMUX_PACKAGE)));
        } catch (ActivityNotFoundException ignored) {
            openTermux();
        }
    }

    private void returnToLauncher() {
        mainHandler.postDelayed(() -> {
            try {
                Intent self = new Intent(this, MainActivity.class);
                self.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(self);
            } catch (RuntimeException ignored) {
                // The launcher may already be in front.
            }
        }, 500);
    }

    private void showExternalAppsInstructions() {
        setState("AÇÃO NECESSÁRIA", AMBER, "Permissão externa bloqueada", "O Termux bloqueou comandos vindos de outros aplicativos.", false);
        setupHint.setText("Ative allow-external-apps no Termux. O botão abaixo copia a configuração sem expor sua API key.");
        primaryAction.setText("Verificar conexão");
        primaryAction.setEnabled(true);
        primaryAction.setOnClickListener(v -> startTermuxAndServer());
        openTermuxAction.setVisibility(View.VISIBLE);
        copyAction.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
        new AlertDialog.Builder(this)
                .setTitle("Permitir comandos do Codex Web")
                .setMessage("O Android não mostra uma caixa de permissão comum para esta integração. O Termux precisa ser configurado uma vez para aceitar comandos externos.")
                .setPositiveButton("Permissões do Termux", (dialog, which) -> openTermuxPermissions())
                .setNeutralButton("Abrir Termux", (dialog, which) -> openTermux())
                .setNegativeButton("Depois", null)
                .show();
    }

    private void requestLog(boolean copy) {
        sendOutputCommand("tail -n 200 \"$HOME/.cache/codex-web/server.log\" 2>/dev/null || echo 'Log ainda não existe.'", LOG_ACTION, copy);
    }

    private void sendOutputCommand(String command, String action, boolean copyLog) {
        if (!isTermuxInstalled()) {
            Toast.makeText(this, "Termux ainda não está instalado.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent result = new Intent(action).setPackage(getPackageName());
            result.putExtra("copy_log", copyLog);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(this, ++commandRequestCode, result, flags);
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName(TERMUX_PACKAGE, TERMUX_SERVICE);
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
            intent.putExtra("com.termux.RUN_COMMAND_RESULT_PENDING_INTENT", pending);
            startService(intent);
        } catch (SecurityException ignored) {
            // The main connection path will show the complete external-app permission guidance.
        }
    }

    private void showLog(String output, boolean copy) {
        if (copy) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Codex Web server.log", output));
            Toast.makeText(this, "Log copiado.", Toast.LENGTH_SHORT).show();
            return;
        }
        TextView logText = text(output, 11, Color.rgb(220, 226, 234), false);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextIsSelectable(true);
        logText.setPadding(dp(14), dp(10), dp(14), dp(10));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(logText);
        new AlertDialog.Builder(this).setTitle("Log do servidor").setView(scroll).setPositiveButton("Fechar", null).show();
    }

    private void showServerFailure() {
        setState("SERVIDOR OFFLINE", RED, "Não foi possível conectar ao servidor", "Verifique o log ~/.cache/codex-web/server.log no Termux e confirme se ~/opencode está completo.", false);
        setupHint.setText("Se esta é a primeira execução, habilite a permissão externa abaixo, reinicie o Termux e tente novamente.");
        primaryAction.setText("Tentar novamente");
        primaryAction.setEnabled(true);
        primaryAction.setOnClickListener(v -> startTermuxAndServer());
        openTermuxAction.setVisibility(View.VISIBLE);
        copyAction.setVisibility(View.VISIBLE);
        viewLogAction.setEnabled(true);
        copyLogAction.setEnabled(true);
        progress.setVisibility(View.GONE);
    }

    private void setState(String chip, int chipColor, String title, String detail, boolean busy) {
        stateChip.setText(chip);
        stateChip.setTextColor(BG);
        stateChip.setBackground(round(chipColor, 24));
        connectionTitle.setText(title);
        connectionDetail.setText(detail);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void openTermux() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(TERMUX_PACKAGE);
        if (launch != null) {
            try {
                startActivity(launch);
                return;
            } catch (ActivityNotFoundException ignored) {
                // Fall through to the store link.
            }
        }
        openUrl("https://f-droid.org/packages/com.termux/");
    }

    private void copySetupCommand() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Configuração do Codex Web", SETUP_COMMAND));
        Toast.makeText(this, "Comando copiado. Cole no Termux.", Toast.LENGTH_SHORT).show();
    }

    private void openUrl(String value) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value)));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, "Nenhum navegador encontrado.", Toast.LENGTH_SHORT).show();
        }
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? BG : TEXT);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setStateListAnimator(null);
        button.setBackground(round(primary ? GREEN : SURFACE_2, 14));
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = column(18, 18, 18, 18);
        card.setBackground(round(SURFACE, 18));
        return card;
    }

    private LinearLayout column(int left, int top, int right, int bottom) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(left), dp(top), dp(right), dp(bottom));
        return layout;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable circle(int color) {
        return round(color, 100);
    }

    private LinearLayout.LayoutParams marginTop(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = margin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String randomPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        Random random = new Random();
        StringBuilder result = new StringBuilder(32);
        for (int i = 0; i < 32; i++) result.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return result.toString();
    }

    private String readAsset(String name) throws IOException {
        try (InputStream input = getAssets().open(name); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }

    private static String shellQuote(String value) {
        return value.replace("'", "'\\''");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForBatteryReturn) {
            waitingForBatteryReturn = false;
            mainHandler.postDelayed(this::startTermuxAndServer, 250);
            return;
        }
        if (componentsStatus != null && fdroidInstaller != null && !fdroidInstaller.isRunning()
                && webView.getVisibility() != View.VISIBLE && !connectionRequested) {
            refreshInstallerState();
            if (!installFlowStarted && !FdroidInstaller.missing(this).isEmpty()) {
                mainHandler.postDelayed(this::autoInstallIfNeeded, 250);
            }
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        if (installReceiver != null) unregisterReceiver(installReceiver);
        if (commandOutputReceiver != null) unregisterReceiver(commandOutputReceiver);
        if (fdroidInstaller != null) fdroidInstaller.close();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.getVisibility() == View.VISIBLE && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}

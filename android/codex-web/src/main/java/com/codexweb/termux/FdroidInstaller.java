package com.codexweb.termux;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Sequential, user-confirmed installation of official Termux packages. */
public final class FdroidInstaller {
    public static final String INSTALL_ACTION = "com.codexweb.termux.FDROID_INSTALL_RESULT";
    private static final String STATE_PREFS = "fdroid_installer_state";
    private static final String ACTIVE = "active";
    private static final String WAITING_PACKAGE = "waiting_package";
    private static final String WAITING_SESSION = "waiting_session";

    public static final Component[] COMPONENTS = new Component[]{
            new Component("Termux", "com.termux", true, "fdroid/com.termux_1022.apk"),
            new Component("Termux:API", "com.termux.api", false, "fdroid/com.termux.api_1002.apk"),
            new Component("Termux:Boot", "com.termux.boot", false, "fdroid/com.termux.boot_1000.apk"),
            new Component("Termux:Widget", "com.termux.widget", false, "fdroid/com.termux.widget_1001.apk")
    };

    public interface Callback {
        void status(String message);
        void permissionRequired();
        void finished(boolean success);
    }

    public static final class Component {
        public final String name;
        public final String packageName;
        public final boolean required;
        public final String assetName;

        Component(String name, String packageName, boolean required, String assetName) {
            this.name = name;
            this.packageName = packageName;
            this.required = required;
            this.assetName = assetName;
        }
    }

    private final Context context;
    private final SharedPreferences state;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Activity activity;
    private Callback callback;
    private List<Component> missing = Collections.emptyList();
    private int next;
    private Component waitingForInstall;
    private boolean running;

    public FdroidInstaller(Context context) {
        this.context = context.getApplicationContext();
        this.state = this.context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
    }

    public static boolean installed(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    public static String installedVersion(Context context, String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return "v" + (info.versionName == null ? "?" : info.versionName) + " (" + code + ")";
        } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
            return "ausente";
        }
    }

    public static List<Component> missing(Context context) {
        List<Component> result = new ArrayList<>();
        for (Component component : COMPONENTS) {
            if (!installed(context, component.packageName)) result.add(component);
        }
        return result;
    }

    public static List<Component> requiredMissing(Context context) {
        List<Component> result = new ArrayList<>();
        for (Component component : COMPONENTS) {
            if (component.required && !installed(context, component.packageName)) result.add(component);
        }
        return result;
    }

    public static String summary(Context context) {
        List<Component> missing = missing(context);
        if (missing.isEmpty()) return "Termux e complementos detectados.";
        StringBuilder result = new StringBuilder("Ausentes: ");
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) result.append(", ");
            result.append(missing.get(i).name);
        }
        return result.toString();
    }

    public static String details(Context context) {
        StringBuilder result = new StringBuilder();
        for (Component component : COMPONENTS) {
            if (result.length() > 0) result.append("\n");
            result.append(component.name).append(": ").append(installedVersion(context, component.packageName));
            if (component.required) result.append(" • obrigatório");
        }
        return result.toString();
    }

    public boolean isRunning() {
        return running;
    }

    public boolean hasPendingState() {
        return state.getBoolean(ACTIVE, false);
    }

    public void start(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            callback.permissionRequired();
            return;
        }
        missing = missing(activity);
        next = 0;
        waitingForInstall = null;
        running = true;
        persistState();
        installNext();
    }

    /** Resumes a sequence after the APK was closed or recreated. */
    public boolean resume(Activity activity, Callback callback) {
        if (!hasPendingState()) return false;
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            callback.permissionRequired();
            return true;
        }
        this.activity = activity;
        this.callback = callback;
        missing = missing(activity);
        String waitingPackage = state.getString(WAITING_PACKAGE, null);
        if (waitingPackage != null && installed(activity, waitingPackage)) {
            next = indexAfter(waitingPackage);
            waitingForInstall = null;
            state.edit().remove(WAITING_PACKAGE).remove(WAITING_SESSION).apply();
        } else if (waitingPackage != null && isInstallSessionActive()) {
            waitingForInstall = component(waitingPackage);
            running = true;
            callback.status("Aguardando a confirmação do Android para " + waitingPackage + "…");
            return true;
        } else {
            next = 0;
            waitingForInstall = null;
            state.edit().remove(WAITING_PACKAGE).remove(WAITING_SESSION).apply();
        }
        running = true;
        installNext();
        return true;
    }

    public void onInstallResult(Intent intent) {
        if (!running || waitingForInstall == null) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        Component completed = waitingForInstall;
        waitingForInstall = null;
        state.edit().remove(WAITING_PACKAGE).remove(WAITING_SESSION).apply();
        if (status != PackageInstaller.STATUS_SUCCESS || !installed(context, completed.packageName)) {
            if (!completed.required) {
                callback.status("Não foi possível instalar " + completed.name + ". É opcional; continuando sem ele.");
                next++;
                running = true;
                installNext();
                return;
            }
            running = false;
            clearState();
            callback.status("Não foi possível instalar " + completed.name + (message == null ? "." : ": " + message));
            callback.finished(false);
            return;
        }
        callback.status(completed.name + " instalado e confirmado pelo Android. Continuando…");
        next++;
        installNext();
    }

    private void installNext() {
        while (next < missing.size() && installed(context, missing.get(next).packageName)) next++;
        if (next >= missing.size()) {
            running = false;
            clearState();
            callback.finished(true);
            return;
        }
        persistState();
        final Component component = missing.get(next);
        callback.status("Baixando e verificando " + component.name + " pelo F-Droid oficial…");
        executor.execute(() -> {
            try {
                File apk = prepare(component);
                activity.runOnUiThread(() -> stageInstall(component, apk));
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    running = false;
                    String message = "Falha ao preparar " + component.name + ": " + safeMessage(error);
                    if (!component.required) {
                        callback.status(message + ". É opcional; continuando sem ele.");
                        next++;
                        running = true;
                        installNext();
                    } else {
                        clearState();
                        callback.status(message);
                        callback.finished(false);
                    }
                });
            }
        });
    }

    private File copyEmbedded(Component component) throws Exception {
        File directory = new File(context.getCacheDir(), "fdroid");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("não foi possível criar o cache");
        File output = new File(directory, component.packageName + "_embedded.apk");
        try (InputStream input = context.getAssets().open(component.assetName);
             OutputStream out = new FileOutputStream(output)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            long total = 0;
            while ((count = input.read(buffer)) != -1) {
                out.write(buffer, 0, count);
                total += count;
            }
            if (total < 1024) throw new IOException("APK vazio ou incompleto");
        }
        verifyArchive(component, output);
        return output;
    }

    private File prepare(Component component) throws Exception {
        try {
            return copyEmbedded(component);
        } catch (IOException embeddedUnavailable) {
            return downloadFromFdroid(component);
        }
    }

    private File downloadFromFdroid(Component component) throws Exception {
        HttpURLConnection metadata = connection("https://f-droid.org/api/v1/packages/" + component.packageName);
        JSONObject json;
        try (InputStream input = metadata.getInputStream()) {
            json = new JSONObject(readAll(input));
        } finally {
            metadata.disconnect();
        }
        int versionCode = json.optInt("suggestedVersionCode", -1);
        JSONArray packages = json.optJSONArray("packages");
        if (packages != null) {
            for (int i = 0; i < packages.length(); i++) {
                JSONObject candidate = packages.optJSONObject(i);
                if (candidate != null) versionCode = Math.max(versionCode, candidate.optInt("versionCode", -1));
            }
        }
        if (versionCode < 0) throw new IOException("versão do F-Droid não encontrada");
        File directory = new File(context.getCacheDir(), "fdroid");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("não foi possível criar o cache");
        File output = new File(directory, component.packageName + "_" + versionCode + ".apk");
        HttpURLConnection apk = connection("https://f-droid.org/repo/" + component.packageName + "_" + versionCode + ".apk");
        try (InputStream input = apk.getInputStream(); OutputStream out = new FileOutputStream(output)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
        } finally {
            apk.disconnect();
        }
        verifyArchive(component, output);
        return output;
    }

    private void verifyArchive(Component component, File apk) throws IOException {
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (archive == null || !component.packageName.equals(archive.packageName)) {
            throw new IOException("o APK não corresponde a " + component.packageName);
        }
    }

    private static HttpURLConnection connection(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(120000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "CodexWeb-Installer/1.0.3-beta");
        if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) throw new IOException("fonte não HTTPS");
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + connection.getResponseCode());
        return connection;
    }

    private static String readAll(InputStream input) throws Exception {
        StringBuilder result = new StringBuilder();
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) result.append(new String(buffer, 0, count, "UTF-8"));
        return result.toString();
    }

    private void stageInstall(Component component, File apk) {
        try {
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(component.packageName);
            int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            try (InputStream input = new FileInputStream(apk); OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                session.fsync(output);
            }
            Intent result = new Intent(INSTALL_ACTION).setPackage(context.getPackageName());
            result.putExtra("package", component.packageName);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, result, flags);
            waitingForInstall = component;
            state.edit().putString(WAITING_PACKAGE, component.packageName).putInt(WAITING_SESSION, sessionId).apply();
            callback.status("Confirme no Android a instalação de " + component.name + ".");
            session.commit(pending.getIntentSender());
            session.close();
        } catch (Exception error) {
            String message = "Falha ao preparar " + component.name + ": " + safeMessage(error);
            if (!component.required) {
                callback.status(message + ". É opcional; continuando sem ele.");
                next++;
                installNext();
            } else {
                running = false;
                clearState();
                callback.status(message);
                callback.finished(false);
            }
        }
    }

    private boolean isInstallSessionActive() {
        int sessionId = state.getInt(WAITING_SESSION, -1);
        if (sessionId < 0) return false;
        PackageInstaller.SessionInfo info = context.getPackageManager().getPackageInstaller().getSessionInfo(sessionId);
        return info != null && info.isActive();
    }

    private Component component(String packageName) {
        for (Component item : COMPONENTS) if (item.packageName.equals(packageName)) return item;
        return null;
    }

    private int indexAfter(String packageName) {
        for (int i = 0; i < missing.size(); i++) if (missing.get(i).packageName.equals(packageName)) return i + 1;
        return 0;
    }

    private void persistState() {
        state.edit().putBoolean(ACTIVE, true).apply();
    }

    private void clearState() {
        state.edit().clear().apply();
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    public void close() {
        executor.shutdownNow();
    }
}

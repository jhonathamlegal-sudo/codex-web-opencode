package com.codexweb.termux;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
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
    private static final String FDROID_CERT_SHA256 =
            "228fb2cfe90831c1499ec3ccaf61e96e8e1ce70766b9474672ce427334d41c42";

    public static final Component[] COMPONENTS = new Component[]{
            new Component("Termux", "com.termux", true, "fdroid/com.termux_1022.apk",
                    "fdd476982cd74f2f00aac12d3683b1fa260a0b2d146411b94e09d773be3a7b56"),
            new Component("Termux:API", "com.termux.api", false, "fdroid/com.termux.api_1002.apk",
                    "4497dbbf81906df52e59ed387a5223d225aa0de3aca817cc557a621e4dadda44"),
            new Component("Termux:Boot", "com.termux.boot", false, "fdroid/com.termux.boot_1000.apk",
                    "6f7cf9b94f539d3efd4af3544ff819947b49395275d8cfa7e5f80de14f3d9cf8"),
            new Component("Termux:Widget", "com.termux.widget", false, "fdroid/com.termux.widget_1001.apk",
                    "45fb3f56a7248cb8e14b24b478357e340c63d6cfabb87ff5be94b667ad1688d6")
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
        public final String sha256;

        Component(String name, String packageName, boolean required, String assetName, String sha256) {
            this.name = name;
            this.packageName = packageName;
            this.required = required;
            this.assetName = assetName;
            this.sha256 = sha256;
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

    private static boolean compatible(Context context, Component component) {
        if (!installed(context, component.packageName)) return false;
        try {
            PackageManager manager = context.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;
            PackageInfo info = manager.getPackageInfo(component.packageName, flags);
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= 28) {
                if (info.signingInfo == null) return false;
                signatures = info.signingInfo.getApkContentsSigners();
            } else {
                signatures = info.signatures;
            }
            if (signatures == null || signatures.length == 0) return false;
            for (Signature signature : signatures) {
                if (FDROID_CERT_SHA256.equalsIgnoreCase(signatureSha256(signature))) return true;
            }
        } catch (Exception ignored) {
            // A package with an unknown signer must never be treated as compatible.
        }
        return false;
    }

    public static List<Component> missing(Context context) {
        List<Component> result = new ArrayList<>();
        for (Component component : COMPONENTS) {
            if (!compatible(context, component)) result.add(component);
        }
        return result;
    }

    public static List<Component> requiredMissing(Context context) {
        List<Component> result = new ArrayList<>();
        for (Component component : COMPONENTS) {
            if (component.required && !compatible(context, component)) result.add(component);
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
            if (installed(context, component.packageName) && !compatible(context, component)) {
                result.append(" • assinatura incompatível; desinstale a edição anterior");
            }
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
        verifyArchive(component, output, component.sha256);
        return output;
    }

    private File prepare(Component component) throws Exception {
        try {
            return copyEmbedded(component);
        } catch (FileNotFoundException embeddedUnavailable) {
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
        String apkName = json.optString("suggestedApk", "");
        String expectedHash = json.optString("suggestedHash", "");
        JSONArray packages = json.optJSONArray("packages");
        if (packages != null) {
            for (int i = 0; i < packages.length(); i++) {
                JSONObject candidate = packages.optJSONObject(i);
                if (candidate == null) continue;
                int candidateVersion = candidate.optInt("versionCode", -1);
                if (candidateVersion >= versionCode) {
                    versionCode = candidateVersion;
                    apkName = candidate.optString("apkName", "");
                    expectedHash = candidate.optString("hash", candidate.optString("sha256", ""));
                }
            }
        }
        if (versionCode < 0) throw new IOException("versão do F-Droid não encontrada");
        if (apkName.isEmpty()) apkName = component.packageName + "_" + versionCode + ".apk";
        if (expectedHash.isEmpty()) throw new IOException("F-Droid não forneceu SHA-256 para o APK");
        File directory = new File(context.getCacheDir(), "fdroid");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("não foi possível criar o cache");
        File output = new File(directory, component.packageName + "_" + versionCode + ".apk");
        HttpURLConnection apk = connection("https://f-droid.org/repo/" + apkName);
        try (InputStream input = apk.getInputStream(); OutputStream out = new FileOutputStream(output)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
        } finally {
            apk.disconnect();
        }
        verifyArchive(component, output, expectedHash);
        return output;
    }

    private void verifyArchive(Component component, File apk, String expectedSha256) throws IOException {
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null || !component.packageName.equals(archive.packageName)) {
            throw new IOException("o APK não corresponde a " + component.packageName);
        }
        if (expectedSha256 == null || expectedSha256.isEmpty()
                || !expectedSha256.equalsIgnoreCase(fileSha256(apk))) {
            throw new IOException("SHA-256 do APK não corresponde ao esperado");
        }
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            if (archive.signingInfo == null) throw new IOException("certificado do APK ausente");
            signatures = archive.signingInfo.getApkContentsSigners();
        } else {
            signatures = archive.signatures;
        }
        if (signatures == null || signatures.length == 0) throw new IOException("certificado do APK ausente");
        boolean trusted = false;
        for (Signature signature : signatures) {
            if (FDROID_CERT_SHA256.equalsIgnoreCase(signatureSha256(signature))) {
                trusted = true;
                break;
            }
        }
        if (!trusted) throw new IOException("certificado do APK não é o certificado oficial do F-Droid");
    }

    private static String signatureSha256(Signature signature) throws IOException {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()));
        } catch (java.security.GeneralSecurityException error) {
            throw new IOException("SHA-256 indisponível", error);
        }
    }

    private static String fileSha256(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            return hex(digest.digest());
        } catch (java.security.GeneralSecurityException error) {
            throw new IOException("SHA-256 indisponível", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
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
        int total = 0;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > 8 * 1024 * 1024) throw new IOException("metadados do F-Droid excedem o limite");
            result.append(new String(buffer, 0, count, "UTF-8"));
        }
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

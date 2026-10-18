package com.owo233.tcqt.loader.zygisk;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

/** WebUI app_process entry point. Runs as Android shell; APK installation is unnecessary. */
public final class LogShare {
    private LogShare() {}

    // This entry point runs in app_process as shell, outside an installed app's
    // hidden-API enforcement. Never invoke it from a host application callback.
    @SuppressLint({"PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi"})
    public static void main(String[] args) {
        try {
            if (args.length != 1) throw new IllegalArgumentException("Expected report path");
            File source = new File(args[0]).getCanonicalFile();
            if (!"/data/local/tmp".equals(source.getParent()) ||
                    !source.getName().matches("tcqt-report-[0-9]+\\.txt") ||
                    !source.isFile() || source.length() < 1 || source.length() > 64L * 1024 * 1024) {
                throw new IllegalArgumentException("Invalid TCQT report");
            }
            Looper.prepareMainLooper();
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("systemMain").invoke(null);
            Context system = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
            // createPackageContext inherits the system context's op package.
            // Attribution and provider acquisition must both use the shell UID.
            java.lang.reflect.Field opPackage = Class.forName("android.app.ContextImpl").getDeclaredField("mOpPackageName");
            opPackage.setAccessible(true);
            opPackage.set(system, "com.android.shell");
            Context context = system.createPackageContext("com.android.shell", 0);
            int userId = android.os.Process.myUid() / 100000;
            Class<?> amClass = Class.forName("android.app.ActivityManager");
            Object am = amClass.getMethod("getService").invoke(null);
            Class<?> amInterface = Class.forName("android.app.IActivityManager");
            IBinder token = new Binder();
            Method acquire = null;
            for (Method method : amInterface.getMethods()) {
                if (method.getName().equals("getContentProviderExternal")) acquire = method;
            }
            if (acquire == null) throw new IllegalStateException("External provider API missing");
            Object holder = acquire.getParameterCount() == 4
                    ? acquire.invoke(am, "media", userId, token, "TCQT")
                    : acquire.invoke(am, "media", userId, token);
            if (holder == null) throw new IllegalStateException("Media provider unavailable");
            Object provider = holder.getClass().getField("provider").get(holder);
            java.lang.reflect.Constructor<ContentProviderClient> constructor = ContentProviderClient.class.getDeclaredConstructor(
                    ContentResolver.class, Class.forName("android.content.IContentProvider"), boolean.class);
            constructor.setAccessible(true);
            ContentProviderClient client = constructor.newInstance(context.getContentResolver(), provider, true);
            // app_process has no AMS application record. The external provider
            // handle, as used by Android's `content` command, avoids that requirement.
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, source.getName());
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/TCQT");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            } else {
                values.put(MediaStore.MediaColumns.DATA, new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), source.getName()).getAbsolutePath());
            }
            Uri collection = Build.VERSION.SDK_INT >= 29 ? MediaStore.Downloads.EXTERNAL_CONTENT_URI : MediaStore.Files.getContentUri("external");
            Uri uri = client.insert(collection, values);
            if (uri == null) throw new IllegalStateException("MediaStore insert failed");
            try {
                try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(client.openFile(uri, "w"));
                     FileInputStream input = new FileInputStream(source)) {
                    if (output == null) throw new IllegalStateException("MediaStore open failed");
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues published = new ContentValues();
                    published.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    client.update(uri, published, null, null);
                }
                Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                send.setClipData(ClipData.newRawUri("TCQT 日志", uri));
                Intent chooser = Intent.createChooser(send, "分享 TCQT 日志")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                launchChooser(am, chooser, userId);
                System.out.println("TCQT_SHARE_OK " + uri);
            } catch (Throwable t) {
                client.delete(uri, null, null);
                throw t;
            }
            System.exit(0);
        } catch (Throwable t) {
            System.err.println("TCQT_SHARE_ERROR");
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    @SuppressLint("PrivateApi") // shell-only app_process entry point, same as main().
    private static void launchChooser(Object am, Intent chooser, int userId) throws Exception {
        boolean taskManager = Build.VERSION.SDK_INT >= 29;
        Object service = taskManager ? Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null) : am;
        Class<?> type = Class.forName(taskManager ? "android.app.IActivityTaskManager" : "android.app.IActivityManager");
        for (Method method : type.getMethods()) {
            if (!method.getName().equals("startActivityAsUser")) continue;
            Class<?>[] parameters = method.getParameterTypes();
            Object[] arguments = new Object[parameters.length];
            boolean packageSet = false;
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i] == Intent.class) arguments[i] = chooser;
                else if (parameters[i] == int.class) arguments[i] = i == parameters.length - 1 ? userId : 0;
                else if (parameters[i] == String.class && !packageSet) { arguments[i] = "com.android.shell"; packageSet = true; }
                else if (parameters[i] == boolean.class) arguments[i] = false;
            }
            int result = (Integer) method.invoke(service, arguments);
            // Positive START_ABORTED / START_SWITCHES_CANCELED are failures too.
            if (result < 0 || result > 3) throw new IllegalStateException("Chooser start failed: " + result);
            return;
        }
        throw new IllegalStateException("System chooser API missing");
    }
}

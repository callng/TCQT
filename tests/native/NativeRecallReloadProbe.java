import android.app.Application;
import dalvik.system.DexClassLoader;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;

/** Disposable ART process with three module loaders; does not change QQ settings. */
public final class NativeRecallReloadProbe {
    private static final String ENTRY_KEY = "tcqt.native_recall.entry.v1";

    public static final class ProbeApplication extends Application {
        private final File cache;

        ProbeApplication(File cache) { this.cache = cache; }

        @Override public File getCacheDir() { return cache; }
    }

    public static void main(String[] args) throws Exception {
        File directory = new File(args[2]);
        Method owner = null;
        int mappings = 0;
        for (int i = 0; i < 3; i++) {
            ClassLoader loader = new DexClassLoader(
                    args[0], directory.getAbsolutePath(), args[1],
                    NativeRecallReloadProbe.class.getClassLoader());
            Class<?> environment = loader.loadClass("com.owo233.tcqt.core.env.HookEnv");
            environment.getMethod("setApplication", Application.class).invoke(
                    environment.getField("INSTANCE").get(null), new ProbeApplication(directory));
            Class<?> libraries = loader.loadClass("com.owo233.tcqt.core.env.NativeLibs");
            Object libraryInstance = libraries.getField("INSTANCE").get(null);
            libraries.getMethod("register", File.class).invoke(
                    libraryInstance, new File(args[1]));
            if (i == 1) {
                check(Boolean.FALSE.equals(libraries.getMethod("load", String.class).invoke(
                        libraryInstance, "tcqtrecall")), "another loader cannot load the owned library directly");
            }
            Class<?> bridge = loader.loadClass("com.owo233.tcqt.host.service.NativeRecallBridge");
            Object instance = bridge.getField("INSTANCE").get(null);
            Method start = bridge.getMethod("start");
            check(Boolean.TRUE.equals(start.invoke(instance)), "loader " + i + " started");
            Method entry = (Method) System.getProperties().get(ENTRY_KEY);
            if (i == 0) {
                owner = entry;
                mappings = countMappings();
                check(owner.getDeclaringClass() == bridge, "first loader owns JNI entry");
                check(mappings > 0, "native library mapped");
            } else {
                check(owner == entry, "loader " + i + " reused original JNI owner");
                check(entry.getDeclaringClass() != bridge, "loader " + i + " has a distinct bridge class");
                check(countMappings() == mappings, "loader " + i + " did not map another library");
            }
            check(Boolean.TRUE.equals(start.invoke(instance)), "loader " + i + " repeated start succeeds");
        }
        System.out.println("NATIVE_RECALL_RELOAD_PASS");
        System.exit(0); // End the disposable native poller and daemon with this process.
    }

    private static int countMappings() throws Exception {
        int count = 0;
        for (String line : Files.readAllLines(new File("/proc/self/maps").toPath())) {
            if (line.endsWith("/libtcqtrecall.so")) count++;
        }
        return count;
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        System.out.println("PASS " + label);
    }
}

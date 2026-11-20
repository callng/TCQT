import java.lang.reflect.Method;

// Runs in a disposable app_process, never inside QQ/TIM. The library links the
// production ART implementation; these bridge methods replace only DexMaker.
public final class ArtRuntimeRegression {
    private static native boolean initialize();
    private static native boolean indexed(Method method);
    private static native long methodId(Method method);
    private static native int hook(Method target, Method backup, Method bridge);
    private static native int unhook(Method target, Method backup);
    private static native Object invoke(Method backup, long id, Class<?> targetClass, Object self, Object[] args);
    private static native int nativeTarget(int value);

    private static Method staticBackup;
    private static Method instanceBackup;
    private static Method nativeBackup;
    private static Method throwingBackup;
    private static Method intrinsicBackup;
    private static long staticId, instanceId, nativeId, throwingId, intrinsicId;

    public static final class Target {
        public static int triple(int value) { return value * 3; }
        public double mixed(long x, float y, double z, Object ignored) { return (double) x + y + z; }
        public static int throwing(int value) { throw new IllegalArgumentException("original:" + value); }
    }

    public static final class StaticBridge {
        public static int backup(int value) { throw new AssertionError("unpatched backup"); }
        public static int bridge(int value) {
            return (Integer) invoke(staticBackup, staticId, Target.class, null, new Object[]{value}) + 10;
        }
    }

    public static final class InstanceBridge {
        public double backup(long x, float y, double z, Object obj) {
            throw new AssertionError("unpatched backup");
        }
        public double bridge(long x, float y, double z, Object obj) {
            return (Double) invoke(instanceBackup, instanceId, Target.class, this, new Object[]{x, y, z, obj}) + 20;
        }
    }

    public static final class NativeBridge {
        public static int backup(int value) { throw new AssertionError("unpatched backup"); }
        public static int bridge(int value) {
            return (Integer) invoke(nativeBackup, nativeId, ArtRuntimeRegression.class, null, new Object[]{value}) + 30;
        }
    }

    public static final class ThrowingBridge {
        public static int backup(int value) { throw new AssertionError("unpatched backup"); }
        public static int bridge(int value) {
            return (Integer) invoke(throwingBackup, throwingId, Target.class, null, new Object[]{value});
        }
    }

    public static final class IntrinsicBridge {
        public static int backup(int value) { throw new AssertionError("unpatched backup"); }
        public static int bridge(int value) {
            return (Integer) invoke(intrinsicBackup, intrinsicId, Integer.class, null, new Object[]{value}) + 100;
        }
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace(System.out);
            System.exit(1);
        });
        System.load(args[0]);
        check(initialize(), "production ART initialization");
        Method target = Target.class.getDeclaredMethod("triple", int.class);
        check(indexed(target) == args[1].equals("indices"), "requested JNI ID mode");
        System.out.println("PASS: initialize ART; JNI IDs=" + args[1]);

        // Initialize each generated class before installing: this is also what
        // the production bridge does when writing its static hookId field.
        Class.forName(StaticBridge.class.getName());
        staticBackup = StaticBridge.class.getDeclaredMethod("backup", int.class);
        staticId = methodId(staticBackup);
        Method bridge = StaticBridge.class.getDeclaredMethod("bridge", int.class);
        check(hook(target, staticBackup, bridge) == 0, "hook static");
        for (int i = 0; i < 2000; ++i) check(Target.triple(i) == i * 3 + 10, "static callback/backup");
        check(unhook(target, staticBackup) == 0, "unhook static");
        check(Target.triple(5) == 15, "static restored");
        System.out.println("PASS: static callback, backup, repeated calls and unhook");

        Class.forName(InstanceBridge.class.getName());
        Class<?>[] params = {long.class, float.class, double.class, Object.class};
        target = Target.class.getDeclaredMethod("mixed", params);
        instanceBackup = InstanceBridge.class.getDeclaredMethod("backup", params);
        instanceId = methodId(instanceBackup);
        bridge = InstanceBridge.class.getDeclaredMethod("bridge", params);
        check(hook(target, instanceBackup, bridge) == 0, "hook instance");
        Target receiver = new Target();
        check(receiver.mixed(10000000000L, 1.5f, 2.25, null) == 10000000023.75, "mixed JNI ABI");
        check(unhook(target, instanceBackup) == 0, "unhook instance");
        check(receiver.mixed(1, 2, 3, null) == 6, "instance restored");
        System.out.println("PASS: instance receiver and long/float/double/reference JNI ABI");

        Class.forName(NativeBridge.class.getName());
        check(nativeTarget(2) == 10, "resolve JNI target before hook");
        target = ArtRuntimeRegression.class.getDeclaredMethod("nativeTarget", int.class);
        nativeBackup = NativeBridge.class.getDeclaredMethod("backup", int.class);
        nativeId = methodId(nativeBackup);
        bridge = NativeBridge.class.getDeclaredMethod("bridge", int.class);
        check(hook(target, nativeBackup, bridge) == 0, "hook native");
        check(nativeTarget(2) == 40, "native backup");
        check(unhook(target, nativeBackup) == 0, "unhook native");
        check(nativeTarget(2) == 10, "native restored");
        System.out.println("PASS: JNI native target, backup and unhook");

        Class.forName(ThrowingBridge.class.getName());
        target = Target.class.getDeclaredMethod("throwing", int.class);
        throwingBackup = ThrowingBridge.class.getDeclaredMethod("backup", int.class);
        throwingId = methodId(throwingBackup);
        bridge = ThrowingBridge.class.getDeclaredMethod("bridge", int.class);
        check(hook(target, throwingBackup, bridge) == 0, "hook throwing");
        try {
            Target.throwing(7);
            throw new AssertionError("lost target exception");
        } catch (IllegalArgumentException expected) {
            check("original:7".equals(expected.getMessage()), "exception message");
        }
        check(unhook(target, throwingBackup) == 0, "unhook throwing");
        System.out.println("PASS: backup exception survives JNI and later calls");

        Class.forName(IntrinsicBridge.class.getName());
        target = Integer.class.getDeclaredMethod("bitCount", int.class);
        intrinsicBackup = IntrinsicBridge.class.getDeclaredMethod("backup", int.class);
        intrinsicId = methodId(intrinsicBackup);
        bridge = IntrinsicBridge.class.getDeclaredMethod("bridge", int.class);
        check(hook(target, intrinsicBackup, bridge) == 0, "hook boot intrinsic");
        // Reflection reaches the ArtMethod; an already inlined call site would
        // require separate caller deoptimization and is not asserted here.
        check((Integer) target.invoke(null, 13) == 103, "intrinsic callback and DEX backup");
        check(unhook(target, intrinsicBackup) == 0, "unhook boot intrinsic");
        check((Integer) target.invoke(null, 13) == 3, "intrinsic restored");
        System.out.println("PASS: boot intrinsic, original DEX body and restoration");
        System.out.println("ART_RUNTIME_PASS " + args[1]);
    }
}

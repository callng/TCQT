import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * ART layout calibration probe.
 *
 * Runs on-device via:
 *   javac --release 8 -d classes tools/artprobe/Probe.java
 *   d8 --release --min-api 26 --output dexout classes/Probe.class
 *   adb push dexout/classes.dex /data/local/tmp/artprobe.dex
 *   adb shell "su -c 'app_process -Djava.class.path=/data/local/tmp/artprobe.dex /system/bin Probe'"
 *
 * It runs in the SAME ART runtime (same libart.so / boot image) as QQ, so the
 * ArtMethod layout it measures is exactly what the Zygisk hook engine sees.
 * Uses only java.lang.reflect + sun.misc.Unsafe (via reflection), so it
 * compiles against a plain JDK (no android.jar needed).
 */
public class Probe {

    static Object unsafe;
    static Method uGetByte, uGetInt, uGetLong;
    static Method uPutByte, uPutInt, uPutLong;

    static void initUnsafe() throws Exception {
        Class<?> uc = Class.forName("sun.misc.Unsafe");
        Field tu = uc.getDeclaredField("theUnsafe");
        tu.setAccessible(true);
        unsafe = tu.get(null);
        uGetByte = uc.getMethod("getByte", long.class);
        uGetInt = uc.getMethod("getInt", long.class);
        uGetLong = uc.getMethod("getLong", long.class);
        uPutByte = uc.getMethod("putByte", long.class, byte.class);
        uPutInt = uc.getMethod("putInt", long.class, int.class);
        uPutLong = uc.getMethod("putLong", long.class, long.class);
    }

    static int getInt(long addr) {
        try { return (Integer) uGetInt.invoke(unsafe, addr); } catch (Exception e) { throw new RuntimeException(e); }
    }
    static long getLong(long addr) {
        try { return (Long) uGetLong.invoke(unsafe, addr); } catch (Exception e) { throw new RuntimeException(e); }
    }
    static int getByte(long addr) {
        try { return (Byte) uGetByte.invoke(unsafe, addr); } catch (Exception e) { throw new RuntimeException(e); }
    }
    static void putByte(long addr, int v) {
        try { uPutByte.invoke(unsafe, addr, (byte) v); } catch (Exception e) { throw new RuntimeException(e); }
    }
    static void putInt(long addr, int v) {
        try { uPutInt.invoke(unsafe, addr, v); } catch (Exception e) { throw new RuntimeException(e); }
    }
    static void putLong(long addr, long v) {
        try { uPutLong.invoke(unsafe, addr, v); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static List<long[]> execRanges = new ArrayList<>(); // {start, end}

    static void loadExecRanges() throws Exception {
        BufferedReader r = new BufferedReader(new FileReader("/proc/self/maps"));
        String line;
        while ((line = r.readLine()) != null) {
            String[] parts = line.split("\\s+");
            if (parts.length < 2) continue;
            String perms = parts[1];
            if (!perms.contains("x")) continue;
            String[] a = parts[0].split("-");
            if (a.length != 2) continue;
            long start = Long.parseLong(a[0], 16);
            long end = Long.parseLong(a[1], 16);
            execRanges.add(new long[]{start, end});
        }
        r.close();
    }

    static boolean isExecutable(long addr) {
        for (long[] rg : execRanges) {
            if (addr >= rg[0] && addr < rg[1]) return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        initUnsafe();
        loadExecRanges();
        System.out.println("== device ==");
        System.out.println("java.version=" + System.getProperty("java.version"));

        Class<?> throwable = Class.forName("java.lang.Throwable");
        Constructor<?>[] ctors = throwable.getDeclaredConstructors();
        System.out.println("Throwable declared constructors: " + ctors.length);

        // Get artMethod address + raw accessFlags for each ctor.
        // Try the public getter first, fall back to the private field.
        Method getArtMethod = null;
        try {
            getArtMethod = Executable.class.getMethod("getArtMethod");
        } catch (Throwable t) { /* not present on this ROM */ }

        Field artMethodF = null;
        try {
            artMethodF = Executable.class.getDeclaredField("artMethod");
            artMethodF.setAccessible(true);
        } catch (Throwable t) {
            System.out.println("WARN: cannot access Executable.artMethod field: " + t);
        }
        Field accessFlagsF = null;
        try {
            accessFlagsF = Executable.class.getDeclaredField("accessFlags");
            accessFlagsF.setAccessible(true);
        } catch (Throwable t) {
            System.out.println("WARN: cannot access Executable.accessFlags field: " + t);
        }

        int n = Math.min(ctors.length, 8);
        long[] arts = new long[n];
        int[] flags = new int[n];
        for (int i = 0; i < n; i++) {
            Constructor<?> c = ctors[i];
            long art = 0;
            try {
                if (getArtMethod != null) {
                    art = (Long) getArtMethod.invoke(c);
                }
            } catch (Throwable t) { }
            if (art == 0 && artMethodF != null) {
                try { art = artMethodF.getLong(c); } catch (Throwable t) { }
            }
            int fl = 0;
            if (accessFlagsF != null) {
                try { fl = accessFlagsF.getInt(c); } catch (Throwable t) { }
            }
            arts[i] = art;
            flags[i] = fl;
            System.out.printf("ctor[%d] %-45s art=%#016x flags=%#010x%n",
                    i, c.toString().replace("java.lang.Throwable", "Throwable"), art, fl);
        }

        // Delta between consecutive ArtMethods.
        System.out.println("== deltas ==");
        for (int i = 1; i < n; i++) {
            System.out.printf("d[%d-%d]=%d%n", i - 1, i, arts[i] - arts[i - 1]);
        }

        // Dump the first 64 bytes of each ArtMethod.
        System.out.println("== ArtMethod bytes (first 64) ==");
        for (int i = 0; i < n; i++) {
            long art = arts[i];
            if (art == 0) continue;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("ctor[%d] %#x: ", i, art));
            for (int b = 0; b < 64; b++) {
                sb.append(String.format("%02x ", getByte(art + b) & 0xff));
                if ((b & 7) == 7 && b != 63) sb.append("| ");
            }
            System.out.println(sb);
        }

        // For each ctor, find where its own flags value appears (4-byte words).
        System.out.println("== flags value location ==");
        for (int i = 0; i < n; i++) {
            long art = arts[i];
            if (art == 0) continue;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("ctor[%d] flags %#x at offsets:", i, flags[i]));
            for (int off = 0; off + 4 <= 64; off += 4) {
                if (getInt(art + off) == flags[i]) sb.append(" " + off);
            }
            System.out.println(sb);
        }

        // Where are executable 8-byte slots (entry point candidates)?
        System.out.println("== executable 8-byte slots ==");
        for (int i = 0; i < n; i++) {
            long art = arts[i];
            if (art == 0) continue;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("ctor[%d]:", i));
            for (int off = 8; off + 8 <= 64; off += 8) {
                long v = getLong(art + off);
                if (isExecutable(v)) sb.append(String.format(" off=%d val=%#x", off, v));
            }
            System.out.println(sb);
        }

        // declared class pointer at offset 0
        System.out.println("== declaring class @0 ==");
        for (int i = 0; i < n; i++) {
            if (arts[i] == 0) continue;
            long cls = getLong(arts[i]);
            System.out.printf("ctor[%d] class=%#x%n", i, cls);
        }

        // ── Crash-reproduction test ─────────────────────────────────────────
        // Replicate what the module's art_hook_method does on this layout:
        //   1. memcpy the target ArtMethod over the backup ArtMethod slot
        //      (here: copy Throwable.<init> over Object.toString);
        //   2. tweak the backup's access_flags @4 exactly like the module
        //      (|ACC_COMPILE_DONT_BOTHER, clear precompiled/fast-interp/
        //      proxy, non-static -> private);
        //   3. point the backup's entry point @24 at the bridge-style
        //      interpreter entry (here: the target's own original entry point);
        //   4. also mutate the target's flags (|CDB, clear fast-interp).
        // Then force GCs and see whether the process dies with the same
        // "GC tried to mark invalid reference" heap-corruption crash.
        System.out.println("== corruption reproduction test ==");
        try {
            Class<?> obj = Class.forName("java.lang.Object");
            Method toStr = obj.getDeclaredMethod("toString");
            long targetArt = arts[0];               // Throwable.<init>()
            long backupArt = 0;
            if (getArtMethod != null) backupArt = (Long) getArtMethod.invoke(toStr);
            if (backupArt == 0 && artMethodF != null) backupArt = artMethodF.getLong(toStr);
            System.out.printf("target(Throwable.<init>) art=%#x  backup(Object.toString) art=%#x%n",
                    targetArt, backupArt);

            // snapshot backup so we can restore it after the test
            byte[] snap = new byte[32];
            for (int i = 0; i < 32; i++) snap[i] = (byte) getByte(backupArt + i);

            // 1. memcpy target over backup (the module does exactly this)
            for (int i = 0; i < 32; i++) {
                putByte(backupArt + i, getByte(targetArt + i));
            }

            // 2. backup flags @4: copy target flags, then the module's tweaks
            int afOff = 4;
            int flagsBackup = getInt(backupArt + afOff);
            final int ACC_COMPILE_DONT_BOTHER = 0x02000000;
            final int ACC_FAST_INTERP = 0x40000000;
            final int ACC_PRECOMPILED = 0x00800000;
            final int ACC_PROXY = 0x00400000;
            final int ACC_PRIVATE = 0x0002, ACC_PUBLIC = 0x0001, ACC_PROTECTED = 0x0004;
            int newBackupFlags = (flagsBackup | ACC_COMPILE_DONT_BOTHER) &
                    ~ACC_FAST_INTERP & ~ACC_PRECOMPILED & ~ACC_PROXY;
            if ((newBackupFlags & 0x0008) == 0) { // non-static
                newBackupFlags = (newBackupFlags | ACC_PRIVATE) & ~(ACC_PUBLIC | ACC_PROTECTED);
            }
            putInt(backupArt + afOff, newBackupFlags);

            // 3. backup entry point @24 = target's original entry point
            putLong(backupArt + 24, getLong(targetArt + 24));

            // 4. target flags: |CDB, clear fast-interp/precompiled (module's target tweaks)
            int flagsTarget = getInt(targetArt + afOff);
            int newTargetFlags = (flagsTarget | ACC_COMPILE_DONT_BOTHER) &
                    ~ACC_FAST_INTERP & ~ACC_PRECOMPILED;
            putInt(targetArt + afOff, newTargetFlags);

            System.out.println("backup flags now=" + Integer.toHexString(newBackupFlags) +
                    " target flags now=" + Integer.toHexString(newTargetFlags));
            System.out.println("forcing GC x8 ...");
            for (int i = 0; i < 8; i++) {
                System.gc();
                Thread.sleep(100);
            }
            System.out.println("GC survived without heap-corruption crash");

            // restore
            for (int i = 0; i < 32; i++) putByte(backupArt + i, snap[i]);
            putInt(targetArt + afOff, flagsTarget);
            System.out.println("restored");
        } catch (Throwable t) {
            System.out.println("test skipped: " + t);
            t.printStackTrace();
        }

        System.out.println("== done ==");
    }
}

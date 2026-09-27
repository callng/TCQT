package bsh.loader;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BshLoaderHelper {
    private static final ConcurrentMap<String, Class<?>> clazzMap = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ClassLoader> loaderMap = new ConcurrentHashMap<>();

    private static String buildLoaderKey(String type, String md5, ClassLoader parentLoader) {
        final int parentId = System.identityHashCode(parentLoader);
        return type + "#" + md5 + "#" + parentId;
    }

    public static Class<?> getClassByCode(String name, byte[] code, ClassLoader parentLoader) {
        final String md5 = DataUtil.getMd5ByBytes(code);
        if (md5 == null) return null;
        final String key = buildLoaderKey(name, md5, parentLoader);
        return clazzMap.computeIfAbsent(key, k -> {
            try {
                ClassLoader classLoader = new BshConvertHelper().convertClassToLoader(name, code, parentLoader);
                return classLoader.loadClass(name);
            } catch (Exception e) {
                System.err.println("[BeanShell] getClassByCode: " + e);
                return null;
            }
        });
    }

    public static Class<?> getClassByCode(String name, byte[] code) {
        return getClassByCode(name, code, BshLoaderHelper.class.getClassLoader());
    }

    public static ClassLoader getLoaderByDex(String dexPath, ClassLoader parentLoader) {
        final String md5 = DataUtil.getMd5ByFilePath(dexPath);
        if (md5 == null) return null;
        final String key = buildLoaderKey("dex", md5, parentLoader);
        return loaderMap.computeIfAbsent(key, k -> {
            try {
                return new BshConvertHelper().convertDexToLoader(dexPath, parentLoader);
            } catch (Exception e) {
                System.err.println("[BeanShell] GetLoaderByDex: " + e);
                return null;
            }
        });
    }

    public static ClassLoader getLoaderByJar(String jarPath, ClassLoader parentLoader) {
        final String md5 = DataUtil.getMd5ByFilePath(jarPath);
        if (md5 == null) return null;
        final String key = buildLoaderKey("jar", md5, parentLoader);
        return loaderMap.computeIfAbsent(key, k -> {
            try {
                return new BshConvertHelper().convertJarToLoader(jarPath, parentLoader);
            } catch (Exception e) {
                System.err.println("[BeanShell] GetLoaderByJar: " + e);
                return null;
            }
        });
    }

    public static ClassLoader getLoaderByAar(String aarPath, ClassLoader parentLoader) {
        final String md5 = DataUtil.getMd5ByFilePath(aarPath);
        if (md5 == null) return null;
        final String key = buildLoaderKey("aar", md5, parentLoader);
        return loaderMap.computeIfAbsent(key, k -> {
            try {
                return new BshConvertHelper().convertAarToLoader(aarPath, parentLoader);
            } catch (Exception e) {
                System.err.println("[BeanShell] GetLoaderByAar: " + e);
                return null;
            }
        });
    }
}

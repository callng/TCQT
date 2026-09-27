package bsh.loader;

import java.util.HashSet;

public class BshLoaderManager {
    private final HashSet<ClassLoader> loaders = new HashSet<>();

    public void addClassLoader(ClassLoader loader) {
        if (loader != null) loaders.add(loader);
    }

    public Class<?> getLoaderClass(String name) {
        for (ClassLoader loader : loaders) {
            try {
                return loader.loadClass(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        System.err.println("[BeanShell] GetLoaderClass: " + name + " is null");
        return null;
    }
}

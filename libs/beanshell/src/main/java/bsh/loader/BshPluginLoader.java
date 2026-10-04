package bsh.loader;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BshPluginLoader extends ClassLoader {
    private final ConcurrentMap<String, Class<?>> clazzMap = new ConcurrentHashMap<>();

    public BshPluginLoader(ClassLoader parent) {
        super(parent);
    }

    public void putClass(String name, Class<?> clazz) {
        clazzMap.put(name, clazz);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = clazzMap.get(name);
        if (clazz != null) return clazz;
        throw new ClassNotFoundException(name);
    }
}

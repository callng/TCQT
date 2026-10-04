package bsh.loader;

import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.command.dexer.DxContext;
import com.android.dx.dex.DexOptions;
import com.android.dx.dex.cf.CfOptions;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.file.DexFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.InMemoryDexClassLoader;

public class BshConvertHelper {
    private void appendClassToDex(
            DxContext dxContext, CfOptions cfOptions, DexOptions dexOptions, DexFile dexFile,
            String filePath, byte[] bytes
    ) {
        DirectClassFile classFile = new DirectClassFile(bytes, filePath, true);
        classFile.setAttributeFactory(StdAttributeFactory.THE_ONE);
        dexFile.add(CfTranslator.translate(dxContext, classFile, bytes, cfOptions, dexOptions, dexFile));
    }

    private int appendJarToDex(
            DxContext dxContext, CfOptions cfOptions, DexOptions dexOptions, DexFile dexFile,
            InputStream inputStream
    ) throws IOException {
        int classCount = 0;
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String entryName = entry.getName();
                if (entryName.endsWith(".class") && !entryName.startsWith("META-INF/")) {
                    byte[] classBytes = DataUtil.readAllBytes(zis);
                    appendClassToDex(dxContext, cfOptions, dexOptions, dexFile, entryName, classBytes);
                    classCount++;
                }
            }
        }
        return classCount;
    }

    private byte[] convertClassToDex(String className, byte[] classBytes) throws IOException {
        DexOptions dexOptions = new DexOptions();
        CfOptions cfOptions = new CfOptions();
        DxContext dxContext = new DxContext();
        String classFilePath = String.format("%s.class", className.replace('.', '/'));
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            DexFile dexFile = new DexFile(dexOptions);
            appendClassToDex(dxContext, cfOptions, dexOptions, dexFile, classFilePath, classBytes);
            dexFile.writeTo(outputStream, null, true);
            return outputStream.toByteArray();
        }
    }

    private byte[] convertJarToDex(String jarPath) throws IOException {
        DexOptions dexOptions = new DexOptions();
        CfOptions cfOptions = new CfOptions();
        DxContext dxContext = new DxContext();
        DexFile dexFile = new DexFile(dexOptions);
        int classCount;
        try (FileInputStream fis = new FileInputStream(jarPath)) {
            classCount = appendJarToDex(dxContext, cfOptions, dexOptions, dexFile, fis);
        }
        if (classCount == 0) {
            throw new IOException("No class file found in jar " + jarPath);
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            dexFile.writeTo(outputStream, null, true);
            return outputStream.toByteArray();
        }
    }

    private byte[] convertAarToDex(String aarPath) throws IOException {
        DexOptions dexOptions = new DexOptions();
        CfOptions cfOptions = new CfOptions();
        DxContext dxContext = new DxContext();
        DexFile dexFile = new DexFile(dexOptions);
        int classCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(aarPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String entryName = entry.getName();
                if (entryName.equals("classes.jar") || (entryName.startsWith("libs/") && entryName.endsWith(".jar"))) {
                    byte[] jarBytes = DataUtil.readAllBytes(zis);
                    classCount += appendJarToDex(dxContext, cfOptions, dexOptions, dexFile, new ByteArrayInputStream(jarBytes));
                }
            }
        }
        if (classCount == 0) {
            throw new IOException("No class file found in aar " + aarPath);
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            dexFile.writeTo(outputStream, null, true);
            return outputStream.toByteArray();
        }
    }

    public ClassLoader createCustomLoader(byte[] bytes, ClassLoader parentLoader) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new InMemoryDexClassLoader(buffer, parentLoader);
    }

    public ClassLoader convertClassToLoader(String className, byte[] classCode, ClassLoader parentLoader) throws IOException {
        byte[] dexBytes = convertClassToDex(className, classCode);
        return createCustomLoader(dexBytes, parentLoader);
    }

    public ClassLoader convertDexToLoader(String dexPath, ClassLoader parentLoader) throws IOException {
        File dexFile = new File(dexPath);
        byte[] dexBytes = Files.readAllBytes(dexFile.toPath());
        return createCustomLoader(dexBytes, parentLoader);
    }

    public ClassLoader convertJarToLoader(String jarPath, ClassLoader parentLoader) throws IOException {
        byte[] dexBytes = convertJarToDex(jarPath);
        return createCustomLoader(dexBytes, parentLoader);
    }

    public ClassLoader convertAarToLoader(String aarPath, ClassLoader parentLoader) throws IOException {
        byte[] dexBytes = convertAarToDex(aarPath);
        return createCustomLoader(dexBytes, parentLoader);
    }
}

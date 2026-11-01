package com.owo233.tcqt.build;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.*;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;

/** Transforms the AGP APK artifact without mutating the package task's output. */
public abstract class DualApkTask extends DefaultTask {
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getInputApkDirectory();
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getModuleDirectory();
    @OutputDirectory public abstract DirectoryProperty getOutputApkDirectory();
    @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getZipalign();
    @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getApksignerJar();
    @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getKeystore();
    @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getJavaExecutable();
    @Input public abstract Property<String> getKeyAlias();
    // Passwords affect access, not output identity. Keep them out of task fingerprints and command lines.
    @Internal public abstract Property<String> getStorePassword();
    @Internal public abstract Property<String> getKeyPassword();
    @Inject protected abstract FileSystemOperations getFiles();
    @Inject protected abstract ExecOperations getExec();

    public void initializeOutput() throws IOException {
        Path input = getInputApkDirectory().get().getAsFile().toPath();
        Path output = getOutputApkDirectory().get().getAsFile().toPath();
        if (input.equals(output)) throw new IOException("Raw and dual APK directories must differ");
        getFiles().delete(spec -> spec.delete(output.toFile(), getTemporaryDir()));
        Files.createDirectories(output);
        Files.createDirectories(getTemporaryDir().toPath());
    }
    public File packArtifact(File inputFile) throws IOException {
            Path apk = inputFile.toPath();
            Path output = getOutputApkDirectory().get().getAsFile().toPath();
            Path merged = getTemporaryDir().toPath().resolve(apk.getFileName() + ".merged");
            Path aligned = getTemporaryDir().toPath().resolve(apk.getFileName() + ".aligned");
            merge(apk, merged);
            getExec().exec(spec -> spec.commandLine(getZipalign().get().getAsFile(), "-f", "-p", "4", merged, aligned))
                .assertNormalExitValue();
            getExec().exec(spec -> {
                spec.environment("TCQT_STORE_PASSWORD", getStorePassword().get());
                spec.environment("TCQT_KEY_PASSWORD", getKeyPassword().get());
                spec.commandLine(getJavaExecutable().get().getAsFile(), "-jar", getApksignerJar().get().getAsFile(), "sign",
                    "--ks", getKeystore().get().getAsFile(), "--ks-pass", "env:TCQT_STORE_PASSWORD",
                    "--ks-key-alias", getKeyAlias().get(), "--key-pass", "env:TCQT_KEY_PASSWORD",
                    "--v1-signing-enabled", "false", "--v2-signing-enabled", "true",
                    "--v3-signing-enabled", "false", "--v4-signing-enabled", "false",
                    "--out", output.resolve(apk.getFileName()), aligned);
            }).assertNormalExitValue();
            return output.resolve(apk.getFileName()).toFile();

    }

    private void merge(Path apk, Path target) throws IOException {
        Path module = getModuleDirectory().get().getAsFile().toPath();
        Map<String, Path> additions = new TreeMap<>();
        try (var paths = Files.walk(module)) {
            for (Path p : paths.filter(Files::isRegularFile).toList()) {
                additions.put(module.relativize(p).toString().replace('\\', '/'), p);
            }
        }
        try (ZipFile zip = new ZipFile(apk.toFile()); ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {
            Map<String, ZipEntry> originals = new TreeMap<>();
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("/") || Arrays.asList(name.split("/")).contains("..")) throw new IOException("Unsafe ZIP entry");
                if (entry.isDirectory() || name.startsWith("webroot/") || additions.containsKey(name)) continue;
                if (name.matches("(?i)META-INF/[^/]+\\.(SF|RSA|DSA|EC)") || name.equals("META-INF/MANIFEST.MF")) continue;
                if (originals.put(name, entry) != null) throw new IOException("Duplicate ZIP entry: " + name);
            }
            TreeSet<String> names = new TreeSet<>(originals.keySet());
            names.addAll(additions.keySet());
            for (String name : names) {
                byte[] data;
                if (additions.containsKey(name)) data = Files.readAllBytes(additions.get(name));
                else try (InputStream stream = zip.getInputStream(originals.get(name))) { data = stream.readAllBytes(); }
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(0);
                if ((name.startsWith("lib/") && name.endsWith(".so")) || name.equals("resources.arsc")) {
                    CRC32 crc = new CRC32(); crc.update(data);
                    entry.setMethod(ZipEntry.STORED); entry.setSize(data.length); entry.setCompressedSize(data.length); entry.setCrc(crc.getValue());
                }
                out.putNextEntry(entry); out.write(data); out.closeEntry();
            }
        }
    }
}

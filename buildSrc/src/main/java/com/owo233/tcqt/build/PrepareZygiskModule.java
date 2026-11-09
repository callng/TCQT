package com.owo233.tcqt.build;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.*;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;

/** Variant-local native output and offline assets are the only packaging inputs. */
public abstract class PrepareZygiskModule extends DefaultTask {
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getTemplateDirectory();
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getWebUiDirectory();
    @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getNativeLibrary();
    @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getStripExecutable();
    @Input public abstract Property<String> getVersionName();
    @Input public abstract Property<Integer> getVersionCode();
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();
    @Inject protected abstract FileSystemOperations getFiles();
    @Inject protected abstract ExecOperations getExec();

    @TaskAction public void prepare() throws IOException {
        File output = getOutputDirectory().get().getAsFile();
        getFiles().delete(spec -> spec.delete(output));
        getFiles().copy(spec -> {
            spec.from(getTemplateDirectory());
            spec.exclude("webroot/**");
            spec.into(output);
        });
        getFiles().copy(spec -> {
            spec.from(getWebUiDirectory());
            spec.into(new File(output, "webroot"));
        });
        try (var paths = Files.walk(output.toPath())) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (path.toString().endsWith(".sh")) {
                    Files.writeString(path, Files.readString(path).replace("\r\n", "\n"));
                }
            }
        }
        Path prop = output.toPath().resolve("module.prop");
        Files.writeString(prop, Files.readString(prop)
            .replace("@VERSION@", getVersionName().get())
            .replace("@VERSION_CODE@", getVersionCode().get().toString()));
        File target = new File(output, "zygisk/arm64-v8a.so");
        Files.createDirectories(target.toPath().getParent());
        getExec().exec(spec -> spec.commandLine(getStripExecutable().get().getAsFile(), "-o",
            target, getNativeLibrary().get().getAsFile())).assertNormalExitValue();
        var identity = Pattern.compile("TCQT_INJECTOR_ID_V1=[0-9a-f]{64}").matcher(
            Files.readString(target.toPath(), StandardCharsets.ISO_8859_1));
        if (!identity.find()) throw new IOException("Injector has no embedded implementation identity");
        String value = identity.group();
        if (identity.find()) throw new IOException("Injector has multiple implementation identities");
        getLogger().lifecycle("Zygisk injector: {}", value);
    }
}

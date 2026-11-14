package com.owo233.tcqt.build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.*;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;

/** A small app_process entry point that does not depend on the active host payload. */
public abstract class CompileLogShareDex extends DefaultTask {
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getClassesDirectory();
    @Classpath public abstract ConfigurableFileCollection getPlatformClasspath();
    @Classpath public abstract RegularFileProperty getD8Jar();
    @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getJavaExecutable();
    @Input public abstract Property<Integer> getMinSdk();
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();
    @Inject protected abstract FileSystemOperations getFiles();
    @Inject protected abstract ExecOperations getExec();

    @TaskAction public void compile() throws IOException {
        File output = getOutputDirectory().get().getAsFile();
        getFiles().delete(spec -> spec.delete(output));
        Files.createDirectories(output.toPath());
        var args = new ArrayList<Object>();
        args.addAll(java.util.List.of(getJavaExecutable().get().getAsFile(), "-cp",
            getD8Jar().get().getAsFile(), "com.android.tools.r8.D8", "--release",
            "--min-api", getMinSdk().get(), "--output", output));
        for (File library : getPlatformClasspath()) args.addAll(java.util.List.of("--lib", library));
        try (var files = Files.walk(getClassesDirectory().get().getAsFile().toPath())) {
            args.addAll(files.filter(p -> p.toString().endsWith(".class")).sorted().toList());
        }
        getExec().exec(spec -> spec.commandLine(args)).assertNormalExitValue();
        if (!new File(output, "classes.dex").isFile() || new File(output, "classes2.dex").exists()) {
            throw new IOException("LogShare must compile to exactly one DEX");
        }
    }
}

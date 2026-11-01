plugins { `java-library` }
dependencies { implementation(gradleApi()) }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

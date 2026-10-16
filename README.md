# Forge Stuck!! — Clean Gradle Java mod project

This repository has been converted to a minimal Gradle-based Java project so you can build a placeholder JAR locally.

Quick start

1. Install Gradle (or use the Gradle wrapper if you add it): https://gradle.org/install/
2. From the repository root run:

   gradle clean build

   or, if you have the Gradle wrapper in this repo (not currently committed):

   ./gradlew clean build

3. The built JAR will be in build/libs/ (forgestuck-1.21.1-1.1.0.jar)

Notes

- This is a minimal scaffold. To produce a working NeoForge mod:
  - Add the NeoForge moddev Gradle plugin to build.gradle and configure runs/mappings.
  - Replace the placeholder Java sources with the real implementation (mod entry, IModFileReader, transformers, shims).
  - Ensure you compile with Java 21 (the project toolchain is configured for Java 21 in build.gradle).

If you'd like, I can add the NeoForge Gradle plugin and full moddev configuration so you can run a client from Gradle (runClient), and I can also move the web UI into a separate branch.

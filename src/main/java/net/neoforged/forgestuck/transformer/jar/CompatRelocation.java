package net.neoforged.forgestuck.transformer.jar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * The compat classes in this jar are loaded by the FML <em>service</em> layer, which cannot see Minecraft or NeoForge
 * game classes (a transformed mod calling {@code compat.ForgeRegistries} failed with
 * {@code NoClassDefFoundError: net/minecraft/core/registries/Registries}). At runtime the locator therefore writes a
 * relocated copy of the compat package ({@link #TO}) as a library jar that FML loads into the game layer, and the
 * transformer ({@link CompatRelocationStep}) points transformed mods at that copy.
 */
public final class CompatRelocation {
    public static final String FROM = "net/neoforged/forgestuck/compat/";
    public static final String TO = "net/neoforged/forgestuck/gamecompat/";
    public static final String CLASS_LIST = "/net/neoforged/forgestuck/compat-classes.txt";
    public static final String MODULE_NAME = "forgestuck_gamecompat";

    private CompatRelocation() {}

    static final Remapper REMAPPER = new Remapper() {
        @Override
        public String map(String internalName) {
            return internalName.startsWith(FROM) ? TO + internalName.substring(FROM.length()) : internalName;
        }
    };

    /** Returns relocated bytes, or the input unchanged when it does not mention the compat package. */
    public static byte[] relocate(byte[] bytes) {
        if (!new String(bytes, StandardCharsets.ISO_8859_1).contains(FROM)) return bytes;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
        return writer.toByteArray();
    }

    /** Class names (relative to {@link #FROM}, without {@code .class}) listed in {@link #CLASS_LIST}. */
    public static List<String> compatClassNames(ClassLoader loader) throws IOException {
        try (InputStream in = loader.getResourceAsStream(CLASS_LIST.substring(1))) {
            if (in == null) throw new IOException("Missing " + CLASS_LIST);
            List<String> out = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (!line.isBlank() && !line.startsWith("#")) out.add(line.trim());
            }
            return out;
        }
    }

    /**
     * Writes {@code forgestuck-compat-<hash>.jar} into {@code dir} (deterministic, reused when present) and returns it.
     * The manifest marks it as an FML library with a fixed automatic module name.
     */
    public static Path writeRuntimeJar(Path dir, ClassLoader loader) throws IOException {
        List<String> names = compatClassNames(loader);
        List<byte[]> relocated = new ArrayList<>();
        MessageDigest sha = sha256();
        for (String n : names) {
            byte[] raw;
            try (InputStream in = loader.getResourceAsStream(FROM + n + ".class")) {
                if (in == null) throw new IOException("Missing compat class " + n);
                raw = in.readAllBytes();
            }
            byte[] data = relocate(raw);
            relocated.add(data);
            sha.update(n.getBytes(StandardCharsets.UTF_8));
            sha.update(data);
        }
        Files.createDirectories(dir);
        Path target = dir.resolve("forgestuck-compat-" + HexFormat.of().formatHex(sha.digest(), 0, 8) + ".jar");
        if (Files.isRegularFile(target) && Files.size(target) > 0) return target;

        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().putValue("FMLModType", "GAMELIBRARY");
        mf.getMainAttributes().putValue("Automatic-Module-Name", MODULE_NAME);
        Path tmp = Files.createTempFile(dir, "forgestuck-", ".tmp");
        try {
            try (OutputStream os = Files.newOutputStream(tmp); JarOutputStream out = new JarOutputStream(os)) {
                JarEntry m = new JarEntry(java.util.jar.JarFile.MANIFEST_NAME);
                m.setTime(0);
                out.putNextEntry(m);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                mf.write(bos);
                out.write(bos.toByteArray());
                out.closeEntry();
                for (int i = 0; i < names.size(); i++) {
                    JarEntry e = new JarEntry(TO + names.get(i) + ".class");
                    e.setTime(0);
                    out.putNextEntry(e);
                    out.write(relocated.get(i));
                    out.closeEntry();
                }
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        return target;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

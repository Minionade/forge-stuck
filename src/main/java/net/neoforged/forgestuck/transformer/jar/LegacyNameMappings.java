package net.neoforged.forgestuck.transformer.jar;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Legacy SRG member name ({@code m_123_}, {@code f_123_}, {@code c_123_}) mappings plus the set of vanilla classes
 * known to be missing at runtime. Only exact, whole-token matches of known names are ever rewritten.
 */
public final class LegacyNameMappings {
    /** Shape of a legacy SRG member name. */
    public static final Pattern SRG_NAME = Pattern.compile("[mfc]_\\d+_");
    private static final Pattern SRG_TOKEN = Pattern.compile("(?<![A-Za-z0-9_$])[mfc]_\\d+_(?![A-Za-z0-9_$])");

    private static final LegacyNameMappings DEFAULT = loadDefault();

    private final Map<String, String> members;
    private final Set<String> missingTargets;

    public LegacyNameMappings(Map<String, String> members, Set<String> missingTargets) {
        this.members = new TreeMap<>(members);
        Set<String> internal = new HashSet<>();
        for (String t : missingTargets) internal.add(t.replace('.', '/'));
        this.missingTargets = Set.copyOf(internal);
    }

    public static LegacyNameMappings defaults() {
        return DEFAULT;
    }

    private static LegacyNameMappings loadDefault() {
        Properties props = new Properties();
        Set<String> missing = new HashSet<>();
        try (InputStream in = LegacyNameMappings.class.getResourceAsStream("/forgestuck/srg-members.properties")) {
            if (in != null) props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        try (InputStream in = LegacyNameMappings.class.getResourceAsStream("/forgestuck/missing-mixin-targets.txt")) {
            if (in != null) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                    String t = line.trim();
                    if (!t.isEmpty() && !t.startsWith("#")) missing.add(t);
                }
            }
        } catch (IOException ignored) {
        }
        Map<String, String> members = new TreeMap<>();
        for (String k : props.stringPropertyNames()) members.put(k, props.getProperty(k).trim());
        return new LegacyNameMappings(members, missing);
    }

    public static boolean looksLikeSrg(String name) {
        return name != null && SRG_NAME.matcher(name).matches();
    }

    /** Mapped runtime name, or {@code null} when no mapping exists. */
    public String map(String srgName) {
        return members.get(srgName);
    }

    /**
     * Owner- and descriptor-aware lookup. Property keys, most specific first: {@code owner#name(desc)ret} /
     * {@code owner#name@fieldDesc}, {@code name(desc)ret} / {@code name@fieldDesc}, {@code owner#name}, bare
     * {@code name}. Bare-name entries remain valid, so existing data files keep working.
     */
    public String map(String owner, String name, String desc) {
        String d = desc == null ? "" : (desc.startsWith("(") ? desc : "@" + desc);
        String qualified = owner == null ? null : owner + "#" + name;
        String mapped = qualified == null ? null : members.get(qualified + d);
        if (mapped == null) mapped = members.get(name + d);
        if (mapped == null && qualified != null) mapped = members.get(qualified);
        return mapped != null ? mapped : members.get(name);
    }

    /** True when the (dotted or internal) class name is known to be absent at runtime. */
    public boolean isMissingTarget(String className) {
        return missingTargets.contains(className.replace('.', '/'));
    }

    /**
     * Replaces whole SRG tokens that have a mapping inside a metadata string (descriptor / target / refmap value).
     * Tokens without a mapping are preserved. Only call this on validated mixin metadata contexts.
     */
    public String remapTokens(String text, Set<String> remapped) {
        Matcher m = SRG_TOKEN.matcher(text);
        StringBuilder sb = null;
        int last = 0;
        while (m.find()) {
            String mapped = members.get(m.group());
            if (mapped == null) continue;
            if (sb == null) sb = new StringBuilder();
            sb.append(text, last, m.start()).append(mapped);
            last = m.end();
            if (remapped != null) remapped.add(m.group() + "->" + mapped);
        }
        if (sb == null) return text;
        return sb.append(text, last, text.length()).toString();
    }

    /** SRG tokens that are still present (unmapped) in the text. */
    public boolean hasUnmappedTokens(String text) {
        Matcher m = SRG_TOKEN.matcher(text);
        while (m.find()) {
            if (!members.containsKey(m.group())) return true;
        }
        return false;
    }
}

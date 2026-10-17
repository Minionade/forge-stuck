package net.neoforged.forgestuck.transformer.jar;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Renders NeoForge 1.21.1 {@code enumextensions.json} (schema verified against FML loader 4.0.42
 * {@code EnumPrototype.load} and the NeoForge "Extensible Enums" documentation).
 *
 * <p>FML validates per owning mod that the field name starts with the mod id (case-insensitive) and that the
 * {@code @NamedEnum} name parameter starts with {@code modid:}; one file is therefore written per mod, containing only
 * entries whose id namespace equals that mod id.
 */
final class EnumExtensionJson {
    static final String ITEM_DISPLAY_CONTEXT_CTOR = "(ILjava/lang/String;Ljava/lang/String;)V";

    private EnumExtensionJson() {}

    static String path(String modId) {
        return "META-INF/forgestuck/enumextensions-" + modId + ".json";
    }

    static String fieldName(String modId, String keyName, Set<String> used) {
        String base = keyName.toLowerCase(Locale.ROOT).startsWith(modId) ? keyName
                : modId.toUpperCase(Locale.ROOT) + "_" + keyName;
        StringBuilder safe = new StringBuilder();
        for (char c : base.toCharArray()) safe.append(Character.isJavaIdentifierPart(c) ? c : '_');
        if (safe.length() == 0 || !Character.isJavaIdentifierStart(safe.charAt(0))) safe.insert(0, '_');
        String name = safe.toString();
        String candidate = name;
        for (int i = 2; !used.add(candidate); i++) candidate = name + "_" + i;
        return candidate;
    }

    static String render(String modId, List<LegacyEnumExtensionPlanner.Entry> entries) {
        JsonArray array = new JsonArray();
        Set<String> used = new HashSet<>();
        for (LegacyEnumExtensionPlanner.Entry entry : entries) {
            if (!entry.namespace().equals(modId)) continue;
            JsonObject json = new JsonObject();
            json.addProperty("enum", LegacyEnumExtensionPlanner.ITEM_DISPLAY_CONTEXT);
            json.addProperty("name", fieldName(modId, entry.keyName(), used));
            json.addProperty("constructor", ITEM_DISPLAY_CONTEXT_CTOR);
            JsonArray parameters = new JsonArray();
            parameters.add(-1);
            parameters.add(entry.id());
            if (entry.fallback() == null) parameters.add(JsonNull.INSTANCE);
            else parameters.add(entry.fallback());
            json.add("parameters", parameters);
            array.add(json);
        }
        if (array.isEmpty()) return null;
        JsonObject root = new JsonObject();
        root.add("entries", array);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
    }
}

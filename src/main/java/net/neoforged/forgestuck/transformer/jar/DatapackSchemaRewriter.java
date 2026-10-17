package net.neoforged.forgestuck.transformer.jar;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Context-aware 1.20.1 -> 1.21.1 data migrations that are verified against the 1.21.1 codecs. Everything else is
 * left byte-for-byte untouched. Output is deterministic and idempotent; no registry is touched.
 */
public final class DatapackSchemaRewriter {
    private DatapackSchemaRewriter() {}

    /** Legacy plural data folder -> 1.21.1 singular folder (relative to {@code data/<ns>/}). */
    static final Map<String, String> FOLDER_RENAMES = Map.ofEntries(
            Map.entry("recipes/", "recipe/"),
            Map.entry("loot_tables/", "loot_table/"),
            Map.entry("advancements/", "advancement/"),
            Map.entry("structures/", "structure/"),
            Map.entry("functions/", "function/"),
            Map.entry("predicates/", "predicate/"),
            Map.entry("item_modifiers/", "item_modifier/"),
            Map.entry("tags/blocks/", "tags/block/"),
            Map.entry("tags/items/", "tags/item/"),
            Map.entry("tags/entity_types/", "tags/entity_type/"),
            Map.entry("tags/fluids/", "tags/fluid/"),
            Map.entry("tags/game_events/", "tags/game_event/"));

    private static final Set<String> CONDITION_TYPES =
            Set.of("and", "false", "item_exists", "mod_loaded", "not", "or", "tag_empty", "true");

    /** Exact key sets of the 1.21.1 flat numeric-provider codecs, by provider type path. */
    private static final Map<String, List<Set<String>>> PROVIDERS = Map.of(
            "uniform", List.of(Set.of("min_inclusive", "max_inclusive"), Set.of("min_inclusive", "max_exclusive")),
            "biased_to_bottom", List.of(Set.of("min_inclusive", "max_inclusive")),
            "clamped", List.of(Set.of("source", "min_inclusive", "max_inclusive")),
            "clamped_normal", List.of(Set.of("mean", "deviation", "min_inclusive", "max_inclusive"),
                    Set.of("mean", "deviation", "min", "max")),
            "trapezoid", List.of(Set.of("min", "max", "plateau")));

    /** New entry name for a legacy plural data folder entry, or {@code null} when no rename applies. */
    public static String renamedEntry(String name) {
        if (!name.startsWith("data/")) return null;
        int slash = name.indexOf('/', 5);
        if (slash < 0) return null;
        String rest = name.substring(slash + 1);
        for (Map.Entry<String, String> e : FOLDER_RENAMES.entrySet()) {
            if (rest.startsWith(e.getKey())) return name.substring(0, slash + 1) + e.getValue() + rest.substring(e.getKey().length());
        }
        return null;
    }

    /** Rewrites one data JSON; returns the same array instance when nothing changes or the data is not migratable. */
    public static byte[] rewriteJson(String path, byte[] data, ResourceRewriteReport report) {
        if (!path.startsWith("data/") || !path.endsWith(".json")) return data;
        JsonElement root;
        try {
            root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            report.unsupported(path, "malformed JSON (" + e.getClass().getSimpleName() + ")");
            return data;
        }
        boolean worldgen = path.indexOf("/worldgen/") > 0;
        int[] changes = {0};
        walk(root, worldgen, changes, report, path);
        if (changes[0] == 0) return data;
        report.rewritten(path, changes[0] + " change(s)");
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void walk(JsonElement el, boolean worldgen, int[] changes, ResourceRewriteReport report, String path) {
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) walk(child, worldgen, changes, report, path);
            return;
        }
        if (!el.isJsonObject()) return;
        JsonObject obj = el.getAsJsonObject();
        if (obj.has("forge:conditions") && !obj.has("neoforge:conditions")) {
            JsonElement conds = obj.remove("forge:conditions");
            renameConditionTypes(conds, changes);
            obj.add("neoforge:conditions", conds);
            changes[0]++;
        }
        if (worldgen) flattenProvider(obj, changes, report, path);
        for (String key : List.copyOf(obj.keySet())) walk(obj.get(key), worldgen, changes, report, path);
    }

    private static void renameConditionTypes(JsonElement el, int[] changes) {
        if (el.isJsonArray()) {
            for (JsonElement c : el.getAsJsonArray()) renameConditionTypes(c, changes);
        } else if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            JsonElement type = o.get("type");
            if (type != null && type.isJsonPrimitive() && type.getAsString().startsWith("forge:")
                    && CONDITION_TYPES.contains(type.getAsString().substring(6))) {
                o.addProperty("type", "neoforge:" + type.getAsString().substring(6));
                changes[0]++;
            }
            for (String k : List.copyOf(o.keySet())) {
                JsonElement child = o.get(k);
                if (child.isJsonObject() || child.isJsonArray()) renameConditionTypes(child, changes);
            }
        }
    }

    /** 1.20.1 {@code {"type":"minecraft:uniform","value":{min_inclusive,max_inclusive}}} -> flat 1.21.1 form. */
    private static void flattenProvider(JsonObject obj, int[] changes, ResourceRewriteReport report, String path) {
        JsonElement type = obj.get("type");
        JsonElement value = obj.get("value");
        if (type == null || !type.isJsonPrimitive() || value == null || !value.isJsonObject()) return;
        String id = type.getAsString();
        String name = id.startsWith("minecraft:") ? id.substring(10) : id;
        List<Set<String>> shapes = PROVIDERS.get(name);
        if (shapes == null) return;
        JsonObject inner = value.getAsJsonObject();
        if (obj.size() != 2 || shapes.stream().noneMatch(s -> s.equals(inner.keySet()))) {
            report.unsupported(path, "'" + id + "' with nested 'value' does not match a verified 1.21.1 shape");
            return;
        }
        obj.remove("value");
        for (Map.Entry<String, JsonElement> e : inner.entrySet()) obj.add(e.getKey(), e.getValue());
        changes[0]++;
    }
}

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
    private static final Map<String, String> GENDUSTRY_FORESTRY_ITEM_RENAMES = Map.of(
            "forestry:sturdy_machine", "forestry:sturdy_casing",
            "forestry:pollen_cluster_normal", "forestry:pollen_cluster");
    private static final Map<String, String> LEGACY_RECIPE_TAG_RENAMES = Map.ofEntries(
            Map.entry("forge:dusts/redstone", "c:dusts/redstone"),
            Map.entry("forge:ender_pearls", "c:ender_pearls"),
            Map.entry("forge:gears/bronze", "c:gears/bronze"),
            Map.entry("forge:gems/amethyst", "c:gems/amethyst"),
            Map.entry("forge:gems/diamond", "c:gems/diamond"),
            Map.entry("forge:gems/emerald", "c:gems/emerald"),
            Map.entry("forge:gems/lapis", "c:gems/lapis"),
            Map.entry("forge:gems/quartz", "c:gems/quartz"),
            Map.entry("forge:glass", "c:glass_blocks"),
            Map.entry("forge:glass_panes", "c:glass_panes"),
            Map.entry("forge:ingots/bronze", "c:ingots/bronze"),
            Map.entry("forge:ingots/copper", "c:ingots/copper"),
            Map.entry("forge:ingots/gold", "c:ingots/gold"),
            Map.entry("forge:ingots/iron", "c:ingots/iron"),
            Map.entry("forge:ingots/netherite", "c:ingots/netherite"),
            Map.entry("forge:ingots/tin", "c:ingots/tin"),
            Map.entry("forge:mushrooms", "c:mushrooms"),
            Map.entry("forge:nuggets/gold", "c:nuggets/gold"),
            Map.entry("forge:nuggets/iron", "c:nuggets/iron"),
            Map.entry("forge:storage_blocks/redstone", "c:storage_blocks/redstone"),
            Map.entry("forge:string", "c:strings"));
    private static final Map<String, String> TCONSTRUCT_VANILLA_STORAGE_ITEMS = Map.ofEntries(
            Map.entry("forge:storage_blocks/amethyst", "minecraft:amethyst_block"),
            Map.entry("forge:storage_blocks/copper", "minecraft:copper_block"),
            Map.entry("forge:storage_blocks/diamond", "minecraft:diamond_block"),
            Map.entry("forge:storage_blocks/emerald", "minecraft:emerald_block"),
            Map.entry("forge:storage_blocks/gold", "minecraft:gold_block"),
            Map.entry("forge:storage_blocks/iron", "minecraft:iron_block"),
            Map.entry("forge:storage_blocks/netherite", "minecraft:netherite_block"),
            Map.entry("forge:storage_blocks/quartz", "minecraft:quartz_block"),
            Map.entry("forge:storage_blocks/redstone", "minecraft:redstone_block"));

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
        boolean recipe = path.contains("/recipes/") || path.contains("/recipe/");
        boolean loot = path.contains("/loot_tables/") || path.contains("/loot_table/")
                || path.contains("/item_modifiers/") || path.contains("/item_modifier/");
        boolean gendustryRecipe = path.startsWith("data/gendustry/recipes/")
                || path.startsWith("data/gendustry/recipe/");
        int[] changes = {0};
        if (recipe && root.isJsonObject()) {
            JsonObject recipeObject = root.getAsJsonObject();
            rewriteRecipeResult(recipeObject, changes);
            rewriteRecipeConditions(recipeObject, changes);
            rewriteTConstructVanillaStorageOutput(path, recipeObject, changes);
        }
        walk(root, worldgen, recipe, loot, gendustryRecipe, changes, report, path);
        if (changes[0] == 0) return data;
        report.rewritten(path, changes[0] + " change(s)");
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void walk(
            JsonElement el,
            boolean worldgen,
            boolean recipe,
            boolean loot,
            boolean gendustryRecipe,
            int[] changes,
            ResourceRewriteReport report,
            String path) {
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                walk(child, worldgen, recipe, loot, gendustryRecipe, changes, report, path);
            }
            return;
        }
        if (!el.isJsonObject()) return;
        JsonObject obj = el.getAsJsonObject();
        if (recipe) rewriteRecipeTag(obj, changes);
        if (loot) rewriteLootSchema(obj, changes, report, path);
        if (gendustryRecipe) rewriteGendustryForestryItems(obj, changes);
        if (obj.has("forge:conditions") && !obj.has("neoforge:conditions")) {
            JsonElement conds = obj.remove("forge:conditions");
            renameConditionTypes(conds, changes);
            obj.add("neoforge:conditions", conds);
            changes[0]++;
        }
        if (worldgen) flattenProvider(obj, changes, report, path);
        for (String key : List.copyOf(obj.keySet())) {
            JsonElement child = obj.get(key);
            if (worldgen && child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()
                    && child.getAsString().equals("minecraft:grass")) {
                obj.addProperty(key, "minecraft:short_grass");
                changes[0]++;
            } else {
                walk(child, worldgen, recipe, loot, gendustryRecipe, changes, report, path);
            }
        }
    }

    private static void rewriteLootSchema(JsonObject obj, int[] changes, ResourceRewriteReport report, String path) {
        JsonElement condition = obj.get("condition");
        if (condition != null && condition.isJsonPrimitive()
                && condition.getAsString().equals("forge:can_tool_perform_action")) {
            JsonElement action = obj.get("action");
            if (action == null || !action.isJsonPrimitive() || !action.getAsJsonPrimitive().isString()) {
                report.unsupported(path, "forge:can_tool_perform_action without a string action");
            } else {
                String ability = action.getAsString();
                if (!ability.contains(":")) ability = "minecraft:" + ability;
                obj.remove("action");
                obj.addProperty("ability", ability);
                obj.addProperty("condition", "neoforge:can_item_perform_ability");
                changes[0]++;
            }
        }

        JsonElement function = obj.get("function");
        if (function == null || !function.isJsonPrimitive()) return;
        String functionId = function.getAsString();
        if (functionId.equals("minecraft:copy_nbt")) {
            JsonElement sourceValue = obj.get("source");
            JsonElement ops = obj.get("ops");
            if (sourceValue != null && sourceValue.isJsonPrimitive()
                    && sourceValue.getAsString().equals("block_entity")
                    && ops != null && ops.isJsonArray()) {
                JsonObject source = new JsonObject();
                source.addProperty("type", "minecraft:context");
                source.addProperty("target", "block_entity");
                obj.add("source", source);
                obj.addProperty("function", "minecraft:copy_custom_data");
                changes[0]++;
            } else {
                report.unsupported(path, "minecraft:copy_nbt source/ops shape is not verified");
            }
        } else if (functionId.equals("minecraft:looting_enchant")) {
            if (obj.has("count")) {
                obj.addProperty("function", "minecraft:enchanted_count_increase");
                if (!obj.has("enchantment")) obj.addProperty("enchantment", "minecraft:looting");
                changes[0]++;
                rewriteLootProvider(obj.get("count"), changes, report, path);
            } else {
                report.unsupported(path, "minecraft:looting_enchant without count provider");
            }
        } else if (functionId.equals("minecraft:enchanted_count_increase") && obj.has("count")) {
            rewriteLootProvider(obj.get("count"), changes, report, path);
        }
    }

    private static void rewriteLootProvider(JsonElement element, int[] changes, ResourceRewriteReport report,
            String path) {
        if (element == null || !element.isJsonObject()) return;
        JsonObject provider = element.getAsJsonObject();
        JsonElement type = provider.get("type");
        if (type == null || !type.isJsonPrimitive() || !type.getAsString().equals("minecraft:uniform")
                || !provider.has("min") || !provider.has("max")
                || provider.has("min_inclusive") || provider.has("max_exclusive")) return;
        if (provider.size() != 3) {
            report.unsupported(path, "minecraft:uniform loot provider has unverified extra fields");
            return;
        }
        provider.remove("type");
        changes[0]++;
    }

    private static void rewriteRecipeTag(JsonObject obj, int[] changes) {
        JsonElement tag = obj.get("tag");
        if (tag == null || !tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString()) return;
        String tagId = tag.getAsString();
        String replacement = LEGACY_RECIPE_TAG_RENAMES.get(tagId);
        if (replacement == null && tagId.startsWith("forge:dusts/") && tagId.length() > "forge:dusts/".length()) {
            replacement = "c:" + tagId.substring("forge:".length());
        }
        if (replacement == null) return;
        obj.addProperty("tag", replacement);
        changes[0]++;
    }

    private static void rewriteGendustryForestryItems(JsonObject obj, int[] changes) {
        JsonElement item = obj.get("item");
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) return;
        String replacement = GENDUSTRY_FORESTRY_ITEM_RENAMES.get(item.getAsString());
        if (replacement == null) return;
        obj.addProperty("item", replacement);
        changes[0]++;
    }

    private static void rewriteRecipeResult(JsonObject obj, int[] changes) {
        JsonElement result = obj.get("result");
        if (result == null) return;
        if (result.isJsonPrimitive() && result.getAsJsonPrimitive().isString()) {
            JsonObject resultObject = new JsonObject();
            resultObject.add("id", result);
            obj.add("result", resultObject);
            changes[0]++;
            return;
        }
        if (!result.isJsonObject()) return;
        JsonObject resultObject = result.getAsJsonObject();
        JsonElement item = resultObject.get("item");
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()
                || resultObject.has("id")) {
            return;
        }
        resultObject.remove("item");
        resultObject.add("id", item);
        changes[0]++;
    }

    private static void rewriteRecipeConditions(JsonObject obj, int[] changes) {
        if (!obj.has("conditions") || obj.has("neoforge:conditions")) return;
        JsonElement conditions = obj.remove("conditions");
        renameConditionTypes(conditions, changes);
        obj.add("neoforge:conditions", conditions);
        changes[0]++;
    }

    private static void rewriteTConstructVanillaStorageOutput(String path, JsonObject obj, int[] changes) {
        if (!path.startsWith("data/tconstruct/recipes/")
                && !path.startsWith("data/tconstruct/recipe/")) return;
        JsonElement result = obj.get("result");
        if (result == null || !result.isJsonObject()) return;
        JsonObject resultObject = result.getAsJsonObject();
        JsonElement tag = resultObject.get("tag");
        if (tag == null || !tag.isJsonPrimitive()) return;
        String item = TCONSTRUCT_VANILLA_STORAGE_ITEMS.get(tag.getAsString());
        if (item == null) return;
        resultObject.remove("tag");
        resultObject.addProperty("item", item);
        changes[0]++;
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

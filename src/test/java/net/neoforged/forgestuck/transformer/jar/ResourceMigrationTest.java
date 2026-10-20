package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceMigrationTest {
    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String s(byte[] d) {
        return new String(d, StandardCharsets.UTF_8);
    }

    private static void assertCodecAccepts(String owner, String field, String json) throws Exception {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        ClassLoader gameLoader = net.neoforged.neoforge.common.util.flag.FeatureFlagLoader.class.getClassLoader();
        Class<?> jsonParser = Class.forName("com.google.gson.JsonParser", true, gameLoader);
        Object element = jsonParser.getMethod("parseString", String.class).invoke(null, json);
        Object codec = Class.forName(owner, true, gameLoader).getField(field).get(null);
        Object ops = Class.forName("com.mojang.serialization.JsonOps", true, gameLoader)
                .getField("INSTANCE").get(null);
        Class<?> dynamicOps = Class.forName("com.mojang.serialization.DynamicOps", true, gameLoader);
        Object result = codec.getClass().getMethod("parse", dynamicOps, Object.class).invoke(codec, ops, element);
        Optional<?> decoded = (Optional<?>) result.getClass().getMethod("result").invoke(result);
        String error = "";
        if (decoded.isEmpty()) {
            Optional<?> codecError = (Optional<?>) result.getClass().getMethod("error").invoke(result);
            if (codecError.isPresent()) {
                error = String.valueOf(codecError.get().getClass().getMethod("message").invoke(codecError.get()));
            }
        }
        assertTrue(decoded.isPresent(), "1.21.1 codec rejected migrated JSON: " + json + " (" + error + ")");
    }

    private static final String LEGACY = "{\"type\":\"minecraft:uniform\",\"value\":{\"min_inclusive\":1,\"max_inclusive\":3}}";

    @Test
    void flattensVerifiedNestedProviderInWorldgenAndIsIdempotent() {
        ResourceRewriteReport r = new ResourceRewriteReport();
        String in = "{\"trunk\":" + LEGACY + ",\"custom:ns\":\"keep\"}";
        byte[] once = DatapackSchemaRewriter.rewriteJson("data/zz/worldgen/configured_feature/t.json", b(in), r);
        String out = s(once);
        assertFalse(out.contains("\"value\""), out);
        assertTrue(out.contains("\"min_inclusive\": 1") && out.contains("\"max_inclusive\": 3"), out);
        assertTrue(out.contains("custom:ns"));
        assertSame(once, DatapackSchemaRewriter.rewriteJson("data/zz/worldgen/configured_feature/t.json", once, r));
    }

    @Test
    void doesNotFlattenOutsideWorldgenOrForUnverifiedShapesOrCustomTypes() {
        ResourceRewriteReport r = new ResourceRewriteReport();
        byte[] outside = b(LEGACY);
        assertSame(outside, DatapackSchemaRewriter.rewriteJson("data/zz/other/x.json", outside, r));
        byte[] custom = b("{\"type\":\"zz:uniform\",\"value\":{\"min_inclusive\":1,\"max_inclusive\":3}}");
        assertSame(custom, DatapackSchemaRewriter.rewriteJson("data/zz/worldgen/x.json", custom, r));
        byte[] odd = b("{\"type\":\"minecraft:uniform\",\"value\":{\"min_inclusive\":1,\"bogus\":3}}");
        assertSame(odd, DatapackSchemaRewriter.rewriteJson("data/zz/worldgen/y.json", odd, r));
        assertEquals(1, r.unsupported().size());
        byte[] plain = b("{\"type\":\"minecraft:constant\",\"value\":4}");
        assertSame(plain, DatapackSchemaRewriter.rewriteJson("data/zz/worldgen/z.json", plain, r));
    }

    @Test
    void renamesLegacyGrassBlockInWorldgenDataOnly() {
        ResourceRewriteReport r = new ResourceRewriteReport();
        byte[] input = b("{\"grasses\":[{\"data\":\"minecraft:grass\",\"weight\":7}]}");
        String out = s(DatapackSchemaRewriter.rewriteJson("data/zz/worldgen/structure/island.json", input, r));
        assertTrue(out.contains("\"minecraft:short_grass\""), out);
        assertFalse(out.contains("\"minecraft:grass\""), out);

        byte[] outside = b("{\"block\":\"minecraft:grass\"}");
        assertSame(outside, DatapackSchemaRewriter.rewriteJson("data/zz/recipe/other.json", outside, r));
    }

    @Test
    void malformedJsonIsReportedAndPreserved() {
        ResourceRewriteReport r = new ResourceRewriteReport();
        byte[] bad = b("{not json");
        assertSame(bad, DatapackSchemaRewriter.rewriteJson("data/zz/recipes/a.json", bad, r));
        assertEquals(1, r.unsupported().size());
    }

    @Test
    void forgeConditionsBecomeNeoForgeConditionsOnly() {
        ResourceRewriteReport r = new ResourceRewriteReport();
        String in = "{\"forge:conditions\":[{\"type\":\"forge:mod_loaded\",\"modid\":\"zz\"},{\"type\":\"forge:custom\"}]}";
        String out = s(DatapackSchemaRewriter.rewriteJson("data/zz/recipes/a.json", b(in), r));
        assertTrue(out.contains("neoforge:conditions") && out.contains("neoforge:mod_loaded"), out);
        assertTrue(out.contains("forge:custom"), "unknown forge types are not guessed: " + out);
    }

    @Test
    void migratesLegacyForgeRecipeConditionsAndKnownConditionTypes() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"type\":\"tconstruct:casting_table\",\"conditions\":["
                + "{\"type\":\"mantle:tag_filled\",\"tag\":\"forge:coins/uranium\"},"
                + "{\"type\":\"forge:or\",\"values\":[{\"type\":\"forge:mod_loaded\",\"modid\":\"zz\"}]}]}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/recipes/smeltery/casting/metal/uranium/coin_gold_cast.json", b(input), report));
        var migrated = com.google.gson.JsonParser.parseString(output).getAsJsonObject();

        assertFalse(migrated.has("conditions"), output);
        var conditions = migrated.getAsJsonArray("neoforge:conditions");
        assertEquals("mantle:tag_filled", conditions.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("forge:coins/uranium", conditions.get(0).getAsJsonObject().get("tag").getAsString());
        assertEquals("neoforge:or", conditions.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("neoforge:mod_loaded", conditions.get(1).getAsJsonObject()
                .getAsJsonArray("values").get(0).getAsJsonObject().get("type").getAsString());
    }

    @Test
    void migratesLegacyItemStackRecipeResultsOnlyInRecipeFiles() {
        ResourceRewriteReport r = new ResourceRewriteReport();
        String input = "{\"type\":\"minecraft:crafting_shaped\",\"result\":{\"item\":\"zz:output\",\"count\":2}}";
        byte[] migrated = DatapackSchemaRewriter.rewriteJson("data/zz/recipes/output.json", b(input), r);
        String output = s(migrated);
        assertTrue(output.contains("\"id\": \"zz:output\""), output);
        assertFalse(output.contains("\"item\""), output);
        assertSame(migrated,
                DatapackSchemaRewriter.rewriteJson("data/zz/recipe/output.json", migrated, r));

        byte[] alreadyCurrent = b("{\"result\":{\"id\":\"zz:output\"}}");
        assertSame(alreadyCurrent,
                DatapackSchemaRewriter.rewriteJson("data/zz/recipe/current.json", alreadyCurrent, r));
        byte[] outsideRecipe = b("{\"result\":{\"item\":\"zz:output\"}}");
        assertSame(outsideRecipe, DatapackSchemaRewriter.rewriteJson("data/zz/custom/output.json", outsideRecipe, r));
    }

    @Test
    void migratesLegacyStringRecipeResultsOnlyAtTheRecipeRoot() {
        ResourceRewriteReport r = new ResourceRewriteReport();
        byte[] input = b("{\"type\":\"minecraft:smelting\",\"result\":\"zz:output\","
                + "\"custom\":{\"result\":\"zz:keep\"}}");
        String output = s(DatapackSchemaRewriter.rewriteJson("data/gendustry/recipes/output.json", input, r));
        var migrated = com.google.gson.JsonParser.parseString(output).getAsJsonObject();
        assertEquals("zz:output", migrated.getAsJsonObject("result").get("id").getAsString());
        assertEquals("zz:keep", migrated.getAsJsonObject("custom").get("result").getAsString());

        byte[] outsideRecipe = b("{\"result\":\"zz:output\"}");
        assertSame(outsideRecipe,
                DatapackSchemaRewriter.rewriteJson("data/zz/custom/output.json", outsideRecipe, r));
    }

    @Test
    void migratesGendustryRecipesToForestryCeItemIdsOnly() {
        ResourceRewriteReport r = new ResourceRewriteReport();
        byte[] input = b("{\"key\":{\"S\":{\"item\":\"forestry:sturdy_machine\"},"
                + "\"P\":{\"item\":\"forestry:pollen_cluster_normal\"}}}");
        String output = s(DatapackSchemaRewriter.rewriteJson("data/gendustry/recipes/machine.json", input, r));
        assertTrue(output.contains("\"forestry:sturdy_casing\""), output);
        assertTrue(output.contains("\"forestry:pollen_cluster\""), output);

        byte[] outsideGendustry = b("{\"item\":\"forestry:sturdy_machine\"}");
        assertSame(outsideGendustry,
                DatapackSchemaRewriter.rewriteJson("data/other/recipes/machine.json", outsideGendustry, r));
    }

    @Test
    void migratesKnownLegacyForgeIngredientTagsToCommonTags() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"key\":{\"B\":{\"tag\":\"forge:gears/bronze\"},"
                + "\"I\":{\"tag\":\"forge:ingots/bronze\"},"
                + "\"D\":{\"tag\":\"forge:dusts/redstone\"},"
                + "\"M\":{\"tag\":\"forge:dusts/iron\"},"
                + "\"G\":{\"tag\":\"forge:glass\"},"
                + "\"X\":{\"tag\":\"forge:custom_material\"}}}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/gendustry/recipes/machine.json", b(input), report));

        assertTrue(output.contains("\"c:gears/bronze\""), output);
        assertTrue(output.contains("\"c:ingots/bronze\""), output);
        assertTrue(output.contains("\"c:dusts/redstone\""), output);
        assertTrue(output.contains("\"c:dusts/iron\""), output);
        assertTrue(output.contains("\"c:glass_blocks\""), output);
        assertTrue(output.contains("\"forge:custom_material\""), output);
        byte[] alreadyMigrated = b("{\"key\":{\"I\":{\"tag\":\"c:ingots/bronze\"}}}");
        assertSame(alreadyMigrated, DatapackSchemaRewriter.rewriteJson(
                "data/gendustry/recipes/current.json", alreadyMigrated, report));
    }

    @Test
    void migratesTConstructIronCastingOutputTag() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"type\":\"tconstruct:casting_table\","
                + "\"fluid\":{\"amount\":90,\"tag\":\"forge:molten_iron\"},"
                + "\"result\":{\"tag\":\"forge:ingots/iron\"}}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/recipes/smeltery/casting/metal/iron/ingot_gold_cast.json", b(input), report));

        assertTrue(output.contains("\"tag\": \"c:ingots/iron\""), output);
        assertTrue(output.contains("\"tag\": \"forge:molten_iron\""), output);
    }

    @Test
    void migratesTConstructNetheriteIngotOutputToCommonTag() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"type\":\"tconstruct:casting_table\","
                + "\"fluid\":{\"amount\":90,\"tag\":\"forge:molten_netherite\"},"
                + "\"result\":{\"tag\":\"forge:ingots/netherite\"}}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/recipes/smeltery/casting/metal/netherite/ingot_sand_cast.json", b(input), report));

        assertTrue(output.contains("\"tag\": \"c:ingots/netherite\""), output);
        assertTrue(output.contains("\"tag\": \"forge:molten_netherite\""), output);
    }

    @Test
    void migratesTConstructIronNuggetOutputToCommonTag() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"type\":\"tconstruct:casting_table\","
                + "\"fluid\":{\"amount\":10,\"tag\":\"forge:molten_iron\"},"
                + "\"result\":{\"tag\":\"forge:nuggets/iron\"}}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/recipes/smeltery/casting/metal/iron/nugget_sand_cast.json", b(input), report));

        assertTrue(output.contains("\"tag\": \"c:nuggets/iron\""), output);
        assertTrue(output.contains("\"tag\": \"forge:molten_iron\""), output);
    }

    @Test
    void migratesLegacyLootFunctionsAndForgeToolAbilityCondition() throws Exception {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"pools\":[{\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond\","
                + "\"conditions\":[{\"condition\":\"forge:can_tool_perform_action\","
                + "\"action\":\"shears_dig\"}],\"functions\":["
                + "{\"function\":\"minecraft:copy_nbt\",\"source\":\"block_entity\",\"ops\":["
                + "{\"source\":\"tank\",\"target\":\"tank\",\"op\":\"replace\"}]},"
                + "{\"function\":\"minecraft:looting_enchant\",\"count\":"
                + "{\"type\":\"minecraft:uniform\",\"min\":0.0,\"max\":1.0}}]}]}]}";

        byte[] migrated = DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/loot_tables/blocks/seared_fuel_tank.json", b(input), report);
        var root = com.google.gson.JsonParser.parseString(s(migrated)).getAsJsonObject();
        var entry = root.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject();
        var condition = entry.getAsJsonArray("conditions").get(0).getAsJsonObject();
        assertEquals("neoforge:can_item_perform_ability", condition.get("condition").getAsString());
        assertEquals("minecraft:shears_dig", condition.get("ability").getAsString());
        assertFalse(condition.has("action"));

        var functions = entry.getAsJsonArray("functions");
        var copy = functions.get(0).getAsJsonObject();
        assertEquals("minecraft:copy_custom_data", copy.get("function").getAsString());
        assertEquals("minecraft:context", copy.getAsJsonObject("source").get("type").getAsString());
        assertEquals("block_entity", copy.getAsJsonObject("source").get("target").getAsString());
        var looting = functions.get(1).getAsJsonObject();
        assertEquals("minecraft:enchanted_count_increase", looting.get("function").getAsString());
        assertEquals("minecraft:looting", looting.get("enchantment").getAsString());
        assertEquals(0.0, looting.getAsJsonObject("count").get("min").getAsDouble());
        assertEquals(1.0, looting.getAsJsonObject("count").get("max").getAsDouble());
        assertFalse(looting.getAsJsonObject("count").has("type"));

        assertCodecAccepts("net.minecraft.world.level.storage.loot.functions.LootItemFunctions",
                "ROOT_CODEC", functions.get(0).toString());

        assertSame(migrated, DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/loot_table/blocks/seared_fuel_tank.json", migrated, report));
    }

    @Test
    void migratesTConstructAmethystGemOutputToCommonTag() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"type\":\"tconstruct:casting_table\","
                + "\"fluid\":{\"amount\":90,\"tag\":\"forge:molten_amethyst\"},"
                + "\"result\":{\"tag\":\"forge:gems/amethyst\"}}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/recipes/smeltery/casting/amethyst/gem_sand_cast.json", b(input), report));

        assertTrue(output.contains("\"tag\": \"c:gems/amethyst\""), output);
        assertTrue(output.contains("\"tag\": \"forge:molten_amethyst\""), output);
    }

    @Test
    void migratesTConstructCopperIngotOutputToCommonTag() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"type\":\"tconstruct:casting_table\","
                + "\"fluid\":{\"amount\":90,\"tag\":\"forge:molten_copper\"},"
                + "\"result\":{\"tag\":\"forge:ingots/copper\"}}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/recipes/smeltery/casting/metal/copper/ingot_sand_cast.json", b(input), report));

        assertTrue(output.contains("\"tag\": \"c:ingots/copper\""), output);
        assertTrue(output.contains("\"tag\": \"forge:molten_copper\""), output);
    }

    @Test
    void migratesTConstructDiamondBlockOutputToVanillaItem() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"type\":\"tconstruct:casting_table\","
                + "\"fluid\":{\"amount\":1296,\"tag\":\"forge:molten_diamond\"},"
                + "\"result\":{\"tag\":\"forge:storage_blocks/diamond\"}}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/recipes/smeltery/casting/diamond/block.json", b(input), report));

        assertTrue(output.contains("\"item\": \"minecraft:diamond_block\""), output);
        assertTrue(output.contains("\"tag\": \"forge:molten_diamond\""), output);
    }

    @Test
    void replacesTConstructAmethystBlockOutputTagWithVanillaItem() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"type\":\"tconstruct:casting_table\","
                + "\"fluid\":{\"amount\":1296,\"tag\":\"forge:molten_amethyst\"},"
                + "\"result\":{\"tag\":\"forge:storage_blocks/amethyst\"}}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/recipes/smeltery/casting/amethyst/block.json", b(input), report));

        assertTrue(output.contains("\"item\": \"minecraft:amethyst_block\""), output);
        assertFalse(output.contains("storage_blocks/amethyst"), output);
        assertTrue(output.contains("\"tag\": \"forge:molten_amethyst\""), output);
    }

    @Test
    void migratesTConstructNetheriteBlockOutputToVanillaItem() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"type\":\"tconstruct:casting_basin\","
                + "\"fluid\":{\"amount\":1296,\"tag\":\"forge:molten_netherite\"},"
                + "\"result\":{\"tag\":\"forge:storage_blocks/netherite\"}}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/recipes/smeltery/casting/metal/netherite/block.json", b(input), report));

        assertTrue(output.contains("\"item\": \"minecraft:netherite_block\""), output);
        assertTrue(output.contains("\"tag\": \"forge:molten_netherite\""), output);
    }

    @Test
    void migratesTConstructEmeraldBlockOutputToVanillaItem() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"type\":\"tconstruct:casting_basin\","
                + "\"fluid\":{\"amount\":1296,\"tag\":\"forge:molten_emerald\"},"
                + "\"result\":{\"tag\":\"forge:storage_blocks/emerald\"}}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/recipes/smeltery/casting/emerald/block.json", b(input), report));

        assertTrue(output.contains("\"item\": \"minecraft:emerald_block\""), output);
        assertTrue(output.contains("\"tag\": \"forge:molten_emerald\""), output);
    }

    @Test
    void migratesTConstructIronBlockOutputToVanillaItem() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"type\":\"tconstruct:casting_basin\","
                + "\"fluid\":{\"amount\":1296,\"tag\":\"forge:molten_iron\"},"
                + "\"result\":{\"tag\":\"forge:storage_blocks/iron\"}}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/recipes/smeltery/casting/metal/iron/block.json", b(input), report));

        assertTrue(output.contains("\"item\": \"minecraft:iron_block\""), output);
        assertTrue(output.contains("\"tag\": \"forge:molten_iron\""), output);
    }

    @Test
    void migratesTConstructCopperBlockOutputToVanillaItem() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"type\":\"tconstruct:casting_basin\","
                + "\"fluid\":{\"amount\":1296,\"tag\":\"forge:molten_copper\"},"
                + "\"result\":{\"tag\":\"forge:storage_blocks/copper\"}}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/recipes/smeltery/casting/metal/copper/block.json", b(input), report));

        assertTrue(output.contains("\"item\": \"minecraft:copper_block\""), output);
        assertTrue(output.contains("\"tag\": \"forge:molten_copper\""), output);
    }

    @Test
    void migratesTConstructQuartzBlockOutputToVanillaItem() {
        ResourceRewriteReport report = new ResourceRewriteReport();
        String input = "{\"type\":\"tconstruct:casting_basin\","
                + "\"fluid\":{\"amount\":1296,\"tag\":\"forge:molten_quartz\"},"
                + "\"result\":{\"tag\":\"forge:storage_blocks/quartz\"}}";

        String output = s(DatapackSchemaRewriter.rewriteJson(
                "data/tconstruct/recipes/smeltery/casting/quartz/block.json", b(input), report));

        assertTrue(output.contains("\"item\": \"minecraft:quartz_block\""), output);
        assertTrue(output.contains("\"tag\": \"forge:molten_quartz\""), output);
    }

    @Test
    void jarFoldersAreRenamedWithoutOverwritingAndUnaffectedBytesSurvive(@TempDir Path dir) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("data/zz/recipes/a.json", b("{\"x\":1}"));
        files.put("data/zz/tags/blocks/t.json", b("{\"values\":[]}"));
        files.put("data/zz/loot_tables/c.json", b("{\"legacy\":true}"));
        files.put("data/zz/loot_table/c.json", b("{\"already\":true}"));
        files.put("data/zz/custom/keep.json", b("{ \"spacing\" :   1 }"));
        Path out = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(SrgCompatTest.jar(dir, "in.jar", files), out);
        assertEquals("{\"x\":1}", s(SrgCompatTest.read(out, "data/zz/recipe/a.json")));
        assertNotNull(SrgCompatTest.read(out, "data/zz/tags/block/t.json"));
        assertEquals("{\"already\":true}", s(SrgCompatTest.read(out, "data/zz/loot_table/c.json")));
        assertEquals("{\"legacy\":true}", s(SrgCompatTest.read(out, "data/zz/loot_tables/c.json")));
        assertEquals("{ \"spacing\" :   1 }", s(SrgCompatTest.read(out, "data/zz/custom/keep.json")));
    }

    @Test
    void unknownModelLoaderIsPreservedByDefault(@TempDir Path dir) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("assets/zz/models/item/a.json", b("{\"loader\":\"zz:custom\"}"));
        Path out = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(SrgCompatTest.jar(dir, "in.jar", files), out);
        assertTrue(s(SrgCompatTest.read(out, "assets/zz/models/item/a.json")).contains("zz:custom"));
    }
}

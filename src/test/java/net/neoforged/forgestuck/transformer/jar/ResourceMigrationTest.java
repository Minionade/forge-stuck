package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
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

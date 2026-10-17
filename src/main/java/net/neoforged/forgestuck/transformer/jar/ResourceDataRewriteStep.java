package net.neoforged.forgestuck.transformer.jar;

import java.util.Set;

/**
 * Jar-entry level resource migration used by {@link JarTransformer}: renames legacy plural data folders to the 1.21.1
 * singular ones (never overwriting an existing entry) and delegates JSON content migration to
 * {@link DatapackSchemaRewriter}.
 */
final class ResourceDataRewriteStep {
    private final Set<String> existingNames;
    private final ResourceRewriteReport report;

    ResourceDataRewriteStep(Set<String> existingNames, ResourceRewriteReport report) {
        this.existingNames = existingNames;
        this.report = report;
    }

    /** Output entry name: the migrated one, or the original on collision (reported). */
    String entryName(String name) {
        String renamed = DatapackSchemaRewriter.renamedEntry(name);
        if (renamed == null) return name;
        if (existingNames.contains(renamed)) {
            report.unsupported(name, "not renamed to " + renamed + ": target entry already exists");
            return name;
        }
        report.renamed(name, renamed);
        return renamed;
    }

    byte[] content(String originalName, byte[] data) {
        return DatapackSchemaRewriter.rewriteJson(originalName, data, report);
    }
}

package net.neoforged.forgestuck.compat;

import java.util.Objects;

/**
 * Lightweight descriptor for a legacy Forge mod candidate detected in a JAR.
 */
public final class LegacyModDescriptor {
    private final String modId;
    private final String displayName;
    private final String fileName;
    private final String entryName;

    private LegacyModDescriptor(String modId, String displayName, String fileName, String entryName) {
        this.modId = modId;
        this.displayName = displayName;
        this.fileName = fileName;
        this.entryName = entryName;
    }

    public String modId() {
        return modId;
    }

    public String displayName() {
        return displayName;
    }

    public String fileName() {
        return fileName;
    }

    public String entryName() {
        return entryName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String modId = "unknown";
        private String displayName = "unknown";
        private String fileName = "unknown";
        private String entryName = "META-INF/mods.toml";

        public Builder modId(String modId) {
            this.modId = Objects.requireNonNullElse(modId, "unknown");
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = Objects.requireNonNullElse(displayName, "unknown");
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = Objects.requireNonNullElse(fileName, "unknown");
            return this;
        }

        public Builder entryName(String entryName) {
            this.entryName = Objects.requireNonNullElse(entryName, "META-INF/mods.toml");
            return this;
        }

        public LegacyModDescriptor build() {
            return new LegacyModDescriptor(modId, displayName, fileName, entryName);
        }
    }
}

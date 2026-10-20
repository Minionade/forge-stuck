package net.neoforged.forgestuck.compat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

public final class LegacyVertexFormatShim {
    private LegacyVertexFormatShim() {}

    public static VertexFormat create(ImmutableMap<String, VertexFormatElement> legacyElements) {
        VertexFormat.Builder builder = VertexFormat.builder();
        legacyElements.forEach((name, element) -> {
            if (name.equalsIgnoreCase("padding")) builder.padding(1);
            else builder.add(name, element);
        });
        return builder.build();
    }

    public static ImmutableList<VertexFormatElement> getElements(VertexFormat format) {
        return ImmutableList.copyOf(format.getElements());
    }

    public static VertexFormatElement paddingElementPlaceholder() {
        return VertexFormatElement.UV;
    }
}

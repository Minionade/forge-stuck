package net.neoforged.forgestuck.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Adapts Gendustry's allele-ID storage and UI to Forestry's typed allele values. */
public final class LegacyForestryGenetics {
    private static final String CHROMOSOME =
            "forestry.api.core.genetics.alleles.IChromosome";
    private static final String KARYOTYPE =
            "forestry.api.core.genetics.alleles.IKaryotype";
    private static final String ALLELE =
            "forestry.api.core.genetics.alleles.Allele";
    private static final String SPECIES_ITEM = "thedarkcolour.gendustry.item.SpeciesTypeItem";

    private LegacyForestryGenetics() {}

    public static String encodeAllele(Object allele, Object chromosome) {
        Object value = invoke(allele, ALLELE, "value");
        Codec<Object> codec = valueCodec(chromosome);
        JsonObject encoded = new JsonObject();
        encoded.add("value", codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow());
        encoded.addProperty("dominant", (Boolean) invoke(allele, ALLELE, "dominant"));
        return encoded.toString();
    }

    public static Object geneSampleChromosome(ItemStack stack) {
        CompoundTag tag = LegacyItemStackShim.getTag(stack);
        if (tag == null || !tag.contains("chromosome")) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("chromosome"));
        if (id == null) return null;
        Object speciesType = speciesType(stack);
        if (speciesType == null) return null;
        Object karyotype = invoke(speciesType,
                "forestry.api.core.genetics.ISpeciesType", "getKaryotype");
        return invoke(karyotype, KARYOTYPE, "getChromosome", ResourceLocation.class, id);
    }

    public static Object geneSampleAllele(ItemStack stack) {
        CompoundTag tag = LegacyItemStackShim.getTag(stack);
        Object chromosome = geneSampleChromosome(stack);
        if (tag == null || chromosome == null || !tag.contains("allele")) return null;
        return decodeAllele(chromosome, tag.getString("allele"));
    }

    public static void addTemplateAlleles(ItemStack stack, Map<?, ?> alleles) {
        CompoundTag alleleTag = LegacyItemStackShim.getOrCreateTag(stack).getCompound("alleles");
        for (Map.Entry<?, ?> entry : alleles.entrySet()) {
            Object chromosome = entry.getKey();
            ResourceLocation id = (ResourceLocation) invoke(chromosome, CHROMOSOME, "id");
            alleleTag.putString(id.toString(), encodeAllele(entry.getValue(), chromosome));
        }
        LegacyItemStackShim.getOrCreateTag(stack).put("alleles", alleleTag);
    }

    public static Map<Object, Object> templateAlleles(ItemStack stack) {
        Map<Object, Object> result = new HashMap<>();
        CompoundTag root = LegacyItemStackShim.getTag(stack);
        Object speciesType = speciesType(stack);
        if (root == null || speciesType == null) return result;
        CompoundTag encodedAlleles = root.getCompound("alleles");
        Object karyotype = invoke(speciesType,
                "forestry.api.core.genetics.ISpeciesType", "getKaryotype");
        for (String key : encodedAlleles.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null) continue;
            Object chromosome = invoke(karyotype, KARYOTYPE, "getChromosome", ResourceLocation.class, id);
            if (chromosome != null) {
                result.put(chromosome, decodeAllele(chromosome, encodedAlleles.getString(key)));
            }
        }
        return result;
    }

    public static List<Object> defaultAlleles(Object karyotype, Object chromosome) {
        Object allele = invoke(karyotype, KARYOTYPE, "getDefaultAllele",
                chromosomeType(karyotype), chromosome);
        return allele == null ? List.of() : List.of(allele);
    }

    public static MutableComponent chromosomeName(Object chromosome) {
        String key = (String) invoke(chromosome, CHROMOSOME, "chromosomeTranslationKey");
        return Component.translatable(key);
    }

    public static MutableComponent alleleName(Object chromosome, Object allele) {
        Object value = invoke(allele, ALLELE, "value");
        String key = (String) invoke(chromosome, CHROMOSOME, "translationKey",
                Object.class, value);
        return Component.translatable(key);
    }

    public static List<?> featureBlocks(Object group) {
        Object blocks = invoke(group, "forestry.core.platform.registration.FeatureBlockGroup", "getList");
        return (List<?>) blocks;
    }

    private static Object speciesType(ItemStack stack) {
        try {
            Class<?> speciesItem = Class.forName(SPECIES_ITEM, true, stack.getItem().getClass().getClassLoader());
            return speciesItem.getMethod("getSpeciesType", ItemStack.class).invoke(null, stack);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to read Gendustry species type", e);
        }
    }

    private static Object decodeAllele(Object chromosome, String encoded) {
        JsonElement json = JsonParser.parseString(encoded);
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Expected encoded typed Forestry allele object");
        }
        JsonObject object = json.getAsJsonObject();
        if (!object.has("value") || !object.has("dominant")) {
            throw new IllegalArgumentException("Typed Forestry allele is missing value or dominance");
        }
        Object value = valueCodec(chromosome).parse(JsonOps.INSTANCE, object.get("value")).getOrThrow();
        boolean dominant = object.get("dominant").getAsBoolean();
        try {
            return Class.forName(ALLELE, true, chromosome.getClass().getClassLoader())
                    .getMethod("of", Object.class, boolean.class)
                    .invoke(null, value, dominant);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to construct Forestry allele value", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Codec<Object> valueCodec(Object chromosome) {
        return (Codec<Object>) invoke(chromosome, CHROMOSOME, "valueCodec");
    }

    private static Object invoke(Object receiver, String api, String name) {
        return invoke(receiver, api, name, new Class<?>[0]);
    }

    private static Class<?> chromosomeType(Object karyotype) {
        try {
            return Class.forName(CHROMOSOME, true, karyotype.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Forestry chromosome API is unavailable", e);
        }
    }

    private static Object invoke(Object receiver, String api, String name, Class<?> parameter, Object argument) {
        return invoke(receiver, api, name, new Class<?>[] {parameter}, argument);
    }

    private static Object invoke(Object receiver, String api, String name, Class<?>[] parameters, Object... arguments) {
        try {
            Method method = null;
            for (Method candidate : receiver.getClass().getMethods()) {
                if (!candidate.getName().equals(name) || candidate.getParameterCount() != parameters.length) continue;
                Class<?>[] candidateParameters = candidate.getParameterTypes();
                boolean matches = true;
                for (int i = 0; i < parameters.length; i++) {
                    if (!candidateParameters[i].getName().equals(parameters[i].getName())
                            && (arguments[i] == null || !candidateParameters[i].isInstance(arguments[i]))) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    method = candidate;
                    break;
                }
            }
            if (method == null) throw new NoSuchMethodException(api + "." + name);
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Forestry API call failed: " + api + "." + name, cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to invoke Forestry API: " + api + "." + name, e);
        }
    }
}

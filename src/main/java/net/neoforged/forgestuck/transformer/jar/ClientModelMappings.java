package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

final class ClientModelMappings {
    private static final String OLD = "net/minecraftforge/client/";
    private static final String NEW = "net/neoforged/neoforge/client/";
    private static final String GROUP = "Lnet/minecraftforge/client/RenderTypeGroup;";
    private static final String TRANSFORMER = "Lnet/minecraftforge/client/model/IQuadTransformer;";
    private static final String BUILDER = "net/minecraftforge/client/model/CompositeModel$Baked$Builder";
    private static final String BAKED_BUILDER = "L" + BUILDER + ";";

    private ClientModelMappings() {}

    static List<ForgeOwnerMappings.Entry> entries() {
        return List.of(
                entry("ChunkRenderTypeSet", Set.of(
                        "iterator()Ljava/util/Iterator;",
                        "of([Lnet/minecraft/client/renderer/RenderType;)"
                                + "Lnet/minecraftforge/client/ChunkRenderTypeSet;",
                        "union([Lnet/minecraftforge/client/ChunkRenderTypeSet;)"
                                + "Lnet/minecraftforge/client/ChunkRenderTypeSet;")),
                entry("RenderTypeGroup", Set.of(
                        "EMPTY" + GROUP,
                        "<init>(Lnet/minecraft/client/renderer/RenderType;"
                                + "Lnet/minecraft/client/renderer/RenderType;)V")),
                entry("model/geometry/IGeometryBakingContext", Set.of(
                        "getMaterial(Ljava/lang/String;)Lnet/minecraft/client/resources/model/Material;",
                        "getModelName()Ljava/lang/String;",
                        "getRenderType(Lnet/minecraft/resources/ResourceLocation;)" + GROUP,
                        "getRenderTypeHint()Lnet/minecraft/resources/ResourceLocation;",
                        "getRootTransform()Lcom/mojang/math/Transformation;",
                        "getTransforms()Lnet/minecraft/client/renderer/block/model/ItemTransforms;",
                        "hasMaterial(Ljava/lang/String;)Z",
                        "isComponentVisible(Ljava/lang/String;Z)Z",
                        "isGui3d()Z",
                        "useAmbientOcclusion()Z",
                        "useBlockLight()Z")),
                entry("model/geometry/IGeometryLoader", Set.of()),
                entry("model/geometry/IUnbakedGeometry", Set.of("getConfigurableComponentNames()Ljava/util/Set;")),
                entry("model/BakedModelWrapper", Set.of(
                        "<init>(Lnet/minecraft/client/resources/model/BakedModel;)V")),
                entry("model/geometry/BlockGeometryBakingContext", Set.of(
                        "ownerLnet/minecraft/client/renderer/block/model/BlockModel;")),
                entry("model/geometry/SimpleUnbakedGeometry", Set.of("<init>()V")),
                entry("model/IQuadTransformer", Set.of(
                        "andThen(" + TRANSFORMER + ")" + TRANSFORMER,
                        "COLORI",
                        "process(Ljava/util/List;)Ljava/util/List;",
                        "processInPlace(Ljava/util/List;)V",
                        "processInPlace(Lnet/minecraft/client/renderer/block/model/BakedQuad;)V",
                        "STRIDEI")),
                entry("model/QuadTransformers", Set.of(
                        "applying(Lcom/mojang/math/Transformation;)" + TRANSFORMER,
                        "applyingLightmap(I)" + TRANSFORMER,
                        "empty()" + TRANSFORMER,
                        "settingEmissivity(I)" + TRANSFORMER)),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/client/model/pipeline/QuadBakingVertexConsumer",
                        "net/neoforged/forgestuck/compat/LegacyQuadBakingVertexConsumer$Adapter",
                        Set.of(
                        "<init>(Ljava/util/function/Consumer;)V",
                        "setDirection(Lnet/minecraft/core/Direction;)V",
                        "setHasAmbientOcclusion(Z)V",
                        "setShade(Z)V",
                        "setSprite(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V",
                        "setTintIndex(I)V")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/client/model/pipeline/QuadBakingVertexConsumer$Buffered",
                        "net/neoforged/forgestuck/compat/LegacyQuadBakingVertexConsumer$Buffered",
                        Set.of(
                                "<init>()V",
                                "getQuad()Lnet/minecraft/client/renderer/block/model/BakedQuad;",
                                "setHasAmbientOcclusion(Z)V",
                                "setShade(Z)V",
                                "setSprite(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V",
                                "setTintIndex(I)V")),
                entry("model/CompositeModel$Baked", Set.of(
                        "builder(Lnet/minecraftforge/client/model/geometry/IGeometryBakingContext;"
                                + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
                                + "Lnet/minecraft/client/renderer/block/model/ItemOverrides;"
                                + "Lnet/minecraft/client/renderer/block/model/ItemTransforms;)"
                                + "Lnet/minecraftforge/client/model/CompositeModel$Baked$Builder;",
                        "builder(ZZZLnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
                                + "Lnet/minecraft/client/renderer/block/model/ItemOverrides;"
                                + "Lnet/minecraft/client/renderer/block/model/ItemTransforms;)"
                                + "Lnet/minecraftforge/client/model/CompositeModel$Baked$Builder;")),
                entry("model/CompositeModel$Baked$Builder", Set.of(
                        "addQuads(" + GROUP + "Ljava/util/Collection;)" + BAKED_BUILDER,
                        "build()Lnet/minecraft/client/resources/model/BakedModel;",
                        "setParticle(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)" + BAKED_BUILDER)),
                entry("model/DynamicFluidContainerModel", Set.of(
                        "getLayerRenderTypes(Z)" + GROUP)),
                entry("model/IModelBuilder", Set.of(
                        "of(ZZZLnet/minecraft/client/renderer/block/model/ItemTransforms;"
                                + "Lnet/minecraft/client/renderer/block/model/ItemOverrides;"
                                + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;" + GROUP + ")"
                                + "Lnet/minecraftforge/client/model/IModelBuilder;",
                        "addCulledFace(Lnet/minecraft/core/Direction;"
                                + "Lnet/minecraft/client/renderer/block/model/BakedQuad;)"
                                + "Lnet/minecraftforge/client/model/IModelBuilder;",
                        "addUnculledFace(Lnet/minecraft/client/renderer/block/model/BakedQuad;)"
                                + "Lnet/minecraftforge/client/model/IModelBuilder;",
                        "build()Lnet/minecraft/client/resources/model/BakedModel;")),
                entry("model/geometry/StandaloneGeometryBakingContext", Set.of(
                        "builder(Lnet/minecraftforge/client/model/geometry/IGeometryBakingContext;)"
                                + "Lnet/minecraftforge/client/model/geometry/StandaloneGeometryBakingContext$Builder;")),
                entry("model/geometry/StandaloneGeometryBakingContext$Builder", Set.of(
                        "build(Lnet/minecraft/resources/ResourceLocation;)"
                                + "Lnet/minecraftforge/client/model/geometry/StandaloneGeometryBakingContext;",
                        "withGui3d(Z)Lnet/minecraftforge/client/model/geometry/StandaloneGeometryBakingContext$Builder;",
                        "withUseBlockLight(Z)Lnet/minecraftforge/client/model/geometry/StandaloneGeometryBakingContext$Builder;")),
                entry("model/geometry/UnbakedGeometryHelper", Set.of(
                        "applyRootTransform(Lnet/minecraft/client/resources/model/ModelState;"
                                + "Lcom/mojang/math/Transformation;)"
                                + "Lnet/minecraftforge/client/model/IQuadTransformer;",
                        "composeRootTransformIntoModelState(Lnet/minecraft/client/resources/model/ModelState;"
                                + "Lcom/mojang/math/Transformation;)"
                                + "Lnet/minecraft/client/resources/model/ModelState;")),
                entry("model/pipeline/TransformingVertexPipeline", Set.of(
                        "<init>(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/math/Transformation;)V")),
                entry("model/SimpleModelState", Set.of(
                        "<init>(Lcom/mojang/math/Transformation;)V",
                        "<init>(Lcom/mojang/math/Transformation;Z)V")),
                entry("extensions/common/IClientItemExtensions", Set.of(
                        "getFont(Lnet/minecraft/world/item/ItemStack;"
                                + "Lnet/minecraftforge/client/extensions/common/IClientItemExtensions$FontContext;)"
                                + "Lnet/minecraft/client/gui/Font;",
                        "of(Lnet/minecraft/world/item/ItemStack;)"
                                + "Lnet/minecraftforge/client/extensions/common/IClientItemExtensions;")),
                entry("extensions/common/IClientItemExtensions$FontContext", Set.of(
                        "TOOLTIPLnet/minecraftforge/client/extensions/common/IClientItemExtensions$FontContext;")),
                entry("extensions/common/IClientMobEffectExtensions", Set.of(
                        "isVisibleInGui(Lnet/minecraft/world/effect/MobEffectInstance;)Z",
                        "of(Lnet/minecraft/world/effect/MobEffectInstance;)"
                                + "Lnet/minecraftforge/client/extensions/common/IClientMobEffectExtensions;")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/client/gui/overlay/ForgeGui",
                        "net/minecraft/client/gui/Gui",
                        Set.of("leftHeightI")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/client/ForgeHooksClient",
                        "net/neoforged/neoforge/client/ClientHooks",
                        Set.of(
                                "fillNormal([ILnet/minecraft/core/Direction;)V",
                                "getArmorModel(Lnet/minecraft/world/entity/LivingEntity;"
                                        + "Lnet/minecraft/world/item/ItemStack;"
                                        + "Lnet/minecraft/world/entity/EquipmentSlot;"
                                        + "Lnet/minecraft/client/model/HumanoidModel;)"
                                        + "Lnet/minecraft/client/model/Model;",
                                "getBlockMaterial(Lnet/minecraft/resources/ResourceLocation;)"
                                        + "Lnet/minecraft/client/resources/model/Material;",
                                "handleCameraTransforms(Lcom/mojang/blaze3d/vertex/PoseStack;"
                                        + "Lnet/minecraft/client/resources/model/BakedModel;"
                                        + "Lnet/minecraft/world/item/ItemDisplayContext;Z)"
                                        + "Lnet/minecraft/client/resources/model/BakedModel;")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/client/model/data/ModelDataManager",
                        "net/neoforged/neoforge/client/model/data/ModelDataManager",
                        Set.of("requestRefresh(Lnet/minecraft/world/level/block/entity/BlockEntity;)V")));
    }

    private static ForgeOwnerMappings.Entry entry(String suffix, Set<String> members) {
        return new ForgeOwnerMappings.Entry(OLD + suffix, NEW + suffix, members);
    }
}

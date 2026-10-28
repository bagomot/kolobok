package ru.bagomot.kolobok.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.entity.Outfit;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The outfits Kolobok wears that are vanilla items, each its own little model: the шелом with its
 * бармица, the ушанка, one лапоть (drawn twice) and the sunglasses. His own hats are item models,
 * drawn by {@link KolobokOutfitLayer} from the head item.
 *
 * <p>Hats are placed from the model's {@code hat} anchor, the top of the ball, with y going down into
 * him; across the top he is 6 pixels wide, 8 a pixel lower and 10 from three pixels down. The glasses
 * hang off {@code body}, in front of the face, and roll with it. A лапоть stands on the ground under
 * one half of him.
 *
 * <p>{@code tools/outfits/GenOutfits.java} draws the textures; it follows the box layouts here.
 */
public final class OutfitModels {

    public static final ModelLayerLocation HELMET = layer("helmet");
    public static final ModelLayerLocation USHANKA = layer("ushanka");
    public static final ModelLayerLocation LAPOT = layer("lapot");
    public static final ModelLayerLocation GLASSES = layer("glasses");

    /** Every layer with its definition, for the loaders to register. */
    public static final Map<ModelLayerLocation, Supplier<LayerDefinition>> LAYERS = Map.of(
            HELMET, OutfitModels::helmet,
            USHANKA, OutfitModels::ushanka,
            LAPOT, OutfitModels::lapot,
            GLASSES, OutfitModels::glasses);

    private static final Map<Outfit.Material, Identifier> HELMETS = new EnumMap<>(Outfit.Material.class);
    private static final Map<Outfit.Material, Identifier> LAPTI = new EnumMap<>(Outfit.Material.class);

    static {
        for (Outfit.Material material : Outfit.Material.values()) {
            HELMETS.put(material, texture("helmet_" + material.serializedName()));
            LAPTI.put(material, texture("lapti_" + material.serializedName()));
        }
    }

    /** Fur in grey, for the dye's colour to tint; the wheat-ear badge is drawn untinted from it. */
    public static final Identifier USHANKA_TEXTURE = texture("ushanka");
    /** Woven bast in grey, for dyed leather. Undyed leather boots are plain bast, {@code lapti_leather}. */
    public static final Identifier LAPTI_DYED_TEXTURE = texture("lapti_dyed");
    public static final Identifier GLASSES_TEXTURE = texture("glasses");

    /** How high the лапти lift him: two pixels of лапоть and a pixel of air. */
    public static final float LAPTI_LIFT = 3.0F;

    private OutfitModels() {
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Kolobok.id("kolobok"), name);
    }

    private static Identifier texture(String name) {
        return Kolobok.id("textures/entity/kolobok/outfit/" + name + ".png");
    }

    public static Identifier helmetTexture(Outfit.Material material) {
        return HELMETS.get(material);
    }

    public static Identifier laptiTexture(Outfit.Material material) {
        return LAPTI.get(material);
    }

    /**
     * Богатырский шелом: a band pulled down to the brows, a tall dome narrowing in four steep steps to
     * a spike, and a nose guard between the eyes. The бармица, the mail hanging from the band at the sides and
     * back, is its own part: only the chainmail one has it.
     */
    static LayerDefinition helmet() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("shelom", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.0F, 1.0F, -5.0F, 10.0F, 1.0F, 10.0F, new CubeDeformation(0.3F, 0.5F, 0.3F))
                        .texOffs(0, 11).addBox(-4.5F, -1.0F, -4.5F, 9.0F, 1.5F, 9.0F)
                        .texOffs(0, 22).addBox(-3.5F, -2.5F, -3.5F, 7.0F, 1.5F, 7.0F)
                        .texOffs(28, 22).addBox(-2.5F, -4.0F, -2.5F, 5.0F, 1.5F, 5.0F)
                        .texOffs(48, 22).addBox(-1.5F, -5.0F, -1.5F, 3.0F, 1.0F, 3.0F)
                        .texOffs(40, 0).addBox(-0.5F, -7.0F, -0.5F, 1.0F, 2.0F, 1.0F)
                        .texOffs(44, 0).addBox(-0.5F, 2.5F, -6.0F, 1.0F, 2.0F, 1.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("aventail", CubeListBuilder.create()
                        .texOffs(0, 32).addBox(-5.5F, 2.5F, 5.0F, 11.0F, 4.0F, 1.0F)
                        .texOffs(0, 37).addBox(5.0F, 2.5F, -3.0F, 1.0F, 4.0F, 8.0F)
                        .texOffs(18, 37).addBox(-6.0F, 2.5F, -3.0F, 1.0F, 4.0F, 8.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * Ушанка: a round fur cap in three tiers on a thick turned-up band of sheepskin, the ear flaps
     * hanging down the sides, the front flap folded up, and a little golden wheat ear on it.
     */
    static LayerDefinition ushanka() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("fur", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.5F, 0.5F, -5.5F, 11.0F, 2.0F, 11.0F)
                        .texOffs(0, 13).addBox(-5.0F, -1.5F, -5.0F, 10.0F, 2.0F, 10.0F)
                        .texOffs(0, 25).addBox(-4.0F, -2.5F, -4.0F, 8.0F, 1.0F, 8.0F)
                        .texOffs(32, 25).addBox(-2.5F, -3.5F, -2.5F, 5.0F, 1.0F, 5.0F)
                        .texOffs(44, 0).addBox(5.5F, 1.5F, -2.5F, 1.0F, 6.0F, 5.0F)
                        .texOffs(44, 13).addBox(-6.5F, 1.5F, -2.5F, 1.0F, 6.0F, 5.0F)
                        .texOffs(0, 34).addBox(-4.5F, -1.5F, -6.5F, 9.0F, 3.0F, 1.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("badge", CubeListBuilder.create()
                        .texOffs(20, 34).addBox(-1.0F, -1.3F, -6.55F, 2.0F, 3.0F, 0.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * Лапоть, the woven bast shoe: a sole, the toe half covered and rounded, the heel open behind a
     * low rim. The part's origin is on the ground under the middle of it.
     */
    static LayerDefinition lapot() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("lapot", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, -1.0F, -4.0F, 4.0F, 1.0F, 8.0F)
                        .texOffs(0, 9).addBox(-2.0F, -2.0F, -3.5F, 4.0F, 1.0F, 4.5F)
                        .texOffs(0, 16).addBox(-1.5F, -1.75F, -4.5F, 3.0F, 1.0F, 1.0F)
                        .texOffs(0, 19).addBox(-2.0F, -2.0F, 3.0F, 4.0F, 1.0F, 1.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    /**
     * Sunglasses: one flat plate just in front of the face, over the eyes, a little wider than it.
     */
    static LayerDefinition glasses() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("glasses", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -2.0F, -5.25F, 8.0F, 2.0F, 0.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }
}

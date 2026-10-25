package ru.bagomot.kolobok.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.block.StovePartBlock;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Mood;
import ru.bagomot.kolobok.entity.Outfit;
import ru.bagomot.kolobok.entity.Perk;
import ru.bagomot.kolobok.entity.RideSlot;
import ru.bagomot.kolobok.entity.Variant;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class KolobokRenderer extends MobRenderer<KolobokEntity, KolobokRenderState, KolobokModel> {

    private static final Map<Variant, Identifier> TEXTURES = new EnumMap<>(Variant.class);

    static {
        for (Variant variant : Variant.values()) {
            TEXTURES.put(variant, Kolobok.id("textures/entity/kolobok/" + variant.serializedName() + ".png"));
        }
    }

    private static final float MINI_SCALE = 0.75F;

    /** Bubble wrap width in font pixels. */
    private static final int BUBBLE_WIDTH = 160;
    private static final int LINE_HEIGHT = 10;
    private static final float BUBBLE_SCALE = 0.025F;
    private static final float BUBBLE_FULL_SIZE_DISTANCE = 5.0F;
    private static final int BUBBLE_COLOUR = 0xF0FFF6E0;
    private static final int TEXT_COLOUR = 0xFF3A2410;
    /** Through blocks the bubble shows faded, as a name tag does: from the горнило, out of leaves. */
    private static final int BUBBLE_THROUGH_COLOUR = 0x60FFF6E0;
    private static final int TEXT_THROUGH_COLOUR = 0x903A2410;
    /** Seen through walls only this close, so it is no way of finding him from afar. */
    private static final double BUBBLE_THROUGH_DISTANCE = 16.0;
    private static final int BURNT_TINT = 0xFF8A6A58;
    private static final int GOLDEN_TINT = 0xFFFFE08A;
    private static final int STALE_TINT = 0xFFC9C3B9;
    private static final int TAN_TINT = 0xFFB89A84;
    private static final int SOGGY_TINT = 0xFFE4E2DA;

    public KolobokRenderer(EntityRendererProvider.Context context) {
        super(context, new KolobokModel(context.bakeLayer(KolobokModel.LAYER)), 0.3F);
        addLayer(new KolobokBiteLayer(this));
        addLayer(new KolobokOutfitLayer(this, context.getModelSet()));
    }

    @Override
    public KolobokRenderState createRenderState() {
        return new KolobokRenderState();
    }

    @Override
    public Identifier getTextureLocation(KolobokRenderState state) {
        return TEXTURES.get(state.variant);
    }

    @Override
    public void extractRenderState(KolobokEntity entity, KolobokRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.variant = entity.getVariant();
        state.roll = Mth.lerp(partialTicks, entity.clientRollO, entity.clientRoll);
        state.squash = Mth.lerp(partialTicks, entity.clientSquashO, entity.clientSquash);
        state.hatLag = Mth.lerp(partialTicks, entity.clientHatLagO, entity.clientHatLag);
        state.sitting = entity.isInSittingPose();
        state.face = face(entity);
        state.onPlayer = false;
        state.hidden = false;

        if (entity.getKState() == KState.ON_PLAYER) {
            Player carrier = entity.getCarrier();
            if (carrier != null) {
                placeOnCarrier(entity, carrier, state, partialTicks);
            }
        }

        extractOutfit(entity, state, partialTicks);
        state.burnt = entity.hasFlag(KolobokEntity.FLAG_BURNT);
        state.golden = entity.hasPerk(Perk.ENDER_DRAGON);
        state.stale = entity.hasFlag(KolobokEntity.FLAG_STALE);
        state.tan = entity.hasFlag(KolobokEntity.FLAG_TAN);
        state.soggy = entity.hasFlag(KolobokEntity.FLAG_SOGGY);
        state.bitten = entity.hasFlag(KolobokEntity.FLAG_BITTEN);
        state.cheeks = entity.cheekPuff();
        state.dancing = entity.clientDancing;
        state.bubble = List.of();
        Component text = entity.clientSongLine != null ? entity.clientSongLine : entity.clientLine;
        if (text != null && KConfig.client().bubbles()) {
            Font font = getFont();
            state.bubble = font.split(text, BUBBLE_WIDTH);
            int width = 0;
            for (FormattedCharSequence line : state.bubble) {
                width = Math.max(width, font.width(line));
            }
            state.bubbleWidth = width;
        }
    }

    /**
     * What he wears, and how big the Head has grown. Without the лапти he rolls; in them he walks, and
     * the Head has none.
     */
    private static void extractOutfit(KolobokEntity entity, KolobokRenderState state, float partialTicks) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        Outfit.Hat hat = Outfit.hat(head);
        state.hat = hat == null ? Outfit.Hat.NONE : hat;
        state.helmetTexture = null;
        state.aventail = false;
        state.hatColour = -1;
        if (state.hat == Outfit.Hat.HELMET) {
            Outfit.Material material = Outfit.material(head);
            state.helmetTexture = OutfitModels.helmetTexture(material);
            state.aventail = material == Outfit.Material.CHAINMAIL;
        } else if (state.hat == Outfit.Hat.USHANKA) {
            state.hatColour = DyedItemColor.getOrDefault(head, Outfit.LEATHER_COLOUR);
        }
        state.headScale = Mth.lerp(partialTicks, entity.clientHeadScaleO, entity.clientHeadScale);
        ItemStack feet = entity.getItemBySlot(EquipmentSlot.FEET);
        state.lapti = null;
        state.laptiColour = -1;
        if (!feet.isEmpty() && state.headScale <= 1.0F) {
            Outfit.Material material = Outfit.material(feet);
            if (material == Outfit.Material.LEATHER && feet.has(DataComponents.DYED_COLOR)) {
                state.lapti = OutfitModels.LAPTI_DYED_TEXTURE;
                state.laptiColour = DyedItemColor.getOrDefault(feet, Outfit.LEATHER_COLOUR);
            } else {
                state.lapti = OutfitModels.laptiTexture(material);
            }
        }
        state.glasses = entity.hasFlag(KolobokEntity.FLAG_GLASSES);
    }

    /**
     * Draws him at the carrier's interpolated shoulder or head. The entity itself is moved there by
     * the server every tick, but its synced position lags by a few ticks; the carrier's own position
     * is what the camera follows, so the shoulder has to come from it.
     */
    private static void placeOnCarrier(KolobokEntity entity, Player carrier, KolobokRenderState state, float partialTicks) {
        RideSlot slot = entity.getRideSlot();
        float bodyYaw = Mth.rotLerp(partialTicks, carrier.yBodyRotO, carrier.yBodyRot);
        Vec3 spot = slot.spot(carrier, carrier.getPosition(partialTicks), bodyYaw);
        state.x = spot.x;
        state.y = spot.y;
        state.z = spot.z;
        state.bodyRot = bodyYaw;
        state.yRot = Mth.wrapDegrees(Mth.rotLerp(partialTicks, carrier.yHeadRotO, carrier.yHeadRot) - bodyYaw);
        state.onPlayer = true;
        state.roll = 0.0F;
        state.squash = 0.0F;
        state.hatLag = 0.0F;
        Minecraft minecraft = Minecraft.getInstance();
        state.hidden = minecraft.getCameraEntity() == carrier && minecraft.options.getCameraType().isFirstPerson();
    }

    private static int face(KolobokEntity entity) {
        Mood mood = entity.getMood();
        boolean eyesOpen = mood != Mood.SLEEP && mood != Mood.GOLOVA_SLEEP && mood != Mood.DIZZY;
        if (eyesOpen && entity.clientBlinkTicks > 0) {
            return KolobokModel.FACE_BLINK;
        }
        if (mood == Mood.HAPPY && KolobokClient.isMumbling(entity) && entity.tickCount % 4 < 2) {
            return 2;
        }
        return switch (mood) {
            case HAPPY -> KolobokModel.FACE_HAPPY;
            // The mouth opens on every sung note.
            case SING -> entity.clientMouthTicks > 0 ? 2 : KolobokModel.FACE_HAPPY;
            case SCARED -> 3;
            case DIZZY -> 4;
            case SLEEP -> 5;
            case SLY -> 6;
            case ANGRY -> 7;
            case SAD -> 8;
            case PUFF -> 9;
            case GOLOVA -> 10;
            case GOLOVA_SLEEP -> 11;
        };
    }

    /**
     * No shadow inside the stove's blocks. The game puts a piece of it on the block under the one he
     * stands on too, where a solid block hides it; on top of the stove, over the горнило, it showed
     * on the под, and in the горнило on the floor of the подпечье.
     */
    @Override
    protected void finalizeRenderState(KolobokEntity entity, KolobokRenderState state) {
        super.finalizeRenderState(entity, state);
        if (state.shadowPieces.isEmpty()) {
            return;
        }
        Level level = entity.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // A piece lies on the floor of the block it was cast into; its corner is a whole block away
        // from him, give or take the float.
        state.shadowPieces.removeIf(piece -> level.getBlockState(pos.set(Mth.floor(state.x + piece.relativeX() + 0.5),
                Mth.floor(state.y + piece.relativeY() + 0.5), Mth.floor(state.z + piece.relativeZ() + 0.5)))
                .getBlock() instanceof StovePartBlock);
    }

    /**
     * Burnt dough is a darker crust all over, and so, for a while, is a tan; the dragon's verse gilds
     * it; soggy, he is paler; stale, duller. They add up.
     */
    @Override
    protected int getModelTint(KolobokRenderState state) {
        int tint = super.getModelTint(state);
        if (state.burnt) {
            tint = ARGB.multiply(tint, BURNT_TINT);
        }
        if (state.tan) {
            tint = ARGB.multiply(tint, TAN_TINT);
        }
        if (state.golden) {
            tint = ARGB.multiply(tint, GOLDEN_TINT);
        }
        if (state.soggy) {
            tint = ARGB.multiply(tint, SOGGY_TINT);
        }
        if (state.stale) {
            tint = ARGB.multiply(tint, STALE_TINT);
        }
        return tint;
    }

    @Override
    protected void scale(KolobokRenderState state, PoseStack poseStack) {
        float scale = (state.variant == Variant.MINI ? MINI_SCALE : 1.0F) * state.headScale;
        if (scale != 1.0F) {
            poseStack.scale(scale, scale, scale);
        }
    }

    @Override
    protected float getShadowRadius(KolobokRenderState state) {
        if (state.onPlayer) {
            return 0.0F;
        }
        float radius = super.getShadowRadius(state) * state.headScale;
        return state.variant == Variant.MINI ? radius * MINI_SCALE : radius;
    }

    @Override
    public void submit(KolobokRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.hidden) {
            return;
        }
        super.submit(state, poseStack, collector, camera);
        if (!state.bubble.isEmpty()) {
            submitBubble(state, poseStack, collector, camera);
        }
    }

    /**
     * A light speech bubble above him, the text-display way: one background quad, then the lines on it
     * with polygon offset so they never fight. Close by, a faded copy is drawn through blocks, so his
     * words are not lost when he sits in the горнило or in leaves.
     */
    private static void submitBubble(KolobokRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                     CameraRenderState camera) {
        // The Head is taller than his box.
        float above = state.boundingBoxHeight * state.headScale + 0.45F + (state.nameTag != null ? 0.3F : 0.0F);
        int width = state.bubbleWidth;
        int height = state.bubble.size() * LINE_HEIGHT - 1;
        int light = LightCoordsUtil.FULL_BRIGHT;
        // Name-tag size from five blocks out; closer, it shrinks with the distance so it keeps its size
        // on screen instead of covering the view of whoever he is talking to.
        float distance = (float) Math.sqrt(state.distanceToCameraSq);
        float scale = BUBBLE_SCALE * Mth.clamp(distance / BUBBLE_FULL_SIZE_DISTANCE, 0.45F, 1.0F);

        poseStack.pushPose();
        poseStack.translate(0.0F, above, 0.0F);
        poseStack.rotate(camera.orientation);
        poseStack.scale(scale, -scale, scale);
        if (state.distanceToCameraSq < BUBBLE_THROUGH_DISTANCE * BUBBLE_THROUGH_DISTANCE) {
            submitBubble(state, poseStack, collector, width, height, Font.DisplayMode.SEE_THROUGH,
                    BUBBLE_THROUGH_COLOUR, TEXT_THROUGH_COLOUR, light);
        }
        submitBubble(state, poseStack, collector, width, height, Font.DisplayMode.POLYGON_OFFSET,
                BUBBLE_COLOUR, TEXT_COLOUR, light);
        poseStack.popPose();
    }

    private static void submitBubble(KolobokRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                     int width, int height, Font.DisplayMode mode, int bubbleColour, int textColour,
                                     int light) {
        float left = -width / 2.0F;
        float top = -height;
        collector.submitTextBackground(poseStack, left - 3.0F, top - 2.0F, left + width + 2.0F, 2.0F, bubbleColour, mode, light);
        // The tail: two steps narrowing toward him.
        collector.submitTextBackground(poseStack, -3.0F, 2.0F, 2.0F, 4.0F, bubbleColour, mode, light);
        collector.submitTextBackground(poseStack, -1.0F, 4.0F, 0.0F, 6.0F, bubbleColour, mode, light);
        float y = top;
        for (FormattedCharSequence line : state.bubble) {
            float x = -Minecraft.getInstance().font.width(line) / 2.0F;
            collector.submitText(poseStack, x, y, line, false, mode, light, textColour, 0, 0);
            y += LINE_HEIGHT;
        }
    }
}

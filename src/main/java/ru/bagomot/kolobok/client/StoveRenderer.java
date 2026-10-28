package ru.bagomot.kolobok.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.block.StoveBlockEntity;
import ru.bagomot.kolobok.block.StovePart;
import ru.bagomot.kolobok.block.StovePattern;
import ru.bagomot.kolobok.entity.Variant;
import ru.bagomot.kolobok.item.DoughItem;
import ru.bagomot.kolobok.registry.KItems;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Draws what the stove holds, which the block model cannot know: the firewood in the подпечье, in
 * the wood that was put there and as much of it as there is, and in the горнило the raw Kolobok
 * slowly browning as he bakes, or the food lying on the под.
 *
 * <p>Pointed at, the подпечье tells how much wood is in it and, while the stove burns, how long it
 * will last; the горнило tells how long until what is in it is done.
 *
 * <p>Everything is placed in the pixels {@link StovePattern} and tools/stove share, for a stove facing
 * north, then turned with it.
 */
public final class StoveRenderer implements BlockEntityRenderer<StoveBlockEntity, StoveRenderer.State> {

    /** With Jade installed the labels move into its tooltip; see {@code compat.jade.StoveProvider}. */
    private static final boolean JADE = Kolobok.platform().isModLoaded("jade");
    private static final Identifier DOUGH = Kolobok.id("textures/entity/kolobok/dough.png");
    /** Full of baking, the pale dough takes on the crust of what it will be. */
    private static final Map<Variant, Integer> CRUST = new EnumMap<>(Variant.class);

    static {
        CRUST.put(Variant.CLASSIC, 0xFFE3A35E);
        CRUST.put(Variant.MINI, 0xFFE6AA62);
        CRUST.put(Variant.BORODINSKY, 0xFF6E4630);
        CRUST.put(Variant.GOLDEN, 0xFFFFD45A);
        CRUST.put(Variant.MOON, 0xFFD4D2E6);
    }

    private static final int BURNT_CRUST = 0xFF8A5A3C;
    private static final float MINI_SCALE = 0.75F;

    /**
     * Logs lie ends out, two rows in the arch, three below and two on top: the middle of each, in
     * pixels across and up. A full stack shows all five.
     */
    private static final float LOG_SIZE = 3.4F;
    private static final float[][] LOGS = {{8.0F, 3.7F}, {4.6F, 3.7F}, {11.4F, 3.7F}, {6.3F, 3.7F + LOG_SIZE}, {9.7F, 3.7F + LOG_SIZE}};
    /** Food lies on the под in front of the coals, two rows of three. */
    private static final float[][] FOOD = {{8.0F, 6.0F}, {4.5F, 6.0F}, {11.5F, 6.0F}, {6.2F, 11.0F}, {9.8F, 11.0F}, {8.0F, 13.5F}};
    /** The labels float this far out of the front face, in pixels… */
    private static final double LABEL_OUT = 2.0;
    /** …centred at these heights: between the подпечье and the устье, and just over the устье's vault. */
    private static final double LOGS_LABEL_Y = (StovePattern.PODPECHYE_Y1 + StovePattern.GORNILO_FLOOR) / 2.0;
    private static final double OVEN_LABEL_Y = StovePattern.GORNILO_FLOOR + StovePattern.GORNILO_HEIGHT + 1.5;
    /** Name-tag size from this far out; closer, the label shrinks with the distance and keeps its size on screen. */
    private static final float LABEL_SCALE = 0.025F;
    private static final float LABEL_FULL_SIZE_DISTANCE = 5.0F;
    private static final int LABEL_LINE_HEIGHT = 10;
    private static final int LABEL_BACKGROUND = 0x60000000;
    private static final int LABEL_COLOUR = 0xFFFFFFFF;

    private final ItemModelResolver items;
    private final KolobokModel model;
    /**
     * The raw Kolobok's pose, the same for every stove: the model only reads it, and his crust is a
     * tint. No face yet: he opens his eyes once he is baked.
     */
    private final KolobokRenderState dough = new KolobokRenderState();

    public StoveRenderer(BlockEntityRendererProvider.Context context) {
        items = context.itemModelResolver();
        model = new KolobokModel(context.bakeLayer(KolobokModel.LAYER));
        dough.face = -1;
    }

    /**
     * Made afresh for every stove drawn, every frame, as vanilla does with block entities: an empty
     * stove gets nothing but this, the logs and the food only as many pieces as are shown.
     */
    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        List<ItemStackRenderState> logs = List.of();
        List<ItemStackRenderState> food = List.of();
        /** The raw Kolobok baking, or {@code null}. */
        @Nullable Variant bread;
        boolean burnt;
        float baked;
        /** What the part pointed at tells, a line each, where, from the mouth's corner, and how big. */
        List<Component> label = List.of();
        Vec3 labelAt = Vec3.ZERO;
        float labelScale;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StoveBlockEntity stove, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(stove, state, partialTicks, cameraPosition, breakProgress);
        state.facing = stove.facing();
        int seed = (int) stove.getBlockPos().asLong();

        ItemStack logs = stove.logs();
        if (!logs.isEmpty()) {
            int pieces = Mth.clamp(Mth.ceil(logs.getCount() * LOGS.length / (float) logs.getMaxStackSize()), 1, LOGS.length);
            state.logs = pieces(logs, pieces, stove, seed);
        }

        ItemStack oven = stove.oven();
        if (oven.is(KItems.RAW_KOLOBOK.get())) {
            state.bread = DoughItem.variant(oven);
            state.burnt = stove.isTooHot();
            state.baked = stove.progress(partialTicks);
        } else if (!oven.isEmpty()) {
            state.food = pieces(oven, Math.min(oven.getCount(), FOOD.length), stove, seed + 16);
        }
        extractLabel(stove, state, partialTicks, cameraPosition);
    }

    private List<ItemStackRenderState> pieces(ItemStack stack, int count, StoveBlockEntity stove, int seed) {
        List<ItemStackRenderState> pieces = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ItemStackRenderState piece = new ItemStackRenderState();
            items.updateForTopItem(piece, stack, ItemDisplayContext.FIXED, stove.getLevel(), null, seed + i);
            pieces.add(piece);
        }
        return pieces;
    }

    /**
     * The подпечье pointed at: «Дрова: 12/64», and while the fire burns, how long the wood will last.
     * The горнило pointed at, with something in it: how long until it is done. None with Jade.
     */
    private static void extractLabel(StoveBlockEntity stove, State state, float partialTicks, Vec3 cameraPosition) {
        state.label = List.of();
        if (JADE || !(Minecraft.getInstance().hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos mouth = stove.getBlockPos();
        Direction facing = stove.facing();
        boolean lower = hit.getBlockPos().equals(mouth)
                && (hit.getLocation().y - mouth.getY()) * 16.0 < StovePattern.GORNILO_FLOOR - 1;
        boolean upper = !lower && (hit.getBlockPos().equals(mouth)
                || hit.getBlockPos().equals(StovePattern.posOf(mouth, facing, StovePart.UPPER_FRONT_MIDDLE)));
        Component oven = upper ? stove.ovenLabel(partialTicks) : null;
        if (lower) {
            Component fire = stove.fireLabel(partialTicks);
            state.label = fire != null ? List.of(stove.woodLabel(), fire) : List.of(stove.woodLabel());
            state.labelAt = StovePattern.local(BlockPos.ZERO, facing, 8, LOGS_LABEL_Y, -LABEL_OUT);
        } else if (oven != null) {
            state.label = List.of(oven);
            state.labelAt = StovePattern.local(BlockPos.ZERO, facing, 8, OVEN_LABEL_Y, -LABEL_OUT);
        } else {
            return;
        }
        float distance = (float) cameraPosition.distanceTo(state.labelAt.add(Vec3.atLowerCornerOf(mouth)));
        state.labelScale = LABEL_SCALE * Mth.clamp(distance / LABEL_FULL_SIZE_DISTANCE, 0.3F, 1.0F);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // Into the frame of a north-facing stove, as the blockstate turns the model.
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.rotate(Axis.YP.rotationDegrees(-modelRotation(state.facing)));
        poseStack.translate(-0.5F, 0.0F, -0.5F);

        float depth = StovePattern.PODPECHYE_DEPTH - 2.0F;
        for (int i = 0; i < state.logs.size(); i++) {
            poseStack.pushPose();
            poseStack.translate(LOGS[i][0] / 16.0F, LOGS[i][1] / 16.0F, (1.0F + depth / 2) / 16.0F);
            // The item is an upright half-size block; lying down, its rings face out of the arch.
            poseStack.rotate(Axis.XP.rotationDegrees(90.0F));
            poseStack.scale(LOG_SIZE / 8.0F, depth / 8.0F, LOG_SIZE / 8.0F);
            state.logs.get(i).submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        for (int i = 0; i < state.food.size(); i++) {
            poseStack.pushPose();
            poseStack.translate(FOOD[i][0] / 16.0F, (StovePattern.GORNILO_FLOOR + 0.3F) / 16.0F, FOOD[i][1] / 16.0F);
            poseStack.rotate(Axis.XP.rotationDegrees(90.0F));
            poseStack.rotate(Axis.ZP.rotationDegrees(i * 67.0F));
            poseStack.scale(0.32F, 0.32F, 0.32F);
            state.food.get(i).submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        if (state.bread != null) {
            poseStack.pushPose();
            poseStack.translate(0.5F, StovePattern.GORNILO_FLOOR / 16.0F, StovePattern.GORNILO_SPOT / 16.0F);
            if (state.bread == Variant.MINI) {
                poseStack.scale(MINI_SCALE, MINI_SCALE, MINI_SCALE);
            }
            // As the entity renderer does it; the model's face is its -z, out of the mouth.
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0F, -1.501F, 0.0F);
            int crust = state.burnt ? BURNT_CRUST : CRUST.get(state.bread);
            int tint = ARGB.srgbLerp(state.baked * state.baked, 0xFFFFFFFF, crust);
            collector.submitModel(model, dough, poseStack, model.renderType(DOUGH), state.lightCoords,
                    OverlayTexture.NO_OVERLAY, tint, null, 0);
            poseStack.popPose();
        }
        poseStack.popPose();

        if (!state.label.isEmpty()) {
            submitLabel(state, poseStack, collector, camera);
        }
    }

    /**
     * The label, outside the stove's turn: it faces the camera like a name tag, its lines centred on
     * its point, on a dim background.
     */
    private static void submitLabel(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(state.labelAt.x, state.labelAt.y, state.labelAt.z);
        poseStack.rotate(camera.orientation);
        poseStack.scale(state.labelScale, -state.labelScale, state.labelScale);
        float y = -(state.label.size() * LABEL_LINE_HEIGHT - 1) / 2.0F;
        for (Component line : state.label) {
            FormattedCharSequence text = line.getVisualOrderText();
            collector.submitText(poseStack, -font.width(text) / 2.0F, y, text, false, Font.DisplayMode.NORMAL,
                    LightCoordsUtil.FULL_BRIGHT, LABEL_COLOUR, LABEL_BACKGROUND, 0);
            y += LABEL_LINE_HEIGHT;
        }
        poseStack.popPose();
    }

    /** The blockstate's turn for a facing, clockwise from north. */
    private static float modelRotation(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }
}

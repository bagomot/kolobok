package ru.bagomot.kolobok.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A bread crumb on the ground: a scrap of the bread item's texture, as an item's crack particle is a
 * scrap of its texture, and like it turned to the camera; lying flat, it all but vanished from a few
 * blocks off. It does not move, lasts a minute and fades over its last five seconds. Made and added
 * on the client only, so it needs no particle type of its own.
 */
final class CrumbParticle extends SingleQuadParticle {

    private static final int LIFETIME = 1200;
    private static final int FADE = 100;
    private static final ItemStackRenderState SCRATCH = new ItemStackRenderState();

    private final float uo;
    private final float vo;

    CrumbParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z, breadSprite(level));
        quadSize = 0.07F + random.nextFloat() * 0.04F;
        // Resting on the ground, not sunk into it to the middle.
        setPos(x, y + quadSize, z);
        xo = this.x;
        yo = this.y;
        zo = this.z;
        lifetime = LIFETIME;
        gravity = 0.0F;
        hasPhysics = false;
        xd = 0.0;
        yd = 0.0;
        zd = 0.0;
        uo = random.nextFloat() * 3.0F;
        vo = random.nextFloat() * 3.0F;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
    }

    private static TextureAtlasSprite breadSprite(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getItemModelResolver().updateForTopItem(SCRATCH, new ItemStack(Items.BREAD), ItemDisplayContext.GROUND, level, null, 0);
        Material.Baked material = SCRATCH.pickParticleMaterial(level.getRandom());
        return material != null ? material.sprite() : minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS).missingSprite();
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        int left = lifetime - age;
        setAlpha(left < FADE ? left / (float) FADE : 1.0F);
    }

    @Override
    protected float getU0() {
        return sprite.getU((uo + 1.0F) / 4.0F);
    }

    @Override
    protected float getU1() {
        return sprite.getU(uo / 4.0F);
    }

    @Override
    protected float getV0() {
        return sprite.getV(vo / 4.0F);
    }

    @Override
    protected float getV1() {
        return sprite.getV((vo + 1.0F) / 4.0F);
    }

    /** Translucent, for the fade; the bread's texture is in the item atlas. */
    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT_ITEMS;
    }
}

package ru.bagomot.kolobok.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.entity.song.Voice;
import ru.bagomot.kolobok.registry.KItems;

/**
 * What Kolobok makes of the things put on him. Vanilla items stay vanilla and only look different on
 * him: a leather helmet is an ушанка, any other helmet a богатырский шелом of its metal, any boots
 * лапти. The crown, the wreath and the kokoshnik are his own. Tinted glass is sunglasses, kept as a
 * flag rather than in a slot.
 */
public final class Outfit {

    /** Undyed leather: the vanilla default, as on a leather helmet's icon. */
    public static final int LEATHER_COLOUR = 0xFFA06540;

    private Outfit() {
    }

    /** What sits on his head, and what it does to his voice. */
    public enum Hat {
        NONE(1.0F),
        USHANKA(0.92F),
        /** A шелом; the pitch is the metal's, see {@link Material#helmetVoice()}. */
        HELMET(0.7F),
        CROWN(0.85F),
        WREATH(1.1F),
        KOKOSHNIK(1.18F);

        private final float voice;

        Hat(float voice) {
            this.voice = voice;
        }

        /**
         * @return the hat's factor in the voice pitch
         */
        public float voice() {
            return voice;
        }

        /**
         * @return how he sings in it: in a шелом a slow humming bass, in the crown grandly
         */
        public @Nullable Voice manner() {
            return switch (this) {
                case HELMET -> Voice.HELMET;
                case CROWN -> Voice.CROWN;
                default -> null;
            };
        }
    }

    /** What a helmet or boots are made of, from the vanilla equipment asset. */
    public enum Material {
        LEATHER("leather", 0.92F),
        COPPER("copper", 0.71F),
        CHAINMAIL("chainmail", 0.72F),
        IRON("iron", 0.7F),
        GOLD("gold", 0.74F),
        DIAMOND("diamond", 0.72F),
        NETHERITE("netherite", 0.67F);

        private final String name;
        private final float helmetVoice;

        Material(String name, float helmetVoice) {
            this.name = name;
            this.helmetVoice = helmetVoice;
        }

        public String serializedName() {
            return name;
        }

        /**
         * @return the voice pitch in a шелом of this metal: the heavier, the lower
         */
        public float helmetVoice() {
            return helmetVoice;
        }

        /**
         * @return the material of that vanilla asset; another mod's is drawn as iron
         */
        static Material of(ResourceKey<EquipmentAsset> asset) {
            if (asset.equals(EquipmentAssets.LEATHER)) {
                return LEATHER;
            }
            if (asset.equals(EquipmentAssets.COPPER)) {
                return COPPER;
            }
            if (asset.equals(EquipmentAssets.CHAINMAIL)) {
                return CHAINMAIL;
            }
            if (asset.equals(EquipmentAssets.GOLD)) {
                return GOLD;
            }
            if (asset.equals(EquipmentAssets.DIAMOND)) {
                return DIAMOND;
            }
            if (asset.equals(EquipmentAssets.NETHERITE)) {
                return NETHERITE;
            }
            return IRON;
        }
    }

    /**
     * @return what that item is on his head, or {@code null} if he will not wear it: a turtle shell,
     *         a pumpkin, a skull, anything that is not a helmet or one of his own hats
     */
    public static @Nullable Hat hat(ItemStack stack) {
        if (stack.isEmpty()) {
            return Hat.NONE;
        }
        if (stack.is(KItems.CROWN.get())) {
            return Hat.CROWN;
        }
        if (stack.is(KItems.WREATH.get())) {
            return Hat.WREATH;
        }
        if (stack.is(KItems.KOKOSHNIK.get())) {
            return Hat.KOKOSHNIK;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != EquipmentSlot.HEAD || equippable.assetId().isEmpty()) {
            return null;
        }
        ResourceKey<EquipmentAsset> asset = equippable.assetId().get();
        if (asset.equals(EquipmentAssets.TURTLE_SCUTE)) {
            return null;
        }
        return asset.equals(EquipmentAssets.LEATHER) ? Hat.USHANKA : Hat.HELMET;
    }

    /**
     * @return whether he puts that on his feet: any boots, which become лапти
     */
    public static boolean isBoots(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.FEET;
    }

    /**
     * @return what a helmet or boots are made of; boots of another mod without an asset are leather
     */
    public static Material material(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty()) {
            return Material.LEATHER;
        }
        return Material.of(equippable.assetId().get());
    }

    /**
     * @return the head item's factor in his voice pitch
     */
    public static float voice(ItemStack head) {
        Hat hat = hat(head);
        if (hat == null) {
            return 1.0F;
        }
        return hat == Hat.HELMET ? material(head).helmetVoice() : hat.voice();
    }

    /**
     * Voice = dough × outfit × mood, clamped to 0.67–1.33 so the one-octave melody stays inside the
     * game's 0.5–2.0 pitch range.
     *
     * @param hat the head item's factor, see {@link #voice(ItemStack)}
     */
    public static float pitch(Variant variant, float hat, Mood mood) {
        return Math.clamp(variant.voice() * hat * mood.voice(), 0.67F, 1.33F);
    }

    /**
     * @return whether that is a metal helmet, the one he plays the Head from «Руслан и Людмила» in
     */
    public static boolean isMetalHelmet(ItemStack head) {
        return hat(head) == Hat.HELMET && material(head) != Material.LEATHER;
    }
}

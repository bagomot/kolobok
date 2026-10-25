package ru.bagomot.kolobok.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Perk;
import ru.bagomot.kolobok.entity.RideSlot;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kolobok looked at: his dough, what he is doing, his crust (stale, soggy, burnt, tanned, bitten) and
 * how many verses his song has; with Jade's details key held, his perks by name. Jade itself adds his
 * health and his owner.
 */
public enum KolobokProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final Identifier UID = Kolobok.id("kolobok");

    /** The crust's flags, named {@code kolobok.jade.crust.<name>} by {@link #CRUST} in the same order. */
    private static final int[] CRUST_FLAGS = {KolobokEntity.FLAG_STALE, KolobokEntity.FLAG_SOGGY,
            KolobokEntity.FLAG_BURNT, KolobokEntity.FLAG_TAN, KolobokEntity.FLAG_BITTEN};
    public static final List<String> CRUST = List.of("stale", "soggy", "burnt", "tan", "bitten");
    /** Between the items of a list: «, » here, «、» in Chinese. */
    private static final Component SEPARATOR = Component.translatable("kolobok.jade.separator");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof KolobokEntity kolobok)) {
            return;
        }
        tooltip.add(Component.translatable("kolobok.songbook.variant",
                Component.translatable("kolobok.variant." + kolobok.getVariant().serializedName())));
        Component doing = Component.translatable(stateKey(kolobok));
        tooltip.add(kolobok.isSinging()
                ? Component.empty().append(doing).append(" · ").append(Component.translatable("kolobok.jade.singing"))
                : doing);
        List<Component> crust = new ArrayList<>();
        for (int i = 0; i < CRUST_FLAGS.length; i++) {
            if (kolobok.hasFlag(CRUST_FLAGS[i])) {
                crust.add(Component.translatable("kolobok.jade.crust." + CRUST.get(i)));
            }
        }
        if (!crust.isEmpty()) {
            tooltip.add(Component.translatable("kolobok.jade.crust", ComponentUtils.formatList(crust, SEPARATOR)));
        }
        int verses = kolobok.verses().size();
        if (verses > 0) {
            tooltip.add(Component.translatable("kolobok.songbook.verses", verses));
        }
        // The fox's perk is not synced, so on the client his perks are those of his verses.
        List<Perk> perks = kolobok.perks();
        if (accessor.showDetails() && !perks.isEmpty()) {
            List<Component> names = new ArrayList<>();
            for (Perk perk : perks) {
                names.add(Component.translatable(perk.key()));
            }
            tooltip.add(Component.translatable("kolobok.jade.perks", ComponentUtils.formatList(names, SEPARATOR))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * @return the lang key of what he is doing, {@code kolobok.jade.state.<state>}: on a player with
     *         {@code .head} or {@code .shoulder} after it, following and dropping crumbs with
     *         {@code .crumbs}
     */
    public static String stateKey(KolobokEntity kolobok) {
        KState state = kolobok.getKState();
        String key = "kolobok.jade.state." + state.name().toLowerCase(Locale.ROOT);
        if (state == KState.ON_PLAYER) {
            return key + (kolobok.getRideSlot() == RideSlot.HEAD ? ".head" : ".shoulder");
        }
        if (state == KState.FOLLOW && kolobok.hasFlag(KolobokEntity.FLAG_CRUMBS)) {
            return key + ".crumbs";
        }
        return key;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}

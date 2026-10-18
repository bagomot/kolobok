package ru.bagomot.kolobok.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.network.Filterable;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Perk;
import ru.bagomot.kolobok.entity.SongWords;
import ru.bagomot.kolobok.entity.song.SongLine;
import ru.bagomot.kolobok.entity.song.SongText;

import java.util.ArrayList;
import java.util.List;

/**
 * Песенник. Shown to one's own Kolobok, it takes down his whole song with every mob he got away
 * from and what he learned from them; used, it opens as a vanilla book. No screen of its own: the
 * pages are the vanilla written-book component, in translatable text.
 */
public class SongbookItem extends Item {

    /** A sung line may wrap on the narrow page; six of them always fit. */
    private static final int LINES_PER_PAGE = 6;
    private static final int PERKS_PER_PAGE = 3;

    public SongbookItem(Item.Properties properties) {
        super(properties);
    }

    public static Item.Properties properties() {
        return new Item.Properties().stacksTo(1);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("kolobok.songbook.empty"));
            }
            return InteractionResult.PASS;
        }
        player.openItemGui(stack, hand);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }

    /**
     * Writes that Kolobok's song into the songbook, replacing whatever was there.
     */
    public static void write(ItemStack stack, KolobokEntity kolobok) {
        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(page(cover(kolobok)));
        for (Component page : song(kolobok.verses())) {
            pages.add(page(page));
        }
        for (Component page : perks(kolobok.perks())) {
            pages.add(page(page));
        }
        String name = kolobok.getName().getString();
        String title = name.length() > WrittenBookContent.TITLE_MAX_LENGTH
                ? name.substring(0, WrittenBookContent.TITLE_MAX_LENGTH)
                : name;
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(title), name, 0, pages, true));
    }

    private static Component cover(KolobokEntity kolobok) {
        MutableComponent page = Component.empty().append(kolobok.getName().copy().withStyle(ChatFormatting.BOLD));
        page.append("\n").append(Component.translatable("kolobok.songbook.cover").withStyle(ChatFormatting.ITALIC));
        page.append("\n\n").append(Component.translatable("kolobok.songbook.variant",
                Component.translatable("kolobok.variant." + kolobok.getVariant().serializedName())));
        page.append("\n").append(Component.translatable("kolobok.songbook.verses", kolobok.verses().size()));
        if (kolobok.hasPerk(Perk.ENDER_DRAGON)) {
            page.append("\n\n").append(Component.translatable("kolobok.songbook.legend").withStyle(ChatFormatting.GOLD));
        }
        return page;
    }

    /**
     * The whole song, a verse to a page; a long list of mobs goes on over several. Herobrine is
     * never written down.
     */
    private static List<Component> song(List<String> mobs) {
        List<Component> pages = new ArrayList<>();
        for (List<SongLine> verse : SongText.verses(SongText.book(mobs))) {
            for (int from = 0; from < verse.size(); from += LINES_PER_PAGE) {
                MutableComponent page = Component.empty();
                for (int i = from; i < Math.min(verse.size(), from + LINES_PER_PAGE); i++) {
                    if (i > from) {
                        page.append("\n");
                    }
                    page.append(SongWords.line(verse.get(i)));
                }
                pages.add(page);
            }
        }
        return pages;
    }

    private static List<Component> perks(List<Perk> perks) {
        List<Component> pages = new ArrayList<>();
        if (perks.isEmpty()) {
            pages.add(perksHeader().append("\n\n").append(Component.translatable("kolobok.songbook.no_perks")));
            return pages;
        }
        for (int from = 0; from < perks.size(); from += PERKS_PER_PAGE) {
            MutableComponent page = perksHeader();
            for (int i = from; i < Math.min(perks.size(), from + PERKS_PER_PAGE); i++) {
                Perk perk = perks.get(i);
                page.append("\n\n")
                        .append(Component.translatable(perk.key()).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED))
                        .append("\n")
                        .append(Component.translatable(perk.key() + ".desc"));
            }
            pages.add(page);
        }
        return pages;
    }

    private static MutableComponent perksHeader() {
        return Component.empty().append(Component.translatable("kolobok.songbook.perks").withStyle(ChatFormatting.BOLD));
    }

    private static Filterable<Component> page(Component text) {
        return Filterable.passThrough(text);
    }
}

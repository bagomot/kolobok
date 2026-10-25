package ru.bagomot.kolobok.guide;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.WrittenBookContent;
import ru.bagomot.kolobok.Kolobok;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * «Как печь Колобка», the guide: only what a player would hardly find out alone. With Modonomicon
 * installed it is that mod's book ({@code data/kolobok/modonomicon/books/guide}), with the stove
 * shown in 3D; without it, a vanilla written book with the same texts and the stove drawn from block
 * sprites in the text. Both read the same lang keys, so the texts are written once.
 */
public final class GuideBook {

    public static final Identifier ID = Kolobok.id("guide");
    public static final String MODONOMICON = "modonomicon";
    /** Lang key prefix of the guide's texts. */
    public static final String KEY = "book.kolobok.guide.";

    /**
     * The guide's entries in order, each with its pages. An entry's name is
     * {@code book.kolobok.guide.<entry>.name}, a page's text {@code book.kolobok.guide.<entry>.<page>}.
     * Modonomicon's book is generated from this list too.
     */
    public static final List<Entry> ENTRIES = List.of(
            new Entry("kolobok", List.of("intro")),
            new Entry("stove", List.of("build", "layout", "fire")),
            new Entry("baking", List.of("dough", "oven")),
            new Entry("taming", List.of("listen")),
            new Entry("controls", List.of("hands", "things")),
            new Entry("care", List.of("care")),
            new Entry("verses", List.of("verses")),
            new Entry("fox", List.of("fox")),
            new Entry("outfits", List.of("outfits")));

    /** The stove page draws the stove; every other page is just its text. */
    public static final String LAYOUT_PAGE = "stove.layout";

    private static final Identifier BRICKS = Identifier.withDefaultNamespace("block/bricks");
    private static final Identifier FURNACE = Identifier.withDefaultNamespace("block/furnace_front");
    private static final Identifier NOTHING = Identifier.withDefaultNamespace("block/glass");

    public record Entry(String id, List<String> pages) {

        public String nameKey() {
            return KEY + id + ".name";
        }

        public String pageKey(String page) {
            return KEY + id + "." + page;
        }
    }

    private GuideBook() {
    }

    /**
     * @return the guide as this game has it: Modonomicon's book if that mod is installed, the written
     *         book otherwise
     */
    public static ItemStack create() {
        if (Kolobok.platform().isModLoaded(MODONOMICON)) {
            Optional<Item> book = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(MODONOMICON, "modonomicon"));
            DataComponentType<?> bookId = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.fromNamespaceAndPath(MODONOMICON, "book_id"));
            if (book.isPresent() && bookId != null) {
                ItemStack stack = new ItemStack(book.get());
                @SuppressWarnings("unchecked")
                DataComponentType<Identifier> type = (DataComponentType<Identifier>) bookId;
                stack.set(type, ID);
                return stack;
            }
            Kolobok.LOG.warn("Modonomicon is installed but has no book item; the guide is a written book");
        }
        return writtenBook();
    }

    /**
     * @return the guide as a vanilla written book: a cover, the contents with a link to each entry, and
     *         the entries. The pages are translatable, so each player reads them in their own language.
     */
    public static ItemStack writtenBook() {
        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(Filterable.passThrough(Component.empty()
                .append(Component.translatable(KEY + "name").withStyle(ChatFormatting.BOLD))
                .append("\n\n")
                .append(Component.translatable(KEY + "cover"))));
        // The contents: entry names that turn to their first page. Cover and contents come first.
        MutableComponent contents = Component.empty()
                .append(Component.translatable(KEY + "contents").withStyle(ChatFormatting.BOLD))
                .append("\n");
        for (Entry entry : ENTRIES) {
            contents.append("\n").append(Component.translatable(entry.nameKey())
                    .withStyle(style -> style.withClickEvent(new ClickEvent.ChangePage(pageOf(entry)))
                            .withColor(ChatFormatting.DARK_BLUE).withUnderlined(true)));
        }
        pages.add(Filterable.passThrough(contents));
        for (Entry entry : ENTRIES) {
            for (int i = 0; i < entry.pages().size(); i++) {
                String id = entry.id() + "." + entry.pages().get(i);
                MutableComponent text = Component.empty();
                if (i == 0) {
                    text.append(Component.translatable(entry.nameKey()).withStyle(ChatFormatting.BOLD)).append("\n\n");
                }
                text.append(id.equals(LAYOUT_PAGE) ? layout() : Component.translatable(KEY + id));
                pages.add(Filterable.passThrough(text));
            }
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Kolobok"), "Kolobok", 0, pages, true));
        book.set(DataComponents.ITEM_NAME, Component.translatable(KEY + "name"));
        book.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.WRITTEN_BOOK_CONTENT, true));
        return book;
    }

    /**
     * @return the page of the written book an entry starts on, counting from one: after the cover and
     *         the contents
     */
    private static int pageOf(Entry wanted) {
        int page = 3;
        for (Entry entry : ENTRIES) {
            if (entry == wanted) {
                return page;
            }
            page += entry.pages().size();
        }
        throw new IllegalArgumentException(wanted.id());
    }

    /**
     * @return the stove layer by layer, drawn with the blocks' own sprites: bricks, the furnace in the
     *         middle of the front row, the chimney brick over the back right corner
     */
    private static Component layout() {
        MutableComponent page = Component.empty();
        page.append(Component.translatable(KEY + "stove.bottom")).append("\n");
        page.append(row(BRICKS, BRICKS, BRICKS)).append(" ").append(Component.translatable(KEY + "stove.back")).append("\n");
        page.append(row(BRICKS, FURNACE, BRICKS)).append(" ").append(Component.translatable(KEY + "stove.front")).append("\n");
        page.append(Component.translatable(KEY + "stove.middle")).append("\n");
        page.append(row(BRICKS, BRICKS, BRICKS)).append("\n");
        page.append(row(BRICKS, BRICKS, BRICKS)).append("\n");
        page.append(Component.translatable(KEY + "stove.top")).append("\n");
        page.append(row(NOTHING, NOTHING, BRICKS)).append(" ").append(Component.translatable(KEY + "stove.back")).append("\n\n");
        page.append(Component.translatable(KEY + LAYOUT_PAGE));
        return page;
    }

    private static Component row(Identifier... sprites) {
        MutableComponent row = Component.empty();
        for (Identifier sprite : sprites) {
            // A sprite is tinted with the text's colour, and a book's text is black.
            row.append(Component.object(new AtlasSprite(AtlasIds.BLOCKS, sprite)).withColor(0xFFFFFF));
        }
        return row;
    }
}

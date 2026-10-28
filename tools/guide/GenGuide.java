import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes the Modonomicon version of the guide, «Как печь Колобка»: the book, its one category, an
 * entry per section with its pages, and the stove as a multiblock. Modonomicon reads these only
 * when it is installed; without it the guide is a written book built in code from the same lang keys
 * ({@code ru.bagomot.kolobok.guide.GuideBook}). The list below must match {@code GuideBook.ENTRIES};
 * a unit test checks that.
 *
 * <pre>java tools/guide/GenGuide.java src/main/resources</pre>
 */
public final class GenGuide {

    private static final String BOOK = "book.kolobok.guide.";

    /** Entry, its icon and its pages, in order. */
    private record Entry(String id, String icon, List<String> pages) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("kolobok", "kolobok:kolobok_spawn_egg", List.of("intro")),
            new Entry("stove", "minecraft:bricks", List.of("build", "layout", "fire")),
            new Entry("baking", "kolobok:dough", List.of("dough", "oven")),
            new Entry("taming", "minecraft:milk_bucket", List.of("listen")),
            new Entry("controls", "kolobok:rolling_pin", List.of("hands", "things")),
            new Entry("care", "minecraft:honey_bottle", List.of("care")),
            new Entry("verses", "kolobok:songbook", List.of("verses")),
            new Entry("fox", "minecraft:sweet_berries", List.of("fox")),
            new Entry("outfits", "kolobok:crown", List.of("outfits")));

    public static void main(String[] args) throws IOException {
        Path data = Path.of(args.length > 0 ? args[0] : "src/main/resources").resolve("data/kolobok/modonomicon");
        Path book = data.resolve("books/guide");
        write(book.resolve("book.json"), """
                {
                  "name": "book.kolobok.guide.name",
                  "tooltip": "book.kolobok.guide.tooltip",
                  "description": "book.kolobok.guide.cover",
                  "display_mode": "index",
                  "generate_book_item": true,
                  "model": "modonomicon:modonomicon_red",
                  "creative_tab": "modonomicon:modonomicon",
                  "page_display_mode": "double_page",
                  "default_auto_scale": true
                }
                """);
        write(book.resolve("categories/tale.json"), """
                {
                  "name": "book.kolobok.guide.tale.name",
                  "icon": {
                    "id": "kolobok:rolling_pin"
                  },
                  "display_mode": "index",
                  "sort_number": 0
                }
                """);
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            write(book.resolve("entries/tale/" + entry.id() + ".json"), """
                    {
                      "type": "modonomicon:content",
                      "id": "kolobok:tale/%s",
                      "category": "kolobok:tale",
                      "name": "%s%s.name",
                      "icon": {
                        "id": "%s"
                      },
                      "x": 0,
                      "y": %d,
                      "sort_number": %d
                    }
                    """.formatted(entry.id(), BOOK, entry.id(), entry.icon(), i * 2, i));
            for (int p = 0; p < entry.pages().size(); p++) {
                String page = entry.pages().get(p);
                String text = BOOK + entry.id() + "." + page;
                String title = p == 0 ? BOOK + entry.id() + ".name" : "";
                String json;
                if (entry.id().equals("stove") && page.equals("layout")) {
                    json = """
                            {
                              "type": "modonomicon:multiblock",
                              "id": "%s",
                              "multiblock_id": "kolobok:stove",
                              "multiblock_name": "%sstove.multiblock",
                              "text": "%s",
                              "show_visualize_button": true,
                              "sort_number": %d
                            }
                            """.formatted(page, BOOK, text, p);
                } else {
                    json = """
                            {
                              "type": "modonomicon:text",
                              "id": "%s",
                              "title": "%s",
                              "text": "%s",
                              "sort_number": %d
                            }
                            """.formatted(page, title, text, p);
                }
                write(book.resolve("entries/tale/" + entry.id() + "/pages/" + page + ".json"), json);
            }
        }
        // Modonomicon reads a dense pattern as pattern[layer][x].charAt(z), the first layer on top: each
        // string is a line of blocks along z (north to south) at one x (west to east). With the
        // furnace facing south the front is the last character, the back the first, and the right as
        // you face the furnace the last string: so the chimney is the first character of the last
        // string of the top layer.
        write(data.resolve("multiblocks/stove.json"), """
                {
                  "type": "modonomicon:dense",
                  "pattern": [
                    [
                      "__",
                      "__",
                      "B_"
                    ],
                    [
                      "BB",
                      "BB",
                      "BB"
                    ],
                    [
                      "BB",
                      "B0",
                      "BB"
                    ]
                  ],
                  "mapping": {
                    "B": {
                      "type": "modonomicon:block",
                      "block": "minecraft:bricks"
                    },
                    "0": {
                      "type": "modonomicon:blockstate",
                      "block": "minecraft:furnace[facing=south]"
                    }
                  }
                }
                """);
    }

    private static void write(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
        System.out.println(file);
    }

    private GenGuide() {
    }
}

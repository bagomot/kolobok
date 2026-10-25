package ru.bagomot.kolobok.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.block.StoveBlockEntity;
import ru.bagomot.kolobok.block.StovePattern;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.event.KolobokEvents;
import ru.bagomot.kolobok.guide.GuideBook;
import ru.bagomot.kolobok.guide.GuideBookRecipe;
import ru.bagomot.kolobok.registry.KEntities;

import java.util.List;

/**
 * The v1.2 scene, «Подсказки»: the guide every player gets on joining and can make again from a book
 * and a brick, read page by page in Russian; then, for each viewer mod run with it
 * ({@code -Pcompat=jei,jade,modonomicon}, or {@code rei} instead of {@code jei}), what it shows: Jade
 * on the stove and on Kolobok, JEI's or REI's stove, guide recipe and hints, Modonomicon's book with
 * the stove in 3D. Without them only the written book is looked at.
 */
public final class GuideVisualTest implements FabricClientGameTest {

    private static final BlockPos MOUTH = new BlockPos(0, -60, -4);
    private static final String CLOSE = "tp @a 0.5 -60 -1.5 180 30";
    /** Before the stove, looking at its горнило, with a Kolobok sitting to the right. */
    private static final String LOOK_AT_OVEN = "tp @a 0.5 -60 -1.4 180 12";
    /** The book's text area: 114 pixels wide, 128 high, nine to a line. */
    private static final int PAGE_WIDTH = 114;
    private static final int PAGE_LINES = 14;

    @Override
    public void runTest(ClientGameTestContext context) {
        boolean modonomicon = FabricLoader.getInstance().isModLoaded(GuideBook.MODONOMICON);
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            TestServerContext server = game.getServer();
            game.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamerule spawn_mobs false");

            given(server);
            recipe(server);
            // Every page fits in every language of the mod; the screenshots are in Russian.
            List<String> languages = context.computeOnClient(mc -> mc.getResourceManager()
                    .listResources("lang", id -> id.getNamespace().equals(Kolobok.MOD_ID) && id.getPath().endsWith(".json"))
                    .keySet().stream().map(id -> id.getPath().substring("lang/".length(), id.getPath().length() - ".json".length()))
                    .sorted().toList());
            if (!languages.containsAll(List.of("en_us", "ru_ru"))) {
                throw new AssertionError("The mod's languages: " + languages);
            }
            for (String language : languages) {
                pagesFit(context, language);
            }
            Language english = context.computeOnClient(mc -> Language.getInstance());
            Language russian = context.computeOnClient(mc -> ClientLanguage.loadFrom(mc.getResourceManager(), List.of("en_us", "ru_ru"), false));
            context.runOnClient(mc -> Language.inject(russian));
            if (modonomicon) {
                modonomicon(context, server);
            } else {
                writtenBook(context, server);
            }
            if (FabricLoader.getInstance().isModLoaded("jade")) {
                jade(context, server, game);
            }
            quiet(context);
            if (FabricLoader.getInstance().isModLoaded("jei")) {
                JeiScreens.show(context);
            }
            if (FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) {
                ReiScreens.show(context);
            }
            context.runOnClient(mc -> Language.inject(english));
        }
    }

    /** Joining for the first time, the player finds the guide in the inventory, and only the first time. */
    private static void given(TestServerContext server) {
        boolean has = server.computeOnServer(s -> {
            ServerPlayer player = player(s);
            ItemStack guide = GuideBook.create();
            boolean found = false;
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                found |= ItemStack.isSameItemSameComponents(stack, guide);
            }
            return found && player.entityTags().contains(KolobokEvents.GUIDE_GIVEN);
        });
        if (!has) {
            throw new AssertionError("The player did not get the guide on joining");
        }
        int books = server.computeOnServer(s -> {
            ServerPlayer player = player(s);
            KolobokEvents.onPlayerJoin(player);
            return count(player);
        });
        if (books != 1) {
            throw new AssertionError("Joining again gave the guide again: " + books);
        }
    }

    /** A book and a brick at the crafting table make the guide; a book alone does not. */
    private static void recipe(TestServerContext server) {
        String problem = server.computeOnServer(s -> {
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Kolobok.id("guide_book"));
            if (s.getRecipeManager().byKey(key).isEmpty()) {
                return "no kolobok:guide_book recipe";
            }
            CraftingInput both = CraftingInput.of(2, 1, List.of(new ItemStack(Items.BRICK), new ItemStack(Items.BOOK)));
            CraftingInput book = CraftingInput.of(1, 1, List.of(new ItemStack(Items.BOOK)));
            if (!GuideBookRecipe.INSTANCE.matches(both, s.overworld()) || GuideBookRecipe.INSTANCE.matches(book, s.overworld())) {
                return "the recipe matches the wrong things";
            }
            return ItemStack.isSameItemSameComponents(GuideBookRecipe.INSTANCE.assemble(both), GuideBook.create()) ? "" : "it makes something else";
        });
        if (!problem.isEmpty()) {
            throw new AssertionError(problem);
        }
    }

    /** No page of the written book runs past its fourteen lines in that language. */
    private static void pagesFit(ClientGameTestContext context, String language) {
        String overflow = context.computeOnClient(mc -> {
            Language previous = Language.getInstance();
            Language.inject(ClientLanguage.loadFrom(mc.getResourceManager(), List.of("en_us", language), false));
            try {
                WrittenBookContent content = GuideBook.writtenBook().get(DataComponents.WRITTEN_BOOK_CONTENT);
                StringBuilder long_ = new StringBuilder();
                int page = 1;
                for (Filterable<Component> text : content.pages()) {
                    int lines = mc.font.split(text.get(false), PAGE_WIDTH).size();
                    if (lines > PAGE_LINES) {
                        long_.append(" page ").append(page).append(": ").append(lines).append(" lines;");
                    }
                    page++;
                }
                return long_.toString();
            } finally {
                Language.inject(previous);
            }
        });
        if (!overflow.isEmpty()) {
            throw new AssertionError("The guide in " + language + " overflows:" + overflow);
        }
    }

    /** The written book: cover, contents, the stove in three pages, then the rest. */
    private static void writtenBook(ClientGameTestContext context, TestServerContext server) {
        TestInput input = context.getInput();
        server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, GuideBook.writtenBook()));
        context.waitTicks(3);
        input.pressKey(options -> options.keyUse);
        context.waitForScreen(BookViewScreen.class);
        context.waitTicks(5);
        context.takeScreenshot("guide-01-cover");
        String[] shots = {"contents", "kolobok", "stove-build", "stove-layout", "stove-fire", "baking-dough",
                "baking-oven", "taming", "controls-hands", "controls-things", "care", "verses", "fox", "outfits"};
        for (int i = 0; i < shots.length; i++) {
            input.pressKey(InputConstants.KEY_PAGEDOWN);
            context.waitTicks(2);
            context.takeScreenshot("guide-" + String.format("%02d", i + 2) + "-" + shots[i]);
        }
        context.setScreen(() -> null);
    }

    /** Modonomicon's book: the index, then the stove's entry with its 3D layout. */
    private static void modonomicon(ClientGameTestContext context, TestServerContext server) {
        TestInput input = context.getInput();
        server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, GuideBook.create()));
        context.waitTicks(3);
        input.pressKey(options -> options.keyUse);
        context.waitTicks(20);
        context.takeScreenshot("guide-modonomicon-01-index");
        context.runOnClient(mc -> {
            try {
                Class<?> manager = Class.forName("com.klikli_dev.modonomicon.client.gui.BookGuiManager");
                Object instance = manager.getMethod("get").invoke(null);
                manager.getMethod("openEntry", Identifier.class, Identifier.class, int.class)
                        .invoke(instance, GuideBook.ID, Kolobok.id("tale/stove"), 0);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Could not open the stove's entry", e);
            }
        });
        context.waitTicks(20);
        context.takeScreenshot("guide-modonomicon-02-stove");
        context.setScreen(() -> null);
    }

    /**
     * Jade on the stove, lit and baking, with the window glass by it: the горнило, the wood and the
     * window, and none of the stove's own labels. Then on a stale, tamed Kolobok.
     */
    private static void jade(ClientGameTestContext context, TestServerContext server, TestSingleplayerContext game) {
        TestInput input = context.getInput();
        server.runCommand("gamemode creative @a");
        server.runCommand("fill -1 -60 -5 1 -59 -4 minecraft:bricks");
        server.runCommand("setblock 0 -60 -4 minecraft:furnace[facing=south]");
        server.runCommand("setblock 1 -58 -5 minecraft:bricks");
        server.runCommand("setblock 3 -60 -3 minecraft:glass");
        server.runCommand(CLOSE);
        context.waitTicks(10);
        server.runCommand("item replace entity @a weapon.mainhand with kolobok:rolling_pin");
        context.waitTicks(3);
        input.holdKey(options -> options.keyShift);
        context.waitTicks(3);
        input.pressKey(options -> options.keyUse);
        context.waitTicks(3);
        input.releaseKey(options -> options.keyShift);
        context.waitTicks(5);
        for (String item : new String[]{"minecraft:oak_log 16", "kolobok:raw_kolobok", "minecraft:flint_and_steel"}) {
            server.runCommand("item replace entity @a weapon.mainhand with " + item);
            context.waitTicks(2);
            input.pressKey(options -> options.keyUse);
            context.waitTicks(4);
        }
        boolean baking = server.computeOnServer(s -> s.overworld().getBlockEntity(MOUTH) instanceof StoveBlockEntity stove
                && StovePattern.isIntact(s.overworld(), MOUTH, stove.facing()) && stove.isLit() && !stove.oven().isEmpty());
        if (!baking) {
            throw new AssertionError("The stove is not built, lit and baking");
        }
        server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
        server.runCommand(LOOK_AT_OVEN);
        context.waitTicks(20);
        game.getConnection().waitForChunksRender();
        quiet(context);
        context.takeScreenshot("guide-jade-01-stove");

        server.runCommand("summon kolobok:kolobok 3.5 -60 -1.5 {Tags:[\"k\"],Rotation:[90f,0f]}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            for (KolobokEntity k : s.overworld().getEntities(KEntities.KOLOBOK.get(), e -> e.entityTags().contains("k"))) {
                k.tame(player(s));
                k.setKState(KState.SIT);
                k.setFlag(KolobokEntity.FLAG_STALE, true);
                k.setFlag(KolobokEntity.FLAG_BITTEN, true);
            }
        });
        server.runCommand("tp @a 0.5 -60 -1.5 -90 24");
        context.waitTicks(20);
        quiet(context);
        context.takeScreenshot("guide-jade-02-kolobok");
        server.runCommand("kill @e[tag=k]");
    }

    /** No chat and no toasts over what the screenshot is of. */
    static void quiet(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            mc.gui.hud.getChat().clearMessages(false);
            mc.gui.toastManager().clear();
        });
        context.waitTicks(1);
    }

    private static int count(ServerPlayer player) {
        int count = 0;
        ItemStack guide = GuideBook.create();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            count += ItemStack.isSameItemSameComponents(stack, guide) ? stack.getCount() : 0;
        }
        return count;
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }
}

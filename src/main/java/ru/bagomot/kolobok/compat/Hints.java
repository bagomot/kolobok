package ru.bagomot.kolobok.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.block.StoveBlockEntity;
import ru.bagomot.kolobok.entity.Variant;
import ru.bagomot.kolobok.guide.GuideBook;
import ru.bagomot.kolobok.item.DoughItem;
import ru.bagomot.kolobok.registry.KItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What JEI and REI show, the same in both: the stove baking raw Kolobok, a page of hints on each of
 * the mod's items, and the guide's recipe, which is code and so not seen by them. The hints are the
 * guide's own texts.
 */
public final class Hints {

    /** The stove's recipes in the viewers. */
    public static final Identifier STOVE = Kolobok.id("stove");
    public static final Identifier GUIDE_RECIPE = Kolobok.id("guide_book");

    private static final String BOOK = GuideBook.KEY;
    /** What the guide is made of; see {@code GuideBookRecipe}. */
    public static final List<Item> GUIDE_INPUTS = List.of(Items.BOOK, Items.BRICK);

    /**
     * A raw Kolobok baked: any of {@code raw} comes out as a Kolobok of {@code variant}, under
     * {@code condition} if there is one.
     */
    public record Baking(List<ItemStack> raw, Variant variant, @Nullable String condition) {

        /** @return the Kolobok baked, as his spawn egg with his dough written on it */
        public ItemStack kolobok() {
            ItemStack egg = new ItemStack(KItems.KOLOBOK_SPAWN_EGG.get());
            egg.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("kolobok.songbook.variant",
                            Component.translatable("kolobok.variant." + variant.serializedName()))
                    .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)))));
            return egg;
        }

        /** @return the line under the recipe: the time and the condition */
        public List<Component> notes() {
            List<Component> notes = new ArrayList<>();
            int seconds = StoveBlockEntity.BAKE_TICKS / 20;
            notes.add(Component.literal(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)));
            if (condition != null) {
                notes.add(Component.translatable(condition));
            }
            return notes;
        }
    }

    /** Items and the hint shown on them. */
    public record Info(List<ItemStack> stacks, List<Component> lines) {
    }

    private Hints() {
    }

    /**
     * @return a recipe for each dough, and the full moon's: classic, rye or golden dough baked on a
     *         full moon night comes out lunar
     */
    public static List<Baking> bakings() {
        Item raw = KItems.RAW_KOLOBOK.get();
        List<Baking> bakings = new ArrayList<>();
        for (Variant variant : List.of(Variant.CLASSIC, Variant.BORODINSKY, Variant.GOLDEN, Variant.MINI)) {
            bakings.add(new Baking(List.of(DoughItem.of(raw, variant)), variant, null));
        }
        bakings.add(new Baking(List.of(DoughItem.of(raw, Variant.CLASSIC), DoughItem.of(raw, Variant.BORODINSKY),
                DoughItem.of(raw, Variant.GOLDEN), DoughItem.of(raw, Variant.MOON)), Variant.MOON, "kolobok.hint.full_moon"));
        return bakings;
    }

    /** @return the logs the stove burns, for the fuel slot */
    public static List<ItemStack> logs() {
        List<ItemStack> logs = new ArrayList<>();
        for (Holder<Item> log : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.LOGS_THAT_BURN)) {
            logs.add(new ItemStack(log));
        }
        return logs;
    }

    /** @return what the stove is built of, shown as its stations: the furnace and the bricks */
    public static List<ItemStack> stations() {
        return List.of(new ItemStack(Items.FURNACE), new ItemStack(Items.BRICKS));
    }

    public static Component stoveName() {
        return Component.translatable("block.kolobok.stove_part");
    }

    /** @return the hint pages: how to build the stove, and the guide's text for each of the mod's items */
    public static List<Info> infos() {
        Component stove = Component.translatable("kolobok.hint.stove");
        return List.of(
                new Info(List.of(new ItemStack(Items.FURNACE)), List.of(stove)),
                new Info(List.of(new ItemStack(KItems.ROLLING_PIN.get())), List.of(stove, page("controls.hands"))),
                new Info(doughs(KItems.DOUGH.get()), List.of(page("baking.dough"))),
                new Info(doughs(KItems.RAW_KOLOBOK.get()), List.of(page("baking.oven"))),
                new Info(List.of(new ItemStack(KItems.KOLOBOK_SPAWN_EGG.get())),
                        List.of(page("kolobok.intro"), page("taming.listen"), page("care.care"))),
                new Info(List.of(new ItemStack(KItems.SONGBOOK.get())), List.of(page("verses.verses"))),
                new Info(List.of(new ItemStack(KItems.BINDLE.get())), List.of(page("controls.things"))),
                new Info(List.of(new ItemStack(KItems.CROWN.get()), new ItemStack(KItems.WREATH.get()),
                        new ItemStack(KItems.KOKOSHNIK.get())), List.of(page("outfits.outfits"))));
    }

    /**
     * @return the guide's recipe as the shapeless one it looks like, a book and a brick, giving the
     *         guide as this game has it
     */
    public static ShapelessRecipe guideRecipe() {
        List<Ingredient> inputs = new ArrayList<>();
        for (Item item : GUIDE_INPUTS) {
            inputs.add(Ingredient.of(item));
        }
        return new ShapelessRecipe(new Recipe.CommonInfo(false),
                new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                ItemStackTemplate.fromNonEmptyStack(GuideBook.create()), inputs);
    }

    private static List<ItemStack> doughs(Item item) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Variant variant : Variant.values()) {
            stacks.add(DoughItem.of(item, variant));
        }
        return stacks;
    }

    private static Component page(String id) {
        return Component.translatable(BOOK + id);
    }
}

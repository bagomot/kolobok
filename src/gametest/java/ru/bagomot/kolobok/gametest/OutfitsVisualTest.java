package ru.bagomot.kolobok.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.AABB;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Mood;
import ru.bagomot.kolobok.registry.KEntities;
import ru.bagomot.kolobok.registry.KItems;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The v0.5 scene: Kolobki in every outfit, a row at a time; a player in the crown, the wreath and the
 * kokoshnik; dressing him from the hand, which gives back what he wore, and shears taking things off
 * one by one; the Head from «Руслан и Людмила», which grows, sneezes a cow away and, hit, rolls off
 * leaving the Меч-кладенец; and an escapade to a herd of cows that come up to hear him sing.
 */
public final class OutfitsVisualTest implements FabricClientGameTest {

    private static final int BLUE = 0x3C44AA;
    private static final int PINK = 0xF38BAA;
    private static final int RED = 0xB02E26;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            TestServerContext server = game.getServer();
            game.getConnection().waitForChunksRender();

            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule spawn_mobs false");
            clear(server);
            server.runCommand("tp @a 0.5 -59.3 3.6 180 20");
            // The hats in the hotbar, to see their icons.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                List<ItemStack> icons = List.of(new ItemStack(KItems.CROWN.get()), new ItemStack(KItems.WREATH.get()),
                        dyed(KItems.WREATH.get(), PINK), new ItemStack(KItems.KOKOSHNIK.get()), dyed(KItems.KOKOSHNIK.get(), BLUE));
                for (int i = 0; i < icons.size(); i++) {
                    player.getInventory().setItem(i, icons.get(i));
                }
            });
            // An empty hand, so the crown in it does not fill the picture.
            context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(8));

            // Helmets: the ушанка, undyed and dyed, and a шелом of every metal.
            row(context, server, "outfits-01-helmets", List.of(
                    () -> new ItemStack(Items.LEATHER_HELMET), () -> dyed(Items.LEATHER_HELMET, BLUE),
                    () -> new ItemStack(Items.COPPER_HELMET), () -> new ItemStack(Items.CHAINMAIL_HELMET),
                    () -> new ItemStack(Items.IRON_HELMET), () -> new ItemStack(Items.GOLDEN_HELMET),
                    () -> new ItemStack(Items.DIAMOND_HELMET), () -> new ItemStack(Items.NETHERITE_HELMET)), List.of(), false);
            // His own hats, undyed and dyed.
            row(context, server, "outfits-02-hats", List.of(
                    () -> new ItemStack(KItems.CROWN.get()), () -> new ItemStack(KItems.WREATH.get()),
                    () -> dyed(KItems.WREATH.get(), PINK), () -> new ItemStack(KItems.KOKOSHNIK.get()),
                    () -> dyed(KItems.KOKOSHNIK.get(), BLUE), () -> ItemStack.EMPTY), List.of(), false);
            // Лапти of bast, dyed and of metal, the last two in sunglasses.
            row(context, server, "outfits-03-lapti", List.of(
                    () -> ItemStack.EMPTY, () -> ItemStack.EMPTY, () -> ItemStack.EMPTY, () -> ItemStack.EMPTY,
                    () -> new ItemStack(KItems.CROWN.get()), () -> ItemStack.EMPTY), List.of(
                    () -> new ItemStack(Items.LEATHER_BOOTS), () -> dyed(Items.LEATHER_BOOTS, RED),
                    () -> new ItemStack(Items.IRON_BOOTS), () -> new ItemStack(Items.GOLDEN_BOOTS),
                    () -> new ItemStack(Items.NETHERITE_BOOTS), () -> ItemStack.EMPTY), true);
            clear(server);

            playerHats(context, server);
            dressing(context, server);
            theHead(context, server);
            escapade(context, server);
        }
    }

    /**
     * A row of Kolobki facing the camera, each in what the suppliers give for his head and feet.
     *
     * @param glasses whether the last two wear sunglasses
     */
    private static void row(ClientGameTestContext context, TestServerContext server, String screenshot,
                            List<Supplier<ItemStack>> heads, List<Supplier<ItemStack>> feet, boolean glasses) {
        clear(server);
        int n = heads.size();
        for (int i = 0; i < n; i++) {
            double x = 0.5 - (n - 1) * 0.5 + i;
            server.runCommand("summon kolobok:kolobok " + x + " -60 0.5 {NoAI:1b,Rotation:[0f,0f],Tags:[\"" + screenshot + i + "\"]}");
        }
        context.waitTicks(3);
        server.runOnServer(s -> {
            for (int i = 0; i < n; i++) {
                KolobokEntity k = tagged(s, screenshot + i);
                k.setItemSlot(EquipmentSlot.HEAD, heads.get(i).get());
                if (i < feet.size()) {
                    k.setItemSlot(EquipmentSlot.FEET, feet.get(i).get());
                }
                k.setFlag(KolobokEntity.FLAG_GLASSES, glasses && i >= n - 2);
            }
        });
        context.waitTicks(25);
        // Whatever he will not wear he spits out; everything here he keeps.
        for (int i = 0; i < n; i++) {
            int index = i;
            ItemStack expected = heads.get(i).get();
            boolean kept = server.computeOnServer(s -> ItemStack.isSameItemSameComponents(
                    tagged(s, screenshot + index).getItemBySlot(EquipmentSlot.HEAD), expected));
            if (!kept) {
                throw new AssertionError("Kolobok " + i + " in " + screenshot + " took off " + expected);
            }
        }
        game(context);
        context.takeScreenshot(screenshot);
    }

    /** Players wear his hats too: the game draws them on the head as their item models. */
    private static void playerHats(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        String[] names = {"crown", "wreath", "kokoshnik"};
        Item[] hats = {KItems.CROWN.get(), KItems.WREATH.get(), KItems.KOKOSHNIK.get()};
        for (int i = 0; i < hats.length; i++) {
            Item hat = hats[i];
            server.runOnServer(s -> player(s).setItemSlot(EquipmentSlot.HEAD, new ItemStack(hat)));
            context.waitTicks(10);
            context.takeScreenshot("outfits-04-player-" + names[i]);
        }
        server.runOnServer(s -> player(s).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY));
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    /**
     * His owner dresses him from the hand, one at a time, and gets back what he wore; shears take off
     * the glasses, then the hat, then the лапти.
     */
    private static void dressing(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runCommand("tp @a 0.5 -59.5 3.0 180 20");
        server.runCommand("summon kolobok:kolobok 0.5 -60 0.5 {NoAI:1b,Rotation:[0f,0f],Tags:[\"k\"]}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            KolobokEntity k = tagged(s, "k");
            k.tame(player);
            k.setKState(KState.SIT);
            interact(player, k, new ItemStack(KItems.CROWN.get()));
            if (!k.getItemBySlot(EquipmentSlot.HEAD).is(KItems.CROWN.get()) || !player.getMainHandItem().isEmpty()
                    || !k.getSayKey().equals("dress_crown")) {
                throw new AssertionError("The crown did not go on: " + k.getItemBySlot(EquipmentSlot.HEAD) + " " + k.getSayKey());
            }
            interact(player, k, new ItemStack(Items.IRON_HELMET));
            if (!k.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
                    || !player.getInventory().contains(new ItemStack(KItems.CROWN.get()))) {
                throw new AssertionError("The helmet did not replace the crown, or the crown was lost");
            }
            interact(player, k, new ItemStack(Items.TINTED_GLASS));
            interact(player, k, dyed(Items.LEATHER_BOOTS, RED));
            if (!k.hasFlag(KolobokEntity.FLAG_GLASSES) || !k.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS)) {
                throw new AssertionError("Glasses or лапти did not go on");
            }
            // A turtle shell he will not wear.
            interact(player, k, new ItemStack(Items.TURTLE_HELMET));
            if (!k.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) || !k.getSayKey().equals("turtle")) {
                throw new AssertionError("He put on the turtle shell");
            }
        });
        context.waitTicks(25);
        check(server, "slower in лапти", k -> k.getAttributeValue(Attributes.MOVEMENT_SPEED)
                < k.getAttributeBaseValue(Attributes.MOVEMENT_SPEED));
        check(server, "singing as the шелом makes him", k -> k.voice() < 0.75F);
        game(context);
        context.takeScreenshot("outfits-05-dressed");

        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            KolobokEntity k = tagged(s, "k");
            interact(player, k, new ItemStack(Items.SHEARS));
            if (k.hasFlag(KolobokEntity.FLAG_GLASSES) || k.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                throw new AssertionError("Shears should take the glasses off first, and only them");
            }
            interact(player, k, new ItemStack(Items.SHEARS));
            if (!k.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || k.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
                throw new AssertionError("Shears should take the helmet off next");
            }
            interact(player, k, new ItemStack(Items.SHEARS));
            if (k.hasAnyOutfit() || !k.getSayKey().equals("undress")) {
                throw new AssertionError("Shears should take the лапти off last");
            }
            List<ItemEntity> fallen = s.overworld().getEntitiesOfClass(ItemEntity.class, k.getBoundingBox().inflate(3.0));
            boolean glass = fallen.stream().anyMatch(item -> item.getItem().is(Items.TINTED_GLASS));
            boolean helmet = fallen.stream().anyMatch(item -> item.getItem().is(Items.IRON_HELMET));
            boolean boots = fallen.stream().anyMatch(item -> item.getItem().is(Items.LEATHER_BOOTS)
                    && item.getItem().has(DataComponents.DYED_COLOR));
            if (!glass || !helmet || !boots) {
                throw new AssertionError("What the shears took off should lie at his feet: " + fallen);
            }
        });
        server.runCommand("kill @e[type=item]");
        clear(server);
        server.runCommand("gamemode creative @a");
    }

    /**
     * Sitting in a metal helmet he plays the Head: grows, snores, sneezes a cow away when tickled with
     * a feather, and hit, rolls off, leaving the Меч-кладенец.
     */
    private static void theHead(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 3.5 -59 3.5 135 15");
        server.runCommand("summon kolobok:kolobok 0.5 -60 0.5 {Rotation:[0f,0f],Tags:[\"k\"]}");
        server.runCommand("summon minecraft:cow 0.5 -60 3.0 {Tags:[\"cow\"]}");
        // It cannot walk off, but a gust still blows it.
        server.runCommand("effect give @e[tag=cow] minecraft:slowness infinite 255 true");
        context.waitTicks(3);
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.tame(player(s));
            k.setKState(KState.SIT);
            k.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            if (!k.startHeadAct(s.overworld())) {
                throw new AssertionError("He did not become the Head");
            }
        });
        context.waitTicks(30);
        check(server, "asleep as the Head", k -> k.getKState() == KState.HEAD_ACT && k.getMood() == Mood.GOLOVA_SLEEP);
        float scale = context.computeOnClient(mc -> clientKolobok(mc).clientHeadScale);
        if (scale < 1.7F) {
            throw new AssertionError("The Head should have grown, it is " + scale);
        }
        game(context);
        context.takeScreenshot("outfits-06-golova");

        double cowZ = server.computeOnServer(s -> cow(s).getZ());
        server.runOnServer(s -> interact(player(s), tagged(s, "k"), new ItemStack(Items.FEATHER)));
        context.waitTicks(6);
        check(server, "sneezing, awake for a moment", k -> k.getSayKey().equals("golova_sneeze") && k.getMood() == Mood.GOLOVA);
        game(context);
        context.takeScreenshot("outfits-07-sneeze");
        context.waitTicks(14);
        double blown = server.computeOnServer(s -> cow(s).getZ()) - cowZ;
        if (blown < 1.0) {
            throw new AssertionError("The sneeze should blow the cow away from him, it moved " + blown);
        }

        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            ServerPlayer player = player(s);
            k.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(player), 1.0F);
        });
        context.waitTicks(30);
        check(server, "awake, sitting again, unhurt, the sword given", k -> k.getKState() == KState.SIT
                && k.getHealth() == k.getMaxHealth() && k.gaveKladenets() && k.getSayKey().equals("golova_kladenets"));
        boolean sword = server.computeOnServer(s -> !s.overworld().getEntitiesOfClass(ItemEntity.class,
                new AABB(-5, -62, -5, 6, -55, 6), item -> item.getItem().is(Items.WOODEN_SWORD)
                        && item.getItem().has(DataComponents.ITEM_NAME)).isEmpty());
        if (!sword) {
            throw new AssertionError("The Меч-кладенец should lie where the Head was");
        }
        game(context);
        context.takeScreenshot("outfits-08-kladenets");
        clear(server);
    }

    /**
     * Following his owner, he sees a herd of cows a way off, rolls to them and sings; they come up and
     * watch. His owner's click calls him off.
     */
    private static void escapade(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 10");
        server.runCommand("summon kolobok:kolobok 1.5 -60 0.5 {Rotation:[0f,0f],Tags:[\"k\"]}");
        for (int i = 0; i < 4; i++) {
            server.runCommand("summon minecraft:cow " + (-1.5 + i * 1.5) + " -60 24.5 {Tags:[\"cow\"]}");
        }
        context.waitTicks(3);
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.tame(player(s));
            k.setKState(KState.FOLLOW);
            k.escapeNow();
        });
        waitFor(context, server, 100, "him to set off to the cows", s -> tagged(s, "k").escapadePhase().equals("going"));
        check(server, "telling his owner", k -> k.getSayKey().equals("escapade"));
        waitFor(context, server, 600, "him to get to the cows and sing",
                s -> tagged(s, "k").escapadePhase().equals("lingering") && tagged(s, "k").isSinging());
        context.waitTicks(80);
        int near = server.computeOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            return s.overworld().getEntitiesOfClass(Cow.class, k.getBoundingBox().inflate(4.5)).size();
        });
        if (near < 2) {
            throw new AssertionError("The cows should come up to hear him, only " + near + " did");
        }
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            player(s).teleportTo(k.getX() + 4.0, -60, k.getZ() - 5.0);
            player(s).lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, k.position().add(0, 0.4, 0));
        });
        context.waitTicks(20);
        game(context);
        context.takeScreenshot("outfits-09-concert");

        // His owner calls him: the escapade is over.
        server.runOnServer(s -> interact(player(s), tagged(s, "k"), ItemStack.EMPTY));
        context.waitTicks(25);
        check(server, "sitting, the escapade over", k -> k.getKState() == KState.SIT && k.escapadePhase().equals("none"));
    }

    /** Takes away every Kolobok, animal and item without a death: no smoke, no drops. */
    private static void clear(TestServerContext server) {
        server.runOnServer(s -> {
            // Gathered first: discarding while walking the level's own list would skip some.
            List<Entity> all = new ArrayList<>();
            s.overworld().getAllEntities().forEach(entity -> {
                if (entity != null && !(entity instanceof Player)) {
                    all.add(entity);
                }
            });
            all.forEach(Entity::discard);
        });
    }

    private static void interact(ServerPlayer player, KolobokEntity k, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        k.mobInteract(player, InteractionHand.MAIN_HAND);
    }

    private static ItemStack dyed(Item item, int colour) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(colour));
        return stack;
    }

    /** The chat and the toasts would cover the picture. */
    private static void game(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            mc.gui.hud.getChat().clearMessages(false);
            mc.gui.toastManager().clear();
        });
        context.waitTicks(1);
    }

    private static void waitFor(ClientGameTestContext context, TestServerContext server, int ticks, String what,
                                Predicate<MinecraftServer> condition) {
        for (int i = 0; i < ticks; i++) {
            if (server.computeOnServer(condition::test)) {
                return;
            }
            context.waitTicks(1);
        }
        String state = server.computeOnServer(s -> describe(tagged(s, "k")));
        throw new AssertionError("Waited " + ticks + " ticks for " + what + ": " + state);
    }

    private static void check(TestServerContext server, String what, Predicate<KolobokEntity> test) {
        boolean ok = server.computeOnServer(s -> test.test(tagged(s, "k")));
        if (!ok) {
            throw new AssertionError("Expected him " + what + ", got " + server.computeOnServer(s -> describe(tagged(s, "k"))));
        }
    }

    private static String describe(KolobokEntity k) {
        return k.getKState() + " " + k.getMood() + " escapade=" + k.escapadePhase() + " singing=" + k.isSinging()
                + " say=" + k.getSayKey() + " at " + k.position() + " health=" + k.getHealth()
                + " head=" + k.getItemBySlot(EquipmentSlot.HEAD) + " voice=" + k.voice();
    }

    private static KolobokEntity tagged(MinecraftServer server, String tag) {
        ServerLevel level = server.overworld();
        // One killed for the last part may still lie there, dying, with the same tag.
        for (KolobokEntity entity : level.getEntities(KEntities.KOLOBOK.get(), e -> e.isAlive() && e.entityTags().contains(tag))) {
            return entity;
        }
        throw new AssertionError("No Kolobok tagged " + tag);
    }

    private static Cow cow(MinecraftServer server) {
        for (Cow cow : server.overworld().getEntitiesOfClass(Cow.class, new AABB(-20, -64, -20, 20, -50, 20),
                c -> c.entityTags().contains("cow"))) {
            return cow;
        }
        throw new AssertionError("No cow");
    }

    private static KolobokEntity clientKolobok(net.minecraft.client.Minecraft mc) {
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof KolobokEntity k) {
                return k;
            }
        }
        throw new AssertionError("The client sees no Kolobok");
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }
}

package ru.bagomot.kolobok.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import ru.bagomot.kolobok.block.StovePattern;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Mood;
import ru.bagomot.kolobok.registry.KEntities;
import ru.bagomot.kolobok.registry.KItems;

import java.util.List;
import java.util.function.Predicate;

/**
 * The v0.3 scene: a tamed Kolobok with a home stove is hit by a zombie and gets away, earns the
 * verse and its perk and sings the new song; «killed», he turns up resting on his stove and the
 * owner wakes him; he writes his song into the songbook, which opens as a book; with the stove
 * gone, the next «death» leaves him a pancake that nothing hurts.
 */
public final class VersesVisualTest implements FabricClientGameTest {

    /** The stove faces south, at the player. */
    private static final BlockPos MOUTH = new BlockPos(0, -60, -4);
    private static final String VIEW = "tp @a 0.5 -60 3.5 180 8";
    /** From higher up and further back, to see the top of the stove. */
    private static final String VIEW_TOP = "tp @a 0.5 -58.5 4.5 180 6";
    private static final String K = "@e[tag=k,limit=1]";

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            TestServerContext server = game.getServer();
            TestInput input = context.getInput();
            game.getConnection().waitForChunksRender();

            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("fill -1 -60 -5 1 -59 -4 minecraft:bricks");
            server.runCommand("setblock 0 -60 -4 minecraft:furnace[facing=south]");
            server.runCommand("setblock 1 -58 -5 minecraft:bricks");
            server.runCommand(VIEW);
            server.runOnServer(s -> StovePattern.assemble(s.overworld(), MOUTH));
            // His own Kolobok, at home in this stove. Without AI he stays in the picture instead of
            // rolling off in a panic when the zombie hits him; verses, rest and the pancake are not AI.
            server.runCommand("summon kolobok:kolobok 0.5 -60 0.5 {NoAI:1b,Tags:[\"k\"],Rotation:[0f,0f]}");
            context.waitTicks(5);
            server.runOnServer(s -> {
                KolobokEntity k = kolobok(s);
                k.tame(player(s));
                k.setKState(KState.SIT);
                k.setHome(MOUTH);
            });

            // A zombie hits him; the zombie is then far off, and he has got away from it.
            server.runCommand("summon minecraft:zombie 2.5 -60 0.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"z\"]}");
            server.runCommand("effect give @e[tag=z] minecraft:fire_resistance infinite 0 true");
            context.waitTicks(5);
            server.runCommand("damage " + K + " 2 minecraft:mob_attack by @e[tag=z,limit=1]");
            context.waitTicks(2);
            server.runCommand("tp @e[tag=z] 0.5 -60 30.5");
            waitFor(context, server, 60, "the zombie's verse", s -> kolobok(s).verses().contains("minecraft:zombie"));
            check(server, "to sing the new song with the zombie's armour",
                    k -> k.isSinging() && k.hasFlag(KolobokEntity.FLAG_SING_NEW)
                            && k.getAttributeValue(Attributes.ARMOR) == 2.0);
            // The bubble comes to the zombie's line about thirteen seconds into the song.
            context.waitFor(c -> songLine(c).contains("zombie"), 600);
            clearChat(context);
            context.takeScreenshot("verses-01-new-verse");

            // More perks: the bear's two hearts, the creeper's indifference to explosions.
            server.runOnServer(s -> {
                KolobokEntity k = kolobok(s);
                k.learnVerse("minecraft:polar_bear");
                k.learnVerse("minecraft:creeper");
                if (k.getMaxHealth() != 14.0F) {
                    throw new AssertionError("The bear's verse should give 14 health, got " + k.getMaxHealth());
                }
            });
            float health = server.computeOnServer(s -> kolobok(s).getHealth());
            server.runCommand("damage " + K + " 3 minecraft:explosion");
            context.waitTicks(2);
            check(server, "unhurt by the explosion", k -> k.getHealth() == health);

            // «Killed», he gets away to his stove and lies on top of it.
            server.runCommand("damage " + K + " 100 minecraft:generic");
            context.waitTicks(3);
            // In the middle of the front middle place, the first of the five.
            check(server, "resting on the stove", k -> k.isAlive() && k.getKState() == KState.RESTING
                    && k.position().distanceTo(StovePattern.topSlots(MOUTH, Direction.SOUTH).getFirst()) < 0.01
                    && k.getMood() == Mood.SLEEP);
            server.runCommand("damage " + K + " 5 minecraft:generic");
            context.waitTicks(2);
            check(server, "unhurt while resting", k -> k.getKState() == KState.RESTING);
            server.runCommand(VIEW_TOP);
            context.waitTicks(30);
            clearChat(context);
            context.takeScreenshot("verses-02-resting");

            // The owner wakes him early.
            server.runOnServer(s -> kolobok(s).mobInteract(player(s), InteractionHand.MAIN_HAND));
            check(server, "woken and following", k -> k.getKState() == KState.FOLLOW && k.getSayKey().equals("woken"));
            server.runOnServer(s -> kolobok(s).setKState(KState.SIT));

            // The songbook takes down his song, then opens as a vanilla book.
            int pages = server.computeOnServer(s -> {
                ServerPlayer player = player(s);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(KItems.SONGBOOK.get()));
                kolobok(s).mobInteract(player, InteractionHand.MAIN_HAND);
                WrittenBookContent book = player.getMainHandItem().get(DataComponents.WRITTEN_BOOK_CONTENT);
                return book == null ? 0 : book.pages().size();
            });
            // The cover, two verses of the head, the mobs' verse and the perks.
            if (pages != 5) {
                throw new AssertionError("Expected a five-page songbook, got " + pages);
            }
            check(server, "to answer about the songbook", k -> k.getSayKey().equals("songbook"));
            server.runCommand("tp @a 0.5 -60 3.5 0 -60");
            context.waitTicks(3);
            input.pressKey(options -> options.keyUse);
            context.waitForScreen(BookViewScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("verses-03-songbook");
            for (int i = 0; i < 3; i++) {
                input.pressKey(InputConstants.KEY_PAGEDOWN);
                context.waitTicks(2);
            }
            context.takeScreenshot("verses-04-songbook-verse");
            input.pressKey(InputConstants.KEY_PAGEDOWN);
            context.waitTicks(2);
            context.takeScreenshot("verses-05-songbook-perks");
            context.setScreen(() -> null);

            // Without a stove to get to, the next «death» leaves a pancake that nothing hurts.
            server.runCommand("setblock -1 -59 -5 minecraft:air");
            context.waitTicks(25);
            server.runCommand("tp " + K + " 0.5 -60 0.5");
            server.runCommand("damage " + K + " 100 minecraft:generic");
            context.waitTicks(3);
            check(server, "a pancake", k -> k.isAlive() && k.getKState() == KState.PANCAKE && k.getMood() == Mood.DIZZY);
            server.runCommand("damage " + K + " 5 minecraft:generic");
            context.waitTicks(2);
            check(server, "unhurt as a pancake", k -> k.getKState() == KState.PANCAKE && k.getHealth() == 1.0F);
            server.runCommand(VIEW);
            context.waitTicks(30);
            clearChat(context);
            context.takeScreenshot("verses-06-pancake");
        }
    }

    /** The chat would cover him in the low half of the picture. */
    private static void clearChat(ClientGameTestContext context) {
        context.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
        context.waitTicks(1);
    }

    /**
     * @return the song line in his bubble on this client, or an empty string
     */
    private static String songLine(Minecraft client) {
        if (client.level == null) {
            return "";
        }
        for (var entity : client.level.entitiesForRendering()) {
            if (entity instanceof KolobokEntity k && k.clientSongLine != null) {
                return k.clientSongLine.getString();
            }
        }
        return "";
    }

    private static void waitFor(ClientGameTestContext context, TestServerContext server, int ticks, String what,
                                Predicate<MinecraftServer> condition) {
        for (int i = 0; i < ticks; i++) {
            if (server.computeOnServer(condition::test)) {
                return;
            }
            context.waitTicks(1);
        }
        throw new AssertionError("Waited " + ticks + " ticks for " + what);
    }

    private static void check(TestServerContext server, String what, Predicate<KolobokEntity> test) {
        boolean ok = server.computeOnServer(s -> test.test(kolobok(s)));
        if (!ok) {
            String state = server.computeOnServer(s -> {
                KolobokEntity k = kolobok(s);
                return k.getKState() + " " + k.getMood() + " health=" + k.getHealth() + " y=" + k.getY()
                        + " say=" + k.getSayKey() + " verses=" + k.verses();
            });
            throw new AssertionError("Expected him " + what + ", got " + state);
        }
    }

    private static KolobokEntity kolobok(MinecraftServer server) {
        List<? extends KolobokEntity> all = server.overworld().getEntities(KEntities.KOLOBOK.get(), e -> true);
        if (all.isEmpty()) {
            throw new AssertionError("No Kolobok in the world");
        }
        return all.getFirst();
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }
}

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.phys.AABB;
import ru.bagomot.kolobok.block.StoveBlockEntity;
import ru.bagomot.kolobok.block.StovePattern;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Mood;
import ru.bagomot.kolobok.entity.ai.Goals;
import ru.bagomot.kolobok.registry.KEntities;
import ru.bagomot.kolobok.registry.KItems;

import java.util.List;
import java.util.function.Predicate;

/**
 * The v0.3 scene and the stove's warmth: a tamed Kolobok is hit by a zombie and gets away, earns the
 * verse and its perk and sings the new song; three days without milk he goes stale, and milk makes
 * him fresh again; badly hurt, he rolls to the warm stove by himself, hops on top and heals there
 * until his owner wakes him; he writes his song into the songbook, which opens as a book; killed, he
 * dies for good though the stove stands, and drops the helmet he wore.
 */
public final class VersesVisualTest implements FabricClientGameTest {

    /** The stove faces south, at the player. */
    private static final BlockPos MOUTH = new BlockPos(0, -60, -4);
    private static final String VIEW = "tp @a 0.5 -60 3.5 180 8";
    /** From higher up and further back, to see the top of the stove. */
    private static final String VIEW_TOP = "tp @a 0.5 -58.5 4.5 180 6";
    /** In front of the stove, the crosshair on the горнило. */
    private static final String CLOSE = "tp @a 0.5 -60 -1.5 180 30";
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
            server.runOnServer(s -> StovePattern.assemble(s.overworld(), MOUTH, pos -> true));
            // Without AI he stays in the picture instead of rolling off in a panic when the zombie hits
            // him; verses and freshness are not AI.
            server.runCommand("summon kolobok:kolobok 0.5 -60 0.5 {NoAI:1b,Tags:[\"k\"],Rotation:[0f,0f]}");
            context.waitTicks(5);
            server.runOnServer(s -> {
                KolobokEntity k = kolobok(s);
                k.tame(player(s));
                k.setKState(KState.SIT);
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

            // Three days without milk, and he is stale: slower, paler, with a harder crust.
            server.runCommand("data merge entity " + K + " {LastFed:-300000L}");
            waitFor(context, server, 30, "him to go stale", s -> kolobok(s).hasFlag(KolobokEntity.FLAG_STALE));
            check(server, "stale, with a crust of armour and a word about it", k -> k.getSayKey().equals("stale")
                    && k.getAttributeValue(Attributes.ARMOR) == 6.0);
            server.runCommand(VIEW);
            context.waitTicks(10);
            clearChat(context);
            context.takeScreenshot("verses-02-stale");
            // Milk, «сметанка», and he is fresh again, though not hurt.
            server.runOnServer(s -> {
                player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MILK_BUCKET));
                kolobok(s).mobInteract(player(s), InteractionHand.MAIN_HAND);
            });
            check(server, "fresh again", k -> !k.hasFlag(KolobokEntity.FLAG_STALE) && k.getSayKey().equals("milk")
                    && k.getAttributeValue(Attributes.ARMOR) == 2.0);

            // Wood into the подпечье and flint and steel into the горнило: the stove is lit, and warm.
            server.runCommand(CLOSE);
            for (String item : new String[]{"minecraft:oak_log 16", "minecraft:flint_and_steel"}) {
                server.runCommand("item replace entity @a weapon.mainhand with " + item);
                context.waitTicks(2);
                input.pressKey(options -> options.keyUse);
                context.waitTicks(4);
            }
            server.runOnServer(s -> {
                if (!(s.overworld().getBlockEntity(MOUTH) instanceof StoveBlockEntity stove) || !stove.isWarm()) {
                    throw new AssertionError("The stove did not light");
                }
            });
            server.runCommand(VIEW_TOP);

            // Hurt below half his health, he stops singing, rolls to the warm stove himself and hops
            // onto the front middle place, the first of the five.
            server.runOnServer(s -> {
                KolobokEntity k = kolobok(s);
                k.setNoAi(false);
                k.setKState(KState.FOLLOW);
            });
            server.runCommand("damage " + K + " 10 minecraft:generic");
            context.waitTicks(2);
            check(server, "hurt below half and quiet", k -> k.getHealth() < k.getMaxHealth() / 2.0F && !k.isSinging());
            for (int i = 0; !server.computeOnServer(s -> kolobok(s).getKState() == KState.RESTING); i++) {
                if (i >= 300) {
                    throw new AssertionError("Waited 300 ticks for him to warm up on the stove: "
                            + server.computeOnServer(VersesVisualTest::describe));
                }
                context.waitTicks(1);
            }
            context.waitTicks(15);
            check(server, "resting on the stove", k -> k.isAlive() && k.getKState() == KState.RESTING
                    && k.position().distanceTo(StovePattern.topSlots(MOUTH, Direction.SOUTH).getFirst()) < 0.01
                    && k.getMood() == Mood.SLEEP);
            server.runCommand("damage " + K + " 5 minecraft:generic");
            context.waitTicks(2);
            check(server, "unhurt on the stove", k -> k.getKState() == KState.RESTING);
            float lying = server.computeOnServer(s -> kolobok(s).getHealth());
            context.waitTicks(45);
            check(server, "healing on the warm stove, a point a second", k -> k.getHealth() >= lying + 2.0F);
            clearChat(context);
            context.takeScreenshot("verses-03-warming-up");

            // The owner wakes him before he is whole: he hops down and follows.
            server.runOnServer(s -> {
                player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                kolobok(s).mobInteract(player(s), InteractionHand.MAIN_HAND);
            });
            check(server, "woken and following", k -> k.getKState() == KState.FOLLOW && k.getSayKey().equals("woken"));
            context.waitTicks(15);
            check(server, "down in front of the stove", k -> Math.abs(k.getY() - MOUTH.getY()) < 0.01);
            server.runOnServer(s -> {
                KolobokEntity k = kolobok(s);
                k.setKState(KState.SIT);
                k.setNoAi(true);
                k.teleportTo(0.5, -60, 0.5);
            });

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
            context.takeScreenshot("verses-04-songbook");
            for (int i = 0; i < 3; i++) {
                input.pressKey(InputConstants.KEY_PAGEDOWN);
                context.waitTicks(2);
            }
            context.takeScreenshot("verses-05-songbook-verse");
            input.pressKey(InputConstants.KEY_PAGEDOWN);
            context.waitTicks(2);
            context.takeScreenshot("verses-06-songbook-perks");
            context.setScreen(() -> null);

            // Killed, he dies for good, warm stove or not, and what he wore falls where he was.
            server.runCommand("item replace entity " + K + " armor.head with minecraft:golden_helmet");
            server.runCommand("damage " + K + " 100 minecraft:generic");
            context.waitTicks(30);
            server.runOnServer(s -> {
                if (!kolobki(s.overworld()).isEmpty()) {
                    throw new AssertionError("He outlived his death");
                }
                List<ItemEntity> helmets = s.overworld().getEntitiesOfClass(ItemEntity.class,
                        new AABB(MOUTH).inflate(8.0), item -> item.getItem().is(Items.GOLDEN_HELMET));
                if (helmets.size() != 1 || helmets.getFirst().getItem().isDamaged()) {
                    throw new AssertionError("Expected his golden helmet on the ground, whole, got " + helmets);
                }
            });
            server.runCommand(VIEW);
            context.waitTicks(10);
            clearChat(context);
            context.takeScreenshot("verses-07-dead");
        }
    }

    /** Where he is and what he is up to, for a failure. */
    private static String describe(MinecraftServer server) {
        KolobokEntity k = kolobok(server);
        StringBuilder goals = new StringBuilder();
        Goals.goals(k).getAvailableGoals().stream().filter(WrappedGoal::isRunning)
                .forEach(goal -> goals.append(goal.getGoal().getClass().getSimpleName()).append(' '));
        String stoves = StoveBlockEntity.loaded(server.overworld()).stream()
                .map(stove -> stove.getBlockPos().toShortString() + (stove.isWarm() ? " warm" : " cold"))
                .toList().toString();
        return k.getKState() + " at " + k.position() + " health=" + k.getHealth() + "/" + k.getMaxHealth()
                + " tame=" + k.isTame() + " noAi=" + k.isNoAi() + " singing=" + k.isSinging() + " say=" + k.getSayKey()
                + " navigating=" + k.getNavigation().isInProgress() + " goals=[" + goals + "] stoves=" + stoves
                + " tick=" + k.tickCount + " id=" + k.getId();
    }

    private static List<? extends KolobokEntity> kolobki(ServerLevel level) {
        return level.getEntities(KEntities.KOLOBOK.get(), e -> true);
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
        List<? extends KolobokEntity> all = kolobki(server.overworld());
        if (all.isEmpty()) {
            throw new AssertionError("No Kolobok in the world");
        }
        return all.getFirst();
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }
}

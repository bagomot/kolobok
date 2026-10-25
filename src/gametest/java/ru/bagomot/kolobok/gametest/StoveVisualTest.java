package ru.bagomot.kolobok.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.block.StoveBlockEntity;
import ru.bagomot.kolobok.block.StovePart;
import ru.bagomot.kolobok.block.StovePartBlock;
import ru.bagomot.kolobok.block.StovePattern;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Mood;
import ru.bagomot.kolobok.registry.KEntities;
import ru.bagomot.kolobok.registry.KItems;

import java.util.function.Predicate;

/**
 * The stove scene with a real client: vanilla bricks and a furnace become the Russian stove under the
 * rolling pin; logs go into the подпечье and a raw Kolobok into the горнило by hand, flint and steel
 * lights the горнило, and pointed at, the горнило tells the time left and the подпечье the wood and
 * how long it lasts. The stove bakes him hot in there, he cools by the window, hops out and runs,
 * stops to sing, is listened to by a crouching player and tamed with milk; the rolling pin then
 * switches his modes. The горнило then roasts a stack of beef at once; with nothing left to cook the
 * fire burns on, and with the wood taken back it goes out and will not light. One broken brick takes
 * the stove apart again.
 */
public final class StoveVisualTest implements FabricClientGameTest {

    /** The stove faces south, at the player; its right hand is east. */
    private static final BlockPos MOUTH = new BlockPos(0, -60, -4);
    private static final Direction FACING = Direction.SOUTH;
    private static final String CLOSE = "tp @a 0.5 -60 -1.5 180 30";
    private static final String VIEW = "tp @a 0.5 -60 3.5 180 8";
    /** Low and close, to look into the горнило. */
    private static final String INSIDE = "tp @a 0.5 -60 -1.3 180 15";
    /** The crosshair on the подпечье. */
    private static final String WOOD = "tp @a 0.5 -60 -1.5 180 40";

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            TestServerContext server = game.getServer();
            TestInput input = context.getInput();
            game.getConnection().waitForChunksRender();

            server.runOnServer(s -> KConfig.server().tameChanceDenominator = 1);
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            // The vanilla layout: the furnace in the middle of the front row, eleven bricks round it,
            // one more above the back right corner. A glass block two blocks off is the window.
            server.runCommand("fill -1 -60 -5 1 -59 -4 minecraft:bricks");
            server.runCommand("setblock 0 -60 -4 minecraft:furnace[facing=south]");
            server.runCommand("setblock 1 -58 -5 minecraft:bricks");
            server.runCommand("setblock 3 -60 -3 minecraft:glass");
            server.runCommand(VIEW);
            context.waitTicks(20);
            game.getConnection().waitForChunksRender();
            context.takeScreenshot("stove-01-vanilla");

            // Shift and right-click on the furnace with the rolling pin.
            server.runCommand(CLOSE);
            server.runCommand("item replace entity @a weapon.mainhand with kolobok:rolling_pin");
            context.waitTicks(3);
            input.holdKey(options -> options.keyShift);
            context.waitTicks(3);
            input.pressKey(options -> options.keyUse);
            context.waitTicks(3);
            input.releaseKey(options -> options.keyShift);
            context.waitTicks(5);
            server.runOnServer(s -> {
                if (!StovePattern.isIntact(s.overworld(), MOUTH, FACING)) {
                    throw new AssertionError("The rolling pin did not build the stove");
                }
            });
            earned(server, "root", "stove");
            server.runCommand(VIEW);
            context.waitTicks(10);
            context.takeScreenshot("stove-02-built");

            // Logs into the подпечье and the raw Kolobok into the горнило, each by a right-click: the
            // stove stays cold until it is lit.
            server.runCommand(CLOSE);
            for (String item : new String[]{"minecraft:oak_log 16", "kolobok:raw_kolobok"}) {
                server.runCommand("item replace entity @a weapon.mainhand with " + item);
                context.waitTicks(2);
                input.pressKey(options -> options.keyUse);
                context.waitTicks(4);
            }
            server.runOnServer(s -> {
                StoveBlockEntity stove = stove(s);
                if (!stove.logs().is(Items.OAK_LOG) || !stove.oven().is(KItems.RAW_KOLOBOK.get()) || stove.isLit()) {
                    throw new AssertionError("Logs or the raw Kolobok missing, or lit by itself: " + stove.status().getString());
                }
            });
            earned(server, "into_the_oven");
            // Flint and steel in the горнило lights it.
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:flint_and_steel");
            context.waitTicks(2);
            input.pressKey(options -> options.keyUse);
            context.waitTicks(4);
            server.runOnServer(s -> {
                StoveBlockEntity stove = stove(s);
                if (!s.overworld().getBlockState(MOUTH).getValue(StovePartBlock.LIT) || stove.logs().getCount() != 15) {
                    throw new AssertionError("Flint and steel did not light the stove: " + stove.status().getString());
                }
            });
            // Pointed at, the горнило tells the time left to bake.
            context.waitTicks(10);
            context.takeScreenshot("stove-03-lit-time-left");
            server.runCommand(WOOD);
            context.waitTicks(10);
            context.takeScreenshot("stove-04-firewood");
            server.runCommand(VIEW);
            context.waitTicks(60);
            context.takeScreenshot("stove-04-burning");
            server.runCommand(INSIDE);
            context.waitTicks(200);
            context.takeScreenshot("stove-05-baking");

            // Thirty seconds in all, and a hot Kolobok sits in the горнило, asleep and steaming.
            waitFor(context, server, 500, "a baked Kolobok", s -> baked(s) != null);
            server.runCommand("tag @e[type=kolobok:kolobok] add baked");
            check(server, "hot in the горнило", k -> k.getKState() == KState.HOT
                    && k.position().distanceTo(StovePattern.gornilo(MOUTH, FACING)) < 0.1);
            context.waitTicks(10);
            context.takeScreenshot("stove-06-hot");
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                baked(s).mobInteract(player, InteractionHand.MAIN_HAND);
            });
            check(server, "to complain about being touched hot", k -> k.getSayKey().equals("hot"));
            // The bubble shows from inside the горнило, through the stove.
            context.waitTicks(5);
            context.takeScreenshot("stove-06-said-hot");

            // By the window he cools in thirty seconds, opens his eyes and rolls away.
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a minecraft:resistance 600 4 true");
            waitFor(context, server, 750, "him to cool and hop out", s -> baked(s).getKState() == KState.RUNAWAY);
            context.waitTicks(12);
            check(server, "out of the горнило", k -> k.position().distanceTo(StovePattern.gornilo(MOUTH, FACING)) > 0.5);
            earned(server, "by_the_window", "ran_away");
            context.waitTicks(10);
            context.takeScreenshot("stove-07-cooled");

            // Ten seconds into the chase his head spins: sneak up and crouch beside him. A chase is
            // only a chase while someone keeps up, so the player follows him every second.
            for (int i = 0; ; i++) {
                if (server.computeOnServer(s -> baked(s).getMood() == Mood.DIZZY)) {
                    break;
                }
                if (i >= 600) {
                    throw new AssertionError("Chased him for 600 ticks and his head never spun");
                }
                if (i % 20 == 0) {
                    server.runCommand("execute at @e[tag=baked,limit=1] run tp @a ~5 ~ ~");
                }
                context.waitTicks(1);
            }
            server.runCommand(server.computeOnServer(s -> aimAt(baked(s))));
            input.holdKey(options -> options.keyShift);
            waitFor(context, server, 60, "him to sing", s -> baked(s).isSinging());
            context.waitTicks(30);
            context.takeScreenshot("stove-08-singing");
            int songTicks = server.computeOnServer(s -> baked(s).composeSong().ticks());
            context.waitTicks(songTicks);
            input.releaseKey(options -> options.keyShift);
            context.waitTicks(3);
            int heard = server.computeOnServer(s -> baked(s).songsListenedBy(player(s)));
            if (heard != 1) {
                throw new AssertionError("Crouched through the verse, but he counts " + heard + " songs listened to");
            }
            context.takeScreenshot("stove-09-listened");

            // Pleased, he stays put for a moment: milk now tames him (the chance is forced to 1 above).
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:milk_bucket");
            server.runCommand(server.computeOnServer(s -> aimAt(baked(s))));
            context.waitTicks(2);
            input.pressKey(options -> options.keyUse);
            context.waitTicks(6);
            check(server, "tamed", k -> k.isTame() && k.getKState() == KState.FOLLOW);
            earned(server, "sour_cream", "tame");
            context.takeScreenshot("stove-10-tamed");

            // The rolling pin: follow → crumbs → guard → wander → follow.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(KItems.ROLLING_PIN.get()));
                KolobokEntity k = baked(s);
                StringBuilder modes = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    k.mobInteract(player, InteractionHand.MAIN_HAND);
                    modes.append(k.getKState()).append(k.hasFlag(KolobokEntity.FLAG_CRUMBS) ? "+crumbs" : "").append(' ');
                }
                if (!modes.toString().equals("FOLLOW+crumbs GUARD WANDER FOLLOW ")) {
                    throw new AssertionError("Modes went " + modes);
                }
                // Out of the way of the clicks on the stove, and not off to sit in its горнило.
                k.setKState(KState.SIT);
                k.teleportTo(4.5, -60, 0.5);
            });

            // Eight raw beef roast in the горнило all at once; an empty hand takes them out.
            server.runCommand(CLOSE);
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:beef 8");
            context.waitTicks(2);
            input.pressKey(options -> options.keyUse);
            context.waitTicks(4);
            server.runOnServer(s -> {
                StoveBlockEntity stove = stove(s);
                if (!stove.oven().is(Items.BEEF) || stove.oven().getCount() != 8 || !stove.isLit()) {
                    throw new AssertionError("The beef did not go in to roast: " + stove.status().getString());
                }
            });
            waitFor(context, server, 500, "the beef to roast", s -> stove(s).isCooked());
            server.runCommand(INSIDE);
            context.waitTicks(5);
            context.takeScreenshot("stove-11-roasted");
            server.runCommand(CLOSE);
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
            context.waitTicks(2);
            input.pressKey(options -> options.keyUse);
            context.waitTicks(4);
            server.runOnServer(s -> {
                int beef = player(s).getInventory().countItem(Items.COOKED_BEEF);
                if (beef != 8 || !stove(s).oven().isEmpty()) {
                    throw new AssertionError("Took out " + beef + " cooked beef: " + stove(s).status().getString());
                }
            });

            // Nothing left to cook, and the fire burns on. With the wood taken back it goes out once the
            // log in it has burned, and with no wood there is nothing to light.
            server.runOnServer(s -> {
                if (!stove(s).isLit()) {
                    throw new AssertionError("The fire went out with wood left: " + stove(s).status().getString());
                }
            });
            // The cooked beef went into the empty hand.
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
            context.waitTicks(2);
            input.holdKey(options -> options.keyShift);
            context.waitTicks(3);
            input.pressKey(options -> options.keyUse);
            context.waitTicks(3);
            input.releaseKey(options -> options.keyShift);
            context.waitTicks(3);
            server.runOnServer(s -> {
                if (!stove(s).logs().isEmpty() || player(s).getInventory().countItem(Items.OAK_LOG) == 0) {
                    throw new AssertionError("Shift did not take the wood back: " + stove(s).status().getString());
                }
            });
            waitFor(context, server, 320, "the last log to burn out", s -> !stove(s).isLit());
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:flint_and_steel");
            context.waitTicks(2);
            input.pressKey(options -> options.keyUse);
            context.waitTicks(4);
            server.runOnServer(s -> {
                if (stove(s).isLit()) {
                    throw new AssertionError("Lit with no wood: " + stove(s).status().getString());
                }
            });
            context.takeScreenshot("stove-12-out");

            // One brick broken: everything else turns back into the bricks and the furnace.
            server.runCommand("setblock -1 -59 -5 minecraft:air");
            context.waitTicks(5);
            server.runOnServer(s -> {
                BlockState mouth = s.overworld().getBlockState(MOUTH);
                if (!mouth.is(Blocks.FURNACE) || mouth.getValue(AbstractFurnaceBlock.FACING) != FACING) {
                    throw new AssertionError("The mouth did not turn back into the furnace: " + mouth);
                }
                for (StovePart part : StovePart.values()) {
                    BlockPos pos = StovePattern.posOf(MOUTH, FACING, part);
                    BlockState state = s.overworld().getBlockState(pos);
                    boolean removed = pos.equals(new BlockPos(-1, -59, -5));
                    if (part != StovePart.MOUTH && !removed && !state.is(Blocks.BRICKS)) {
                        throw new AssertionError(part + " did not turn back into bricks: " + state);
                    }
                }
            });
            server.runCommand(VIEW);
            context.waitTicks(10);
            context.takeScreenshot("stove-13-apart");
        }
    }

    /**
     * @return a command that puts the player a step from him, standing, with the crosshair on him
     */
    private static String aimAt(KolobokEntity k) {
        double x = k.getX() + 1.4, y = k.getY(), z = k.getZ();
        double dx = k.getX() - x, dz = k.getZ() - z;
        double dy = k.getY() + k.getBbHeight() / 2 - (y + 1.62);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return "tp @a " + x + " " + y + " " + z + " " + yaw + " " + pitch;
    }

    private static void waitFor(ClientGameTestContext context, TestServerContext server, int ticks, String what,
                                Predicate<MinecraftServer> condition) {
        for (int i = 0; i < ticks; i++) {
            if (server.computeOnServer(condition::test)) {
                return;
            }
            context.waitTicks(1);
        }
        String stove = server.computeOnServer(s -> s.overworld().getBlockEntity(MOUTH) instanceof StoveBlockEntity found
                ? found.status().getString() + " lit=" + found.isLit() : "no stove");
        throw new AssertionError("Waited " + ticks + " ticks for " + what + "; the stove: " + stove);
    }

    private static void check(TestServerContext server, String what, Predicate<KolobokEntity> test) {
        boolean ok = server.computeOnServer(s -> test.test(baked(s)));
        if (!ok) {
            String state = server.computeOnServer(s -> {
                KolobokEntity k = baked(s);
                return k.getKState() + " " + k.getMood() + " tame=" + k.isTame() + " say=" + k.getSayKey();
            });
            throw new AssertionError("Expected him " + what + ", got " + state);
        }
    }

    /** «Сказка о Колобке»: those advancements are done by now. */
    private static void earned(TestServerContext server, String... names) {
        for (String name : names) {
            boolean done = server.computeOnServer(s -> {
                AdvancementHolder holder = s.getAdvancements().get(Kolobok.id("tale/" + name));
                return holder != null && player(s).getAdvancements().getOrStartProgress(holder).isDone();
            });
            if (!done) {
                throw new AssertionError("The advancement tale/" + name + " should be done by now");
            }
        }
    }

    private static StoveBlockEntity stove(MinecraftServer server) {
        if (server.overworld().getBlockEntity(MOUTH) instanceof StoveBlockEntity stove) {
            return stove;
        }
        throw new AssertionError("No stove at " + MOUTH);
    }

    private static KolobokEntity baked(MinecraftServer server) {
        for (KolobokEntity entity : server.overworld().getEntities(KEntities.KOLOBOK.get(), e -> true)) {
            return entity;
        }
        return null;
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }
}

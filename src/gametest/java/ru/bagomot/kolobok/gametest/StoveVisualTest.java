package ru.bagomot.kolobok.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
 * The v0.2 scene with a real client: vanilla bricks and a furnace become the Russian stove under the
 * rolling pin, the dough and the fire go in by hand, the stove bakes a hot Kolobok, he cools by the
 * window and runs, stops to sing, is listened to by a crouching player and tamed with milk; the
 * rolling pin then switches his modes, and one broken brick takes the stove apart again.
 */
public final class StoveVisualTest implements FabricClientGameTest {

    /** The stove faces south, at the player; its right hand is east. */
    private static final BlockPos MOUTH = new BlockPos(0, -60, -4);
    private static final Direction FACING = Direction.SOUTH;
    private static final String CLOSE = "tp @a 0.5 -60 -1.5 180 30";
    private static final String VIEW = "tp @a 0.5 -60 3.5 180 8";

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
            server.runCommand(VIEW);
            context.waitTicks(10);
            context.takeScreenshot("stove-02-built");

            // Three wheat, an egg, «сметанка» and coal, each by a right-click on the stove.
            server.runCommand(CLOSE);
            for (String item : new String[]{"wheat", "wheat", "wheat", "egg", "milk_bucket", "coal"}) {
                server.runCommand("item replace entity @a weapon.mainhand with minecraft:" + item);
                context.waitTicks(2);
                input.pressKey(options -> options.keyUse);
                context.waitTicks(4);
            }
            server.runOnServer(s -> {
                StoveBlockEntity stove = stove(s);
                if (!stove.doughReady() || !s.overworld().getBlockState(MOUTH).getValue(StovePartBlock.LIT)) {
                    throw new AssertionError("Dough or fire missing: " + stove.status().getString());
                }
            });
            context.takeScreenshot("stove-03-status");
            server.runCommand(VIEW);
            context.waitTicks(80);
            context.takeScreenshot("stove-04-burning");

            // Thirty seconds later a hot Kolobok lies on the hearth, asleep and steaming.
            waitFor(context, server, 700, "a baked Kolobok", s -> baked(s) != null);
            server.runCommand("tag @e[type=kolobok:kolobok] add baked");
            check(server, "hot on the hearth", k -> k.getKState() == KState.HOT);
            server.runCommand("tp @a 0.5 -60 -1.2 180 22");
            context.waitTicks(10);
            context.takeScreenshot("stove-05-hot");
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                baked(s).mobInteract(player, InteractionHand.MAIN_HAND);
            });
            check(server, "to complain about being touched hot", k -> k.getSayKey().equals("hot"));

            // By the window he cools in thirty seconds, opens his eyes and rolls away.
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a minecraft:resistance 600 4 true");
            waitFor(context, server, 700, "him to cool", s -> baked(s).getKState() == KState.RUNAWAY);
            context.waitTicks(10);
            context.takeScreenshot("stove-06-cooled");

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
            context.takeScreenshot("stove-07-singing");
            int songTicks = server.computeOnServer(s -> baked(s).composeSong().ticks());
            context.waitTicks(songTicks);
            input.releaseKey(options -> options.keyShift);
            context.waitTicks(3);
            int heard = server.computeOnServer(s -> baked(s).songsListenedBy(player(s)));
            if (heard != 1) {
                throw new AssertionError("Crouched through the verse, but he counts " + heard + " songs listened to");
            }
            context.takeScreenshot("stove-08-listened");

            // Pleased, he stays put for a moment: milk now tames him (the chance is forced to 1 above).
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:milk_bucket");
            server.runCommand(server.computeOnServer(s -> aimAt(baked(s))));
            context.waitTicks(2);
            input.pressKey(options -> options.keyUse);
            context.waitTicks(6);
            check(server, "tamed", k -> k.isTame() && k.getKState() == KState.FOLLOW);
            context.takeScreenshot("stove-09-tamed");

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
            });

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
            context.takeScreenshot("stove-10-apart");
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
        throw new AssertionError("Waited " + ticks + " ticks for " + what);
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

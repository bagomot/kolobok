package ru.bagomot.kolobok.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.AABB;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Mood;
import ru.bagomot.kolobok.registry.KEntities;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The v1.1 scene: what was left of the design. A slime he takes for a brother; golden boots become
 * «лапти-люкс»; Шарик, his best friend, whom he goes to when wandering and does not grudge a pat;
 * Pigstep on a jukebox; and a jump, stretched in the air and squashed on landing, the hat a moment
 * behind.
 */
public final class NeighboursVisualTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            TestServerContext server = game.getServer();
            game.getConnection().waitForChunksRender();

            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            clear(server);

            slimeAndLapti(context, server);
            sharik(context, server);
            pigstep(context, server);
            jump(context, server);
        }
    }

    /** A slime close by: «Брат?! Ты чего такой квадратный?» Then golden boots: «Лапти-люкс!» */
    private static void slimeAndLapti(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 -3.5 0 20");
        server.runCommand("summon kolobok:kolobok 0.5 -60 0.5 {Tags:[\"k\"],Rotation:[180f,0f]}");
        server.runCommand("summon minecraft:slime 2.5 -60 1.5 {NoAI:1b,Size:0,PersistenceRequired:1b}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.tame(player(s));
            k.setKState(KState.SIT);
        });
        waitFor(context, server, 160, "a word for the slime", s -> tagged(s, "k").getSayKey().equals("slime"));
        context.waitTicks(10);
        game(context);
        context.takeScreenshot("neighbours-01-slime");

        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_BOOTS));
            tagged(s, "k").mobInteract(player, InteractionHand.MAIN_HAND);
        });
        context.waitTicks(3);
        check(server, "k", "proud of his лапти-люкс", k -> k.getSayKey().equals("dress_lapti_gold"));
        clear(server);
    }

    /**
     * Шарик, a tamed dog of that name: wandering, Kolobok rolls over to him and greets him, and a pat
     * for Шарик does not make him jealous.
     */
    private static void sharik(ClientGameTestContext context, TestServerContext server) {
        TestInput input = context.getInput();
        server.runCommand("tp @a 6.5 -60 -2.5 0 30");
        server.runCommand("summon minecraft:wolf 6.5 -60 0.5 {Tags:[\"sharik\"]}");
        server.runCommand("summon kolobok:kolobok -6.5 -60 0.5 {Tags:[\"k\"]}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            Wolf dog = sharik(s);
            dog.tame(player);
            dog.setOrderedToSit(true);
            dog.setCustomName(Component.literal("Шарик"));
            KolobokEntity k = tagged(s, "k");
            k.tame(player);
            k.setKState(KState.WANDER);
            if (!KolobokEntity.isSharik(dog)) {
                throw new AssertionError("A tamed dog named Шарик is not Шарик");
            }
        });
        waitFor(context, server, 300, "him to greet Шарик", s -> tagged(s, "k").getSayKey().equals("sharik"));
        waitFor(context, server, 300, "him to roll over to Шарик", s -> tagged(s, "k").distanceTo(sharik(s)) <= 4.5);
        server.runCommand("tp @a 6.5 -59 -6.5 0 20");
        context.waitTicks(10);
        game(context);
        context.takeScreenshot("neighbours-02-sharik");

        // A real right-click on Шарик, through each loader's own interaction event.
        server.runCommand("tp @a 6.5 -60 -1.0 0 30");
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            KolobokEntity k = tagged(s, "k");
            // Beside the dog, where he sees the pat, and out of the way of the click.
            k.setKState(KState.SIT);
            k.teleportTo(9.5, -60.0, 0.5);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, sharik(s).position().add(0.0, 0.4, 0.0));
        });
        context.waitTicks(3);
        input.pressKey(options -> options.keyUse);
        context.waitTicks(4);
        boolean stood = server.computeOnServer(s -> !sharik(s).isOrderedToSit());
        if (!stood) {
            throw new AssertionError("The click did not reach Шарик");
        }
        check(server, "k", "not jealous of Шарик", k -> !k.getSayKey().equals("jealous") && k.getMood() != Mood.SAD);
        clear(server);
    }

    /** Pigstep on a jukebox beside him: he dances and asks whether that is piglin rap. */
    private static void pigstep(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 -3.5 0 20");
        server.runCommand("setblock 2 -60 0 minecraft:jukebox");
        server.runCommand("summon kolobok:kolobok 0.5 -60 0.5 {Tags:[\"k\"],Rotation:[180f,0f]}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.tame(player(s));
            k.setKState(KState.SIT);
            if (s.overworld().getBlockEntity(new BlockPos(2, -60, 0)) instanceof JukeboxBlockEntity jukebox) {
                jukebox.setTheItem(new ItemStack(Items.MUSIC_DISC_PIGSTEP));
            } else {
                throw new AssertionError("No jukebox");
            }
        });
        waitFor(context, server, 60, "a word about Pigstep", s -> tagged(s, "k").getSayKey().equals("pigstep"));
        context.waitTicks(10);
        game(context);
        context.takeScreenshot("neighbours-03-pigstep");
        server.runCommand("setblock 2 -60 0 minecraft:air");
        clear(server);
    }

    /** A jump: stretched going up, squashed landing, the hat over his head on the way down. */
    private static void jump(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 -3.5 0 -5");
        server.runCommand("summon kolobok:kolobok 0.5 -60 0.5 {Tags:[\"k\"],Rotation:[180f,0f]}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.tame(player(s));
            k.setKState(KState.SIT);
            k.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        });
        context.waitTicks(10);
        game(context);
        server.runOnServer(s -> tagged(s, "k").setDeltaMovement(0.0, 0.5, 0.0));
        float stretch = 0.0F;
        float squash = 0.0F;
        float hatAbove = 0.0F;
        boolean shot = false;
        for (int i = 0; i < 30; i++) {
            context.waitTicks(1);
            float[] now = context.computeOnClient(mc -> {
                KolobokEntity k = clientKolobok(mc);
                return new float[]{k.clientSquash, k.clientHatLag};
            });
            stretch = Math.max(stretch, now[0]);
            squash = Math.min(squash, now[0]);
            hatAbove = Math.min(hatAbove, now[1]);
            if (!shot && now[0] > 0.05F) {
                // Stretched on the way up.
                shot = true;
                context.takeScreenshot("neighbours-04-jump");
            }
        }
        if (stretch < 0.03F || squash > -0.03F || hatAbove > -0.5F) {
            throw new AssertionError("The jump should stretch, squash and leave the hat behind: stretch " + stretch
                    + ", squash " + squash + ", hat " + hatAbove);
        }
        clear(server);
    }

    private static KolobokEntity clientKolobok(Minecraft mc) {
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof KolobokEntity k) {
                return k;
            }
        }
        throw new AssertionError("No Kolobok on the client");
    }

    private static Wolf sharik(MinecraftServer server) {
        for (Wolf wolf : server.overworld().getEntitiesOfClass(Wolf.class, new AABB(-30, -64, -30, 30, -50, 30),
                w -> w.entityTags().contains("sharik"))) {
            return wolf;
        }
        throw new AssertionError("No Шарик");
    }

    /** Takes away every Kolobok, animal and item without a death: no smoke, no drops. */
    private static void clear(TestServerContext server) {
        server.runOnServer(s -> {
            List<Entity> all = new ArrayList<>();
            s.overworld().getAllEntities().forEach(entity -> {
                if (entity != null && !(entity instanceof Player)) {
                    all.add(entity);
                }
            });
            all.forEach(Entity::discard);
        });
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
        throw new AssertionError("Waited " + ticks + " ticks for " + what + ": " + server.computeOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            return k.getKState() + " " + k.getMood() + " say=" + k.getSayKey() + " at " + k.position();
        }));
    }

    private static void check(TestServerContext server, String tag, String what, Predicate<KolobokEntity> test) {
        boolean ok = server.computeOnServer(s -> test.test(tagged(s, tag)));
        if (!ok) {
            throw new AssertionError("Expected " + tag + " " + what + ", got " + server.computeOnServer(s -> {
                KolobokEntity k = tagged(s, tag);
                return k.getKState() + " " + k.getMood() + " say=" + k.getSayKey() + " at " + k.position();
            }));
        }
    }

    private static KolobokEntity tagged(MinecraftServer server, String tag) {
        ServerLevel level = server.overworld();
        for (KolobokEntity entity : level.getEntities(KEntities.KOLOBOK.get(), e -> e.isAlive() && e.entityTags().contains(tag))) {
            return entity;
        }
        throw new AssertionError("No Kolobok tagged " + tag);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }
}

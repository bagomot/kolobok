package ru.bagomot.kolobok.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.phys.Vec3;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Perk;
import ru.bagomot.kolobok.entity.ai.CatOnStoveGoal;
import ru.bagomot.kolobok.entity.ai.Goals;

import java.util.function.Predicate;

/**
 * The v0.4 scene: foxes and cats get their goals when they load; a fox's bite carries a tamed
 * Kolobok off in its mouth instead of hurting him, berries on the ground make it drop him and teach
 * him «Не на ту напала». A fox that creeps up while an untamed one sings grabs him as the song ends,
 * and lets go when it jumps into water. A mob that hits the owner is drawn off onto Kolobok.
 */
public final class FoxVisualTest implements FabricClientGameTest {

    private static final String VIEW = "tp @a 0.5 -60 4.5 180 12";
    private static final String TAMED = "@e[tag=tamed,limit=1]";
    private static final String WILD = "@e[tag=wild,limit=1]";

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            TestServerContext server = game.getServer();
            game.getConnection().waitForChunksRender();

            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand(VIEW);
            // Without AI the fox stays in the picture; the catch, the release and the song trap are not AI.
            server.runCommand("summon minecraft:fox -0.5 -60 0.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"fox\"],Rotation:[270f,0f]}");
            server.runCommand("summon minecraft:cat 3.5 -60 -2.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"cat\"]}");
            server.runCommand("summon kolobok:kolobok 0.6 -60 0.5 {NoAI:1b,Tags:[\"tamed\"],Rotation:[90f,0f]}");
            context.waitTicks(5);
            server.runOnServer(s -> {
                if (!has(Goals.targets(fox(s)), "FoxHuntGoal")) {
                    throw new AssertionError("The fox did not learn to hunt Kolobok");
                }
                Mob cat = s.overworld().getEntities(EntityTypes.CAT, e -> true).getFirst();
                if (!has(Goals.goals(cat), CatOnStoveGoal.class.getSimpleName())) {
                    throw new AssertionError("The cat did not learn to sit on the stove");
                }
                KolobokEntity k = kolobok(s, "tamed");
                k.tame(player(s));
                k.setKState(KState.WANDER);
                if (!k.foxMayHunt()) {
                    throw new AssertionError("A fox may not hunt a wandering Kolobok");
                }
            });
            server.runCommand("kill @e[type=minecraft:cat]");

            // The fox bites: no wound, he is in its mouth, and the fox still steers itself.
            float health = server.computeOnServer(s -> kolobok(s, "tamed").getHealth());
            server.runCommand("damage " + TAMED + " 2 minecraft:mob_attack by @e[tag=fox,limit=1]");
            context.waitTicks(5);
            check(server, "tamed", "carried off unhurt", k -> k.getKState() == KState.CAUGHT && k.getHealth() == health
                    && k.getVehicle() instanceof Fox && k.getSayKey().equals("caught"));
            server.runOnServer(s -> {
                Fox fox = fox(s);
                KolobokEntity k = kolobok(s, "tamed");
                if (fox.getControllingPassenger() != null) {
                    throw new AssertionError("Kolobok steers the fox that carries him");
                }
                Vec3 mouth = fox.position().add(Vec3.directionFromRotation(0.0F, fox.yBodyRot).scale(0.62));
                if (k.position().multiply(1, 0, 1).distanceTo(mouth.multiply(1, 0, 1)) > 0.15) {
                    throw new AssertionError("Not in the fox's mouth: " + k.position() + " vs " + mouth);
                }
            });
            context.waitTicks(20);
            context.takeScreenshot("fox-01-caught");

            // Berries on the ground: the fox drops him, and he has learned his lesson.
            server.runCommand("summon minecraft:item 2.5 -60 2.5 {Item:{id:\"minecraft:sweet_berries\",count:1},PickupDelay:32767}");
            waitFor(context, server, 40, "the fox to drop him for the berries", s -> !kolobok(s, "tamed").isPassenger());
            check(server, "tamed", "rescued, wandering again, with the fox's perk",
                    k -> k.getKState() == KState.WANDER && k.getSayKey().equals("rescued") && k.hasPerk(Perk.FOX)
                            && !k.foxMayHunt());
            context.waitTicks(15);
            context.takeScreenshot("fox-02-rescued");
            server.runCommand("kill @e[type=minecraft:item]");
            server.runCommand("tp " + TAMED + " 4.5 -60 -3.5");

            // An untamed one sings; the fox creeps up meanwhile and grabs him as the song ends.
            server.runCommand("summon kolobok:kolobok 0.6 -60 0.5 {NoAI:1b,Tags:[\"wild\"],Rotation:[90f,0f]}");
            context.waitTicks(3);
            int songTicks = server.computeOnServer(s -> {
                KolobokEntity wild = kolobok(s, "wild");
                fox(s).setTarget(wild);
                wild.startSong(1);
                return wild.composeSong().ticks();
            });
            check(server, "wild", "singing to the fox", KolobokEntity::isSinging);
            context.waitTicks(songTicks + 5);
            check(server, "wild", "caught as the song ends", k -> k.getKState() == KState.CAUGHT && k.getVehicle() instanceof Fox);

            // Into the water with him: the fox lets go, and no perk for that.
            server.runCommand("setblock -1 -61 6 minecraft:water");
            server.runCommand("tp @e[tag=fox] -0.5 -61 6.5");
            waitFor(context, server, 40, "the fox to let go in the water", s -> !kolobok(s, "wild").isPassenger());
            check(server, "wild", "out of the fox's mouth, running", k -> k.getKState() == KState.RUNAWAY
                    && k.getSayKey().equals("fox_escape") && !k.hasPerk(Perk.FOX) && !k.foxMayHunt());
            server.runCommand("kill @e[type=minecraft:fox]");
            server.runCommand("kill " + WILD);

            // A zombie hits the owner: the tamed one calls it off onto himself.
            // On peaceful a zombie can neither be there nor hurt anyone.
            server.runCommand("difficulty normal");
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a minecraft:resistance 600 4 true");
            server.runCommand("tp " + TAMED + " 1.5 -60 2.5");
            server.runOnServer(s -> kolobok(s, "tamed").setKState(KState.FOLLOW));
            server.runCommand("summon minecraft:zombie 0.5 -60 -4.5 {PersistenceRequired:1b,Tags:[\"z\"]}");
            server.runCommand("effect give @e[tag=z] minecraft:fire_resistance infinite 0 true");
            context.waitTicks(3);
            server.runCommand("damage @p 1 minecraft:mob_attack by @e[tag=z,limit=1]");
            waitFor(context, server, 40, "the lure", s -> kolobok(s, "tamed").getSayKey().equals("lure"));
            // A second later it is still after him, not back after the owner.
            context.waitTicks(20);
            server.runOnServer(s -> {
                Mob zombie = s.overworld().getEntities(EntityTypes.ZOMBIE, e -> true).getFirst();
                if (zombie.getTarget() != kolobok(s, "tamed")) {
                    throw new AssertionError("The zombie still goes for " + zombie.getTarget());
                }
            });
            context.takeScreenshot("fox-03-lure");
            server.runCommand("kill @e[type=minecraft:zombie]");

            // At night a fox with its own AI finds a Kolobok that cannot run, and carries him off.
            server.runCommand("gamemode creative @a");
            server.runCommand("time set midnight");
            server.runCommand("tp " + TAMED + " 30.5 -60 30.5");
            server.runCommand("summon minecraft:fox -3.5 -60 -2.5 {PersistenceRequired:1b,Tags:[\"hunter\"]}");
            server.runCommand("summon kolobok:kolobok 1.5 -60 -2.5 {NoAI:1b,Tags:[\"prey\"]}");
            for (int i = 0; !server.computeOnServer(s -> kolobok(s, "prey").getKState() == KState.CAUGHT); i++) {
                if (i >= 600) {
                    throw new AssertionError("The fox never caught him: " + server.computeOnServer(s -> {
                        Fox fox = fox(s);
                        StringBuilder out = new StringBuilder("target=" + fox.getTarget() + " sleeping=" + fox.isSleeping()
                                + " sitting=" + fox.isSitting() + " crouching=" + fox.isCrouching() + " distance="
                                + fox.distanceTo(kolobok(s, "prey")) + " goals:");
                        for (var goal : Goals.goals(fox).getAvailableGoals()) {
                            if (goal.isRunning()) {
                                out.append(' ').append(goal.getGoal().getClass().getSimpleName());
                            }
                        }
                        out.append(" targets:");
                        for (var goal : Goals.targets(fox).getAvailableGoals()) {
                            out.append(' ').append(goal.getGoal().getClass().getSimpleName()).append(goal.isRunning() ? "*" : "");
                        }
                        return out.toString();
                    }));
                }
                context.waitTicks(1);
            }
            context.waitTicks(10);
            context.takeScreenshot("fox-04-hunted");
        }
    }

    private static boolean has(GoalSelector selector, String simpleName) {
        return selector.getAvailableGoals().stream().anyMatch(g -> g.getGoal().getClass().getSimpleName().equals(simpleName));
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

    private static void check(TestServerContext server, String tag, String what, Predicate<KolobokEntity> test) {
        boolean ok = server.computeOnServer(s -> test.test(kolobok(s, tag)));
        if (!ok) {
            String state = server.computeOnServer(s -> {
                KolobokEntity k = kolobok(s, tag);
                Entity vehicle = k.getVehicle();
                return k.getKState() + " health=" + k.getHealth() + " say=" + k.getSayKey()
                        + " vehicle=" + (vehicle == null ? "none" : vehicle.getType().toShortString())
                        + " fox perk=" + k.hasPerk(Perk.FOX) + " foxMayHunt=" + k.foxMayHunt();
            });
            throw new AssertionError("Expected the " + tag + " Kolobok " + what + ", got " + state);
        }
    }

    private static Fox fox(MinecraftServer server) {
        return server.overworld().getEntities(EntityTypes.FOX, e -> true).getFirst();
    }

    private static KolobokEntity kolobok(MinecraftServer server, String tag) {
        for (Entity entity : server.overworld().getAllEntities()) {
            if (entity instanceof KolobokEntity k && k.entityTags().contains(tag)) {
                return k;
            }
        }
        throw new AssertionError("No " + tag + " Kolobok in the world");
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }
}

package ru.bagomot.kolobok.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.registry.KEntities;
import ru.bagomot.kolobok.registry.KItems;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The v1.0 scene: «Сказка о Колобке», the advancements the stove scene does not reach. Each is earned
 * the way a player would earn it, through the mod's own code: dressed and on the head, the Head's
 * sneeze and sword, the crown and the verses, the fox and the berries, the strike, the chicken over
 * lava, the bite, the tan, a concert of six cows and the beetroot. On the way, a dog petted where he
 * sees it makes him jealous. At the end, the tab itself.
 */
public final class TaleVisualTest implements FabricClientGameTest {

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

            server.runCommand("give @a kolobok:rolling_pin");
            server.runCommand("give @a kolobok:dough");
            context.waitTicks(3);
            earned(server, "root", "dough");
            server.runOnServer(s -> player(s).getInventory().clearContent());

            dressAndHead(context, server);
            verses(context, server);
            fox(context, server);
            strike(context, server);
            chickens(context, server);
            biteAndTan(context, server);
            concert(context, server);
            repkaAndJealousy(context, server);
            tab(context);
        }
    }

    /**
     * Tamed, on the head; a metal helmet, the Head's sneeze and sword; the crown, and in it the verses
     * about the Wither and the dragon.
     */
    private static void dressAndHead(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        server.runCommand("summon kolobok:kolobok 0.5 -60 2.5 {Tags:[\"k\"]}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            KolobokEntity k = tagged(s, "k");
            k.tame(player);
            k.setKState(KState.FOLLOW);
            // Shoulder, then head, then down.
            player.setShiftKeyDown(true);
            for (int i = 0; i < 3; i++) {
                interact(player, k, ItemStack.EMPTY);
            }
            player.setShiftKeyDown(false);
        });
        earned(server, "tame", "on_head");

        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            KolobokEntity k = tagged(s, "k");
            k.setKState(KState.SIT);
            interact(player, k, new ItemStack(Items.IRON_HELMET));
            // In front of the player, facing him, with room above to grow into the Head.
            k.teleportTo(0.5, -60.0, 3.5);
            face(k, 180.0F);
            if (!k.startHeadAct(s.overworld())) {
                throw new AssertionError("He should fall asleep as the Head");
            }
        });
        earned(server, "helmet");
        context.waitTicks(25);
        server.runOnServer(s -> interact(player(s), tagged(s, "k"), new ItemStack(Items.FEATHER)));
        check(server, "k", "sneezing", k -> k.getSayKey().equals("golova_sneeze"));
        earned(server, "sneezed_away");
        game(context);
        context.takeScreenshot("tale-01-golova");
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            KolobokEntity k = tagged(s, "k");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            k.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(player), 1.0F);
        });
        check(server, "k", "rolled off, the sword left", k -> k.gaveKladenets() && k.getKState() == KState.SIT);
        earned(server, "kladenets");

        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            interact(player(s), k, new ItemStack(KItems.CROWN.get()));
            k.learnVerse("minecraft:wither");
            k.learnVerse("minecraft:ender_dragon");
        });
        earned(server, "crown", "immortal");
        server.runCommand("kill @e[type=item]");
        server.runOnServer(s -> player(s).getInventory().clearContent());
    }

    /** The wolf, then ten mobs in all, the ravager and the Warden among them. */
    private static void verses(ClientGameTestContext context, TestServerContext server) {
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.learnVerse("minecraft:wolf");
        });
        earned(server, "wolf");
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            for (String mob : List.of("minecraft:ravager", "minecraft:warden", "minecraft:zombie", "minecraft:skeleton",
                    "minecraft:creeper", "minecraft:spider", "minecraft:rabbit")) {
                k.learnVerse(mob);
            }
            if (k.verses().size() != 10) {
                throw new AssertionError("Ten verses expected: " + k.verses());
            }
        });
        earned(server, "ravager", "warden", "sigma");
        clear(server);
    }

    /** In a fox's mouth; berries thrown by the player get him out, and the player earns it. */
    private static void fox(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 20");
        server.runCommand("summon kolobok:kolobok 0.5 -60 3.5 {Tags:[\"f\"]}");
        server.runCommand("summon minecraft:fox 0.5 -60 4.5 {NoAI:1b}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "f");
            k.tame(player(s));
            k.setKState(KState.FOLLOW);
            Fox fox = s.overworld().getEntitiesOfClass(Fox.class, k.getBoundingBox().inflate(4.0)).getFirst();
            k.hurtServer(s.overworld(), s.overworld().damageSources().mobAttack(fox), 1.0F);
        });
        check(server, "f", "in the fox's mouth", k -> k.getKState() == KState.CAUGHT);
        server.runOnServer(s -> {
            ItemEntity berries = new ItemEntity(s.overworld(), 0.5, -59.5, 2.0, new ItemStack(Items.SWEET_BERRIES));
            berries.setThrower(player(s));
            berries.setPickUpDelay(200);
            s.overworld().addFreshEntity(berries);
        });
        waitFor(context, server, 40, "the berries to get him out", s -> tagged(s, "f").getKState() != KState.CAUGHT);
        earned(server, "fox");
        server.runCommand("kill @e[type=item]");
        clear(server);
    }

    /** «Катись!» through ten chickens in a row. */
    private static void strike(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        server.runCommand("summon kolobok:kolobok 0.5 -60 2.0 {Tags:[\"k\"]}");
        for (int i = 0; i < 10; i++) {
            server.runCommand("summon minecraft:chicken 0.5 -60 " + (5.5 + i) + " {Tags:[\"pin\"]}");
        }
        server.runCommand("effect give @e[tag=pin] minecraft:slowness infinite 255 true");
        context.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            KolobokEntity k = tagged(s, "k");
            k.tame(player);
            k.setKState(KState.SIT);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(KItems.ROLLING_PIN.get()));
            player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(0.5, -59.7, 17.0));
            player.getMainHandItem().use(s.overworld(), player, InteractionHand.MAIN_HAND);
        });
        waitFor(context, server, 80, "the roll to end", s -> tagged(s, "k").getKState() != KState.BOWLING);
        check(server, "k", "a strike", k -> k.getSayKey().equals("strike"));
        earned(server, "strike");
        server.runOnServer(s -> player(s).getInventory().clearContent());
        clear(server);
    }

    /**
     * On a chicken; then on one gliding down over lava, which turns to grass under them before they
     * land, so the landing is whole.
     */
    private static void chickens(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 20.5 -60 -4.5 0 -10");
        server.runCommand("fill 19 -61 -1 21 -61 1 minecraft:lava");
        server.runCommand("summon kolobok:kolobok 20.5 -60 -2.5 {Tags:[\"c\"]}");
        server.runCommand("summon minecraft:chicken 20.5 -55 0.5 {Tags:[\"hen\"]}");
        context.waitTicks(2);
        boolean riding = server.computeOnServer(s -> {
            KolobokEntity k = tagged(s, "c");
            k.tame(player(s));
            k.setKState(KState.FOLLOW);
            Chicken hen = s.overworld().getEntitiesOfClass(Chicken.class, new AABB(18, -62, -2, 23, -50, 3)).getFirst();
            return k.rideChicken(hen);
        });
        if (!riding) {
            throw new AssertionError("He should be riding the chicken");
        }
        earned(server, "chicken_jockey");
        context.waitTicks(12);
        game(context);
        context.takeScreenshot("tale-02-lava-chicken");
        boolean airborne = server.computeOnServer(s -> tagged(s, "c").getVehicle() instanceof Chicken hen && !hen.onGround());
        if (!airborne) {
            throw new AssertionError("The chicken should still be gliding down");
        }
        server.runCommand("fill 19 -61 -1 21 -61 1 minecraft:grass_block");
        waitFor(context, server, 120, "the landing", s -> s.getAdvancements().get(Kolobok.id("tale/lava_chicken")) != null
                && player(s).getAdvancements().getOrStartProgress(s.getAdvancements().get(Kolobok.id("tale/lava_chicken"))).isDone());
        clear(server);
    }

    /** A hungry owner bites a sitting Kolobok; fire gives another a tan. */
    private static void biteAndTan(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        server.runCommand("summon kolobok:kolobok 0.5 -60 2.0 {Tags:[\"k\"]}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            KolobokEntity k = tagged(s, "k");
            k.tame(player);
            k.setKState(KState.SIT);
            player.getFoodData().setFoodLevel(4);
            player.setShiftKeyDown(true);
            interact(player, k, ItemStack.EMPTY);
            player.setShiftKeyDown(false);
            k.hurtServer(s.overworld(), s.overworld().damageSources().inFire(), 2.0F);
        });
        check(server, "k", "bitten and tanned", k -> k.hasFlag(KolobokEntity.FLAG_BITTEN) && k.hasFlag(KolobokEntity.FLAG_TAN));
        earned(server, "bite", "tan");
        server.runOnServer(s -> player(s).getFoodData().setFoodLevel(20));
        clear(server);
    }

    /** Six cows close by hear his whole song. */
    private static void concert(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 -3.5 0 20");
        server.runCommand("summon kolobok:kolobok 0.5 -60 0.5 {Tags:[\"k\"]}");
        // A ring of cows close enough not to be called up, each with its back to him: the hardest
        // audience to turn. Free to walk, they would stroll off unless the song holds them.
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * 2.0 * i / 6.0;
            float away = (float) Math.toDegrees(Math.atan2(-Math.cos(angle), Math.sin(angle)));
            server.runCommand("summon minecraft:cow " + (0.5 + Math.cos(angle) * 2.25) + " -60 " + (0.5 + Math.sin(angle) * 2.25)
                    + " {Tags:[\"cow\"],Rotation:[" + away + "f,0f]}");
        }
        context.waitTicks(3);
        // A cow the song annoys would stop it with a butt.
        server.runOnServer(s -> KConfig.server().annoyingSong = false);
        int ticks = server.computeOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.tame(player(s));
            k.setKState(KState.SIT);
            k.startSong(0);
            return k.composeSong().ticks();
        });
        // Every cow stays and watches him, head and, a moment later, body too, whatever the player does.
        context.waitTicks(80);
        String astray = server.computeOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            StringBuilder out = new StringBuilder();
            for (Mob cow : s.overworld().getEntitiesOfClass(Mob.class, k.getBoundingBox().inflate(8.0),
                    mob -> mob.entityTags().contains("cow"))) {
                Vec3 to = k.position().subtract(cow.position());
                float want = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                float head = Math.abs(Mth.wrapDegrees(cow.getYHeadRot() - want));
                float body = Math.abs(Mth.wrapDegrees(cow.yBodyRot - want));
                double off = Math.sqrt(to.horizontalDistanceSqr());
                if (head > 20.0F || body > 45.0F || off > 3.0) {
                    out.append(String.format(" head %.0f° body %.0f° off, %.1f blocks away;", head, body, off));
                }
            }
            return out.toString();
        });
        if (!astray.isEmpty()) {
            throw new AssertionError("Cows do not stand watching the singer:" + astray);
        }
        game(context);
        context.takeScreenshot("tale-03-concert");
        waitFor(context, server, ticks + 40, "the song to end", s -> !tagged(s, "k").isSinging());
        server.runOnServer(s -> KConfig.server().annoyingSong = true);
        earned(server, "concert");
        clear(server);
    }

    /**
     * The player pets his dog where his Kolobok sees it: he is jealous. Then the beetroot comes up with
     * the dog, the cat and Kolobok about.
     */
    private static void repkaAndJealousy(ClientGameTestContext context, TestServerContext server) {
        TestInput input = context.getInput();
        server.runCommand("tp @a 0.5 -60 -1.5 0 30");
        server.runCommand("setblock 0 -61 3 minecraft:farmland");
        server.runCommand("setblock 0 -60 3 minecraft:beetroots[age=3]");
        server.runCommand("summon minecraft:wolf 0.5 -60 0.5 {Tags:[\"pet\"]}");
        server.runCommand("summon minecraft:cat -1.5 -60 3.5 {Tags:[\"pet\"]}");
        server.runCommand("summon kolobok:kolobok 2.5 -60 1.5 {Tags:[\"k\"]}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            KolobokEntity k = tagged(s, "k");
            k.tame(player);
            k.setKState(KState.SIT);
            for (Wolf wolf : s.overworld().getEntitiesOfClass(Wolf.class, new AABB(-5, -62, -5, 6, -55, 6))) {
                wolf.tame(player);
                wolf.setOrderedToSit(true);
            }
            for (var cat : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.animal.feline.Cat.class,
                    new AABB(-5, -62, -5, 6, -55, 6))) {
                cat.tame(player);
                cat.setOrderedToSit(true);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(0.5, -59.6, 0.5));
        });
        // A real right-click on the dog, so that each loader's own interaction event sees it.
        context.waitTicks(3);
        input.pressKey(options -> options.keyUse);
        context.waitTicks(4);
        check(server, "k", "jealous", k -> k.getSayKey().equals("jealous"));
        game(context);
        context.takeScreenshot("tale-04-jealous");

        server.runOnServer(s -> player(s).gameMode.destroyBlock(new net.minecraft.core.BlockPos(0, -60, 3)));
        context.waitTicks(3);
        earned(server, "repka");
        server.runCommand("kill @e[type=item]");
        clear(server);
    }

    /** The tab, as the player opens it. */
    private static void tab(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            ClientAdvancements advancements = mc.getConnection().getAdvancements();
            AdvancementHolder root = advancements.get(Kolobok.id("tale/root"));
            if (root == null) {
                throw new AssertionError("The client knows no «Сказка о Колобке» tab");
            }
            advancements.setSelectedTab(root, true);
        });
        context.setScreen(() -> new AdvancementsScreen(Minecraft.getInstance().getConnection().getAdvancements()));
        context.waitTicks(10);
        context.takeScreenshot("tale-05-tab");
        context.setScreen(() -> null);
    }

    private static void face(KolobokEntity k, float yaw) {
        k.setYRot(yaw);
        k.setYBodyRot(yaw);
        k.setYHeadRot(yaw);
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

    private static void interact(ServerPlayer player, KolobokEntity k, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        k.mobInteract(player, InteractionHand.MAIN_HAND);
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
        throw new AssertionError("Waited " + ticks + " ticks for " + what);
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

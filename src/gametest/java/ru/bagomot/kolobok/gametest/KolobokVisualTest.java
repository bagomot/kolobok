package ru.bagomot.kolobok.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.RideSlot;
import ru.bagomot.kolobok.registry.KEntities;

/**
 * Walks a real client through the v0.1 scene and saves a screenshot at every step: the five doughs,
 * taming with milk and the bubble, the shoulder, the head, getting off with a double Shift, and
 * rolling after the owner. Each step also checks the server-side state, so a broken interaction
 * fails the run instead of producing a pretty but wrong picture.
 */
public final class KolobokVisualTest implements FabricClientGameTest {

    /** The classic one stands in the middle, straight ahead of the player. */
    private static final String[] VARIANTS = {"borodinsky", "golden", "classic", "moon", "mini"};

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            TestServerContext server = game.getServer();
            TestInput input = context.getInput();
            game.getConnection().waitForChunksRender();

            server.runOnServer(s -> KConfig.server().tameChanceDenominator = 1);
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0.5 -60 0.5 180 28");
            for (int i = 0; i < VARIANTS.length; i++) {
                double x = -1.5 + i;
                server.runCommand("summon kolobok:kolobok " + x + " -60 -2.0 {NoAI:1b,Rotation:[0f,0f],Variant:\""
                        + VARIANTS[i] + "\",Tags:[\"" + VARIANTS[i] + "\"]}");
            }
            context.waitTicks(30);
            game.getConnection().waitForChunksRender();
            context.takeScreenshot("kolobok-01-variants");

            // Milk tames him (chance forced to 1 above) and he answers in the bubble.
            server.runCommand("item replace entity @a weapon.mainhand with milk_bucket");
            context.waitTicks(2);
            input.pressKey(options -> options.keyUse);
            context.waitTicks(6);
            check(server, "tamed", k -> k.isTame() && k.getKState() == KState.FOLLOW);
            context.takeScreenshot("kolobok-02-tamed");

            // Shift and right-click with an empty hand: onto the shoulder.
            server.runCommand("item replace entity @a weapon.mainhand with air");
            context.waitTicks(2);
            input.holdKey(options -> options.keyShift);
            context.waitTicks(3);
            input.pressKey(options -> options.keyUse);
            context.waitTicks(3);
            input.releaseKey(options -> options.keyShift);
            context.waitTicks(15);
            check(server, "on the shoulder", k -> k.getKState() == KState.ON_PLAYER && k.getRideSlot().isShoulder());
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(3);
            context.takeScreenshot("kolobok-03-shoulder");

            // Again: onto the head. Aiming at one's own shoulder from the front camera is not
            // reproducible, so this step goes straight to the interaction the click would make.
            server.runOnServer(s -> {
                ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
                player.setShiftKeyDown(true);
                find(s, "classic").mobInteract(player, InteractionHand.MAIN_HAND);
                player.setShiftKeyDown(false);
            });
            context.waitTicks(15);
            check(server, "on the head", k -> k.getKState() == KState.ON_PLAYER && k.getRideSlot() == RideSlot.HEAD);
            context.takeScreenshot("kolobok-04-head");

            // Two quick presses of Shift: down in front of the player.
            input.holdKeyFor(options -> options.keyShift, 2);
            context.waitTicks(2);
            input.holdKeyFor(options -> options.keyShift, 2);
            context.waitTicks(10);
            check(server, "off the player", k -> k.getKState() == KState.FOLLOW);
            context.takeScreenshot("kolobok-05-dismounted");

            // Let him follow: the owner steps away and he rolls after him, face going under.
            server.runCommand("data merge entity @e[tag=classic,limit=1] {NoAI:0b}");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("tp @a 0.5 -60 9.5 180 20");
            context.waitTicks(12);
            int id = server.computeOnServer(s -> find(s, "classic").getId());
            float roll = context.computeOnClient(mc -> clientKolobok(mc, id).clientRoll);
            context.takeScreenshot("kolobok-06-rolling");
            if (roll == 0.0F) {
                throw new AssertionError("He moved to follow but did not roll");
            }
            context.waitTicks(60);
            context.takeScreenshot("kolobok-07-arrived");
        }
    }

    private static void check(TestServerContext server, String what, java.util.function.Predicate<KolobokEntity> test) {
        boolean ok = server.computeOnServer(s -> test.test(find(s, "classic")));
        if (!ok) {
            String state = server.computeOnServer(s -> {
                KolobokEntity k = find(s, "classic");
                return k.getKState() + " " + k.getRideSlot() + " tame=" + k.isTame();
            });
            throw new AssertionError("Expected him " + what + ", got " + state);
        }
    }

    private static KolobokEntity find(MinecraftServer server, String tag) {
        for (KolobokEntity entity : server.overworld().getEntities(KEntities.KOLOBOK.get(), e -> e.entityTags().contains(tag))) {
            return entity;
        }
        throw new AssertionError("No Kolobok tagged " + tag);
    }

    private static KolobokEntity clientKolobok(Minecraft mc, int id) {
        Entity entity = mc.level.getEntity(id);
        if (entity instanceof KolobokEntity kolobok) {
            return kolobok;
        }
        throw new AssertionError("The client does not see Kolobok " + id);
    }
}

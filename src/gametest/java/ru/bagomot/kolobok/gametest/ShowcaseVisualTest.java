package ru.bagomot.kolobok.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import ru.bagomot.kolobok.block.StovePattern;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Variant;
import ru.bagomot.kolobok.guide.GuideBook;
import ru.bagomot.kolobok.registry.KEntities;
import ru.bagomot.kolobok.registry.KItems;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Not a test but the README's pictures: run with {@code -Pshowcase}, this scene stages the mod at
 * its best and saves 1280×720 JPEGs, most without the HUD, into {@code media/screenshots}. Without
 * that property it does nothing. Run with {@code -Pcompat=jei,jade} as well, it adds Jade and JEI.
 */
public final class ShowcaseVisualTest implements FabricClientGameTest {

    /** Where the pictures go; set by the build for {@code -Pshowcase} only. */
    private static final String DIR = System.getProperty("kolobok.showcase", "");
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final BlockPos MOUTH = new BlockPos(0, -60, -4);
    /** Other scenes stand away from the stove, along x. */
    private static final int AWAY = 40;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (DIR.isEmpty()) {
            return;
        }
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            TestServerContext server = game.getServer();
            context.getInput().resizeWindow(WIDTH, HEIGHT);
            game.getConnection().waitForChunksRender();
            server.runCommand("time set 1000");
            server.runCommand("weather clear");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            meadow(server, 0);
            meadow(server, AWAY);
            meadow(server, 2 * AWAY);

            stove(context, server, game);
            hud(context, false);
            family(context, server);
            outfits(context, server);
            shoulder(context, server);
            concert(context, server);
            theHead(context, server);
            fox(context, server);
            hud(context, true);
            book(context, server);
            if (FabricLoader.getInstance().isModLoaded("jade")) {
                jade(context, server);
            }
            if (FabricLoader.getInstance().isModLoaded("jei")) {
                JeiScreens.showcase(context);
            }
        }
    }

    /** Flowers and grass round a clearing at x, so the flat world looks like somewhere. */
    private static void meadow(TestServerContext server, int x) {
        String[] plants = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "short_grass",
                "short_grass", "short_grass", "allium", "short_grass"};
        long seed = 7;
        for (int i = 0; i < 160; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int dx = (int) ((seed >>> 33) % 31) - 15;
            int dz = (int) ((seed >>> 13) % 31) - 15;
            // The clearing: where the Kolobki stand and the camera looks from.
            if (Math.abs(dx) < 6 && dz > -8 && dz < 6) {
                continue;
            }
            server.runCommand("setblock " + (x + dx) + " -60 " + dz + " minecraft:" + plants[i % plants.length]);
        }
    }

    /** The stove, lit, a Kolobok baking in the горнило, a cat and a resting Kolobok on top. */
    private static void stove(ClientGameTestContext context, TestServerContext server, TestSingleplayerContext game) {
        TestInput input = context.getInput();
        server.runCommand("fill -1 -60 -5 1 -59 -4 minecraft:bricks");
        server.runCommand("setblock 0 -60 -4 minecraft:furnace[facing=south]");
        server.runCommand("setblock 1 -58 -5 minecraft:bricks");
        server.runCommand("tp @a 0.5 -60 -1.5 180 30");
        context.waitTicks(10);
        server.runCommand("item replace entity @a weapon.mainhand with kolobok:rolling_pin");
        context.waitTicks(3);
        input.holdKey(options -> options.keyShift);
        context.waitTicks(3);
        input.pressKey(options -> options.keyUse);
        context.waitTicks(3);
        input.releaseKey(options -> options.keyShift);
        context.waitTicks(5);
        for (String item : new String[]{"minecraft:oak_log 16", "kolobok:raw_kolobok", "minecraft:flint_and_steel"}) {
            server.runCommand("item replace entity @a weapon.mainhand with " + item);
            context.waitTicks(2);
            input.pressKey(options -> options.keyUse);
            context.waitTicks(4);
        }
        server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
        server.runOnServer(s -> {
            if (!StovePattern.isIntact(s.overworld(), MOUTH, Direction.SOUTH)) {
                throw new AssertionError("The showcase stove did not go up");
            }
            List<Vec3> top = StovePattern.topSlots(MOUTH, Direction.SOUTH);
            Vec3 cat = top.get(1);
            Vec3 resting = top.get(2);
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "summon minecraft:cat "
                    + cat.x + " " + cat.y + " " + cat.z + " {NoAI:1b,Sitting:1b,PersistenceRequired:1b,Tags:[\"top\"],Rotation:[200f,0f]}");
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "summon kolobok:kolobok "
                    + resting.x + " " + resting.y + " " + resting.z + " {NoAI:1b,Tags:[\"top\"],Rotation:[323f,0f]}");
        });
        context.waitTicks(3);
        server.runOnServer(s -> tagged(s, "top").setVariant(Variant.BORODINSKY));
        // At sunset, when the fire in the горнило shows and the smoke stands against the sky.
        server.runCommand("time set 12600");
        server.runCommand("tp @a 4.1 -59.6 0.5 143 6");
        hud(context, false);
        context.waitTicks(120);
        game.getConnection().waitForChunksRender();
        boolean lit = server.computeOnServer(s -> s.overworld().getBlockEntity(MOUTH)
                instanceof ru.bagomot.kolobok.block.StoveBlockEntity stove && stove.isLit());
        if (!lit) {
            throw new AssertionError("The showcase stove is not lit");
        }
        shot(context, "stove");
        hud(context, true);
        server.runCommand("time set 1000");
    }

    /** Every dough in a row: wheat, Borodinsky rye, golden, lunar and the little Kolobochek. */
    private static void family(ClientGameTestContext context, TestServerContext server) {
        int x = AWAY;
        Variant[] variants = {Variant.CLASSIC, Variant.BORODINSKY, Variant.GOLDEN, Variant.MOON, Variant.MINI};
        for (int i = 0; i < variants.length; i++) {
            server.runCommand("summon kolobok:kolobok " + (x + 0.5 - 2.4 + i * 1.2) + " -60 0.5 {NoAI:1b,Rotation:[0f,0f],Tags:[\"v" + i + "\"]}");
        }
        context.waitTicks(3);
        server.runOnServer(s -> {
            for (int i = 0; i < variants.length; i++) {
                tagged(s, "v" + i).setVariant(variants[i]);
            }
        });
        server.runCommand("tp @a " + (x + 0.5) + " -59.4 3.9 180 18");
        context.waitTicks(30);
        shot(context, "family");
        clear(server);
    }

    /** Helmets, his own hats, лапти and sunglasses. */
    private static void outfits(ClientGameTestContext context, TestServerContext server) {
        int x = AWAY;
        List<ItemStack> heads = List.of(new ItemStack(Items.IRON_HELMET), dyed(Items.LEATHER_HELMET, 0x3B6FD8),
                new ItemStack(KItems.CROWN.get()), new ItemStack(KItems.WREATH.get()), new ItemStack(KItems.KOKOSHNIK.get()),
                new ItemStack(Items.GOLDEN_HELMET));
        List<ItemStack> feet = List.of(ItemStack.EMPTY, new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.GOLDEN_BOOTS),
                ItemStack.EMPTY, dyed(Items.LEATHER_BOOTS, 0xC03030), new ItemStack(Items.IRON_BOOTS));
        for (int i = 0; i < heads.size(); i++) {
            server.runCommand("summon kolobok:kolobok " + (x + 0.5 - 2.5 * 1.1 + i * 1.1) + " -60 0.5 {NoAI:1b,Rotation:[0f,0f],Tags:[\"o" + i + "\"]}");
        }
        context.waitTicks(3);
        server.runOnServer(s -> {
            for (int i = 0; i < heads.size(); i++) {
                KolobokEntity k = tagged(s, "o" + i);
                k.setItemSlot(EquipmentSlot.HEAD, heads.get(i).copy());
                k.setItemSlot(EquipmentSlot.FEET, feet.get(i).copy());
                k.setFlag(KolobokEntity.FLAG_GLASSES, i == 0 || i == 5);
            }
        });
        server.runCommand("tp @a " + (x + 0.5) + " -59.4 3.7 180 18");
        context.waitTicks(30);
        shot(context, "outfits");
        clear(server);
    }

    /** The player, seen from the front, with one Kolobok on the shoulder and another on the head. */
    private static void shoulder(ClientGameTestContext context, TestServerContext server) {
        int x = AWAY;
        server.runCommand("tp @a " + (x + 0.5) + " -60 0.5 0 0");
        server.runCommand("summon kolobok:kolobok " + (x + 1.5) + " -60 1.5 {Tags:[\"head\"]}");
        server.runCommand("summon kolobok:kolobok " + (x - 0.5) + " -60 1.5 {Tags:[\"shoulder\"]}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(KItems.ROLLING_PIN.get()));
            KolobokEntity head = tagged(s, "head");
            KolobokEntity shoulder = tagged(s, "shoulder");
            head.tame(player);
            shoulder.tame(player);
            head.setVariant(Variant.GOLDEN);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            ItemStack pin = player.getMainHandItem();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);
            head.mobInteract(player, InteractionHand.MAIN_HAND);
            head.mobInteract(player, InteractionHand.MAIN_HAND);
            shoulder.mobInteract(player, InteractionHand.MAIN_HAND);
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, pin);
        });
        context.waitTicks(20);
        boolean seated = server.computeOnServer(s -> tagged(s, "head").getKState() == KState.ON_PLAYER
                && tagged(s, "shoulder").getKState() == KState.ON_PLAYER);
        if (!seated) {
            throw new AssertionError("They did not get on the player");
        }
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(10);
        shot(context, "shoulder");
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        clear(server);
    }

    /** A song with cows round him to listen, the line he sings in the bubble. */
    private static void concert(ClientGameTestContext context, TestServerContext server) {
        int x = 2 * AWAY;
        server.runCommand("summon kolobok:kolobok " + (x + 0.5) + " -60 0.5 {Tags:[\"k\"],Rotation:[0f,0f]}");
        for (int i = 0; i < 5; i++) {
            double angle = Math.PI * (1.15 + 0.175 * i);
            server.runCommand("summon minecraft:cow " + (x + 0.5 + Math.cos(angle) * 2.4) + " -60 " + (0.5 + Math.sin(angle) * 2.4)
                    + " {Tags:[\"cow\"]}");
        }
        context.waitTicks(3);
        server.runOnServer(s -> KConfig.server().annoyingSong = false);
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.tame(player(s));
            k.setKState(KState.SIT);
            k.startSong(0);
        });
        server.runCommand("tp @a " + (x + 0.5) + " -59.5 3.4 180 14");
        context.waitTicks(90);
        shot(context, "concert");
        server.runOnServer(s -> KConfig.server().annoyingSong = true);
        clear(server);
    }

    /** «Голова»: asleep in a metal helmet, he dreams he is the giant Head from «Руслан и Людмила». */
    private static void theHead(ClientGameTestContext context, TestServerContext server) {
        int x = 2 * AWAY;
        server.runCommand("summon kolobok:kolobok " + (x + 0.5) + " -60 0.5 {Tags:[\"k\"],Rotation:[0f,0f]}");
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
        server.runCommand("tp @a " + (x + 2.6) + " -59.6 3.4 145 12");
        context.waitTicks(40);
        shot(context, "golova");
        clear(server);
    }

    /** A fox carrying him off in its teeth. */
    private static void fox(ClientGameTestContext context, TestServerContext server) {
        int x = 2 * AWAY;
        server.runCommand("summon minecraft:fox " + (x - 0.5) + " -60 0.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"fox\"],Rotation:[250f,0f]}");
        server.runCommand("summon kolobok:kolobok " + (x + 0.6) + " -60 0.5 {NoAI:1b,Tags:[\"k\"],Rotation:[90f,0f]}");
        context.waitTicks(5);
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.tame(player(s));
            k.setKState(KState.WANDER);
        });
        server.runCommand("damage @e[tag=k,limit=1] 2 minecraft:mob_attack by @e[tag=fox,limit=1]");
        context.waitTicks(10);
        boolean caught = server.computeOnServer(s -> tagged(s, "k").getKState() == KState.CAUGHT);
        if (!caught) {
            throw new AssertionError("The fox did not catch him");
        }
        server.runCommand("tp @a " + (x + 0.9) + " -59.9 2.7 160 14");
        context.waitTicks(20);
        shot(context, "fox");
        clear(server);
    }

    /** The guide, open at the stove's layout. */
    private static void book(ClientGameTestContext context, TestServerContext server) {
        TestInput input = context.getInput();
        server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, GuideBook.writtenBook()));
        server.runCommand("tp @a 0.5 -60 3.5 180 -10");
        context.waitTicks(3);
        input.pressKey(options -> options.keyUse);
        context.waitForScreen(BookViewScreen.class);
        for (int i = 0; i < 4; i++) {
            input.pressKey(InputConstants.KEY_PAGEDOWN);
            context.waitTicks(2);
        }
        context.waitTicks(5);
        shot(context, "guide");
        context.setScreen(() -> null);
        server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
    }

    /** Jade on the stove. */
    private static void jade(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 -1.4 180 12");
        context.waitTicks(30);
        GuideVisualTest.quiet(context);
        shot(context, "jade");
    }

    /** F1: the HUD shown or hidden. */
    private static void hud(ClientGameTestContext context, boolean shown) {
        context.runOnClient(mc -> {
            if (mc.gui.hud.isHidden() == shown) {
                mc.gui.hud.toggle();
            }
        });
    }

    /** A picture into the showcase directory, as a JPEG: a tenth of the PNG's size, for the repository's sake. */
    static void shot(ClientGameTestContext context, String name) {
        GuideVisualTest.quiet(context);
        Path png = context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(Path.of(DIR)).disableCounterPrefix());
        try {
            BufferedImage image = ImageIO.read(png.toFile());
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.getGraphics().drawImage(image, 0, 0, null);
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.9F);
            try (ImageOutputStream out = ImageIO.createImageOutputStream(Path.of(DIR, name + ".jpg").toFile())) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            Files.delete(png);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ItemStack dyed(Item item, int colour) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(colour));
        return stack;
    }

    private static void clear(TestServerContext server) {
        server.runOnServer(s -> {
            List<Entity> all = new ArrayList<>();
            s.overworld().getAllEntities().forEach(entity -> {
                // What sits on the stove stays for the Jade picture.
                if (entity != null && !(entity instanceof Player) && !entity.entityTags().contains("top")) {
                    all.add(entity);
                }
            });
            all.forEach(Entity::discard);
        });
    }

    private static KolobokEntity tagged(MinecraftServer server, String tag) {
        for (KolobokEntity entity : server.overworld().getEntities(KEntities.KOLOBOK.get(), e -> e.isAlive() && e.entityTags().contains(tag))) {
            return entity;
        }
        throw new AssertionError("No Kolobok tagged " + tag);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }
}

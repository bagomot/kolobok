package ru.bagomot.kolobok.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Mood;
import ru.bagomot.kolobok.entity.RideSlot;
import ru.bagomot.kolobok.entity.Variant;
import ru.bagomot.kolobok.item.StoredKolobok;
import ru.bagomot.kolobok.registry.KComponents;
import ru.bagomot.kolobok.registry.KEntities;
import ru.bagomot.kolobok.registry.KItems;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The v0.6 scene: a goat horn calls him from near and far; he goes into a bindle and out of it with
 * a new id, and a dropped full bindle never despawns; the rolling pin bowls him through a line of
 * chickens for a strike; on the shoulder he spots a creeper hissing behind, on the head he takes a
 * falling anvil's bonk; his cheeks fill with food and he brings it to his owner; he rides a chicken;
 * soggy, tanned, bitten and dancing to a jukebox; he shows the way to a village leaving crumbs; and
 * the dog, the cat and Kolobok pull up six more beetroots.
 */
public final class SkillsVisualTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            TestServerContext server = game.getServer();
            game.getConnection().waitForChunksRender();

            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamerule spawn_mobs false");
            clear(server);

            horn(context, server);
            bindle(context, server);
            bowling(context, server);
            shoulderAndHead(context, server);
            cheeks(context, server);
            chicken(context, server);
            states(context, server);
            guide(context, server);
            repka(context, server);
        }
    }

    /** Any goat horn his owner blows calls him: walking from close by, fetched from far off. */
    private static void horn(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runCommand("tp @a 0.5 -60 0.5 0 10");
        server.runCommand("summon kolobok:kolobok 0.5 -60 14.5 {Tags:[\"k\"]}");
        server.runCommand("summon kolobok:kolobok 0.5 -60 40.5 {Tags:[\"far\"]}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            for (String tag : List.of("k", "far")) {
                KolobokEntity k = tagged(s, tag);
                k.tame(player(s));
                k.setKState(KState.SIT);
            }
        });
        server.runCommand("item replace entity @a weapon.mainhand with minecraft:goat_horn[minecraft:instrument=\"minecraft:ponder_goat_horn\"]");
        context.waitTicks(2);
        server.runOnServer(s -> player(s).getMainHandItem().use(s.overworld(), player(s), InteractionHand.MAIN_HAND));
        context.waitTicks(10);
        check(server, "k", "following at the horn's call", k -> k.getKState() == KState.FOLLOW && k.getSayKey().equals("horn"));
        check(server, "far", "fetched from far off", k -> k.getKState() == KState.FOLLOW && k.distanceTo(owner(k)) < 8.0F);
        waitFor(context, server, 200, "him to come", s -> tagged(s, "k").distanceTo(player(s)) < 4.0F);
        game(context);
        context.takeScreenshot("skills-01-horn");
        server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        // Until the horn is ready again, a Kolobok tamed later would take it for a fresh call.
        waitFor(context, server, 200, "the horn to be ready", s -> !player(s).getCooldowns().isOnCooldown(Items.GOAT_HORN.getDefaultInstance()));
        clear(server);
    }

    /**
     * Into his owner's bindle whole — name, dough, verses — and out of it on a block with an id of his
     * own; a full bindle dropped on the ground never despawns.
     */
    private static void bindle(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 3.5 180 25");
        server.runCommand("summon kolobok:kolobok 0.5 -60 0.5 {Tags:[\"k\"]}");
        context.waitTicks(3);
        UUID before = server.computeOnServer(s -> {
            ServerPlayer player = player(s);
            KolobokEntity k = tagged(s, "k");
            k.tame(player);
            k.setKState(KState.SIT);
            k.setVariant(Variant.BORODINSKY);
            k.setCustomName(Component.literal("Тимоша"));
            k.learnVerse("minecraft:zombie");
            UUID id = k.getUUID();
            interact(player, k, new ItemStack(KItems.BINDLE.get()));
            return id;
        });
        context.waitTicks(2);
        int left = server.computeOnServer(s -> s.overworld().getEntities(KEntities.KOLOBOK.get(), Entity::isAlive).size());
        StoredKolobok stored = server.computeOnServer(s -> player(s).getMainHandItem().get(KComponents.STORED_KOLOBOK.get()));
        if (left != 0 || stored == null || stored.variant() != Variant.BORODINSKY || stored.verses() != 1
                || stored.name().map(name -> !name.getString().equals("Тимоша")).orElse(true)) {
            throw new AssertionError("He should be in the bindle, whole: " + left + " left, " + stored);
        }
        // Out on the grass in front of the player.
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            player.getInventory().setItem(1, new ItemStack(KItems.BINDLE.get()));
            BlockHitResult grass = new BlockHitResult(new Vec3(0.5, -61.0, 0.5), Direction.UP, new BlockPos(0, -61, 0), false);
            player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, grass));
        });
        context.waitTicks(3);
        boolean out = server.computeOnServer(s -> {
            KolobokEntity k = s.overworld().getEntities(KEntities.KOLOBOK.get(), Entity::isAlive).getFirst();
            return !k.getUUID().equals(before) && k.getVariant() == Variant.BORODINSKY && k.verses().size() == 1
                    && k.isOwnedBy(player(s)) && k.getKState() == KState.SIT && k.getSayKey().equals("bindle_out")
                    && !player(s).getMainHandItem().has(KComponents.STORED_KOLOBOK.get());
        });
        if (!out) {
            throw new AssertionError("He should be out of the bindle, the same but for his id, and the bindle empty");
        }
        // The empty bindle in hand, the full one beside it, to see both icons.
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            KolobokEntity k = s.overworld().getEntities(KEntities.KOLOBOK.get(), Entity::isAlive).getFirst();
            ItemStack spare = new ItemStack(KItems.BINDLE.get());
            interact(player, k, spare);
            player.getInventory().setItem(2, spare);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        server.runCommand("summon kolobok:kolobok 0.5 -60 0.5 {NoAI:1b,Rotation:[0f,0f]}");
        context.waitTicks(5);
        game(context);
        context.takeScreenshot("skills-02-bindle");

        // Dropped full on the ground, it stays for good.
        server.runOnServer(s -> {
            ItemStack full = player(s).getInventory().getItem(2).copy();
            player(s).getInventory().setItem(2, ItemStack.EMPTY);
            ItemEntity item = new ItemEntity(s.overworld(), 2.5, -59.5, 2.5, full);
            s.overworld().addFreshEntity(item);
        });
        context.waitTicks(3);
        int age = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(-5, -62, -5, 6, -55, 6),
                item -> item.getItem().has(KComponents.STORED_KOLOBOK.get())).getFirst().getAge());
        if (age > -30000) {
            throw new AssertionError("A dropped full bindle should never despawn, its age is " + age);
        }
        server.runOnServer(s -> player(s).getInventory().clearContent());
        clear(server);
    }

    /** «Катись!»: the rolling pin bowls him through ten chickens in a row: a strike, and his head spins. */
    private static void bowling(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("gamemode creative @a");
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
        context.waitTicks(6);
        check(server, "k", "bowling", k -> k.getKState() == KState.BOWLING);
        game(context);
        context.takeScreenshot("skills-03-bowling");
        waitFor(context, server, 80, "the roll to end", s -> tagged(s, "k").getKState() != KState.BOWLING);
        check(server, "k", "a strike, dizzy, sitting again", k -> k.getSayKey().equals("strike") && k.getMood() == Mood.DIZZY
                && k.getKState() == KState.SIT);
        context.waitTicks(10);
        game(context);
        context.takeScreenshot("skills-04-strike");
        server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        clear(server);
    }

    /**
     * On the shoulder he spots a creeper hissing behind his owner and makes it glow; on the head he is
     * two points of armour and takes a falling anvil's bonk, neither of them hurt.
     */
    private static void shoulderAndHead(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        server.runCommand("summon kolobok:kolobok 0.5 -60 1.5 {Tags:[\"k\"]}");
        context.waitTicks(3);
        int armour = server.computeOnServer(s -> {
            ServerPlayer player = player(s);
            KolobokEntity k = tagged(s, "k");
            k.tame(player);
            k.setKState(KState.FOLLOW);
            player.setShiftKeyDown(true);
            interact(player, k, ItemStack.EMPTY);
            player.setShiftKeyDown(false);
            return player.getArmorValue();
        });
        check(server, "k", "on the shoulder", k -> k.getKState() == KState.ON_PLAYER && k.getRideSlot() != RideSlot.HEAD);
        server.runCommand("summon minecraft:creeper 0.5 -60 -2.5 {NoAI:1b,Fuse:600s,Tags:[\"creeper\"]}");
        context.waitTicks(2);
        server.runOnServer(s -> creeper(s).setSwellDir(1));
        waitFor(context, server, 40, "him to see the creeper", s -> creeper(s).hasEffect(MobEffects.GLOWING)
                && tagged(s, "k").getSayKey().equals("behind_creeper"));
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(3);
        game(context);
        context.takeScreenshot("skills-05-shoulder");
        server.runOnServer(s -> creeper(s).discard());

        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            player.setShiftKeyDown(true);
            interact(player, tagged(s, "k"), ItemStack.EMPTY);
            player.setShiftKeyDown(false);
        });
        context.waitTicks(3);
        int onHead = server.computeOnServer(s -> player(s).getArmorValue());
        if (onHead != armour + 2) {
            throw new AssertionError("On the head he should add two points of armour: " + armour + " → " + onHead);
        }
        server.runCommand("summon minecraft:falling_block 0.5 -53 0.5 {BlockState:{Name:\"minecraft:anvil\"},Time:1,HurtEntities:1b,FallHurtAmount:2.0f,FallHurtMax:40}");
        waitFor(context, server, 60, "the anvil to land", s -> !s.overworld().getBlockState(new BlockPos(0, -60, 0)).isAir()
                || !s.overworld().getBlockState(new BlockPos(0, -59, 0)).isAir());
        boolean whole = server.computeOnServer(s -> player(s).getHealth() == player(s).getMaxHealth()
                && tagged(s, "k").getHealth() == tagged(s, "k").getMaxHealth() && tagged(s, "k").getSayKey().equals("bonk"));
        if (!whole) {
            throw new AssertionError("The anvil should bonk him and hurt nobody: player " + server.computeOnServer(s -> player(s).getHealth())
                    + ", " + server.computeOnServer(s -> describe(tagged(s, "k"))));
        }
        game(context);
        context.takeScreenshot("skills-06-head");
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        server.runCommand("fill -2 -60 -2 2 -58 2 air");
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            player.setShiftKeyDown(true);
            interact(player, tagged(s, "k"), ItemStack.EMPTY);
            player.setShiftKeyDown(false);
        });
        context.waitTicks(3);
        int after = server.computeOnServer(s -> player(s).getArmorValue());
        if (after != armour) {
            throw new AssertionError("Off the head, his armour should go: " + after);
        }
        clear(server);
    }

    /**
     * Food dropped about him goes into his cheeks; full, he brings it to his owner and spits it out.
     * Twelve kinds: the world starts at the full moon, when his cheeks hold twelve stacks, not nine.
     */
    private static void cheeks(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -59.5 2.6 180 25");
        server.runCommand("summon kolobok:kolobok 0.5 -60 0.5 {Rotation:[0f,0f],Tags:[\"k\"]}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.tame(player(s));
            k.setKState(KState.GUARD);
            List<ItemStack> food = List.of(new ItemStack(Items.BREAD), new ItemStack(Items.APPLE), new ItemStack(Items.CARROT),
                    new ItemStack(Items.POTATO), new ItemStack(Items.BEEF), new ItemStack(Items.PORKCHOP), new ItemStack(Items.COOKIE),
                    new ItemStack(Items.MELON_SLICE), new ItemStack(Items.SWEET_BERRIES), new ItemStack(Items.CHICKEN),
                    new ItemStack(Items.MUTTON), new ItemStack(Items.COD));
            for (ItemStack stack : food) {
                ItemEntity item = new ItemEntity(s.overworld(), 0.5, -59.8, 0.5, stack, 0.0, 0.0, 0.0);
                s.overworld().addFreshEntity(item);
            }
        });
        waitFor(context, server, 60, "his cheeks to fill", s -> tagged(s, "k").cheeksFull() && tagged(s, "k").cheekPuff() == 3);
        check(server, "k", "puffed out", k -> k.getMood() == Mood.PUFF);
        // The last pickups still fly into his mouth for a few ticks.
        context.waitTicks(10);
        game(context);
        context.takeScreenshot("skills-07-cheeks");
        server.runCommand("tp @a 0.5 -60 7.5 180 20");
        context.waitTicks(3);
        server.runOnServer(s -> tagged(s, "k").setKState(KState.FOLLOW));
        waitFor(context, server, 300, "him to bring his cheeks home", s -> tagged(s, "k").cheeksEmpty());
        check(server, "k", "spitting it out", k -> k.getSayKey().equals("spit"));
        context.waitTicks(20);
        boolean brought = server.computeOnServer(s -> player(s).getInventory().contains(new ItemStack(Items.BREAD))
                || !s.overworld().getEntitiesOfClass(ItemEntity.class, player(s).getBoundingBox().inflate(4.0),
                item -> item.getItem().is(Items.BREAD)).isEmpty());
        if (!brought) {
            throw new AssertionError("The bread should be at his owner's feet or in his pockets");
        }
        server.runOnServer(s -> player(s).getInventory().clearContent());
        clear(server);
    }

    /** Чикен-жокей: he rides a chicken and gets off at his owner's click. */
    private static void chicken(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("gamemode creative @a");
        server.runCommand("tp @a 0.5 -60 4.0 180 20");
        server.runCommand("summon kolobok:kolobok 0.5 -60 0.5 {Tags:[\"k\"]}");
        server.runCommand("summon minecraft:chicken 1.0 -60 0.5 {Tags:[\"hen\"]}");
        context.waitTicks(3);
        boolean riding = server.computeOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.tame(player(s));
            k.setKState(KState.FOLLOW);
            Chicken hen = s.overworld().getEntitiesOfClass(Chicken.class, k.getBoundingBox().inflate(4.0)).getFirst();
            return k.rideChicken(hen);
        });
        context.waitTicks(10);
        check(server, "k", "riding the chicken", k -> riding && k.getKState() == KState.ON_CHICKEN && k.getVehicle() instanceof Chicken);
        game(context);
        context.takeScreenshot("skills-08-chicken");
        server.runOnServer(s -> interact(player(s), tagged(s, "k"), ItemStack.EMPTY));
        context.waitTicks(3);
        check(server, "k", "off the chicken", k -> k.getKState() == KState.FOLLOW && !k.isPassenger());
        clear(server);
    }

    /**
     * A row: soggy from standing in water, tanned by fire, bitten by his hungry owner, and dancing to a
     * jukebox. Honey then heals the bitten one.
     */
    private static void states(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runCommand("tp @a 0.0 -59.3 4.0 180 20");
        String[] tags = {"soggy", "tan", "bitten", "dancing"};
        // The first stands in a pit of water, one block, so it cannot spread over the others.
        server.runCommand("setblock -2 -61 0 minecraft:water");
        for (int i = 0; i < tags.length; i++) {
            server.runCommand("summon kolobok:kolobok " + (-1.5 + i) + (i == 0 ? " -61" : " -60") + " 0.5 {NoAI:1b,Rotation:[0f,0f],Tags:[\"" + tags[i] + "\"]}");
        }
        context.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            for (String tag : tags) {
                tagged(s, tag).tame(player);
            }
            // Fire gives a tan, not a burn.
            KolobokEntity tan = tagged(s, "tan");
            tan.hurtServer(s.overworld(), s.overworld().damageSources().inFire(), 2.0F);
            // A hungry owner bites a sitting one.
            KolobokEntity bitten = tagged(s, "bitten");
            bitten.setKState(KState.SIT);
            player.getFoodData().setFoodLevel(4);
            player.setShiftKeyDown(true);
            interact(player, bitten, ItemStack.EMPTY);
            player.setShiftKeyDown(false);
            // A jukebox beside the last one, with a record put in.
            s.overworld().setBlockAndUpdate(new BlockPos(3, -60, 0), net.minecraft.world.level.block.Blocks.JUKEBOX.defaultBlockState());
            JukeboxPlayable.tryInsertIntoJukebox(s.overworld(), new BlockPos(3, -60, 0), new ItemStack(Items.MUSIC_DISC_CAT), player);
        });
        check(server, "tan", "tanned, unhurt", k -> k.hasFlag(KolobokEntity.FLAG_TAN) && k.getHealth() == k.getMaxHealth()
                && k.getSayKey().equals("tan") && k.fireImmune());
        check(server, "bitten", "bitten", k -> k.hasFlag(KolobokEntity.FLAG_BITTEN) && k.getMaxHealth() < 8.0F
                && k.getSayKey().equals("bitten"));
        int food = server.computeOnServer(s -> player(s).getFoodData().getFoodLevel());
        if (food != 7) {
            throw new AssertionError("The bite should give three food, the player has " + food);
        }
        waitFor(context, server, 160, "the one in the water to go soggy", s -> tagged(s, "soggy").hasFlag(KolobokEntity.FLAG_SOGGY));
        server.runOnServer(s -> tagged(s, "soggy").teleportTo(-1.5, -60.0, 0.5));
        server.runCommand("setblock -2 -61 0 minecraft:grass_block");
        context.waitTicks(10);
        boolean dancing = context.computeOnClient(SkillsVisualTest::dancerDances);
        if (!dancing) {
            throw new AssertionError("He should dance to the jukebox");
        }
        game(context);
        context.takeScreenshot("skills-09-states");
        // The bite from close above, the music off so he holds still.
        server.runCommand("setblock 3 -60 0 minecraft:air");
        server.runOnServer(s -> {
            KolobokEntity bitten = tagged(s, "bitten");
            player(s).teleportTo(bitten.getX(), -60.0, bitten.getZ() + 1.6);
            player(s).lookAt(EntityAnchorArgument.Anchor.EYES, bitten.position().add(0.0, 0.3, 0.0));
        });
        context.waitTicks(10);
        game(context);
        context.takeScreenshot("skills-09-bite");

        // Honey heals the bitten one and leaves the bottle.
        server.runOnServer(s -> {
            KolobokEntity bitten = tagged(s, "bitten");
            bitten.setHealth(2.0F);
            interact(player(s), bitten, new ItemStack(Items.HONEY_BOTTLE));
        });
        boolean healed = server.computeOnServer(s -> tagged(s, "bitten").getHealth() > 6.0F && player(s).getMainHandItem().is(Items.GLASS_BOTTLE)
                && tagged(s, "bitten").getSayKey().equals("honey"));
        if (!healed) {
            throw new AssertionError("Honey should heal him and give back the bottle");
        }
        server.runOnServer(s -> player(s).getInventory().clearContent());
        clear(server);
    }

    /**
     * «Покажи дорогу»: for an emerald he finds the nearest village and rolls towards it leaving crumbs;
     * the crumbs mode leaves them behind him as he follows.
     */
    private static void guide(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 30");
        server.runCommand("summon kolobok:kolobok 0.5 -60 1.5 {Tags:[\"k\"]}");
        context.waitTicks(3);
        String said = server.computeOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.tame(player(s));
            k.setKState(KState.FOLLOW);
            interact(player(s), k, new ItemStack(Items.EMERALD, 2));
            return k.getSayKey();
        });
        if (said.equals("guide")) {
            check(server, "k", "showing the way, the emerald paid", k -> k.getKState() == KState.GUIDING
                    && owner(k).getMainHandItem().getCount() == 1);
            waitFor(context, server, 500, "him to show the way", s -> tagged(s, "k").getSayKey().equals("guide_there"));
            server.runOnServer(s -> {
                KolobokEntity k = tagged(s, "k");
                player(s).lookAt(EntityAnchorArgument.Anchor.EYES, k.position());
            });
            context.waitTicks(10);
            game(context);
            context.takeScreenshot("skills-10-guide");
        } else if (!said.equals("guide_none")) {
            throw new AssertionError("An emerald should send him to show the way, or tell there is no village: " + said);
        }
        waitFor(context, server, 200, "him to follow again", s -> tagged(s, "k").getKState() == KState.FOLLOW);
        // The crumbs mode: he leaves crumbs as he follows.
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.setFlag(KolobokEntity.FLAG_CRUMBS, true);
            // Not twelve blocks or more: from there a pet is fetched, not walked.
            player(s).teleportTo(k.getX() + 10.0, -60, k.getZ());
        });
        waitFor(context, server, 200, "him to follow", s -> tagged(s, "k").distanceTo(player(s)) < 4.0F);
        // Sat down, and seen from the side: from behind him the trail hides behind his back.
        server.runOnServer(s -> {
            KolobokEntity k = tagged(s, "k");
            k.setKState(KState.SIT);
            player(s).teleportTo(k.getX() - 3.5, -60.0, k.getZ() + 4.5);
            player(s).lookAt(EntityAnchorArgument.Anchor.EYES, k.position().add(-3.5, 0.0, 0.0));
        });
        context.waitTicks(5);
        game(context);
        context.takeScreenshot("skills-11-crumbs");
        server.runOnServer(s -> player(s).getInventory().clearContent());
        clear(server);
    }

    /** «Тянут-потянут»: ripe beetroot picked with the dog, the cat and Kolobok about: six more come up. */
    private static void repka(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 3.5 180 30");
        server.runCommand("setblock 0 -61 0 minecraft:farmland");
        server.runCommand("setblock 0 -60 0 minecraft:beetroots[age=3]");
        server.runCommand("summon minecraft:wolf 2.5 -60 0.5 {Tags:[\"pet\"]}");
        server.runCommand("summon minecraft:cat -1.5 -60 0.5 {Tags:[\"pet\"]}");
        server.runCommand("summon kolobok:kolobok 1.5 -60 1.5 {Tags:[\"k\"]}");
        context.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            tagged(s, "k").tame(player);
            tagged(s, "k").setKState(KState.SIT);
            for (Wolf wolf : s.overworld().getEntitiesOfClass(Wolf.class, new AABB(-5, -62, -5, 6, -55, 6))) {
                wolf.tame(player);
                wolf.setOrderedToSit(true);
            }
            for (Cat cat : s.overworld().getEntitiesOfClass(Cat.class, new AABB(-5, -62, -5, 6, -55, 6))) {
                cat.tame(player);
                cat.setOrderedToSit(true);
            }
            player.gameMode.destroyBlock(new BlockPos(0, -60, 0));
        });
        context.waitTicks(5);
        int beetroots = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(-5, -62, -5, 6, -55, 6),
                item -> item.getItem().is(Items.BEETROOT)).stream().mapToInt(item -> item.getItem().getCount()).sum());
        if (beetroots < 7) {
            throw new AssertionError("Six more beetroots should come up with the one, there are " + beetroots);
        }
        check(server, "k", "pleased", k -> k.getSayKey().equals("repka"));
        game(context);
        context.takeScreenshot("skills-12-repka");
        server.runCommand("kill @e[type=item]");
        clear(server);
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
            throw new AssertionError("Expected " + tag + " " + what + ", got " + server.computeOnServer(s -> describe(tagged(s, tag))));
        }
    }

    private static String describe(KolobokEntity k) {
        return k.getKState() + " " + k.getMood() + " say=" + k.getSayKey() + " at " + k.position() + " health=" + k.getHealth()
                + "/" + k.getMaxHealth() + " cheeks=" + k.cheekPuff();
    }

    private static KolobokEntity tagged(MinecraftServer server, String tag) {
        ServerLevel level = server.overworld();
        for (KolobokEntity entity : level.getEntities(KEntities.KOLOBOK.get(), e -> e.isAlive() && e.entityTags().contains(tag))) {
            return entity;
        }
        throw new AssertionError("No Kolobok tagged " + tag);
    }

    private static Creeper creeper(MinecraftServer server) {
        return server.overworld().getEntitiesOfClass(Creeper.class, new AABB(-10, -64, -10, 10, -50, 10)).getFirst();
    }

    private static boolean dancerDances(Minecraft mc) {
        // Tags are the server's; the client tells the dancer by his place, last in the row.
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof KolobokEntity k && k.getX() > 1.0 && k.clientDancing) {
                return true;
            }
        }
        return false;
    }

    private static Player owner(KolobokEntity k) {
        return (Player) k.getOwner();
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }
}

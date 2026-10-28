package ru.bagomot.kolobok.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ru.bagomot.kolobok.advancement.Tale;
import ru.bagomot.kolobok.advancement.TaleTrigger;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.ai.CatOnStoveGoal;
import ru.bagomot.kolobok.entity.ai.Goals;
import ru.bagomot.kolobok.guide.GuideBook;
import ru.bagomot.kolobok.item.BindleItem;

import java.util.List;

/**
 * Common handlers that each loader calls from its own events. A spectator sets none of them off:
 * some loader events come before vanilla's own spectator checks.
 */
public final class KolobokEvents {

    /** As a fox goes for a chicken or a rabbit. */
    private static final int FOX_HUNT_PRIORITY = 4;
    /** A fox looks round for Kolobok about once in this many ticks. */
    private static final int FOX_HUNT_INTERVAL = 40;
    /** «Репка»: the dog, the cat and Kolobok must be this close to the beetroot. */
    private static final double REPKA_RANGE = 6.0;
    private static final int REPKA_BEETROOTS = 6;
    /** Kolobki this close to a petted dog may see it. */
    private static final double JEALOUS_LOOK = 8.0;
    /** A player who has had the guide carries this tag, a vanilla one saved with the player. */
    public static final String GUIDE_GIVEN = "kolobok.guide_given";

    private KolobokEvents() {
    }

    /**
     * The start of every server tick, before any level ticks: a Kolobok left behind by the player he
     * sits on — through a portal, by a teleport — is brought after him while his chunk is still loaded.
     */
    public static void onServerTickStart(MinecraftServer server) {
        KolobokEntity.followCarriers(server);
    }

    /**
     * The server has stopped, its levels closed: who carried whom and where the villages were belong
     * to that world.
     */
    public static void onServerStopped(MinecraftServer server) {
        KolobokEntity.serverStopped();
        KolobokEntity.forgetVillages();
    }

    /**
     * A player joins the server, already in the level: the first time, they get the guide, «Как печь
     * Колобка», unless the server config says otherwise. A spectator gets it on a later join, out of
     * spectator mode.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        if (!KConfig.server().guideBook || player.isSpectator() || player.entityTags().contains(GUIDE_GIVEN)) {
            return;
        }
        player.addTag(GUIDE_GIVEN);
        ItemStack guide = GuideBook.create();
        if (!player.getInventory().add(guide)) {
            player.drop(guide, false, Prediction.SERVER_ONLY);
        }
    }

    /**
     * An entity is loaded into a server level: foxes learn to hunt Kolobok, tame cats to sit on the
     * stove, and a bindle with a Kolobok in it, dropped or thrown, never despawns.
     */
    public static void onEntityLoad(Entity entity) {
        if (entity instanceof ItemEntity item) {
            if (BindleItem.isFull(item.getItem())) {
                item.setUnlimitedLifetime();
            }
        } else if (entity instanceof Fox fox) {
            Goals.addOnce(Goals.targets(fox), FOX_HUNT_PRIORITY, new FoxHuntGoal(fox));
        } else if (entity instanceof Cat cat) {
            CatOnStoveGoal.teach(cat);
        }
    }

    /**
     * A living entity is about to be hurt.
     *
     * @return whether the damage goes ahead: a Kolobok on a player's head takes a falling anvil's bonk
     */
    public static boolean allowDamage(LivingEntity entity, DamageSource source) {
        return entity.isSpectator() || !KolobokEntity.bonk(entity, source);
    }

    /**
     * A player used something on an entity: his own dog petted where his Kolobok can see it makes the
     * Kolobok jealous. Only looks; the interaction goes ahead as it would. Fabric calls this before
     * vanilla turns a spectator away, so a spectator is turned away here.
     */
    public static void onEntityInteract(Player player, Entity target) {
        if (player.level().isClientSide() || player.isSpectator() || !(target instanceof Wolf dog) || !dog.isTame()
                || !dog.isOwnedBy(player)) {
            return;
        }
        for (KolobokEntity kolobok : player.level().getEntitiesOfClass(KolobokEntity.class, dog.getBoundingBox().inflate(JEALOUS_LOOK),
                kolobok -> kolobok.isTame() && kolobok.isOwnedBy(player))) {
            kolobok.sawDogPetted(dog);
        }
    }

    /**
     * «Тянут-потянут»: a player picks ripe beetroot with his own dog, cat and Kolobok close by, and six
     * more beetroots come up with it, as the turnip did for the whole family. Only for a pick that
     * drops anything, so never in creative: NeoForge calls this with the drops, Fabric after any break.
     */
    public static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state) {
        if (level.isClientSide() || player.isSpectator() || player.preventsBlockDrops() || !KConfig.server().repka
                || !(state.getBlock() instanceof BeetrootBlock crop) || !crop.isMaxAge(state)) {
            return;
        }
        AABB around = new AABB(pos).inflate(REPKA_RANGE);
        boolean dog = !level.getEntitiesOfClass(Wolf.class, around, wolf -> wolf.isTame() && wolf.isOwnedBy(player)).isEmpty();
        boolean cat = !level.getEntitiesOfClass(Cat.class, around, c -> c.isTame() && c.isOwnedBy(player)).isEmpty();
        List<KolobokEntity> kolobki = level.getEntitiesOfClass(KolobokEntity.class, around,
                kolobok -> kolobok.isTame() && kolobok.isOwnedBy(player));
        if (dog && cat && !kolobki.isEmpty()) {
            Block.popResource(level, pos, new ItemStack(Items.BEETROOT, REPKA_BEETROOTS));
            kolobki.getFirst().say("repka");
            TaleTrigger.award(player, Tale.REPKA);
        }
    }

    /**
     * The fox's own hunting goal, aimed at Kolobok. Its own class so that it is added only once.
     */
    private static final class FoxHuntGoal extends NearestAttackableTargetGoal<KolobokEntity> {

        FoxHuntGoal(Fox fox) {
            super(fox, KolobokEntity.class, FOX_HUNT_INTERVAL, true, false,
                    (target, level) -> target instanceof KolobokEntity kolobok && kolobok.foxMayHunt());
        }
    }
}

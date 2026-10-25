package ru.bagomot.kolobok.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.registry.KTriggers;

import java.util.Optional;

/**
 * {@code kolobok:tale}: one trigger for every moment of the tale, told apart by the event. The
 * advancements of «Сказка о Колобке» wait for it, and a data pack may too:
 *
 * <pre>{@code
 * "trigger": "kolobok:tale",
 * "conditions": {"event": "verse", "mob": "minecraft:wolf"}
 * }</pre>
 *
 * <p>{@code mob} and {@code count} are optional; what they mean for each event is told in
 * {@link Tale}.
 */
public class TaleTrigger extends SimpleCriterionTrigger<TaleTrigger.Instance> {

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, Tale event, @Nullable String mob, int count) {
        Identifier mobId = mob == null ? null : Identifier.tryParse(mob);
        trigger(player, instance -> instance.matches(event, mobId, count));
    }

    /**
     * Tells the tale's trigger about that moment, if the entity is a player on the server; otherwise
     * does nothing, so callers need not check who did it.
     */
    public static void award(@Nullable Entity entity, Tale event) {
        award(entity, event, null, 1);
    }

    public static void award(@Nullable Entity entity, Tale event, int count) {
        award(entity, event, null, count);
    }

    public static void award(@Nullable Entity entity, Tale event, @Nullable String mob, int count) {
        if (entity instanceof ServerPlayer player) {
            KTriggers.TALE.get().trigger(player, event, mob, count);
        }
    }

    public record Instance(Optional<Holder<LootItemCondition>> player, Tale event, Optional<Identifier> mob,
                           MinMaxBounds.Ints count) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Tale.CODEC.fieldOf("event").forGetter(Instance::event),
                Identifier.CODEC.optionalFieldOf("mob").forGetter(Instance::mob),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(Instance::count)
        ).apply(i, Instance::new));

        boolean matches(Tale event, @Nullable Identifier mob, int count) {
            return this.event == event && (this.mob.isEmpty() || this.mob.get().equals(mob)) && this.count.matches(count);
        }
    }
}

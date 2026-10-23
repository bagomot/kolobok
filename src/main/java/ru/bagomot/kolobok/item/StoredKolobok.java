package ru.bagomot.kolobok.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.component.CustomData;
import ru.bagomot.kolobok.entity.Variant;

import java.util.Optional;

/**
 * A Kolobok carried in a bindle: all of his saved data, and what the bindle's tooltip tells of him
 * without reading it.
 *
 * @param entity  his data as the game saves him, without his place in the world or his id
 * @param name    the name he was given, if any
 * @param variant his dough
 * @param verses  how many verses his song has
 */
public record StoredKolobok(CustomData entity, Optional<Component> name, Variant variant, int verses) {

    public static final Codec<StoredKolobok> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CustomData.CODEC.fieldOf("entity").forGetter(StoredKolobok::entity),
            ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(StoredKolobok::name),
            Codec.STRING.xmap(Variant::byName, Variant::serializedName).optionalFieldOf("variant", Variant.CLASSIC)
                    .forGetter(StoredKolobok::variant),
            Codec.INT.optionalFieldOf("verses", 0).forGetter(StoredKolobok::verses)
    ).apply(instance, StoredKolobok::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StoredKolobok> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
}

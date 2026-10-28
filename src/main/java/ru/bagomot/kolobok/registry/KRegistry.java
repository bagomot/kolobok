package ru.bagomot.kolobok.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import ru.bagomot.kolobok.Kolobok;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A catalogue of everything Kolobok adds to one game registry.
 *
 * <p>Common code declares entries; each loader walks the catalogue in its own way — Fabric writes
 * straight into the open registry at init, NeoForge waits for its {@code RegisterEvent}. Values are
 * created at that moment, never earlier, so an entry is safe to declare in a static initialiser.
 *
 * @param <T> the registry's element type
 */
public final class KRegistry<T> {

    /**
     * Every catalogue, in the order {@code Kolobok.init} declares them, each after the ones its
     * entries refer to: blocks before their block entity type, the entity type before its spawn egg.
     * That is not the game's own registry order, which has entity types before block entity types
     * and data components after items. Fabric registers in this order; NeoForge registers each
     * catalogue when its registry's turn comes.
     */
    static final List<KRegistry<?>> ALL = new ArrayList<>();

    private final ResourceKey<? extends Registry<T>> registryKey;
    private final List<Entry<T, ? extends T>> entries = new ArrayList<>();

    KRegistry(ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = registryKey;
        ALL.add(this);
    }

    public static List<KRegistry<?>> all() {
        return Collections.unmodifiableList(ALL);
    }

    public ResourceKey<? extends Registry<T>> registryKey() {
        return registryKey;
    }

    public List<Entry<T, ? extends T>> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Declares an entry. The factory receives the element's own key because most 26.x builders
     * (items, blocks, entity types) want it at construction time.
     */
    public <V extends T> Entry<T, V> register(String name, Function<ResourceKey<T>, V> factory) {
        @SuppressWarnings("unchecked")
        ResourceKey<Registry<T>> key = (ResourceKey<Registry<T>>) registryKey;
        Entry<T, V> entry = new Entry<>(ResourceKey.create(key, Kolobok.id(name)), factory);
        entries.add(entry);
        return entry;
    }

    /**
     * One declared registry element.
     *
     * @param <T> the registry's element type
     * @param <V> the concrete type of this element
     */
    public static final class Entry<T, V extends T> implements Supplier<V> {

        private final ResourceKey<T> key;
        private final Function<ResourceKey<T>, V> factory;
        private V value;

        private Entry(ResourceKey<T> key, Function<ResourceKey<T>, V> factory) {
            this.key = key;
            this.factory = factory;
        }

        public ResourceKey<T> key() {
            return key;
        }

        public Identifier id() {
            return key.identifier();
        }

        /**
         * Builds the value. Called exactly once, by the loader, when the registry is open.
         */
        public V create() {
            if (value != null) {
                throw new IllegalStateException("Registered twice: " + key);
            }
            value = factory.apply(key);
            return value;
        }

        @Override
        public V get() {
            if (value == null) {
                throw new IllegalStateException("Not registered yet: " + key);
            }
            return value;
        }

        public boolean isBound() {
            return value != null;
        }
    }
}

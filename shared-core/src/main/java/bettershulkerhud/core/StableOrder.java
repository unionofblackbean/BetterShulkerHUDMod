package bettershulkerhud.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Session order shared by the four 26.x adapters; no game or loader dependency. */
public final class StableOrder {
    private StableOrder() {}

    /** Refresh values by identity, remove exhausted entries, append newly seen identities. */
    public static <T, K> List<T> refresh(
            List<T> previous, List<T> current, Function<T, K> identity) {
        if (previous.isEmpty() || current.isEmpty()) return List.copyOf(current);
        Map<K, T> currentByKey = new HashMap<>();
        for (T item : current) currentByKey.put(identity.apply(item), item);
        List<T> ordered = new ArrayList<>(current.size());
        Set<K> used = new HashSet<>();
        for (T item : previous) {
            K key = identity.apply(item);
            T refreshed = currentByKey.get(key);
            if (refreshed != null && used.add(key)) ordered.add(refreshed);
        }
        for (T item : current) {
            if (used.add(identity.apply(item))) ordered.add(item);
        }
        return List.copyOf(ordered);
    }
}

package com.bot.logic;

import com.bot.model.ResourceDatabase;
import com.bot.model.ResourceSpawn;

import java.util.*;

public class RouteManager {

    private final ResourceDatabase resourceDb;
    private final Set<String> selectedNames = new LinkedHashSet<>();
    private final Map<String, Long> cooldowns = new HashMap<>();

    public static final long COOLDOWN_MS = 10 * 60 * 1000L;

    public RouteManager(ResourceDatabase resourceDb) {
        this.resourceDb = resourceDb;
    }

    public ResourceSpawn getNextTarget(float px, float py, float pz) {
        if (selectedNames.isEmpty()) return null;
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(e -> e.getValue() <= now);

        ResourceSpawn best = null;
        float bestDist = Float.MAX_VALUE;
        for (ResourceSpawn sp : resourceDb.getAllSpawns()) {
            if (!selectedNames.contains(sp.getName())) continue;
            if (cooldowns.containsKey(spawnKey(sp))) continue;
            float d = sp.distanceTo(px, py, pz);
            if (d < bestDist) {
                bestDist = d;
                best = sp;
            }
        }
        return best;
    }

    public void markCooldown(ResourceSpawn sp) {
        cooldowns.put(spawnKey(sp), System.currentTimeMillis() + COOLDOWN_MS);
    }

    public boolean isOnCooldown(ResourceSpawn sp) {
        Long exp = cooldowns.get(spawnKey(sp));
        return exp != null && exp > System.currentTimeMillis();
    }

    public String spawnKey(ResourceSpawn sp) {
        return String.format("%.0f:%.0f:%.0f", sp.getX(), sp.getY(), sp.getZ());
    }

    public long getCooldownRemainingMs(ResourceSpawn sp) {
        Long exp = cooldowns.get(spawnKey(sp));
        if (exp == null) return 0;
        long rem = exp - System.currentTimeMillis();
        return rem > 0 ? rem : 0;
    }

    public boolean isRouteActive() {
        return !selectedNames.isEmpty();
    }

    public Set<String> getSelectedNames() {
        return selectedNames;
    }

    public void setSelectedNames(Set<String> names) {
        selectedNames.clear();
        selectedNames.addAll(names);
    }

    public Map<String, Long> getCooldowns() {
        long now = System.currentTimeMillis();
        Map<String, Long> active = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : cooldowns.entrySet()) {
            if (e.getValue() > now) active.put(e.getKey(), e.getValue());
        }
        return active;
    }

    public int getActiveCooldownCount() {
        long now = System.currentTimeMillis();
        return (int) cooldowns.values().stream().filter(v -> v > now).count();
    }

    public int getTotalSpawnCount() {
        int count = 0;
        for (ResourceSpawn sp : resourceDb.getAllSpawns()) {
            if (selectedNames.contains(sp.getName())) count++;
        }
        return count;
    }

    public ResourceDatabase getResourceDb() {
        return resourceDb;
    }
}

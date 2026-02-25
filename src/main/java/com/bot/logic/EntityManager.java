package com.bot.logic;

import com.bot.constants.BotSettings;
import com.bot.constants.GameConstants;
import com.bot.memory.WinMemoryReader;
import com.bot.model.Entity;
import com.bot.model.Player;
import java.util.*;

public class EntityManager {

    private final WinMemoryReader memory;
    private final long dynamicBaseAddress;

    private final Map<Integer, Entity> mobCache      = new LinkedHashMap<>();
    private final Map<Integer, Entity> materialCache = new LinkedHashMap<>();
    private final Map<Integer, float[]> prevPositions = new HashMap<>();
    private final Map<Integer, Integer> stableCount = new HashMap<>();

    private final Set<Long> mobAddresses = new HashSet<>();

    private int tickCount = 0;
    private boolean firstLog = false;

    private static final float MOB_MAX_DIST     = 300.0f;
    private static final float RES_MAX_DIST     = 100.0f;
    private static final float MOVE_THRESH      = 0.3f;
    private static final float MOB_OVERLAP_DIST = 5.0f;
    private static final int   MIN_STABLE       = 10;
    private static final long  MIN_PTR          = 0x10000L;
    private static final int   MAX_MATS         = 50;

    private static final int[] RES_POS_OFFS = {0x3C, 0xB0, 0x180};
    private static final int[] RES_MGR_OFFS = {0x18, 0x20, 0x5C, 0x60, 0x64, 0x7C, 0x80, 0xC0, 0xD0, 0xD4, 0xE4};
    private static final int[] RES_ARR_OFFS = {0x08, 0x20, 0x34, 0x50, 0x64, 0x68, 0x6C, 0x80, 0x8C, 0x98, 0xB0, 0xBC};

    public EntityManager(WinMemoryReader memory, long dynamicBaseAddress) {
        this.memory = memory;
        this.dynamicBaseAddress = dynamicBaseAddress;
    }

    public void update(long moduleBase, Player player) {
        tickCount++;

        Set<Integer> mobIds = new HashSet<>();
        Set<Integer> matIds = new HashSet<>();
        mobAddresses.clear();

        scanNpcArray(moduleBase, player, mobIds, matIds);
        scanResourceSubsystem(moduleBase, player, matIds);

        mobCache.keySet().retainAll(mobIds);
        materialCache.keySet().retainAll(matIds);

        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, Entity> entry : materialCache.entrySet()) {
            int uid = entry.getKey();
            Entity e = entry.getValue();
            float[] cur = {e.getX(), e.getY(), e.getZ()};
            float[] prev = prevPositions.get(uid);

            if (prev != null) {
                float dx = cur[0] - prev[0];
                float dy = cur[1] - prev[1];
                float dz = cur[2] - prev[2];
                float moved = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (moved > MOVE_THRESH) {
                    toRemove.add(uid);
                    stableCount.remove(uid);
                } else {
                    stableCount.merge(uid, 1, Integer::sum);
                }
            } else {
                stableCount.put(uid, 0);
            }
            prevPositions.put(uid, cur);
        }
        for (int uid : toRemove) {
            materialCache.remove(uid);
            matIds.remove(uid);
            prevPositions.remove(uid);
        }

        toRemove.clear();
        for (Map.Entry<Integer, Entity> entry : materialCache.entrySet()) {
            Entity mat = entry.getValue();
            if (isNearAnyMob(mat.getX(), mat.getY(), mat.getZ())) {
                toRemove.add(entry.getKey());
            }
        }
        for (int uid : toRemove) {
            materialCache.remove(uid);
            stableCount.remove(uid);
            prevPositions.remove(uid);
        }

        if (!firstLog && tickCount >= 15) {
            firstLog = true;
            int confirmed = 0;
            for (Map.Entry<Integer, Entity> entry : materialCache.entrySet()) {
                Integer sc = stableCount.get(entry.getKey());
                if (sc != null && sc >= MIN_STABLE) confirmed++;
            }
            logInfo(String.format("[DETECT] %d mobs, %d resource candidates, %d confirmed (stable>=%d ticks)",
                    mobCache.size(), materialCache.size(), confirmed, MIN_STABLE));
            for (Entity e : materialCache.values()) {
                Integer sc = stableCount.get((int)(e.getBaseAddress() & 0x7FFFFFFFL));
                logInfo(String.format("[DETECT]   mat addr=0x%X tid=%d pos=(%.1f,%.1f,%.1f) dist=%.1f stable=%d%s",
                        e.getBaseAddress(), e.getTemplateId(),
                        e.getX(), e.getY(), e.getZ(), e.getDistance(),
                        sc != null ? sc : 0,
                        (sc != null && sc >= MIN_STABLE) ? " CONFIRMED" : ""));
            }
        }

        if (tickCount % 50 == 1) {
            Entity nearMat = getNearestMaterial();
            Entity nearMob = getNearestMob();
            int confirmed = 0;
            for (Integer sc : stableCount.values()) {
                if (sc >= MIN_STABLE) confirmed++;
            }
            StringBuilder matInfo = new StringBuilder();
            for (Entity e : getMaterials()) {
                matInfo.append(String.format(" [tid=%d d=%.1f]", e.getTemplateId(), e.getDistance()));
            }
            logInfo(String.format("[STATUS] pos=(%.1f,%.1f,%.1f) mobs=%d mats=%d(confirmed=%d) nearMat=%.1fm nearMob=%.1fm%s",
                    player.getX(), player.getY(), player.getZ(),
                    mobCache.size(), materialCache.size(), confirmed,
                    nearMat != null ? nearMat.getDistance() : -1f,
                    nearMob != null ? nearMob.getDistance() : -1f,
                    matInfo.toString()));
        }
    }

    private boolean isNearAnyMob(float x, float y, float z) {
        for (Entity mob : mobCache.values()) {
            float dx = x - mob.getX();
            float dy = y - mob.getY();
            float dz = z - mob.getZ();
            float d = dx * dx + dy * dy + dz * dz;
            if (d < MOB_OVERLAP_DIST * MOB_OVERLAP_DIST) {
                return true;
            }
        }
        return false;
    }

    private void scanNpcArray(long moduleBase, Player player, Set<Integer> mobIds, Set<Integer> matIds) {
        long rootVal = readPtr(moduleBase + GameConstants.BASE_OFFSET);
        if (rootVal < MIN_PTR) return;

        long step1 = readPtr(rootVal + 0x18);
        if (step1 < MIN_PTR) return;

        long mgr = readPtr(step1 + 0x24);
        if (mgr < MIN_PTR) return;

        long arr = readPtr(mgr + 0x5C);
        int  cnt = memory.readInt(mgr + 0x60);
        if (arr < MIN_PTR || cnt <= 0 || cnt > 5000) return;

        if (tickCount <= 2) {
            logInfo(String.format("[NPC] chain ok - mgr=0x%X arr=0x%X cnt=%d", mgr, arr, cnt));
        }

        for (int i = 0; i < cnt; i++) {
            long ep = readPtr(arr + ((long) i * 4));
            if (ep < MIN_PTR) continue;

            float x = memory.readFloat(ep + 0x3C);
            float y = memory.readFloat(ep + 0x40);
            float z = memory.readFloat(ep + 0x44);
            if (!isValidPos(x, y, z)) continue;

            int type = memory.readInt(ep + GameConstants.OFFSET_TYPE);
            float dist = dist3D(x, y, z, player);

            if (type == GameConstants.TYPE_MATERIAL) {
                if (dist > RES_MAX_DIST || dist < 0.1f) continue;
                int uid = (int) (ep & 0x7FFFFFFFL);
                matIds.add(uid);
                Entity ent = getOrCreate(materialCache, uid, GameConstants.TYPE_MATERIAL);
                fill(ent, ep, x, y, z, GameConstants.TYPE_MATERIAL, player);
                readAndSetTemplateId(ent, ep);
                continue;
            }

            mobAddresses.add(ep);
            if (dist > MOB_MAX_DIST || dist < 0.5f) continue;
            int uid = (int) (ep & 0x7FFFFFFFL);
            mobIds.add(uid);
            fill(getOrCreate(mobCache, uid, GameConstants.TYPE_MOB), ep, x, y, z, GameConstants.TYPE_MOB, player);
        }
    }

    private void scanResourceSubsystem(long moduleBase, Player player, Set<Integer> matIds) {
        long rootVal = readPtr(moduleBase + GameConstants.BASE_OFFSET);
        if (rootVal < MIN_PTR) return;

        int[] rootOffs = {0x58, 0x3C};
        for (int rootOff : rootOffs) {
            long resSub = readPtr(rootVal + rootOff);
            if (resSub < MIN_PTR) continue;
            scanSubChain(resSub, player, matIds);
        }

        long npcSub = readPtr(rootVal + 0x18);
        if (npcSub >= MIN_PTR) {
            long altMgr = readPtr(npcSub + 0x20);
            if (altMgr >= MIN_PTR) {
                for (int arrOff : RES_ARR_OFFS) {
                    long arr = readPtr(altMgr + arrOff);
                    int cnt = memory.readInt(altMgr + arrOff + 4);
                    if (arr < MIN_PTR || cnt <= 0 || cnt > 200) continue;
                    scanResArray(arr, cnt, player, matIds);
                }
            }
        }
    }

    private void scanSubChain(long sub, Player player, Set<Integer> matIds) {
        for (int mgrOff : RES_MGR_OFFS) {
            long mgr = readPtr(sub + mgrOff);
            if (mgr < MIN_PTR) continue;

            for (int arrOff : RES_ARR_OFFS) {
                long arr = readPtr(mgr + arrOff);
                int  cnt = memory.readInt(mgr + arrOff + 4);
                if (arr < MIN_PTR || cnt <= 0 || cnt > 200) continue;

                scanResArray(arr, cnt, player, matIds);
            }
        }
    }

    private void scanResArray(long arr, int cnt, Player player, Set<Integer> matIds) {
        for (int i = 0; i < cnt && materialCache.size() < MAX_MATS; i++) {
            long ep = readPtr(arr + ((long) i * 4));
            if (ep < MIN_PTR) continue;

            if (mobAddresses.contains(ep)) continue;

            int uid = (int) (ep & 0x7FFFFFFFL);
            if (mobCache.containsKey(uid) || matIds.contains(uid)) continue;

            if (!isLikelyMaterial(ep)) continue;

            float bestX = 0, bestY = 0, bestZ = 0;
            boolean hit = false;

            for (int posOff : RES_POS_OFFS) {
                float x = memory.readFloat(ep + posOff);
                float y = memory.readFloat(ep + posOff + 4);
                float z = memory.readFloat(ep + posOff + 8);
                if (!isValidPos(x, y, z)) continue;
                float d = dist3D(x, y, z, player);
                if (d >= 0.1f && d < RES_MAX_DIST) {
                    if (isNearAnyMob(x, y, z)) continue;
                    bestX = x; bestY = y; bestZ = z;
                    hit = true;
                    break;
                }
            }

            if (!hit) continue;

            matIds.add(uid);
            Entity ent = getOrCreate(materialCache, uid, GameConstants.TYPE_MATERIAL);
            ent.setBaseAddress(ep);
            ent.setX(bestX); ent.setY(bestY); ent.setZ(bestZ);
            ent.setType(GameConstants.TYPE_MATERIAL);
            ent.calculateDistance(player);

            readAndSetTemplateId(ent, ep);
        }
    }

    private void readAndSetTemplateId(Entity ent, long ep) {
        int tid = memory.readInt(ep + GameConstants.OFFSET_MATTER_ID);
        ent.setTemplateId(tid);
        String name = GameConstants.MATERIAL_NAMES.get(tid);
        ent.setName(name);
    }

    private long readPtr(long addr) {
        return memory.readInt(addr) & 0xFFFFFFFFL;
    }

    private boolean isLikelyMaterial(long ep) {
        int tid = memory.readInt(ep + GameConstants.OFFSET_MATTER_ID);
        if (tid <= 0 || tid > 100000) return false;
        int typeField = memory.readInt(ep + GameConstants.OFFSET_TYPE);
        if (typeField == GameConstants.TYPE_MOB) return false;
        return true;
    }

    private boolean isValidPos(float x, float y, float z) {
        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) return false;
        if (Math.abs(x) > 10000 || Math.abs(y) > 10000 || Math.abs(z) > 10000) return false;
        return !(x == 0f && y == 0f && z == 0f);
    }

    private float dist3D(float x, float y, float z, Player p) {
        float dx = x - p.getX(), dy = y - p.getY(), dz = z - p.getZ();
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void fill(Entity ent, long ep, float x, float y, float z, int type, Player player) {
        ent.setBaseAddress(ep);
        ent.setX(x); ent.setY(y); ent.setZ(z);
        ent.setType(type);
        ent.calculateDistance(player);
    }

    private Entity getOrCreate(Map<Integer, Entity> cache, int uid, int type) {
        return cache.computeIfAbsent(uid, k -> new Entity(uid, 0, 0, 0, type));
    }

    public List<Entity> getMaterials() {
        List<Entity> list = new ArrayList<>();
        for (Map.Entry<Integer, Entity> entry : materialCache.entrySet()) {
            Integer sc = stableCount.get(entry.getKey());
            if (sc != null && sc >= MIN_STABLE) {
                list.add(entry.getValue());
            }
        }
        list.sort(Comparator.comparingDouble(Entity::getDistance));
        return list;
    }

    public Entity getNearestMob() {
        return mobCache.values().stream()
                .min(Comparator.comparingDouble(Entity::getDistance)).orElse(null);
    }

    public Entity getNearestMaterial() {
        Entity best = null;
        for (Map.Entry<Integer, Entity> entry : materialCache.entrySet()) {
            Integer sc = stableCount.get(entry.getKey());
            if (sc != null && sc >= MIN_STABLE) {
                Entity e = entry.getValue();
                if (best == null || e.getDistance() < best.getDistance()) {
                    best = e;
                }
            }
        }
        return best;
    }

    private void logInfo(String msg) {
        System.out.println(msg);
        BotSettings.logToUi(msg);
    }
}

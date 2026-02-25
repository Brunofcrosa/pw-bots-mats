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
    private final Map<Integer, Integer> stableCount  = new HashMap<>();

    private int tickCount = 0;
    private boolean firstLog = false;

    private static final float MOB_MAX_DIST     = 300.0f;
    private static final float MAT_MAX_DIST     = 100.0f;
    private static final float MOVE_THRESH      = 0.3f;
    private static final float MOB_OVERLAP_DIST = 5.0f;
    private static final int   MIN_STABLE       = 5;
    private static final long  MIN_PTR          = 0x10000L;

    public EntityManager(WinMemoryReader memory, long dynamicBaseAddress) {
        this.memory = memory;
        this.dynamicBaseAddress = dynamicBaseAddress;
    }

    public void update(long moduleBase, Player player) {
        tickCount++;

        if (tickCount == 1) {
            diagScan(moduleBase, player);
        }

        Set<Integer> mobIds = new HashSet<>();
        Set<Integer> matIds = new HashSet<>();

        scanNpcArray(moduleBase, player, mobIds, matIds);

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
                stableCount.put(uid, 1);
            }
            prevPositions.put(uid, cur);
        }
        for (int uid : toRemove) {
            materialCache.remove(uid);
            matIds.remove(uid);
            prevPositions.remove(uid);
        }

        if (!firstLog && tickCount >= 10) {
            firstLog = true;
            int confirmed = 0;
            for (Integer sc : stableCount.values()) {
                if (sc >= MIN_STABLE) confirmed++;
            }
            logInfo(String.format("[DETECT] %d mobs, %d material candidates, %d confirmed (stable>=%d)",
                    mobCache.size(), materialCache.size(), confirmed, MIN_STABLE));
            for (Entity e : materialCache.values()) {
                Integer sc = stableCount.get((int)(e.getBaseAddress() & 0x7FFFFFFFL));
                int type = memory.readInt(e.getBaseAddress() + GameConstants.OFFSET_TYPE);
                logInfo(String.format("[DETECT]   addr=0x%X type=%d pos=(%.1f,%.1f,%.1f) dist=%.1f stable=%d",
                        e.getBaseAddress(), type,
                        e.getX(), e.getY(), e.getZ(), e.getDistance(),
                        sc != null ? sc : 0));
            }
        }

        if (tickCount % 50 == 1) {
            Entity nearMat = getNearestMaterial();
            Entity nearMob = getNearestMob();
            int confirmed = 0;
            for (Integer sc : stableCount.values()) {
                if (sc >= MIN_STABLE) confirmed++;
            }
            logInfo(String.format("[STATUS] pos=(%.1f,%.1f,%.1f) mobs=%d mats=%d(confirmed=%d) nearMat=%.1fm nearMob=%.1fm",
                    player.getX(), player.getY(), player.getZ(),
                    mobCache.size(), materialCache.size(), confirmed,
                    nearMat != null ? nearMat.getDistance() : -1f,
                    nearMob != null ? nearMob.getDistance() : -1f));
        }
    }

    private void diagScan(long moduleBase, Player player) {
        logInfo("=== [SCAN] Analisando NPC array e subsistemas ===");
        logInfo(String.format("[SCAN] playerPos=(%.1f, %.1f, %.1f)", player.getX(), player.getY(), player.getZ()));

        long rootVal = readPtr(moduleBase + GameConstants.BASE_OFFSET);
        if (rootVal < MIN_PTR) {
            logInfo("[SCAN] rootVal invalido");
            return;
        }

        long step1 = readPtr(rootVal + 0x18);
        if (step1 < MIN_PTR) { logInfo("[SCAN] step1 invalido"); return; }

        long mgr = readPtr(step1 + 0x24);
        if (mgr < MIN_PTR) { logInfo("[SCAN] mgr invalido"); return; }

        long arr = readPtr(mgr + 0x5C);
        int cnt = memory.readInt(mgr + 0x60);
        if (arr < MIN_PTR || cnt <= 0 || cnt > 5000) {
            logInfo(String.format("[SCAN] NPC array invalido arr=0x%X cnt=%d", arr, cnt));
            return;
        }

        Map<Integer, Integer> typeCounts = new TreeMap<>();
        int nearCount = 0;
        for (int i = 0; i < cnt; i++) {
            long ep = readPtr(arr + ((long) i * 4));
            if (ep < MIN_PTR) continue;

            int type = memory.readInt(ep + GameConstants.OFFSET_TYPE);
            typeCounts.merge(type, 1, Integer::sum);

            float x = memory.readFloat(ep + 0x3C);
            float y = memory.readFloat(ep + 0x40);
            float z = memory.readFloat(ep + 0x44);
            if (!isValidPos(x, y, z)) continue;
            float d = dist3D(x, y, z, player);
            if (d < 30.0f) {
                nearCount++;
                logInfo(String.format("[SCAN] NEAR: type=%d ep=0x%X d=%.1f pos=(%.1f,%.1f,%.1f)",
                        type, ep, d, x, y, z));
            }
        }
        logInfo(String.format("[SCAN] NPC array types: %s (total=%d, near<30m=%d)", typeCounts, cnt, nearCount));

        logInfo("[SCAN] --- Buscando chains alternativas para materiais ---");
        int[] rootOffs = {0x14, 0x18, 0x1C, 0x20, 0x24, 0x28, 0x2C, 0x30, 0x34, 0x38, 0x3C,
                          0x40, 0x44, 0x48, 0x4C, 0x50, 0x54, 0x58, 0x5C, 0x60, 0x64, 0x68};
        for (int rootOff : rootOffs) {
            long sub = readPtr(rootVal + rootOff);
            if (sub < MIN_PTR || sub > 0xFFFFFFF0L) continue;

            int[] mgrOffs = {0x00, 0x04, 0x08, 0x0C, 0x10, 0x14, 0x18, 0x1C, 0x20, 0x24, 0x28, 0x2C, 0x30};
            for (int mgrOff : mgrOffs) {
                long subMgr = (mgrOff == 0x00) ? sub : readPtr(sub + mgrOff);
                if (subMgr < MIN_PTR || subMgr > 0xFFFFFFF0L) continue;

                int[] arrOffs = {0x04, 0x08, 0x0C, 0x10, 0x14, 0x18, 0x1C, 0x20, 0x24, 0x28, 0x2C, 0x30,
                                 0x34, 0x38, 0x3C, 0x40, 0x44, 0x48, 0x4C, 0x50, 0x54, 0x58, 0x5C, 0x60,
                                 0x64, 0x68, 0x6C, 0x70, 0x74, 0x78, 0x7C, 0x80};
                for (int arrOff : arrOffs) {
                    long subArr = readPtr(subMgr + arrOff);
                    int subCnt = memory.readInt(subMgr + arrOff + 4);
                    if (subArr < MIN_PTR || subCnt <= 0 || subCnt > 500) continue;

                    int nearHits = 0;
                    StringBuilder hitInfo = new StringBuilder();
                    int limit = Math.min(subCnt, 30);
                    for (int i = 0; i < limit; i++) {
                        long ep = readPtr(subArr + ((long) i * 4));
                        if (ep < MIN_PTR) continue;

                        int[] posOffs = {0x3C, 0x44, 0xB0};
                        for (int pOff : posOffs) {
                            float xp = memory.readFloat(ep + pOff);
                            float yp = memory.readFloat(ep + pOff + 4);
                            float zp = memory.readFloat(ep + pOff + 8);
                            if (!isValidPos(xp, yp, zp)) continue;
                            float d = dist3D(xp, yp, zp, player);
                            if (d > 0.5f && d < 100.0f) {
                                nearHits++;
                                int etype = memory.readInt(ep + GameConstants.OFFSET_TYPE);
                                if (nearHits <= 3) {
                                    hitInfo.append(String.format(" [i=%d type=%d pOff=0x%X d=%.1f]", i, etype, pOff, d));
                                }
                                break;
                            }
                        }
                    }

                    if (nearHits > 0) {
                        logInfo(String.format("[SCAN] CHAIN rootOff=0x%X mgrOff=0x%X arrOff=0x%X cnt=%d nearHits=%d%s",
                                rootOff, mgrOff, arrOff, subCnt, nearHits, hitInfo.toString()));
                    }
                }
            }
        }

        logInfo("=== [SCAN] FIM ===");
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

        if (tickCount % 100 == 0) {
            Map<Integer, Integer> types = new TreeMap<>();
            for (int i = 0; i < cnt; i++) {
                long ep = readPtr(arr + ((long) i * 4));
                if (ep < MIN_PTR) continue;
                int t = memory.readInt(ep + GameConstants.OFFSET_TYPE);
                types.merge(t, 1, Integer::sum);
            }
            logInfo(String.format("[NPC] types: %s cnt=%d", types, cnt));
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

            if (type == GameConstants.TYPE_MOB) {
                if (dist > MOB_MAX_DIST || dist < 0.5f) continue;
                int uid = (int) (ep & 0x7FFFFFFFL);
                mobIds.add(uid);
                fill(getOrCreate(mobCache, uid, type), ep, x, y, z, type, player);
                continue;
            }

            if (dist > MAT_MAX_DIST || dist < 0.1f) continue;

            if (isNearAnyMob(x, y, z)) continue;

            int uid = (int) (ep & 0x7FFFFFFFL);
            matIds.add(uid);
            Entity ent = getOrCreate(materialCache, uid, type);
            fill(ent, ep, x, y, z, type, player);
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

    private long readPtr(long addr) {
        return memory.readInt(addr) & 0xFFFFFFFFL;
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

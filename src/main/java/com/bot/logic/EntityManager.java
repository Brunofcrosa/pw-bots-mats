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

    private int resStep1Off = -1;
    private int resArrOff = -1;
    private int resCntOff = -1;
    private int bestResNear = 0;

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
        scanResourceArray(moduleBase, player, matIds);

        mobCache.keySet().retainAll(mobIds);
        materialCache.keySet().retainAll(matIds);

        stableCount.keySet().retainAll(materialCache.keySet());
        prevPositions.keySet().retainAll(materialCache.keySet());

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
            for (Map.Entry<Integer, Entity> entry : materialCache.entrySet()) {
                Integer sc = stableCount.get(entry.getKey());
                if (sc != null && sc >= MIN_STABLE) confirmed++;
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
            for (Map.Entry<Integer, Entity> entry : materialCache.entrySet()) {
                Integer sc = stableCount.get(entry.getKey());
                if (sc != null && sc >= MIN_STABLE) confirmed++;
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

        logInfo("[SCAN] --- Buscando resource chain (lista de recursos separada) ---");
        boolean foundRes = false;
        bestResNear = 0;
        for (int resMgrOff = 0x04; resMgrOff <= 0x80; resMgrOff += 4) {
            if (resMgrOff == 0x24) continue;
            long resMgr = readPtr(step1 + resMgrOff);
            if (resMgr < MIN_PTR || resMgr > 0xFFFFFFF0L) continue;
            foundRes |= probeResMgr(resMgr, resMgrOff, player, "step1", resMgrOff);
        }

        if (resStep1Off >= 0) {
            logInfo(String.format("[SCAN] BEST resource chain: step1+0x%X arr=0x%X cnt=0x%X (near=%d)",
                    resStep1Off, resArrOff, resCntOff, bestResNear));
            probeNamesOnChain(step1, player);
        } else {
            logInfo("[SCAN] Nenhuma resource chain encontrada via step1");
        }

        probeNpcNames(arr, cnt, player);

        logInfo("=== [SCAN] FIM ===");
    }

    private boolean probeResMgr(long resMgr, int mgrOff, Player player, String src, int step1OffParam) {
        boolean found = false;
        for (int arrOff = 0x04; arrOff <= 0xC0; arrOff += 4) {
            long resArr = readPtr(resMgr + arrOff);
            if (resArr < MIN_PTR) continue;

            int[] cntTry = {arrOff - 4, arrOff + 4, 0x14, 0x10, 0x60, 0x64};
            for (int cntOff : cntTry) {
                if (cntOff < 0 || cntOff > 0xC4 || cntOff == arrOff) continue;
                int resCnt = memory.readInt(resMgr + cntOff);
                if (resCnt <= 0 || resCnt > 200) continue;

                int nearHits = 0;
                StringBuilder info = new StringBuilder();
                int limit = Math.min(resCnt, 40);

                for (int i = 0; i < limit; i++) {
                    long ptr = readPtr(resArr + (long) i * 4);
                    if (ptr < MIN_PTR) continue;

                    float x = memory.readFloat(ptr + 0x3C);
                    float y = memory.readFloat(ptr + 0x40);
                    float z = memory.readFloat(ptr + 0x44);
                    if (!isValidPos(x, y, z)) continue;
                    float d = dist3D(x, y, z, player);
                    if (d < 0.5f || d > 200.0f) continue;

                    nearHits++;
                    if (nearHits <= 3) {
                        int rid = memory.readInt(ptr + 0x04);
                        int rid2 = memory.readInt(ptr + 0x08);
                        info.append(String.format(" [i=%d d=%.1f id4=0x%X id8=0x%X]",
                                i, d, rid, rid2));
                    }
                }

                if (nearHits > 0) {
                    logInfo(String.format("[SCAN] RES src=%s mgrOff=0x%X arrOff=0x%X cntOff=0x%X cnt=%d near=%d%s",
                            src, mgrOff, arrOff, cntOff, resCnt, nearHits, info.toString()));
                    found = true;
                    if (step1OffParam >= 0 && nearHits > bestResNear) {
                        bestResNear = nearHits;
                        resStep1Off = step1OffParam;
                        resArrOff = arrOff;
                        resCntOff = cntOff;
                    }
                }
            }
        }
        return found;
    }

    private void probeNamesOnChain(long step1, Player player) {
        if (resStep1Off < 0) return;
        long resMgr = readPtr(step1 + resStep1Off);
        if (resMgr < MIN_PTR) return;
        long arr = readPtr(resMgr + resArrOff);
        int cnt = memory.readInt(resMgr + resCntOff);
        if (arr < MIN_PTR || cnt <= 0) return;

        logInfo("[SCAN] --- Name probe on resource entities ---");
        int probed = 0;
        for (int i = 0; i < Math.min(cnt, 20) && probed < 3; i++) {
            long ep = readPtr(arr + (long) i * 4);
            if (ep < MIN_PTR) continue;
            float x = memory.readFloat(ep + 0x3C);
            float y = memory.readFloat(ep + 0x40);
            float z = memory.readFloat(ep + 0x44);
            if (!isValidPos(x, y, z)) continue;
            float d = dist3D(x, y, z, player);
            if (d > 200.0f) continue;
            probeEntityNames(ep, d, "RES");
            probed++;
        }
        if (probed == 0) logInfo("[SCAN] No valid resource entities to probe names");
    }

    private void probeNpcNames(long arr, int cnt, Player player) {
        logInfo("[SCAN] --- Name probe on NPC type!=6 entities ---");
        int probed = 0;
        for (int i = 0; i < cnt && probed < 2; i++) {
            long ep = readPtr(arr + (long) i * 4);
            if (ep < MIN_PTR) continue;
            int type = memory.readInt(ep + GameConstants.OFFSET_TYPE);
            if (type == GameConstants.TYPE_MOB) continue;
            float x = memory.readFloat(ep + 0x3C);
            float y = memory.readFloat(ep + 0x40);
            float z = memory.readFloat(ep + 0x44);
            if (!isValidPos(x, y, z)) continue;
            float d = dist3D(x, y, z, player);
            probeEntityNames(ep, d, "NPC-type" + type);
            probed++;
        }
        if (probed == 0) logInfo("[SCAN] No NPC type!=6 entities found for name probe");
    }

    private void probeEntityNames(long ep, float dist, String label) {
        logInfo(String.format("[NAME-PROBE] %s ep=0x%X dist=%.1f", label, ep, dist));
        int[] offsets = {
            0x08, 0x0C, 0x10, 0x14, 0x18, 0x1C, 0x20, 0x24, 0x28, 0x2C, 0x30, 0x34, 0x38,
            0x58, 0x5C, 0x60, 0x64, 0x68, 0x6C, 0x70, 0x74, 0x78, 0x7C, 0x80,
            0xB0, 0xB4, 0xB8, 0xBC, 0xC0, 0xC4, 0xC8, 0xCC, 0xD0,
            0x100, 0x104, 0x108, 0x10C, 0x110, 0x114, 0x118, 0x11C, 0x120,
            0x148, 0x14C, 0x150, 0x154, 0x158, 0x15C, 0x160, 0x164, 0x168, 0x16C, 0x170,
            0x180, 0x184, 0x188, 0x18C, 0x190, 0x194, 0x198, 0x19C, 0x1A0,
            0x1C0, 0x1D0, 0x1E0, 0x1F0, 0x200, 0x210, 0x220, 0x230, 0x234, 0x238, 0x23C, 0x240,
            0x268, 0x26C, 0x270, 0x274, 0x278, 0x27C, 0x280, 0x284, 0x288, 0x28C, 0x290,
            0x2C0, 0x2D0, 0x2E0, 0x2F0, 0x300, 0x310, 0x320, 0x340, 0x360, 0x380, 0x3A0, 0x3C0
        };
        for (int off : offsets) {
            try {
                long ptr = readPtr(ep + off);
                if (ptr >= MIN_PTR && ptr <= 0x7FFFFFFFL) {
                    String s = memory.readString(ptr, 20);
                    if (isRealName(s)) {
                        logInfo(String.format("[NAME-PROBE]   ptr@+0x%X -> 0x%X = '%s'", off, ptr, clip(s)));
                    }
                }
            } catch (Exception ignored) {}
            try {
                String s = memory.readString(ep + off, 20);
                if (isRealName(s)) {
                    logInfo(String.format("[NAME-PROBE]   wchar@+0x%X = '%s'", off, clip(s)));
                }
            } catch (Exception ignored) {}
        }
        long sub = readPtr(ep);
        if (sub >= MIN_PTR && sub <= 0x7FFFFFFFL) {
            for (int off : new int[]{0x160, 0x164, 0x168, 0x23C, 0x240, 0x280, 0x284, 0x288}) {
                try {
                    long ptr = readPtr(sub + off);
                    if (ptr >= MIN_PTR && ptr <= 0x7FFFFFFFL) {
                        String s = memory.readString(ptr, 20);
                        if (isRealName(s)) {
                            logInfo(String.format("[NAME-PROBE]   [ep]+0x%X -> 0x%X = '%s'", off, ptr, clip(s)));
                        }
                    }
                } catch (Exception ignored) {}
                try {
                    String s = memory.readString(sub + off, 20);
                    if (isRealName(s)) {
                        logInfo(String.format("[NAME-PROBE]   [ep]wchar@+0x%X = '%s'", off, clip(s)));
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private boolean isRealName(String s) {
        if (s == null || s.isEmpty() || s.equals("Unknown")) return false;
        int len = 0, good = 0;
        for (int i = 0; i < s.length() && i < 60; i++) {
            char c = s.charAt(i);
            if (c == 0) break;
            len++;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == ' ' || c == '_' || c == '-' || c == '(' || c == ')') {
                good++;
            } else if (c >= 0x4E00 && c <= 0x9FFF) {
                good++;
            } else if (c >= 0x3000 && c <= 0x30FF) {
                good++;
            } else if (c >= 0x00C0 && c <= 0x024F) {
                good++;
            }
        }
        return len >= 2 && good >= 2 && (float) good / len >= 0.6f;
    }

    private String clip(String s) {
        if (s == null) return "?";
        int end = s.indexOf(0);
        if (end >= 0) s = s.substring(0, end);
        return s.length() > 40 ? s.substring(0, 40) : s;
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

            if (dist > MAT_MAX_DIST || dist < 0.1f) {
                if (tickCount % 50 == 0) {
                    logInfo(String.format("[MAT-SKIP] type=%d ep=0x%X dist=%.1f (out of range)", type, ep, dist));
                }
                continue;
            }

            int uid = (int) (ep & 0x7FFFFFFFL);
            matIds.add(uid);
            Entity ent = getOrCreate(materialCache, uid, type);
            fill(ent, ep, x, y, z, type, player);
            if (tickCount % 50 == 0) {
                logInfo(String.format("[MAT-OK] type=%d ep=0x%X dist=%.1f pos=(%.1f,%.1f,%.1f)", type, ep, dist, x, y, z));
            }
        }
    }

    private void scanResourceArray(long moduleBase, Player player, Set<Integer> matIds) {
        if (resStep1Off < 0) return;
        long rootVal = readPtr(moduleBase + GameConstants.BASE_OFFSET);
        if (rootVal < MIN_PTR) return;
        long step1 = readPtr(rootVal + 0x18);
        if (step1 < MIN_PTR) return;
        long resMgr = readPtr(step1 + resStep1Off);
        if (resMgr < MIN_PTR) return;
        long arr = readPtr(resMgr + resArrOff);
        int cnt = memory.readInt(resMgr + resCntOff);
        if (arr < MIN_PTR || cnt <= 0 || cnt > 800) return;

        if (tickCount <= 2) {
            logInfo(String.format("[RES] chain ok - resMgr=0x%X arr=0x%X cnt=%d", resMgr, arr, cnt));
        }

        for (int i = 0; i < cnt; i++) {
            long ep = readPtr(arr + (long) i * 4);
            if (ep < MIN_PTR) continue;
            float x = memory.readFloat(ep + 0x3C);
            float y = memory.readFloat(ep + 0x40);
            float z = memory.readFloat(ep + 0x44);
            if (!isValidPos(x, y, z)) continue;
            float dist = dist3D(x, y, z, player);
            if (dist > MAT_MAX_DIST || dist < 0.1f) continue;

            int uid = (int) (ep & 0x7FFFFFFFL);
            if (!matIds.contains(uid)) {
                matIds.add(uid);
                Entity ent = getOrCreate(materialCache, uid, GameConstants.TYPE_MATERIAL);
                fill(ent, ep, x, y, z, GameConstants.TYPE_MATERIAL, player);
                if (tickCount % 50 == 0) {
                    logInfo(String.format("[RES-OK] ep=0x%X dist=%.1f pos=(%.1f,%.1f,%.1f)", ep, dist, x, y, z));
                }
            }
        }
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

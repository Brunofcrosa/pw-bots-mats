package com.bot.logic;

import com.bot.constants.BotSettings;
import com.bot.constants.GameConstants;
import com.bot.memory.WinMemoryReader;
import com.bot.model.Entity;
import com.bot.model.Player;
import com.bot.model.ResourceDatabase;
import com.bot.model.ResourceSpawn;
import java.util.*;

public class EntityManager {

    private final WinMemoryReader memory;
    private final long dynamicBaseAddress;
    private final ResourceDatabase resourceDb;

    private final Map<Integer, Entity> mobCache      = new LinkedHashMap<>();
    private final Map<Integer, Entity> materialCache = new LinkedHashMap<>();
    private final Map<Integer, float[]> prevPositions = new HashMap<>();
    private final Map<Integer, float[]> initialPositions = new HashMap<>();
    private final Map<Integer, Integer> stableCount  = new HashMap<>();
    private final Set<Integer> resChainUids = new HashSet<>();  

    private final List<int[]> resChains = new ArrayList<>();

    
    private final Map<Integer, String> smallIdToName = new HashMap<>();
    private final Map<Integer, Integer> smallIdToTemplateId = new HashMap<>();

    
    
    
    
    
    private long matterManPtr = 0;            
    private int htCountOff = -1;              
    private int htBucketDataOff = -1;         
    private int htBucketCountOff = -1;        
    
    private int matterMidOff = 0x110;         
    private int matterTidOff = 0x114;         
    private int matterTypeOff = 0x150;        

    private int tickCount = 0;
    private boolean firstLog = false;

    private static final float MOB_MAX_DIST     = 300.0f;
    private static final float MAT_MAX_DIST     = 150.0f;
    private static final float MOVE_THRESH      = 0.3f;
    private static final float DRIFT_THRESH     = 1.5f;   
    private static final float DRIFT_THRESH_RES = 15.0f;  
    private static final float MIN_MAT_DIST     = 2.0f;   
    private static final float MOB_OVERLAP_DIST = 5.0f;
    private static final int   MIN_STABLE       = 5;
    private static final long  MIN_PTR          = 0x10000L;

    
    private static final int MATTER_MINE = 2;  

    public EntityManager(WinMemoryReader memory, long dynamicBaseAddress, ResourceDatabase resourceDb) {
        this.memory = memory;
        this.dynamicBaseAddress = dynamicBaseAddress;
        this.resourceDb = resourceDb;
    }

    public void update(long moduleBase, Player player) {
        tickCount++;

        if (tickCount == 1) {
            diagScan(moduleBase, player);
        }

        Set<Integer> mobIds = new HashSet<>();
        Set<Integer> matIds = new HashSet<>();

        scanNpcArray(moduleBase, player, mobIds, matIds);
        scanMatterHashtable(moduleBase, player, matIds);

        mobCache.keySet().retainAll(mobIds);
        materialCache.keySet().retainAll(matIds);

        stableCount.keySet().retainAll(materialCache.keySet());
        prevPositions.keySet().retainAll(materialCache.keySet());
        initialPositions.keySet().retainAll(materialCache.keySet());
        resChainUids.retainAll(materialCache.keySet());

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
                    initialPositions.remove(uid);
                } else {
                    
                    float[] init = initialPositions.get(uid);
                    if (init != null) {
                        float dix = cur[0] - init[0];
                        float diy = cur[1] - init[1];
                        float diz = cur[2] - init[2];
                        float drift = (float) Math.sqrt(dix * dix + diy * diy + diz * diz);
                        
                        
                        float maxDrift = resChainUids.contains(uid) ? DRIFT_THRESH_RES : DRIFT_THRESH;
                        if (drift > maxDrift) {
                            if (tickCount % 50 == 0 || stableCount.getOrDefault(uid, 0) >= MIN_STABLE) {
                                logInfo(String.format("[GHOST] Removed uid=%d drift=%.2fm (max=%.1f) from initial pos", uid, drift, maxDrift));
                            }
                            toRemove.add(uid);
                            stableCount.remove(uid);
                            initialPositions.remove(uid);
                        } else {
                            stableCount.merge(uid, 1, Integer::sum);
                        }
                    } else {
                        stableCount.merge(uid, 1, Integer::sum);
                    }
                }
            } else {
                stableCount.put(uid, 1);
                initialPositions.put(uid, cur.clone());
            }
            prevPositions.put(uid, cur);
        }
        for (int uid : toRemove) {
            materialCache.remove(uid);
            matIds.remove(uid);
            prevPositions.remove(uid);
            initialPositions.remove(uid);
            resChainUids.remove(uid);
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
                String name = e.getName() != null ? e.getName() : "?";
                long ep = e.getBaseAddress();
                int sid = memory.readInt(ep + 0x04);
                String matchInfo = "";
                if (resourceDb != null) {
                    ResourceSpawn sp = resourceDb.matchToSpawn(e.getX(), e.getY(), e.getZ());
                    if (sp != null) {
                        float md = sp.distanceTo(e.getX(), e.getY(), e.getZ());
                        int types = resourceDb.countTypesAtPosition(e.getX(), e.getY(), e.getZ());
                        if (types > 1) {
                            matchInfo = String.format(" dbMatch=%s(%.0fm, %d types at spawn)", sp.getCategory(), md, types);
                        } else {
                            matchInfo = String.format(" dbMatch=%s(%.0fm)", sp.getName(), md);
                        }
                    } else if (smallIdToName.containsKey(sid)) {
                        matchInfo = String.format(" idMatch=sid%d->%s", sid, smallIdToName.get(sid));
                    } else {
                        
                        StringBuilder ids = new StringBuilder(" rawIds=[");
                        for (int off : new int[]{0x04, 0x08, 0x0C, 0x10, 0x14, 0x24, 0xB4, 0xB8, 0x190}) {
                            int v = memory.readInt(ep + off);
                            ids.append(String.format("+0x%X=%d ", off, v));
                        }
                        ids.append("]");
                        matchInfo = ids.toString();
                    }
                }
                logInfo(String.format("[DETECT]   addr=0x%X name=%s sid=%d pos=(%.1f,%.1f,%.1f) dist=%.1f stable=%d%s",
                        ep, name, sid,
                        e.getX(), e.getY(), e.getZ(), e.getDistance(),
                        sc != null ? sc : 0, matchInfo));
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

        
        logInfo("[SCAN] --- Buscando MatterMan (CECMatterMan via source PW 1.5.5) ---");
        diagMatterMan(step1, player);

        
        logInfo("[SCAN] --- Buscando resource chain (legacy, para comparacao) ---");
        boolean foundRes = false;
        resChains.clear();

        for (int resMgrOff = 0x04; resMgrOff <= 0x80; resMgrOff += 4) {
            if (resMgrOff == 0x24) continue;
            long resMgr = readPtr(step1 + resMgrOff);
            if (resMgr < MIN_PTR || resMgr > 0xFFFFFFF0L) continue;
            foundRes |= probeResMgr(resMgr, resMgrOff, player, "step1", resMgrOff);
        }

        if (!resChains.isEmpty()) {
            logInfo(String.format("[SCAN] Found %d resource chains:", resChains.size()));
            for (int[] ch : resChains) {
                logInfo(String.format("[SCAN]   step1+0x%X arr=0x%X cnt=0x%X", ch[0], ch[1], ch[2]));
            }
            probeNamesOnChain(step1, player);
            hexDumpResourceEntities(step1, player);
        } else {
            logInfo("[SCAN] Nenhuma resource chain encontrada via step1");
        }

        probeNpcNames(arr, cnt, player);

        
        if (resourceDb != null && resourceDb.getSpawnCount() > 0) {
            List<ResourceSpawn> nearSpawns = resourceDb.findNearby(
                    player.getX(), player.getY(), player.getZ(), 150.0f);
            logInfo(String.format("[RES-DB] %d spawns conhecidos dentro de 150m do jogador:", nearSpawns.size()));
            Map<String, Integer> catCount = new LinkedHashMap<>();
            for (ResourceSpawn sp : nearSpawns) {
                catCount.merge(sp.getCategory(), 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> e : catCount.entrySet()) {
                logInfo(String.format("[RES-DB]   %s: %d spawns", e.getKey(), e.getValue()));
            }
            int shown = 0;
            for (ResourceSpawn sp : nearSpawns) {
                if (shown >= 10) break;
                float d = sp.distanceTo(player.getX(), player.getY(), player.getZ());
                logInfo(String.format("[RES-DB]   %s Lv%d dist=%.1fm pos=(%.1f,%.1f,%.1f)",
                        sp.getName(), sp.getLevel(), d, sp.getX(), sp.getY(), sp.getZ()));
                shown++;
            }
        }

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

                int nearHits = 0, smallIdCount = 0;
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
                    if (d > 200.0f) continue;

                    nearHits++;
                    int rid = memory.readInt(ptr + 0x04);
                    if (rid > 0 && rid < 0x10000) smallIdCount++;
                    if (nearHits <= 3) {
                        int rid2 = memory.readInt(ptr + 0x08);
                        info.append(String.format(" [i=%d d=%.1f id4=0x%X id8=0x%X]",
                                i, d, rid, rid2));
                    }
                }

                if (nearHits > 0) {
                    boolean hasSmallIds = smallIdCount > 0;
                    logInfo(String.format("[SCAN] RES src=%s mgrOff=0x%X arrOff=0x%X cntOff=0x%X cnt=%d near=%d smallId=%d/%d%s%s",
                            src, mgrOff, arrOff, cntOff, resCnt, nearHits, smallIdCount, nearHits,
                            hasSmallIds ? " [RESOURCE]" : "", info.toString()));
                    found = true;

                    if (step1OffParam >= 0 && hasSmallIds) {
                        boolean dup = false;
                        for (int[] existing : resChains) {
                            if (existing[0] == step1OffParam && existing[1] == arrOff && existing[2] == cntOff) {
                                dup = true;
                                break;
                            }
                        }
                        if (!dup) {
                            resChains.add(new int[]{step1OffParam, arrOff, cntOff});
                        }
                    }
                }
            }
        }
        return found;
    }

    
    private void hexDumpResourceEntities(long step1, Player player) {
        if (resChains.isEmpty()) return;
        int[] ch = resChains.get(0);
        long resMgr = readPtr(step1 + ch[0]);
        if (resMgr < MIN_PTR) return;
        long arr = readPtr(resMgr + ch[1]);
        int cnt = memory.readInt(resMgr + ch[2]);
        if (arr < MIN_PTR || cnt <= 0) return;

        logInfo("[SCAN] --- Hex dump of resource entities (finding template ID offset) ---");

        
        List<long[]> entities = new ArrayList<>(); 
        for (int i = 0; i < Math.min(cnt, 30); i++) {
            long ep = readPtr(arr + (long) i * 4);
            if (ep < MIN_PTR) continue;
            float x = memory.readFloat(ep + 0x3C);
            float y = memory.readFloat(ep + 0x40);
            float z = memory.readFloat(ep + 0x44);
            if (!isValidPos(x, y, z)) continue;
            float d = dist3D(x, y, z, player);
            if (d > 150.0f) continue;
            entities.add(new long[]{ep, (long)(d * 100)});
        }
        entities.sort(Comparator.comparingLong(a -> a[1]));
        int dumpCount = Math.min(entities.size(), 4);
        if (dumpCount < 2) {
            logInfo("[SCAN] Not enough resource entities for comparison dump");
            return;
        }

        
        int maxOff = 0x200;
        int slots = maxOff / 4;
        int[][] values = new int[dumpCount][slots];
        long[] eps = new long[dumpCount];

        for (int e = 0; e < dumpCount; e++) {
            eps[e] = entities.get(e)[0];
            for (int s = 0; s < slots; s++) {
                values[e][s] = memory.readInt(eps[e] + (long) s * 4);
            }
        }

        
        
        StringBuilder diffLog = new StringBuilder("[HEXDIFF] Offsets where entities differ:\n");
        int diffCount = 0;
        for (int s = 0; s < slots; s++) {
            int off = s * 4;
            if (off >= 0x3C && off <= 0x44) continue; 
            boolean allSame = true;
            for (int e = 1; e < dumpCount; e++) {
                if (values[e][s] != values[0][s]) {
                    allSame = false;
                    break;
                }
            }
            if (!allSame) {
                diffCount++;
                StringBuilder line = new StringBuilder(String.format("  +0x%03X:", off));
                for (int e = 0; e < dumpCount; e++) {
                    int v = values[e][s];
                    
                    float f = Float.intBitsToFloat(v);
                    if (!Float.isNaN(f) && !Float.isInfinite(f) && Math.abs(f) > 0.001f && Math.abs(f) < 100000f) {
                        line.append(String.format(" [e%d=%d(0x%X)(%.2f)]", e, v, v, f));
                    } else {
                        line.append(String.format(" [e%d=%d(0x%X)]", e, v, v));
                    }
                }
                diffLog.append(line).append("\n");
            }
        }

        
        for (int e = 0; e < dumpCount; e++) {
            float d = entities.get(e)[1] / 100.0f;
            logInfo(String.format("[HEXDUMP] e%d: ep=0x%X dist=%.1f sid=%d",
                    e, eps[e], d, values[e][1])); 
        }
        logInfo(String.format("[HEXDIFF] %d offsets differ out of %d (0x00-0x%X, excl pos)",
                diffCount, slots, maxOff));
        if (diffCount > 0 && diffCount <= 40) {
            logInfo(diffLog.toString());
        } else if (diffCount > 40) {
            logInfo("[HEXDIFF] Too many diffs (" + diffCount + "), showing first 20:");
            String[] lines = diffLog.toString().split("\n");
            for (int i = 0; i < Math.min(lines.length, 21); i++) {
                logInfo(lines[i]);
            }
        }
    }

    private void probeNamesOnChain(long step1, Player player) {
        if (resChains.isEmpty()) return;
        int[] ch = resChains.get(0);
        long resMgr = readPtr(step1 + ch[0]);
        if (resMgr < MIN_PTR) return;
        long arr = readPtr(resMgr + ch[1]);
        int cnt = memory.readInt(resMgr + ch[2]);
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

            if (dist > MAT_MAX_DIST || dist < MIN_MAT_DIST) {
                if (tickCount % 50 == 0 && dist < MIN_MAT_DIST) {
                    logInfo(String.format("[MAT-SKIP] type=%d ep=0x%X dist=%.1f (too close, ghost)", type, ep, dist));
                }
                continue;
            }

            int uid = (int) (ep & 0x7FFFFFFFL);
            matIds.add(uid);
            Entity ent = getOrCreate(materialCache, uid, type);
            fill(ent, ep, x, y, z, type, player);
            if (tickCount % 50 == 0) {
                String name = ent.getName() != null ? ent.getName() : "?";
                logInfo(String.format("[MAT-OK] type=%d name=%s ep=0x%X dist=%.1f pos=(%.1f,%.1f,%.1f)", type, name, ep, dist, x, y, z));
            }
        }
    }

    
    private void diagMatterMan(long step1, Player player) {
        
        
        
        
        
        int[] matterManOffsets = {0x28, 0x20, 0x2C, 0x30, 0x34};

        
        long bestMm = 0;
        int bestHtBase = 0;
        int bestMmOff = 0;
        int bestCount = 0;
        int bestMineTypeCount = 0;  
        int bestValidTidCount = 0;  
        long bestBucketData = 0;
        int bestBucketCount = 0;

        for (int mmOff : matterManOffsets) {
            long mm = readPtr(step1 + mmOff);
            if (mm < MIN_PTR || mm > 0xFFFFFFF0L) continue;

            logInfo(String.format("[MATTER] Probing MatterMan candidate at step1+0x%X = 0x%X", mmOff, mm));

            
            StringBuilder dump = new StringBuilder("[MATTER] MatterMan dump: ");
            for (int off = 0; off < 0x60; off += 4) {
                long v = readPtr(mm + off);
                dump.append(String.format("+0x%02X=%d(0x%X) ", off, (int) v, v));
            }
            logInfo(dump.toString());

            
            
            for (int htBase = 0x08; htBase <= 0x50; htBase += 4) {
                int count = memory.readInt(mm + htBase + 0x04);
                if (count <= 0 || count > 500) continue;

                long bucketData = readPtr(mm + htBase + 0x0C);
                if (bucketData < MIN_PTR) continue;

                int bucketCount = memory.readInt(mm + htBase + 0x18);
                if (bucketCount < count || bucketCount > 10000) continue;

                logInfo(String.format("[MATTER] Potential hashtab at MatterMan+0x%X: count=%d bucketData=0x%X bucketCount=%d",
                        htBase, count, bucketData, bucketCount));

                
                int validNodes = 0;
                int mineTypeCount = 0;  
                int validTidCount = 0;  

                for (int b = 0; b < bucketCount; b++) {
                    long nodePtr = readPtr(bucketData + (long) b * 4);
                    if (nodePtr < MIN_PTR) continue;

                    long node = nodePtr;
                    int chainLen = 0;
                    while (node >= MIN_PTR && chainLen < 20) {
                        chainLen++;

                        long matterPtr = readPtr(node + 0x04);  
                        int matterKey = memory.readInt(node + 0x08);

                        if (matterPtr >= MIN_PTR && matterPtr < 0xFFFFFFF0L) {
                            float x = memory.readFloat(matterPtr + 0x3C);
                            float y = memory.readFloat(matterPtr + 0x40);
                            float z = memory.readFloat(matterPtr + 0x44);

                            if (isValidPos(x, y, z)) {
                                validNodes++;
                                
                                int mType = memory.readInt(matterPtr + 0x150);
                                if ((mType & 0xFF) == MATTER_MINE) mineTypeCount++;
                                
                                int tid = memory.readInt(matterPtr + 0x114);
                                if (tid >= 2800 && tid <= 4000 && ResourceDatabase.getNameById(tid) != null) validTidCount++;

                                if (validNodes <= 8) {
                                    int mid = memory.readInt(matterPtr + 0x110);
                                    String dbName = "";
                                    if (resourceDb != null) {
                                        ResourceSpawn sp = resourceDb.matchToSpawn(x, y, z);
                                        if (sp != null) dbName = " dbMatch=" + sp.getName();
                                    }
                                    logInfo(String.format("[MATTER]   CECMatter=0x%X key=%d pos=(%.1f,%.1f,%.1f) d=%.1f mid=%d tid=%d mType=%d(0x%08X)%s",
                                            matterPtr, matterKey, x, y, z, dist3D(x,y,z,player), mid, tid, mType & 0xFF, mType, dbName));
                                }
                            }
                        }

                        node = readPtr(node);  
                    }
                }

                logInfo(String.format("[MATTER] Hashtab score: validNodes=%d mineType=%d validTid=%d (step1+0x%X ht+0x%X)",
                        validNodes, mineTypeCount, validTidCount, mmOff, htBase));

                
                int score = mineTypeCount * 1000 + validTidCount;
                int bestScore = bestMineTypeCount * 1000 + bestValidTidCount;
                if (score > bestScore) {
                    bestMm = mm;
                    bestHtBase = htBase;
                    bestMmOff = mmOff;
                    bestCount = count;
                    bestMineTypeCount = mineTypeCount;
                    bestValidTidCount = validTidCount;
                    bestBucketData = bucketData;
                    bestBucketCount = bucketCount;
                }
            }
        }

        
        if (bestMm != 0 && bestMineTypeCount > 0) {
            logInfo(String.format("[MATTER] *** CONFIRMED hashtab at MatterMan+0x%X (step1+0x%X) count=%d mineType=%d validTid=%d ***",
                    bestHtBase, bestMmOff, bestCount, bestMineTypeCount, bestValidTidCount));
            matterManPtr = bestMm;
            htCountOff = bestHtBase + 0x04;
            htBucketDataOff = bestHtBase + 0x0C;
            htBucketCountOff = bestHtBase + 0x18;
            
            
            logInfo(String.format("[MATTER] Using tid=+0x%X type=+0x%X mid=+0x%X (pre-confirmed offsets)",
                    matterTidOff, matterTypeOff, matterMidOff));
        } else {
            logInfo("[MATTER] WARN: MatterMan hashtable not confirmado (mineTypeCount=0). Usando legacy scan.");
        }
    }

    
    private void probeMatterOffsets(long mm, long bucketData, int bucketCount, Player player) {
        
        List<long[]> matters = new ArrayList<>(); 
        for (int b = 0; b < bucketCount && matters.size() < 30; b++) {
            long node = readPtr(bucketData + (long) b * 4);
            while (node >= MIN_PTR && matters.size() < 30) {
                long matterPtr = readPtr(node + 0x04);
                int key = memory.readInt(node + 0x08);
                if (matterPtr >= MIN_PTR) {
                    float x = memory.readFloat(matterPtr + 0x3C);
                    float y = memory.readFloat(matterPtr + 0x40);
                    float z = memory.readFloat(matterPtr + 0x44);
                    if (isValidPos(x, y, z)) {
                        matters.add(new long[]{matterPtr, key});
                    }
                }
                node = readPtr(node);
            }
        }

        if (matters.isEmpty()) return;

        
        int[] tidCandidates = {0x110, 0x114, 0x118, 0x10C, 0x108, 0x120, 0x124, 0x128, 0x104, 0x100};
        int bestTidOff = -1;
        int bestTidMatches = 0;
        for (int tidOff : tidCandidates) {
            int matches = 0;
            for (long[] m : matters) {
                int v = memory.readInt(m[0] + tidOff);
                if (v >= 2800 && v <= 4000 && ResourceDatabase.getNameById(v) != null) {
                    matches++;
                }
            }
            if (matches > bestTidMatches) {
                bestTidMatches = matches;
                bestTidOff = tidOff;
            }
        }

        
        int[] midCandidates = {0x110, 0x10C, 0x108, 0x114, 0x104, 0x100};
        
        int bestMidOff = -1;
        int bestMidMatches = 0;
        for (int midOff : midCandidates) {
            if (midOff == bestTidOff) continue; 
            int matches = 0;
            for (long[] m : matters) {
                int v = memory.readInt(m[0] + midOff);
                if (v == (int) m[1]) { 
                    matches++;
                }
            }
            if (matches > bestMidMatches) {
                bestMidMatches = matches;
                bestMidOff = midOff;
            }
        }

        
        int[] typeCandidates = {0x150, 0x14C, 0x148, 0x154, 0x158, 0x15C, 0x144, 0x140};
        int bestTypeOff = -1;
        int bestTypeMatches = 0;
        for (int typeOff : typeCandidates) {
            int countMine = 0;
            int countItem = 0;
            int countMoney = 0;
            for (long[] m : matters) {
                int v = memory.readInt(m[0] + typeOff) & 0xFF;
                if (v == 1) countItem++;
                else if (v == 2) countMine++;
                else if (v == 3) countMoney++;
            }
            
            int total = countMine + countItem + countMoney;
            if (total > bestTypeMatches) {
                bestTypeMatches = total;
                bestTypeOff = typeOff;
            }
        }

        if (bestTidOff >= 0) {
            matterTidOff = bestTidOff;
            logInfo(String.format("[MATTER] Template ID offset: +0x%X (%d/%d matches)", bestTidOff, bestTidMatches, matters.size()));
        } else {
            logInfo("[MATTER] WARN: no template ID offset found");
        }
        if (bestMidOff >= 0) {
            matterMidOff = bestMidOff;
            logInfo(String.format("[MATTER] Matter ID offset: +0x%X (%d/%d matches)", bestMidOff, bestMidMatches, matters.size()));
        }
        if (bestTypeOff >= 0) {
            matterTypeOff = bestTypeOff;
            logInfo(String.format("[MATTER] Matter type offset: +0x%X (%d/%d matches)", bestTypeOff, bestTypeMatches, matters.size()));
        }
    }

    
    private void scanMatterHashtable(long moduleBase, Player player, Set<Integer> matIds) {
        if (matterManPtr == 0) {
            
            scanResourceArrayLegacy(moduleBase, player, matIds);
            return;
        }

        
        long rootVal = readPtr(moduleBase + GameConstants.BASE_OFFSET);
        if (rootVal < MIN_PTR) return;
        long step1 = readPtr(rootVal + 0x18);
        if (step1 < MIN_PTR) return;
        
        
        
        long mm = matterManPtr;
        
        int count = memory.readInt(mm + htCountOff);
        if (count <= 0 || count > 5000) {
            
            return;
        }

        long bucketData = readPtr(mm + htBucketDataOff);
        int bucketCount = memory.readInt(mm + htBucketCountOff);
        if (bucketData < MIN_PTR || bucketCount <= 0 || bucketCount > 50000) return;

        if (tickCount <= 3) {
            logInfo(String.format("[MATTER] Scanning hashtab: count=%d buckets=%d", count, bucketCount));
        }

        int found = 0;
        for (int b = 0; b < bucketCount; b++) {
            long node = readPtr(bucketData + (long) b * 4);
            int chainLen = 0;

            while (node >= MIN_PTR && chainLen < 50) {
                chainLen++;
                long matterPtr = readPtr(node + 0x04);  
                int matterKey = memory.readInt(node + 0x08);  

                if (matterPtr >= MIN_PTR && matterPtr < 0xFFFFFFF0L) {
                    float x = memory.readFloat(matterPtr + 0x3C);
                    float y = memory.readFloat(matterPtr + 0x40);
                    float z = memory.readFloat(matterPtr + 0x44);

                    if (isValidPos(x, y, z)) {
                        float dist = dist3D(x, y, z, player);
                        if (dist <= MAT_MAX_DIST && dist >= MIN_MAT_DIST) {
                            int uid = (int) (matterPtr & 0x7FFFFFFFL);

                            if (!matIds.contains(uid)) {
                                matIds.add(uid);
                                resChainUids.add(uid);

                                Entity ent = getOrCreate(materialCache, uid, GameConstants.TYPE_MATERIAL);
                                ent.setBaseAddress(matterPtr);
                                ent.setX(x); ent.setY(y); ent.setZ(z);
                                ent.setType(GameConstants.TYPE_MATERIAL);
                                ent.calculateDistance(player);
                                ent.setId(matterKey);

                                
                                if (matterTidOff >= 0) {
                                    int tid = memory.readInt(matterPtr + matterTidOff);
                                    if (tid >= 2800 && tid <= 4000) {
                                        ent.setTemplateId(tid);
                                        String name = ResourceDatabase.getNameById(tid);
                                        if (name != null) {
                                            ent.setName(name);
                                            ent.setDbMatched(true);
                                        }
                                    }
                                }

                                
                                if (!ent.isDbMatched() && resourceDb != null) {
                                    String smartName = resourceDb.smartIdentify(x, y, z);
                                    if (smartName != null) {
                                        ent.setName(smartName);
                                        ent.setDbMatched(true);
                                    } else if (ent.getName() == null) {
                                        ent.setName("Recurso");
                                    }
                                }

                                found++;
                                if (tickCount % 50 == 0) {
                                    String name = ent.getName() != null ? ent.getName() : "?";
                                    logInfo(String.format("[MATTER-OK] name=%s mid=%d tid=%d ep=0x%X dist=%.1f pos=(%.1f,%.1f,%.1f)",
                                            name, matterKey, ent.getTemplateId(), matterPtr, dist, x, y, z));
                                }
                            }
                        }
                    }
                }

                node = readPtr(node);  
            }
        }

        if (tickCount % 50 == 0 && found > 0) {
            logInfo(String.format("[MATTER] Tick %d: found %d matters within %.0fm", tickCount, found, MAT_MAX_DIST));
        }
    }

    private void scanResourceArrayLegacy(long moduleBase, Player player, Set<Integer> matIds) {
        if (resChains.isEmpty()) return;
        long rootVal = readPtr(moduleBase + GameConstants.BASE_OFFSET);
        if (rootVal < MIN_PTR) return;
        long step1 = readPtr(rootVal + 0x18);
        if (step1 < MIN_PTR) return;

        
        Set<Long> scannedArrays = new HashSet<>();

        for (int[] ch : resChains) {
            long resMgr = readPtr(step1 + ch[0]);
            if (resMgr < MIN_PTR) continue;
            long arr = readPtr(resMgr + ch[1]);
            int cnt = memory.readInt(resMgr + ch[2]);
            if (arr < MIN_PTR || cnt <= 0 || cnt > 800) continue;

            
            if (!scannedArrays.add(arr)) continue;

            if (tickCount <= 2) {
                logInfo(String.format("[RES] chain step1+0x%X arr=0x%X cnt=%d", ch[0], arr, cnt));
            }

            for (int i = 0; i < cnt; i++) {
                long ep = readPtr(arr + (long) i * 4);
                if (ep < MIN_PTR) continue;
                float x = memory.readFloat(ep + 0x3C);
                float y = memory.readFloat(ep + 0x40);
                float z = memory.readFloat(ep + 0x44);
                if (!isValidPos(x, y, z)) continue;
                float dist = dist3D(x, y, z, player);
                if (dist > MAT_MAX_DIST || dist < MIN_MAT_DIST) continue;

                int uid = (int) (ep & 0x7FFFFFFFL);
                if (!matIds.contains(uid)) {
                    matIds.add(uid);
                    resChainUids.add(uid);  
                    
                    
                    Entity ent = getOrCreate(materialCache, uid, GameConstants.TYPE_MATERIAL);
                    fill(ent, ep, x, y, z, GameConstants.TYPE_MATERIAL, player);
                    if (tickCount % 50 == 0) {
                        String name = ent.getName() != null ? ent.getName() : "?";
                        logInfo(String.format("[RES-OK] ch=0x%X name=%s ep=0x%X dist=%.1f pos=(%.1f,%.1f,%.1f)", ch[0], name, ep, dist, x, y, z));
                    }
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

        
        int smallId = memory.readInt(ep + 0x04);

        
        if (type != GameConstants.TYPE_MOB && resourceDb != null) {
            
            
            String smartName = resourceDb.smartIdentify(x, y, z);
            if (smartName != null) {
                ent.setName(smartName);
                ent.setDbMatched(true);  
                ResourceSpawn sp = resourceDb.matchToSpawn(x, y, z);
                if (sp != null) {
                    ent.setTemplateId(sp.getTemplateId());
                    
                    if (smallId > 0 && smallId < 0x10000) {
                        smallIdToName.put(smallId, smartName);
                        smallIdToTemplateId.put(smallId, sp.getTemplateId());
                    }
                }
            } else if (!ent.isDbMatched()) {
                
                
                
                String nameById = tryReadTemplateId(ent, ep, type);
                if (nameById != null) {
                    ent.setName(nameById);
                    ent.setDbMatched(true);  
                }
                
                else if (smallId > 0 && smallId < 0x10000 && smallIdToName.containsKey(smallId)) {
                    ent.setName(smallIdToName.get(smallId));
                    if (smallIdToTemplateId.containsKey(smallId)) {
                        ent.setTemplateId(smallIdToTemplateId.get(smallId));
                    }
                }
                else if (ent.getName() == null) {
                    ent.setName(type == GameConstants.TYPE_MATERIAL ? "Material" : "Recurso");
                }
            }
        }
    }

    
    private String tryReadTemplateId(Entity ent, long ep, int type) {
        
        int[] probeOffsets = { 0x04, 0x08, 0x0C, 0x10, 0x14, 0x24, 0xB8, 0xBC };
        for (int off : probeOffsets) {
            int val = memory.readInt(ep + off);
            if (val >= 3074 && val <= 3550) {
                String name = ResourceDatabase.getNameById(val);
                if (name != null) {
                    ent.setTemplateId(val);
                    return name;
                }
            }
        }
        
        if (type == GameConstants.TYPE_MATERIAL) {
            long vt = readPtr(ep);
            if (vt >= MIN_PTR && vt < 0x7FFFFFFFL) {
                for (int off : new int[]{ 0x04, 0x08, 0x0C, 0x10 }) {
                    int val = memory.readInt(vt + off);
                    if (val >= 3074 && val <= 3550) {
                        String name = ResourceDatabase.getNameById(val);
                        if (name != null) {
                            ent.setTemplateId(val);
                            return name;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Entity getOrCreate(Map<Integer, Entity> cache, int uid, int type) {
        return cache.computeIfAbsent(uid, k -> new Entity(uid, 0, 0, 0, type));
    }

    public List<Entity> getMaterials() {
        List<Entity> list = new ArrayList<>();
        for (Map.Entry<Integer, Entity> entry : materialCache.entrySet()) {
            Integer sc = stableCount.get(entry.getKey());
            if (sc != null && sc >= MIN_STABLE) {
                Entity e = entry.getValue();
                
                if (!e.isDbMatched()) {
                    if (tickCount % 200 == 0) {
                        logInfo(String.format("[GHOST-SKIP] %s ep=0x%X dist=%.1f (no DB match, skipping)",
                                e.getName(), e.getBaseAddress(), e.getDistance()));
                    }
                    continue;
                }
                list.add(e);
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
                if (!e.isDbMatched()) continue;  
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

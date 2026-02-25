package com.bot.memory;

import com.bot.constants.BotSettings;
import com.bot.constants.GameConstants;
import com.bot.model.Player;
import java.util.*;

public class MemoryDiffScanner {
    private final WinMemoryReader memory;
    private final long moduleBase;
    private Map<String, Integer> snapshot = new HashMap<>();
    private static final long MIN_VALID_PTR = 0x100000L;
    private static final long MAX_VALID_PTR = 0x7FFFFFFFL;
    private static final float MAX_COORD_ABS = 10000.0f;

    public MemoryDiffScanner(WinMemoryReader memory, long moduleBase) {
        this.memory = memory;
        this.moduleBase = moduleBase;
    }

    public int takeSnapshot() {
        snapshot.clear();
        long rootContext = memory.readInt(moduleBase + GameConstants.BASE_OFFSET) & 0xFFFFFFFFL;
        if (rootContext == 0) return 0;
        Map<Long, Integer> cachedCounts = new HashMap<>();

        for (int off1 = 0x10; off1 <= 0x200; off1 += 4) {
            long level1 = memory.readInt(rootContext + off1) & 0xFFFFFFFFL;
            if (level1 < 0x1000) continue;

            for (int off2 = 0x10; off2 <= 0x200; off2 += 4) {
                long level2 = memory.readInt(level1 + off2) & 0xFFFFFFFFL;
                if (level2 < 0x1000) continue;

                int validL2 = countLikelyMaterialsCached(cachedCounts, level2, GameConstants.SNAPSHOT_SCAN_SIZE);
                if (validL2 >= GameConstants.SNAPSHOT_MIN_HITS) {
                    snapshot.put(String.format("0x%X, 0x%X", off1, off2), validL2);
                }

                for (int off3 = 0x10; off3 <= 0x200; off3 += 4) {
                    long level3 = memory.readInt(level2 + off3) & 0xFFFFFFFFL;
                    if (level3 < 0x1000) continue;

                    int validL3 = countLikelyMaterialsCached(cachedCounts, level3, GameConstants.SNAPSHOT_SCAN_SIZE);
                    if (validL3 >= GameConstants.SNAPSHOT_MIN_HITS) {
                        snapshot.put(String.format("0x%X, 0x%X, 0x%X", off1, off2, off3), validL3);
                    }
                }
            }
        }
        return snapshot.size();
    }

    public List<String> compareDecreased() {
        return filterSnapshot(true);
    }

    public List<String> compareUnchanged() {
        return filterSnapshot(false);
    }

    private List<String> filterSnapshot(boolean expectDecrease) {
        List<String> candidates = new ArrayList<>();
        long rootContext = memory.readInt(moduleBase + GameConstants.BASE_OFFSET) & 0xFFFFFFFFL;
        if (rootContext == 0) return candidates;

        Map<String, Integer> newSnapshot = new HashMap<>();
        Map<Long, Integer> cachedCounts = new HashMap<>();

        for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
            int[] offsets = parseOffsets(entry.getKey());
            long currentPtr = resolveChain(rootContext, offsets);
            if (currentPtr == 0) continue;

            int currentValid = countLikelyMaterialsCached(cachedCounts, currentPtr, GameConstants.SNAPSHOT_SCAN_SIZE);
            int previousValid = entry.getValue();
            int delta = previousValid - currentValid;

            boolean conditionMet = expectDecrease
                    ? (delta >= 1 && delta <= 8 && currentValid > 0)
                    : (Math.abs(currentValid - previousValid) <= 1);

            if (conditionMet) {
                candidates.add(entry.getKey());
                newSnapshot.put(entry.getKey(), currentValid);
            }
        }

        snapshot = newSnapshot;
        return candidates;
    }

    private int countLikelyMaterialsCached(Map<Long, Integer> cache, long arrayAddr, int maxElements) {
        Integer existing = cache.get(arrayAddr);
        if (existing != null) {
            return existing;
        }

        int prefilterHits = countLikelyMaterials(arrayAddr, Math.min(maxElements, GameConstants.SNAPSHOT_PREFILTER_SIZE));
        int result;
        if (prefilterHits == 0) {
            result = 0;
        } else if (maxElements <= GameConstants.SNAPSHOT_PREFILTER_SIZE) {
            result = prefilterHits;
        } else {
            result = countLikelyMaterials(arrayAddr, maxElements);
        }

        cache.put(arrayAddr, result);
        return result;
    }

    private int countLikelyMaterials(long arrayAddr, int maxElements) {
        int[] pointers = memory.readIntArray(arrayAddr, maxElements);
        if (pointers.length == 0) return 0;

        int count = 0;
        for (int ptr : pointers) {
            long unsignedPtr = ptr & 0xFFFFFFFFL;
            if (unsignedPtr < MIN_VALID_PTR || unsignedPtr > MAX_VALID_PTR || unsignedPtr % 4 != 0) {
                continue;
            }

            float x = memory.readFloat(unsignedPtr + GameConstants.OFFSET_X);
            float y = memory.readFloat(unsignedPtr + GameConstants.OFFSET_Y);
            float z = memory.readFloat(unsignedPtr + GameConstants.OFFSET_Z);
            if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
                continue;
            }
            if (Math.abs(x) > MAX_COORD_ABS || Math.abs(y) > MAX_COORD_ABS || Math.abs(z) > MAX_COORD_ABS) {
                continue;
            }

            int matterId = memory.readInt(unsignedPtr + GameConstants.OFFSET_MATTER_ID);
            int type = memory.readInt(unsignedPtr + GameConstants.OFFSET_TYPE);
            int id = memory.readInt(unsignedPtr + GameConstants.OFFSET_ID);

            if (matterId > 0 || type == GameConstants.MATTER_TYPE || id > 0) {
                count++;
            }
        }
        return count;
    }

    private int[] parseOffsets(String candidate) {
        String[] parts = candidate.split(", ");
        int[] offsets = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            offsets[i] = Integer.decode(parts[i]);
        }
        return offsets;
    }

    private long resolveChain(long rootContext, int[] offsets) {
        long currentPtr = rootContext;
        for (int offset : offsets) {
            currentPtr = memory.readInt(currentPtr + offset) & 0xFFFFFFFFL;
            if (currentPtr < 0x1000) {
                return 0;
            }
        }
        return currentPtr;
    }

    public String findCorrectChain(List<String> candidates, Player player) {
        long rootContext = memory.readInt(moduleBase + GameConstants.BASE_OFFSET) & 0xFFFFFFFFL;
        if (rootContext == 0) return null;

        String bestCandidate = null;
        int bestNearHits = -1;
        float bestDistance = Float.MAX_VALUE;

        for (String candidate : candidates) {
            int[] offsets = parseOffsets(candidate);
            long currentPtr = resolveChain(rootContext, offsets);
            if (currentPtr == 0) continue;

            int[] pointers = memory.readIntArray(currentPtr, 800);
            int nearHits = 0;
            float closestDistSq = Float.MAX_VALUE;

            for (int ptr : pointers) {
                long entityPtr = ptr & 0xFFFFFFFFL;
                if (entityPtr < MIN_VALID_PTR || entityPtr > MAX_VALID_PTR || entityPtr % 4 != 0) {
                    continue;
                }

                float x = memory.readFloat(entityPtr + GameConstants.OFFSET_X);
                float y = memory.readFloat(entityPtr + GameConstants.OFFSET_Y);
                float z = memory.readFloat(entityPtr + GameConstants.OFFSET_Z);
                if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
                    continue;
                }

                float dx = x - player.getX();
                float dz = z - player.getZ();
                float horizontalDistSq = dx * dx + dz * dz;

                if (horizontalDistSq < closestDistSq) {
                    closestDistSq = horizontalDistSq;
                }

                int matterId = memory.readInt(entityPtr + GameConstants.OFFSET_MATTER_ID);
                int type = memory.readInt(entityPtr + GameConstants.OFFSET_TYPE);
                if (horizontalDistSq <= GameConstants.GPS_HIT_DISTANCE * GameConstants.GPS_HIT_DISTANCE &&
                        (matterId > 0 || type == GameConstants.MATTER_TYPE)) {
                    nearHits++;
                }
            }

            float closestDist = closestDistSq == Float.MAX_VALUE ? Float.MAX_VALUE : (float) Math.sqrt(closestDistSq);
            if (nearHits > bestNearHits || (nearHits == bestNearHits && closestDist < bestDistance)) {
                bestNearHits = nearHits;
                bestDistance = closestDist;
                bestCandidate = candidate;
            }
        }

        if (bestCandidate != null && (bestNearHits > 0 || bestDistance <= GameConstants.GPS_FALLBACK_DISTANCE)) {
            logInfo("[OFFSET] MATTER_LIST_CHAIN = { " + bestCandidate + " }");
            logDiagnostic("[DIAG] ==============================================");
            logDiagnostic("[DIAG] Sucesso: array lógico encontrado.");
            logDiagnostic("[DIAG] Cadeia: MATTER_LIST_CHAIN = { " + bestCandidate + " }");
            logDiagnostic(String.format("[DIAG] nearHits=%d closestDist=%.2f", bestNearHits, bestDistance));
            logDiagnostic("[DIAG] ==============================================");
            return bestCandidate;
        }
        return null;
    }

    private void logDiagnostic(String msg) {
        if (BotSettings.isDiagnosticLogsEnabled()) {
            System.out.println(msg);
        }
    }

    private void logInfo(String msg) {
        System.out.println(msg);
        BotSettings.logToUi(msg);
    }
}
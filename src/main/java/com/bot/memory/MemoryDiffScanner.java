package com.bot.memory;

import com.bot.constants.GameConstants;
import com.bot.model.Player;
import java.util.*;

public class MemoryDiffScanner {
    private final WinMemoryReader memory;
    private final long moduleBase;
    private Map<String, Integer> snapshot = new HashMap<>();

    public MemoryDiffScanner(WinMemoryReader memory, long moduleBase) {
        this.memory = memory;
        this.moduleBase = moduleBase;
    }

    public int takeSnapshot() {
        snapshot.clear();
        long rootContext = memory.readInt(moduleBase + GameConstants.BASE_OFFSET) & 0xFFFFFFFFL;
        if (rootContext == 0) return 0;

        for (int off1 = 0x10; off1 <= 0x200; off1 += 4) {
            long level1 = memory.readInt(rootContext + off1) & 0xFFFFFFFFL;
            if (level1 < 0x1000) continue;

            for (int off2 = 0x10; off2 <= 0x200; off2 += 4) {
                long level2 = memory.readInt(level1 + off2) & 0xFFFFFFFFL;
                if (level2 < 0x1000) continue;

                int validL2 = countValidPointers(level2, 800);
                if (validL2 > 0) {
                    snapshot.put(String.format("0x%X, 0x%X", off1, off2), validL2);
                }

                for (int off3 = 0x10; off3 <= 0x200; off3 += 4) {
                    long level3 = memory.readInt(level2 + off3) & 0xFFFFFFFFL;
                    if (level3 < 0x1000) continue;

                    int validL3 = countValidPointers(level3, 800);
                    if (validL3 > 0) {
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

        for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
            String[] parts = entry.getKey().split(", ");
            int[] offsets = new int[parts.length];
            for (int i = 0; i < parts.length; i++) offsets[i] = Integer.decode(parts[i]);

            long currentPtr = rootContext;
            boolean isValidChain = true;

            for (int offset : offsets) {
                currentPtr = memory.readInt(currentPtr + offset) & 0xFFFFFFFFL;
                if (currentPtr < 0x1000) {
                    isValidChain = false;
                    break;
                }
            }

            if (!isValidChain) continue;

            int currentValid = countValidPointers(currentPtr, 800);
            int previousValid = entry.getValue();

            boolean conditionMet = expectDecrease
                    ? (currentValid < previousValid && currentValid >= previousValid - 3)
                    : (currentValid == previousValid);

            if (conditionMet) {
                candidates.add(entry.getKey());
                newSnapshot.put(entry.getKey(), currentValid);
            }
        }

        snapshot = newSnapshot;
        return candidates;
    }

    private int countValidPointers(long arrayAddr, int maxElements) {
        int[] pointers = memory.readIntArray(arrayAddr, maxElements);
        if (pointers.length == 0) return 0;
        int count = 0;
        for (int ptr : pointers) {
            long unsignedPtr = ptr & 0xFFFFFFFFL;
            if (unsignedPtr > 0x1000000 && unsignedPtr % 4 == 0) count++;
        }
        return count;
    }

    /**
     * Técnica GPS: Procura um array onde haja um objeto exatamente nas mesmas coordenadas do Player.
     */
    public String findCorrectChain(List<String> candidates, Player player) {
        long rootContext = memory.readInt(moduleBase + GameConstants.BASE_OFFSET) & 0xFFFFFFFFL;
        if (rootContext == 0) return null;

        for (String candidate : candidates) {
            String[] parts = candidate.split(", ");
            int[] offsets = new int[parts.length];
            for (int i = 0; i < parts.length; i++) offsets[i] = Integer.decode(parts[i]);

            long currentPtr = rootContext;
            boolean isValidChain = true;
            for (int offset : offsets) {
                currentPtr = memory.readInt(currentPtr + offset) & 0xFFFFFFFFL;
                if (currentPtr < 0x1000) { isValidChain = false; break; }
            }
            if (!isValidChain) continue;

            int[] pointers = memory.readIntArray(currentPtr, 800);
            for (int ptr : pointers) {
                long entityPtr = ptr & 0xFFFFFFFFL;
                if (entityPtr > 0x1000000 && entityPtr % 4 == 0) {
                    // Busca bloco contíguo de floats (X, Y, Z) no range de offsets comuns
                    for (int offset = 0x10; offset <= 0x150; offset += 4) {
                        float x = memory.readFloat(entityPtr + GameConstants.OFFSET_X);
                        float y = memory.readFloat(entityPtr + GameConstants.OFFSET_Y);
                        float z = memory.readFloat(entityPtr + GameConstants.OFFSET_Z);

                        // Tolerância de 2.0 metros, pois o player e o matinho nunca estão no 0.0 absoluto
                        if (Math.abs(x - player.getX()) < 1.5f &&
                                Math.abs(y - player.getY()) < 1.5f) { // Remova a verificação de Z temporariamente se houver dúvidas de altitude

                            System.out.println("\n==============================================");
                            System.out.println("[!] SUCESSO ABSOLUTO! O ARRAY LÓGICO FOI ACHADO!");
                            System.out.println("-> CADEIA EXATA: MATTER_LIST_CHAIN = { " + candidate + " }");
                            System.out.println("==============================================\n");
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }
}
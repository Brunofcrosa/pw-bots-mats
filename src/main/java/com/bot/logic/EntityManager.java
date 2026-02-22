package com.bot.logic;

import com.bot.constants.GameConstants;
import com.bot.memory.WinMemoryReader;
import com.bot.model.Entity;
import com.bot.model.Player;
import java.util.*;

public class EntityManager {
    private final WinMemoryReader memory;
    private final long dynamicBaseAddress;

    private final Map<Integer, Entity> mobCache = new HashMap<>();
    private final Map<Integer, Entity> materialCache = new HashMap<>();

    public EntityManager(WinMemoryReader memory, long dynamicBaseAddress) {
        this.memory = memory;
        this.dynamicBaseAddress = dynamicBaseAddress;
    }

    public void update(long moduleBase, Player player) {
        updateMobs(moduleBase, player);
        updateMaterials(moduleBase, player); // Correção: Passando o moduleBase aqui
    }

    private void updateMobs(long moduleBase, Player player) {
        long staticAddress = moduleBase + GameConstants.BASE_OFFSET;
        long managerPtrAddr = memory.readPointerAddress(staticAddress, GameConstants.ENTITY_LIST_CHAIN);
        if (managerPtrAddr == 0) return;

        long managerAddr = memory.readInt(managerPtrAddr) & 0xFFFFFFFFL;
        if (managerAddr < 0x1000) return;

        long arrayAddr = memory.readInt(managerAddr + GameConstants.OFFSET_NPC_ARRAY) & 0xFFFFFFFFL;
        int size = memory.readInt(managerAddr + GameConstants.OFFSET_NPC_COUNT);

        if (arrayAddr < 0x1000000 || size <= 0 || size > 2000) return;

        List<Integer> currentTickIds = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            long entityPtr = memory.readInt(arrayAddr + (i * 4)) & 0xFFFFFFFFL;
            if (entityPtr < 0x1000000 || entityPtr % 4 != 0) continue;

            int id = memory.readInt(entityPtr + GameConstants.OFFSET_ID);
            int type = memory.readInt(entityPtr + GameConstants.OFFSET_TYPE);

            if (id <= 0 || id == player.getId() || type != GameConstants.TYPE_MOB) continue;

            currentTickIds.add(id);
            Entity ent = mobCache.computeIfAbsent(id, k -> new Entity(id, 0, 0, 0, type));
            ent.setBaseAddress(entityPtr);
            ent.setX(memory.readFloat(entityPtr + GameConstants.OFFSET_X));
            ent.setY(memory.readFloat(entityPtr + GameConstants.OFFSET_Y));
            ent.setZ(memory.readFloat(entityPtr + GameConstants.OFFSET_Z));
            ent.setHp(memory.readInt(entityPtr + GameConstants.OFFSET_HP));
            ent.setMaxHp(memory.readInt(entityPtr + GameConstants.OFFSET_MAX_HP));
            ent.calculateDistance(player);
        }
        mobCache.keySet().retainAll(currentTickIds);
    }

    private void updateMaterials(long moduleBase, Player player) {
        long staticMatterAddress = moduleBase + GameConstants.MATTER_BASE_OFFSET;

        long pointerLocation = memory.readPointerAddress(staticMatterAddress, GameConstants.MATTER_LIST_CHAIN);
        if (pointerLocation == 0) return;

        long arrayAddr = memory.readInt(pointerLocation) & 0xFFFFFFFFL;
        if (arrayAddr < 0x100000) return;

        List<Integer> currentTickIds = new ArrayList<>();

        // EXPANSÃO DO BUFFER: Lemos 2048 slots para cobrir toda a alocação da engine.
        // Isso resolve o problema das ervas ocultas em índices altos do array esparso.
        int[] entityPointers = memory.readIntArray(arrayAddr, 2048);

        for (int ptr : entityPointers) {
            long entityPtr = ptr & 0xFFFFFFFFL;

            // Filtro de Ponteiro Inválido
            if (entityPtr < 0x100000) continue;

            float x = memory.readFloat(entityPtr + GameConstants.OFFSET_X);
            float y = memory.readFloat(entityPtr + GameConstants.OFFSET_Y);
            float z = memory.readFloat(entityPtr + GameConstants.OFFSET_Z);

            // 1. FILTRO DE CORRUPÇÃO (BLINDAGEM CONTRA NaN)
            // Se a memória lida não for um float válido, aborta a leitura deste slot imediatamente.
            if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) continue;

            // 2. FILTRO ANTI-LIXO E LIMITES DO MAPA
            // O mapa do PW não possui coordenadas acima de 10.000. Se passar disso, é lixo de memória.
            if ((x == 0.0f && y == 0.0f) || Math.abs(x) > 10000.0f || Math.abs(y) > 10000.0f) continue;

            // 3. CULLING ESPACIAL
            if (Math.abs(x - player.getX()) > 150.0f || Math.abs(y - player.getY()) > 150.0f) continue;

            int uniqueId = (int) entityPtr;
            currentTickIds.add(uniqueId);

            Entity ent = materialCache.computeIfAbsent(uniqueId, k -> new Entity(uniqueId, 0, 0, 0, GameConstants.TYPE_MATERIAL));

            ent.setBaseAddress(entityPtr);
            ent.setX(x);
            ent.setY(y);
            ent.setZ(z);
            ent.calculateDistance(player);
        }

        materialCache.keySet().retainAll(currentTickIds);
    }

    public List<Entity> getMaterials() {
        List<Entity> list = new ArrayList<>(materialCache.values());
        list.sort(Comparator.comparingDouble(Entity::getDistance));
        return list;
    }

    public Entity getNearestMob() {
        return mobCache.values().stream().min(Comparator.comparingDouble(Entity::getDistance)).orElse(null);
    }

    public Entity getNearestMaterial() {
        return materialCache.values().stream().min(Comparator.comparingDouble(Entity::getDistance)).orElse(null);
    }
}
package com.bot.logic;

import com.bot.constants.GameConstants;
import com.bot.memory.WinMemoryReader;
import com.bot.model.Entity;
import com.bot.model.Player;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EntityManager {
    private WinMemoryReader memory;
    private List<Entity> mobs = new ArrayList<>();
    private List<Entity> materials = new ArrayList<>();

    public EntityManager(WinMemoryReader memory) {
        this.memory = memory;
    }

    public void update(long moduleBase, Player player) {
        mobs.clear();
        materials.clear();

        long staticAddress = moduleBase + GameConstants.BASE_OFFSET;
        long entityManBase = memory.readPointerAddress(staticAddress, GameConstants.ENTITY_LIST_CHAIN);
        if (entityManBase == 0) return;

        long pEntityArray = memory.readInt(entityManBase + 0x1C) & 0xFFFFFFFFL;
        int size = memory.readInt(entityManBase + 0x20);

        for (int i = 0; i < size; i++) {
            long entityPtr = memory.readInt(pEntityArray + (i * 4)) & 0xFFFFFFFFL;
            if (entityPtr == 0) continue;

            int id = memory.readInt(entityPtr + GameConstants.OFFSET_ID);
            int type = memory.readInt(entityPtr + GameConstants.OFFSET_TYPE);

            if (id == 0 || id == player.getId()) continue;

            Entity ent = new Entity(id,
                    memory.readFloat(entityPtr + GameConstants.OFFSET_X),
                    memory.readFloat(entityPtr + GameConstants.OFFSET_Y),
                    memory.readFloat(entityPtr + GameConstants.OFFSET_Z),
                    type);

            ent.setHp(memory.readInt(entityPtr + GameConstants.OFFSET_HP));
            ent.calculateDistance(player);

            if (type == GameConstants.TYPE_MOB && ent.getHp() > 0) {
                mobs.add(ent);
            } else if (type == GameConstants.TYPE_MATERIAL) {
                materials.add(ent);
            }
        }
    }
    public Entity getNearestMob() {
        return mobs.stream()
                .min(Comparator.comparingDouble(Entity::getDistance))
                .orElse(null);
    }

    public List<Entity> getMobs() { return mobs; }
    public List<Entity> getMaterials() { return materials; }
}
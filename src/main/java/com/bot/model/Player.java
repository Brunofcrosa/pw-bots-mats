package com.bot.model;

import com.bot.memory.WinMemoryReader;
import com.bot.constants.GameConstants;

public class Player {
    private int id;
    private int targetId;
    private String name;
    private float x, y, z;
    private int hp, maxHp, mp, maxMp, level;

    public void update(WinMemoryReader memory, long moduleBase) {
        long staticAddress = moduleBase + GameConstants.BASE_OFFSET;
        long pointerLocation = memory.readPointerAddress(staticAddress, GameConstants.PLAYER_STRUCTURE_CHAIN);
        if (pointerLocation == 0) return;

        long playerStructAddress = memory.readInt(pointerLocation) & 0xFFFFFFFFL;

        if (playerStructAddress != 0) {
            this.id = memory.readInt(playerStructAddress + GameConstants.OFFSET_ID);
            this.name = memory.readStringFromPointer(playerStructAddress, GameConstants.OFFSET_NAME_PTR);
            this.level = memory.readInt(playerStructAddress + GameConstants.OFFSET_LEVEL);
            this.hp = memory.readInt(playerStructAddress + GameConstants.OFFSET_HP);
            this.mp = memory.readInt(playerStructAddress + GameConstants.OFFSET_MP);
            this.maxHp = memory.readInt(playerStructAddress + GameConstants.OFFSET_MAX_HP);
            this.maxMp = memory.readInt(playerStructAddress + GameConstants.OFFSET_MAX_MP);
            this.targetId = memory.readInt(playerStructAddress + GameConstants.OFFSET_TARGET_ID);
            this.x = memory.readFloat(playerStructAddress + GameConstants.OFFSET_X);
            this.y = memory.readFloat(playerStructAddress + GameConstants.OFFSET_Y);
            this.z = memory.readFloat(playerStructAddress + GameConstants.OFFSET_Z);
        }
    }

    public int getId() { return id; }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public int getHp() { return hp; }
    public String getName() { return name; }
    public int getLevel() { return level; }
    public int getMaxHp() { return maxHp; }
    public int getMp() { return mp; }
    public int getMaxMp() { return maxMp; }

    public void setTarget(WinMemoryReader memory, long moduleBase, int targetId) {
        long staticAddress = moduleBase + GameConstants.BASE_OFFSET;
        long pointerLocation = memory.readPointerAddress(staticAddress, GameConstants.PLAYER_STRUCTURE_CHAIN);

        if (pointerLocation == 0) return;

        long playerStructAddress = memory.readInt(pointerLocation) & 0xFFFFFFFFL;

        if (playerStructAddress != 0) {
            memory.writeInt(playerStructAddress + GameConstants.OFFSET_TARGET_ID, targetId);
        }
    }

    public int getTargetId() { return targetId; }

    @Override
    public String toString() {
        return String.format("ID: %d | %s [Lvl: %d | HP: %d/%d]", id, name, level, hp, maxHp);
    }
}
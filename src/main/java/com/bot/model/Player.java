package com.bot.model;

import com.bot.memory.WinMemoryReader;
import com.bot.constants.GameConstants;

public class Player {
    private String name;
    private float x, y, z;
    private int hp, maxHp, mp, maxMp, level;

    public void update(WinMemoryReader memory, long moduleBase) {
        long staticAddress = moduleBase + GameConstants.BASE_OFFSET;
        long pointerLocation = memory.readPointerAddress(staticAddress, GameConstants.PLAYER_STRUCTURE_CHAIN);

        if (pointerLocation == 0) return;

        long playerStructAddress = memory.readInt(pointerLocation) & 0xFFFFFFFFL;

        if (playerStructAddress != 0) {
            this.name = memory.readStringFromPointer(playerStructAddress, GameConstants.OFFSET_NAME_PTR);
            this.level = memory.readInt(playerStructAddress + GameConstants.OFFSET_LEVEL);
            this.hp = memory.readInt(playerStructAddress + GameConstants.OFFSET_HP);
            this.mp = memory.readInt(playerStructAddress + GameConstants.OFFSET_MP);
            this.maxHp = memory.readInt(playerStructAddress + GameConstants.OFFSET_MAX_HP);
            this.maxMp = memory.readInt(playerStructAddress + GameConstants.OFFSET_MAX_MP);

            this.x = memory.readFloat(playerStructAddress + GameConstants.OFFSET_X);
            this.y = memory.readFloat(playerStructAddress + GameConstants.OFFSET_Y);
            this.z = memory.readFloat(playerStructAddress + GameConstants.OFFSET_Z);
        }
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public int getHp() { return hp; }

    @Override
    public String toString() {
        return String.format("%s [Lvl: %d | HP: %d/%d | MP: %d/%d | Pos: %.2f, %.2f, %.2f]",
                name, level, hp, maxHp, mp, maxMp, x, y, z);
    }
}
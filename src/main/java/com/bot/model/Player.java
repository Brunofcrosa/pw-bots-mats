package com.bot.model;

import com.bot.memory.WinMemoryReader;
import com.bot.constants.GameConstants;

public class Player {
    private float x, y, z;
    private int hp, maxHp, mp;

    public void update(WinMemoryReader memory, long moduleBase) {
        long staticAddress = moduleBase + GameConstants.BASE_OFFSET;
        long pointerLocation = memory.readPointerAddress(staticAddress, GameConstants.PLAYER_STRUCTURE_CHAIN);

        if (pointerLocation == 0) return;

        // Lê o valor (ESI) contido no ponteiro final da cadeia
        long playerStructAddress = memory.readInt(pointerLocation) & 0xFFFFFFFFL;

        if (playerStructAddress != 0) {
            this.x = memory.readFloat(playerStructAddress + GameConstants.OFFSET_X);
            this.y = memory.readFloat(playerStructAddress + GameConstants.OFFSET_Y);
            this.z = memory.readFloat(playerStructAddress + GameConstants.OFFSET_Z);
            this.hp = memory.readInt(playerStructAddress + GameConstants.OFFSET_HP);
            this.mp = memory.readInt(playerStructAddress + GameConstants.OFFSET_MP);
            this.maxHp = memory.readInt(playerStructAddress + GameConstants.OFFSET_MAX_HP);
        }
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public int getHp() { return hp; }

    @Override
    public String toString() {
        return String.format("Player [HP: %d/%d | Pos: %.2f, %.2f, %.2f]", hp, maxHp, x, y, z);
    }
}
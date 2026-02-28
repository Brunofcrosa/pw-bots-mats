package com.bot.model;

import com.bot.memory.WinMemoryReader;
import com.bot.constants.GameConstants;

public class Player {
    private int id, targetId, hp, maxHp, mp, maxMp, level;
    private String name;
    private float x, y, z;

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

    private static void moveLog(String msg) {
        System.out.println(msg);
        com.bot.constants.BotSettings.logToUi(msg);
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
    public int getTargetId() { return targetId; }

    
    public long getPlayerStructAddr(WinMemoryReader memory, long moduleBase) {
        long staticAddress = moduleBase + GameConstants.BASE_OFFSET;
        long pointerLocation = memory.readPointerAddress(staticAddress, GameConstants.PLAYER_STRUCTURE_CHAIN);
        if (pointerLocation == 0) return 0;
        return memory.readInt(pointerLocation) & 0xFFFFFFFFL;
    }

    
    public float[] walkStepTo(WinMemoryReader memory, long moduleBase,
                              float tx, float ty, float tz, float maxStepMeters) {
        long ps = getPlayerStructAddr(memory, moduleBase);
        if (ps == 0) {
            moveLog("[WALKSTEP] WARN: playerStruct nulo, sem escrita de movimento");
            return new float[]{this.x, this.y, this.z};
        }

        float px = memory.readFloat(ps + GameConstants.OFFSET_X);
        float py = memory.readFloat(ps + GameConstants.OFFSET_Y);
        float pz = memory.readFloat(ps + GameConstants.OFFSET_Z);

        float dx = tx - px;
        float dz = tz - pz;
        float horizDist = (float) Math.sqrt(dx * dx + dz * dz);

        float nx, nz;
        if (horizDist <= Math.max(0.05f, maxStepMeters)) {
            nx = tx;
            nz = tz;
        } else {
            float ratio = maxStepMeters / horizDist;
            nx = px + dx * ratio;
            nz = pz + dz * ratio;
        }

        
        boolean wx = memory.writeFloat(ps + GameConstants.OFFSET_X, nx);
        boolean wz = memory.writeFloat(ps + GameConstants.OFFSET_Z, nz);

        moveLog(String.format(
                "[WALKSTEP] (%.1f, %.1f) -> (%.1f, %.1f) target=(%.1f, %.1f) d=%.1fm step=%.1fm (mem write X=%b Z=%b)",
                px, pz, nx, nz, tx, tz, horizDist, maxStepMeters, wx, wz));

        return new float[]{nx, py, nz};
    }

    public void setTarget(WinMemoryReader memory, long moduleBase, int targetId) {
        long staticAddress = moduleBase + GameConstants.BASE_OFFSET;
        long pointerLocation = memory.readPointerAddress(staticAddress, GameConstants.PLAYER_STRUCTURE_CHAIN);
        if (pointerLocation == 0) return;
        long playerStructAddress = memory.readInt(pointerLocation) & 0xFFFFFFFFL;
        if (playerStructAddress != 0) memory.writeInt(playerStructAddress + GameConstants.OFFSET_TARGET_ID, targetId);
    }

    
    public void navigateTo(WinMemoryReader memory, long moduleBase,
                           com.bot.input.InputSimulator input, String windowName,
                           float targetX, float targetZ, float dist) {
        long staticAddress = moduleBase + GameConstants.BASE_OFFSET;
        long pointerLocation = memory.readPointerAddress(staticAddress, GameConstants.PLAYER_STRUCTURE_CHAIN);
        long ps = (pointerLocation != 0) ? (memory.readInt(pointerLocation) & 0xFFFFFFFFL) : 0;

        float px = (ps != 0) ? memory.readFloat(ps + GameConstants.OFFSET_X) : this.x;
        float pz = (ps != 0) ? memory.readFloat(ps + GameConstants.OFFSET_Z) : this.z;

        float dx = targetX - px;
        float dz = targetZ - pz;
        // heading convention confirmed: +0x6C=sin(yaw), +0x74=cos(yaw), atan2(dx,dz)=angle toward target
        float angle = (float) Math.atan2(dx, dz);
        float sinVal = (float) Math.sin(angle);
        float cosVal = (float) Math.cos(angle);

        // Write heading so the game engine walks in the right direction when W is held
        if (ps != 0) {
            memory.writeFloat(ps + DIR_SIN_OFF, sinVal);
            memory.writeFloat(ps + DIR_COS_OFF, cosVal);
        }
        moveLog(String.format("[NAV] heading=%.3frad pos=(%.1f,%.1f) target=(%.1f,%.1f) dist=%.1fm",
                angle, px, pz, targetX, targetZ, dist));

        // Bring window to foreground and press W via SendInput (confirmed moves character)
        boolean fg = input.bringToForeground(windowName);
        if (!fg) {
            moveLog("[NAV] WARN: janela nao encontrada, W nao enviado");
            return;
        }
        int holdMs = (int) Math.min(700, Math.max(250, dist * 60));
        input.sendKeyInput(com.bot.input.InputSimulator.Keys.VK_W, holdMs);
        moveLog(String.format("[NAV] W %dms", holdMs));
    }

    
    private static final int DIR_SIN_OFF = 0x6C;
    private static final int DIR_COS_OFF = 0x74;
    
    private static final float MS_PER_RAD = 520f;  

    private float lastNavDist = Float.MAX_VALUE;
    private int consecutiveWrongDir = 0;

    
    public void wasdNavigate(WinMemoryReader memory, long moduleBase,
                              com.bot.input.InputSimulator input, String windowName,
                              float targetX, float targetZ, float currentDist) {
        long staticAddress = moduleBase + GameConstants.BASE_OFFSET;
        long pointerLocation = memory.readPointerAddress(staticAddress, GameConstants.PLAYER_STRUCTURE_CHAIN);
        long playerStructAddr = (pointerLocation != 0) ? (memory.readInt(pointerLocation) & 0xFFFFFFFFL) : 0;

        // Read live player position and heading direction vector
        float px = (playerStructAddr != 0) ? memory.readFloat(playerStructAddr + GameConstants.OFFSET_X) : this.x;
        float pz = (playerStructAddr != 0) ? memory.readFloat(playerStructAddr + GameConstants.OFFSET_Z) : this.z;

        float playerHeading = Float.NaN;
        if (playerStructAddr != 0) {
            float dirSin = memory.readFloat(playerStructAddr + DIR_SIN_OFF);
            float dirCos = memory.readFloat(playerStructAddr + DIR_COS_OFF);
            if (!Float.isNaN(dirSin) && !Float.isNaN(dirCos) && !Float.isInfinite(dirSin) && !Float.isInfinite(dirCos)) {
                playerHeading = (float) Math.atan2(dirSin, dirCos);
                moveLog(String.format("[WASD] heading=%.3frad (sin=%.3f cos=%.3f) pos=(%.1f,%.1f)",
                        playerHeading, dirSin, dirCos, px, pz));
            }
        }

        // Detect wrong direction: dist grew by >1.5m since last call
        boolean wrongDir = (lastNavDist < Float.MAX_VALUE && currentDist > lastNavDist + 1.5f);
        lastNavDist = currentDist;

        // Bring game window to foreground so SendInput works
        boolean fg = input.bringToForeground(windowName);
        if (!fg) {
            moveLog("[WASD] WARN: janela nao encontrada, teclas nao enviadas");
            return;
        }

        if (wrongDir) {
            consecutiveWrongDir++;
            // Extra corrective turn: amount scales with how many consecutive wrong moves
            int extraMs = Math.min(800, 200 + consecutiveWrongDir * 150);
            moveLog(String.format("[WASD] WRONG_DIR dist=%.1f>prev extra turn %dms (#%d)",
                    currentDist, extraMs, consecutiveWrongDir));
            // Determine correction direction from heading — use arrow keys (A/D = strafe in PW)
            if (!Float.isNaN(playerHeading)) {
                float dx = targetX - px, dz = targetZ - pz;
                float angleToTarget = (float) Math.atan2(dx, dz);
                float delta = angleToTarget - playerHeading;
                while (delta >  Math.PI) delta -= 2 * (float) Math.PI;
                while (delta < -Math.PI) delta += 2 * (float) Math.PI;
                // LEFT arrow = turn left (CCW, delta>0); RIGHT arrow = turn right (CW, delta<0)
                int turnKey = (delta >= 0) ? com.bot.input.InputSimulator.Keys.VK_LEFT
                                           : com.bot.input.InputSimulator.Keys.VK_RIGHT;
                input.sendKeyInput(turnKey, extraMs);
            } else {
                input.sendKeyInput(com.bot.input.InputSimulator.Keys.VK_LEFT, extraMs);
            }
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        } else {
            consecutiveWrongDir = 0;
        }

        // Turn toward target using arrow keys (LEFT/RIGHT = turn in PW; A/D = strafe)
        if (!Float.isNaN(playerHeading)) {
            float dx = targetX - px;
            float dz = targetZ - pz;
            float angleToTarget = (float) Math.atan2(dx, dz);
            float delta = angleToTarget - playerHeading;
            while (delta >  Math.PI) delta -= 2 * (float) Math.PI;
            while (delta < -Math.PI) delta += 2 * (float) Math.PI;
            float absDelta = Math.abs(delta);
            if (absDelta > 0.10f) {  // >~6° → turn to align
                int turnMs = (int) Math.min(1400, absDelta * MS_PER_RAD);
                // LEFT arrow = counterclockwise (delta>0); RIGHT arrow = clockwise (delta<0)
                int turnKey = (delta >= 0) ? com.bot.input.InputSimulator.Keys.VK_LEFT
                                           : com.bot.input.InputSimulator.Keys.VK_RIGHT;
                moveLog(String.format("[WASD] Turn%s %dms delta=%.2frad target=%.2f current=%.2f",
                        (delta>=0?"LEFT":"RIGHT"), turnMs, delta, angleToTarget, playerHeading));
                input.sendKeyInput(turnKey, turnMs);
                try { Thread.sleep(80); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }

        // Adaptive W hold: dist*55ms, clamped to [120, 600]ms
        int holdMs = (int) Math.min(600, Math.max(120, currentDist * 55));
        input.sendKeyInput(com.bot.input.InputSimulator.Keys.VK_W, holdMs);
        moveLog(String.format("[WASD] W %dms dist=%.1fm -> (%.1f,%.1f)",
                holdMs, currentDist, targetX, targetZ));
    }

    
    public void resetNavState() { lastNavDist = Float.MAX_VALUE; consecutiveWrongDir = 0; }

    private static final int[] ACTION_STRUCT_OFFSETS = {
        0x11C, 0x118, 0x120, 0x114, 0x110, 0x124, 0x128, 0x12C, 0x130, 0x140, 0x150
    };
    private long confirmedActionStructOffset = -1;

    public void clickToMove(WinMemoryReader memory, long moduleBase, Vector3 destination) {
        long staticAddress = moduleBase + GameConstants.BASE_OFFSET;
        long pointerLocation = memory.readPointerAddress(staticAddress, GameConstants.PLAYER_STRUCTURE_CHAIN);
        if (pointerLocation == 0) return;
        long playerStructAddr = memory.readInt(pointerLocation) & 0xFFFFFFFFL;
        if (playerStructAddr == 0) return;

        if (confirmedActionStructOffset < 0) {
            for (int off : ACTION_STRUCT_OFFSETS) {
                long candidate = memory.readInt(playerStructAddr + off) & 0xFFFFFFFFL;
                if (candidate > 0x10000L && candidate < 0x7FFF0000L) {
                    float cx = memory.readFloat(candidate + 0x20);
                    float cy = memory.readFloat(candidate + 0x24);
                    float cz = memory.readFloat(candidate + 0x28);
                    float dx = Math.abs(cx - this.x);
                    float dy = Math.abs(cy - this.y);
                    float dz = Math.abs(cz - this.z);
                    if (dx < 200 && dy < 200 && dz < 200) {
                        confirmedActionStructOffset = off;
                        moveLog(String.format(
                            "[MOVE] actionStruct confirmado em playerStruct+0x%X = 0x%X (pos=%.1f,%.1f,%.1f)",
                            off, candidate, cx, cy, cz));
                        break;
                    }
                }
            }
            if (confirmedActionStructOffset < 0) {
                StringBuilder sb = new StringBuilder("[MOVE] WARN: nenhum actionStruct valido. Candidatos:");
                for (int off : ACTION_STRUCT_OFFSETS) {
                    long v = memory.readInt(playerStructAddr + off) & 0xFFFFFFFFL;
                    sb.append(String.format(" +0x%X=0x%X", off, v));
                }
                moveLog(sb.toString());
                confirmedActionStructOffset = -2; 
            }
        }

        if (confirmedActionStructOffset == -2) return; 

        long actionStruct = memory.readInt(playerStructAddr + (int) confirmedActionStructOffset) & 0xFFFFFFFFL;
        if (actionStruct != 0 && actionStruct > 0x10000L) {
            boolean w1 = memory.writeFloat(actionStruct + 0x20, destination.getX());
            boolean w2 = memory.writeFloat(actionStruct + 0x24, destination.getY());
            boolean w3 = memory.writeFloat(actionStruct + 0x28, destination.getZ());
            boolean w4 = memory.writeInt(actionStruct + 0x18, 1);
            moveLog(String.format(
                "[MOVE] write dest=(%.0f,%.0f,%.0f) struct=0x%X writes=[%b,%b,%b,flag=%b]",
                destination.getX(), destination.getY(), destination.getZ(),
                actionStruct, w1, w2, w3, w4));
        } else {
            moveLog(String.format(
                "[MOVE] WARN: actionStruct=0x%X invalido (+0x%X)",
                actionStruct, (int) confirmedActionStructOffset));
        }
    }
}

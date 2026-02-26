package com.bot.logic.states;

import com.bot.logic.BotContext;
import com.bot.constants.GameConstants;
import com.bot.constants.BotSettings;
import com.bot.memory.PacketSender;
import com.bot.model.Entity;


public class CollectionState implements BotState {

    private final Entity target;
    private int phase = 0;  
    private long phaseStartTime;
    private int readTargetId = 0;
    private final float originalX, originalY, originalZ;

    private static final long GATHER_TIMEOUT_MS  = 15000; 
    private static final long SELECT_DELAY_MS    = 500;   
    private static final long MIN_GATHER_TIME_MS = 3000;  
    private static final long ENTITY_CHECK_MS    = 1000;  

    public CollectionState() {
        this.target = null;
        this.originalX = 0; this.originalY = 0; this.originalZ = 0;
    }

    public CollectionState(Entity target) {
        this.target = target;
        this.originalX = target != null ? target.getX() : 0;
        this.originalY = target != null ? target.getY() : 0;
        this.originalZ = target != null ? target.getZ() : 0;
    }

    @Override
    public void execute(BotContext ctx) {
        PacketSender pkt = ctx.getPacketSender();

        if (target == null || target.getBaseAddress() == 0) {
            log("[COLLECT] Sem alvo valido, voltando a idle");
            ctx.setState(new IdleState());
            return;
        }

        if (pkt != null && pkt.isInitialized()) {
            executeWithPackets(ctx, pkt);
        } else {
            executeFallback(ctx);
        }
    }

    private void executeWithPackets(BotContext ctx, PacketSender pkt) {
        long now = System.currentTimeMillis();

        switch (phase) {
            case 0: 
                readTargetId = ctx.getMemory().readInt(target.getBaseAddress() + GameConstants.OFFSET_ID);
                if (readTargetId != 0) {
                    boolean ok = pkt.selectTarget(readTargetId);
                    String name = target.getName() != null ? target.getName() : "?";
                    
                    
                    
                    boolean isMatterId = (readTargetId & 0xC0000000) == 0xC0000000;
                    boolean isNpcId = (readTargetId & 0x80000000) != 0 && (readTargetId & 0x40000000) == 0;
                    log(String.format("[COLLECT] Select %s id=%d (0x%X) %s dist=%.1fm %s",
                            name, readTargetId, readTargetId,
                            isMatterId ? "MATTER" : isNpcId ? "NPC" : "PLAYER",
                            target.getDistance(),
                            ok ? "OK" : "FALHOU"));
                } else {
                    log(String.format("[COLLECT] WARN: targetId=0 para addr=0x%X, abortando",
                            target.getBaseAddress()));
                    ctx.blacklist(target.getBaseAddress());
                    ctx.setState(new IdleState());
                    return;
                }
                phase = 1;
                phaseStartTime = now;
                break;

            case 1: 
                if (now - phaseStartTime < SELECT_DELAY_MS) return;
                if (readTargetId != 0) {
                    boolean ok;
                    boolean isMatterId = (readTargetId & 0xC0000000) == 0xC0000000;
                    String name = target.getName() != null ? target.getName() : "?";

                    if (isMatterId) {
                        
                        
                        ok = pkt.gatherMaterial(readTargetId);
                        log(String.format("[COLLECT] GatherMaterial %s (mid=%d) %s",
                                name, readTargetId, ok ? "OK" : "FALHOU"));
                    } else {
                        
                        
                        ok = pkt.gatherMaterial(readTargetId);
                        log(String.format("[COLLECT] GatherMaterial (NPC id) %s (nid=%d) %s",
                                name, readTargetId, ok ? "OK" : "FALHOU"));
                        if (!ok) {
                            
                            ok = pkt.startNpcDialogue(readTargetId);
                            log(String.format("[COLLECT] Fallback NpcDialogue %s (nid=%d) %s",
                                    name, readTargetId, ok ? "OK" : "FALHOU"));
                        }
                    }
                    if (!ok) {
                        log("[COLLECT] Falha na interacao, blacklistando");
                        ctx.blacklist(target.getBaseAddress());
                        ctx.setState(new IdleState());
                        return;
                    }
                }
                phase = 2;
                phaseStartTime = now;
                break;

            case 2: 
                long elapsed = now - phaseStartTime;

                
                if (elapsed > GATHER_TIMEOUT_MS) {
                    log("[COLLECT] Timeout de coleta, blacklistando alvo");
                    ctx.blacklist(target.getBaseAddress());
                    ctx.setState(new IdleState());
                    return;
                }

                
                if (elapsed > MIN_GATHER_TIME_MS) {
                    if (isEntityGone(ctx)) {
                        log("[COLLECT] Coleta concluida! Entidade desapareceu da memoria");
                        ctx.setState(new IdleState());
                        return;
                    }
                }
                break;

            default:
                ctx.setState(new IdleState());
                break;
        }
    }

    
    private boolean isEntityGone(BotContext ctx) {
        try {
            long addr = target.getBaseAddress();
            float x = ctx.getMemory().readFloat(addr + 0x3C);
            float y = ctx.getMemory().readFloat(addr + 0x40);
            float z = ctx.getMemory().readFloat(addr + 0x44);

            
            if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) return true;
            if (x == 0f && y == 0f && z == 0f) return true;

            
            float dx = x - originalX;
            float dy = y - originalY;
            float dz = z - originalZ;
            float moved = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (moved > 10.0f) return true;

            return false;
        } catch (Exception e) {
            return true; 
        }
    }

    
    private void executeFallback(BotContext ctx) {
        ctx.getInput().sendKey(GameConstants.WINDOW_NAME, 0x46, 200);
        try { Thread.sleep(3000); } catch (Exception ignored) {}
        ctx.setState(new IdleState());
    }

    private void log(String msg) {
        System.out.println(msg);
        BotSettings.logToUi(msg);
    }
}

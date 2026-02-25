package com.bot.logic.states;

import com.bot.logic.BotContext;
import com.bot.constants.GameConstants;
import com.bot.memory.PacketSender;
import com.bot.model.Entity;

/**
 * State that handles gathering a targeted material/herb.
 * 
 * Flow:
 * 1. Select the target via packet (opcode 0x02)
 * 2. Open NPC dialogue to start gathering (opcode 0x23)
 * 3. Wait for gathering to complete
 * 4. Return to PatrolState or IdleState
 *
 * Falls back to keyboard interaction if PacketSender is not available.
 */
public class CollectionState implements BotState {

    private final Entity target;
    private int phase = 0;  // 0=select, 1=interact, 2=wait
    private long phaseStartTime;
    private static final long GATHER_TIMEOUT_MS = 12000; // max gather time
    private static final long SELECT_DELAY_MS   = 500;
    private static final long INTERACT_DELAY_MS = 800;

    public CollectionState() {
        this.target = null;
    }

    public CollectionState(Entity target) {
        this.target = target;
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
            case 0: // Select target
                int targetId = ctx.getMemory().readInt(target.getBaseAddress() + GameConstants.OFFSET_ID);
                if (targetId != 0) {
                    boolean ok = pkt.selectTarget(targetId);
                    log(String.format("[COLLECT] Select target id=%d addr=0x%X dist=%.1fm %s",
                            targetId, target.getBaseAddress(), target.getDistance(),
                            ok ? "OK" : "FALHOU"));
                } else {
                    log("[COLLECT] WARN: targetId=0, tentando com baseAddress como uid");
                    pkt.selectTarget((int)(target.getBaseAddress() & 0x7FFFFFFFL));
                }
                phase = 1;
                phaseStartTime = now;
                break;

            case 1: // Start interaction (after select delay)
                if (now - phaseStartTime < SELECT_DELAY_MS) return;
                int npcId = ctx.getMemory().readInt(target.getBaseAddress() + GameConstants.OFFSET_ID);
                if (npcId != 0) {
                    boolean ok = pkt.startNpcDialogue(npcId);
                    String name = target.getName() != null ? target.getName() : "?";
                    log(String.format("[COLLECT] Interagindo com %s (id=%d) %s", name, npcId, ok ? "OK" : "FALHOU"));
                }
                phase = 2;
                phaseStartTime = now;
                break;

            case 2: // Wait for gathering to complete
                if (now - phaseStartTime > GATHER_TIMEOUT_MS) {
                    log("[COLLECT] Timeout de coleta, voltando");
                    ctx.setState(new IdleState());
                    return;
                }
                // Check if gather is still active by reading player action state
                // For now, use simple timer
                if (now - phaseStartTime > 6000) {
                    log("[COLLECT] Coleta concluida (estimativa)");
                    ctx.setState(new IdleState());
                }
                break;

            default:
                ctx.setState(new IdleState());
                break;
        }
    }

    /** Fallback: use keyboard 'F' key for interaction */
    private void executeFallback(BotContext ctx) {
        // Press F key (0x46) to interact with nearest target
        ctx.getInput().sendKey(GameConstants.WINDOW_NAME, 0x46, 200);
        try { Thread.sleep(3000); } catch (Exception ignored) {}
        ctx.setState(new IdleState());
    }

    private void log(String msg) {
        System.out.println(msg);
        com.bot.constants.BotSettings.logToUi(msg);
    }
}
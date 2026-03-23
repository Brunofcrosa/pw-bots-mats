package com.bot.logic.states;

import com.bot.logic.BotContext;
import com.bot.constants.GameConstants;
import com.bot.model.Entity;
import com.bot.logic.RouteManager;


public class CollectionState implements BotState {

    private final Entity target;
    private int phase = 0;
    private long phaseStartTime;
    private int matterId = 0;
    private final float originalX, originalY, originalZ;
    private int gatherAttempts = 0;
    private long lastAttemptTime = 0;
    private long lastMovePacketTime = 0;
    private long lastLogTime = 0;

    
    private static final float GATHER_RANGE_M = 3.5f;
    private static final long MOVE_TIMEOUT_MS = 30_000;
    private static final long MOVE_PACKET_INTERVAL = 500; 
    private static final long GATHER_TIMEOUT_MS = 40_000;
    private static final long GATHER_RETRY_MS = 5_000;
    private static final long MIN_GATHER_TIME_MS = 3_500;
    private static final int MAX_GATHER_ATTEMPTS = 5;
    private static final long POST_GATHER_BLACKLIST_MS = 12_000;
    private static final float DEFAULT_RUN_SPEED = 7.0f;

    public CollectionState(Entity target) {
        this.target = target;
        this.originalX = target != null ? target.getX() : 0;
        this.originalY = target != null ? target.getY() : 0;
        this.originalZ = target != null ? target.getZ() : 0;
    }

    @Override
    public void execute(BotContext ctx) {
        if (target == null || target.getBaseAddress() == 0) {
            toNext(ctx);
            return;
        }

        long now = System.currentTimeMillis();

        switch (phase) {
            case 0: { 
                matterId = (target.getId() != 0) ? target.getId()
                        : ctx.getMemory().readInt(target.getBaseAddress() + 0x110);
                if (matterId == 0) {
                    ctx.blacklist(target.getBaseAddress());
                    toNext(ctx);
                    return;
                }
                float dist = currentDist(ctx);
                log(String.format("[COLLECT] Iniciando coleta: mid=0x%X dist=%.1fm", matterId, dist));

                if (dist > GATHER_RANGE_M) {
                    ctx.getPlayer().resetSmoothMove();
                    phase = 5;
                    phaseStartTime = now;
                } else {
                    phase = 6;
                }
                break;
            }

            case 5: { 
                float dist = currentDist(ctx);
                if (dist <= GATHER_RANGE_M || now - phaseStartTime > MOVE_TIMEOUT_MS) {
                    phase = 6;
                    phaseStartTime = now;
                    break;
                }

                
                boolean arrived = ctx.getPlayer().smoothWalkTo(ctx.getMemory(), ctx.getModuleBase(), ctx.getInput(),
                        ctx.getPacketSender(),
                        originalX,
                        originalY, originalZ, DEFAULT_RUN_SPEED);

                
                if (now - lastMovePacketTime >= MOVE_PACKET_INTERVAL) {
                    lastMovePacketTime = now;
                    float[] pp = readPlayerPos(ctx);
                    ctx.getPacketSender().moveTowards(pp[0], pp[1], pp[2], originalX, originalY, originalZ,
                            DEFAULT_RUN_SPEED);
                }

                if (now - lastLogTime > 2000) {
                    lastLogTime = now;
                    log(String.format("[COLLECT] Caminhando... dist=%.1fm", dist));
                }

                if (arrived) {
                    phase = 6;
                    phaseStartTime = now;
                }
                break;
            }

            case 6: { 
                ctx.getPlayer().resetSmoothMove();
                float[] pp = readPlayerPos(ctx);
                ctx.getPacketSender().stopMove(pp[0], pp[1], pp[2], DEFAULT_RUN_SPEED);
                log("[COLLECT] Parado. Sincronizando com servidor...");

                
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                phase = 1;
                phaseStartTime = now;
                gatherAttempts = 0;
                lastAttemptTime = 0;
                break;
            }

            case 1: { 
                if (gatherAttempts >= MAX_GATHER_ATTEMPTS) {
                    ctx.blacklist(target.getBaseAddress());
                    toNext(ctx);
                    return;
                }
                if (lastAttemptTime > 0 && now - lastAttemptTime < GATHER_RETRY_MS)
                    return;

                ctx.getPacketSender().cancelAction();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                boolean sent = ctx.getPacketSender().gatherMaterial(matterId);
                log(String.format("[COLLECT] Enviando pacote de coleta #%d (mid=0x%X) sent=%b", gatherAttempts + 1,
                        matterId, sent));

                gatherAttempts++;
                lastAttemptTime = now;
                phase = 2;
                break;
            }

            case 2: { 
                long elapsed = now - phaseStartTime;
                if (elapsed > GATHER_TIMEOUT_MS) {
                    ctx.blacklist(target.getBaseAddress());
                    toNext(ctx);
                    return;
                }

                if (elapsed > MIN_GATHER_TIME_MS && isEntityGone(ctx)) {
                    log("[COLLECT] Sucesso! Item coletado.");
                    ctx.blacklist(target.getBaseAddress(), POST_GATHER_BLACKLIST_MS);
                    toNext(ctx);
                    return;
                }

                if (now - lastAttemptTime >= GATHER_RETRY_MS)
                    phase = 1;
                break;
            }

            default:
                toNext(ctx);
        }
    }

    private float[] readPlayerPos(BotContext ctx) {
        long staticAddr = ctx.getModuleBase() + GameConstants.BASE_OFFSET;
        long ptrLoc = ctx.getMemory().readPointerAddress(staticAddr, GameConstants.PLAYER_STRUCTURE_CHAIN);
        if (ptrLoc != 0) {
            long ps = ctx.getMemory().readInt(ptrLoc) & 0xFFFFFFFFL;
            return new float[] {
                    ctx.getMemory().readFloat(ps + GameConstants.OFFSET_X),
                    ctx.getMemory().readFloat(ps + GameConstants.OFFSET_Y),
                    ctx.getMemory().readFloat(ps + GameConstants.OFFSET_Z)
            };
        }
        return new float[] { ctx.getPlayer().getX(), ctx.getPlayer().getY(), ctx.getPlayer().getZ() };
    }

    private float currentDist(BotContext ctx) {
        float[] pp = readPlayerPos(ctx);
        float dx = originalX - pp[0], dz = originalZ - pp[2];
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private boolean isEntityGone(BotContext ctx) {
        try {
            long addr = target.getBaseAddress();
            if (ctx.getEntityManager() != null && !ctx.getEntityManager().isMaterialPresent(addr))
                return true;
            float x = ctx.getMemory().readFloat(addr + GameConstants.OFFSET_X);
            return Float.isNaN(x) || x == 0f || Math.abs(x - originalX) > 5.0f;
        } catch (Exception e) {
            return true;
        }
    }

    private void toNext(BotContext ctx) {
        ctx.getPlayer().resetSmoothMove();
        RouteManager rm = ctx.getRouteManager();
        if (rm != null && rm.isRouteActive())
            ctx.setState(new SpawnRouteState());
        else
            ctx.setState(new IdleState());
    }

    private void log(String msg) {
        System.out.println(msg);
        com.bot.constants.BotSettings.logToUi(msg);
    }
}

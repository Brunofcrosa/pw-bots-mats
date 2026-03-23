package com.bot.logic.states;

import com.bot.logic.BotContext;
import com.bot.logic.RouteManager;
import com.bot.constants.BotSettings;
import com.bot.model.Entity;
import com.bot.model.ResourceSpawn;

public class SpawnRouteState implements BotState {

    private ResourceSpawn currentTarget;
    private long lastMoveTime = 0;
    private long arrivedTime = 0;
    private boolean arrived = false;

    private static final float REACHED_RANGE = 8.0f;
    private static final long MOVE_INTERVAL_MS = 500; 
    private static final long CHECK_DELAY_MS = 2500;
    private static final long STUCK_TIMEOUT_MS = 12000;

    private float lastCheckedDist = -1;
    private long lastDistImprovedTime = 0;

    @Override
    public void execute(BotContext ctx) {
        RouteManager rm = ctx.getRouteManager();
        if (rm == null || !rm.isRouteActive()) {
            ctx.setState(new IdleState());
            return;
        }

        float px = ctx.getPlayer().getX();
        float py = ctx.getPlayer().getY();
        float pz = ctx.getPlayer().getZ();

        Entity mem = findMemoryTarget(ctx, rm);
        if (mem != null) {
            log(String.format("[ROUTE] Material detectado em memoria: %s dist=%.1fm, coletando",
                    mem.getName(), mem.getDistance()));
            ctx.setState(new CollectionState(mem));
            return;
        }

        if (currentTarget == null || rm.isOnCooldown(currentTarget)) {
            currentTarget = rm.getNextTarget(px, py, pz);
            arrived = false;
            arrivedTime = 0;
            if (currentTarget == null) {
                log("[ROUTE] Todos os pontos em cooldown. Aguardando...");
                return;
            }
            log(String.format("[ROUTE] Novo alvo: %s em (%.0f, %.0f, %.0f)",
                    currentTarget.getName(),
                    currentTarget.getX(), currentTarget.getY(), currentTarget.getZ()));
        }

        float dist = currentTarget.distanceTo(px, py, pz);

        if (lastDistImprovedTime == 0)
            lastDistImprovedTime = System.currentTimeMillis();
        if (lastCheckedDist < 0 || dist < lastCheckedDist - 0.5f) {
            lastCheckedDist = dist;
            lastDistImprovedTime = System.currentTimeMillis();
        } else if (!arrived && !BotSettings.isStaticMode()
                && System.currentTimeMillis() - lastDistImprovedTime > STUCK_TIMEOUT_MS) {
            log(String.format("[ROUTE] STUCK: distancia nao melhorou em %ds, pulando alvo %s",
                    STUCK_TIMEOUT_MS / 1000, currentTarget.getName()));
            rm.markCooldown(currentTarget);
            currentTarget = null;
            arrived = false;
            lastCheckedDist = -1;
            lastDistImprovedTime = 0;
            return;
        }

        if (dist <= REACHED_RANGE) {
            if (!arrived) {
                arrived = true;
                arrivedTime = System.currentTimeMillis();
                log(String.format("[ROUTE] Chegou em %s (%.0f,%.0f,%.0f), verificando...",
                        currentTarget.getName(),
                        currentTarget.getX(), currentTarget.getY(), currentTarget.getZ()));
            }
            if (System.currentTimeMillis() - arrivedTime >= CHECK_DELAY_MS) {
                if (findMemoryTarget(ctx, rm) == null) {
                    log(String.format("[ROUTE] %s nao encontrado - cooldown 10 min",
                            currentTarget.getName()));
                    rm.markCooldown(currentTarget);
                    currentTarget = null;
                    arrived = false;
                }
            }
        } else {
            arrived = false;
            arrivedTime = 0;
            long now = System.currentTimeMillis();
            if (!BotSettings.isStaticMode()) {
                
                ctx.getPlayer().smoothWalkTo(ctx.getMemory(), ctx.getModuleBase(), ctx.getInput(),
                        ctx.getPacketSender(),
                        currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(), 7.0f);

                
                if (now - lastMoveTime >= MOVE_INTERVAL_MS) {
                    lastMoveTime = now;
                    ctx.getPacketSender().moveTowards(px, py, pz, currentTarget.getX(), currentTarget.getY(),
                            currentTarget.getZ(), 7.0f);
                    log(String.format("[ROUTE] Movendo para %s dist=%.1fm",
                            currentTarget.getName(), dist));
                }
            } else {
                
                if (now - lastMoveTime >= 5_000) {
                    lastMoveTime = now;
                    log(String.format("[ROUTE] Aguardando: va para %s (%.0f,%.0f,%.0f) dist=%.1fm",
                            currentTarget.getName(),
                            currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(), dist));
                }
            }
        }
    }

    private Entity findMemoryTarget(BotContext ctx, RouteManager rm) {
        Entity nearest = null;
        float nearestDist = Float.MAX_VALUE;
        float px = ctx.getPlayer().getX();
        float py = ctx.getPlayer().getY();
        float pz = ctx.getPlayer().getZ();

        for (Entity e : ctx.getEntityManager().getMaterials()) {
            if (!rm.getSelectedNames().contains(e.getName()))
                continue;
            if (ctx.isBlacklisted(e.getBaseAddress()))
                continue;
            float dx = e.getX() - px, dy = e.getY() - py, dz = e.getZ() - pz;
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > 70.0f)
                continue; 
            if (d < nearestDist) {
                nearestDist = d;
                nearest = e;
            }
        }

        
        
        
        if (nearest == null && BotSettings.isStaticMode()) {
            for (Entity e : ctx.getEntityManager().getMaterials()) {
                if (ctx.isBlacklisted(e.getBaseAddress()))
                    continue;
                float dx = e.getX() - px, dy = e.getY() - py, dz = e.getZ() - pz;
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d <= 5.0f && d < nearestDist) {
                    nearestDist = d;
                    nearest = e;
                }
            }
        }

        return nearest;
    }

    public ResourceSpawn getCurrentTarget() {
        return currentTarget;
    }

    public String getCurrentTargetName() {
        return currentTarget != null ? currentTarget.getName() : null;
    }

    private void log(String msg) {
        System.out.println(msg);
        BotSettings.logToUi(msg);
    }
}

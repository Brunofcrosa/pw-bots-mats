package com.bot.logic.states;

import com.bot.logic.BotContext;
import com.bot.logic.WaypointManager;
import com.bot.model.Entity;
import com.bot.model.Vector3;
import com.bot.constants.BotSettings;

import java.util.List;

public class PatrolState implements BotState {

    private long lastMoveTime = 0;
    private static final long MOVE_INTERVAL_MS = 500;
    private static final float WAYPOINT_REACH_DIST = 8.0f;

    @Override
    public void execute(BotContext ctx) {
        Entity target = findBestTarget(ctx);
        if (target != null) {
            String name = target.getName() != null ? target.getName() : "Material";
            log(String.format("[PATROL] Material detectado: %s dist=%.1fm, indo coletar",
                    name, target.getDistance()));
            ctx.getPlayer().resetSmoothMove();
            ctx.setTargetEntity(target);
            ctx.setState(new CollectionState(target));
            return;
        }

        WaypointManager wm = ctx.getWaypointManager();
        if (wm == null || wm.getCurrentTarget() == null) {
            ctx.setState(new IdleState());
            return;
        }

        Vector3 wp = wm.getCurrentTarget();
        float px = ctx.getPlayer().getX();
        float pz = ctx.getPlayer().getZ();
        float dx = wp.getX() - px;
        float dz = wp.getZ() - pz;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        if (dist < WAYPOINT_REACH_DIST) {
            ctx.getPlayer().resetSmoothMove();
            log(String.format("[PATROL] Waypoint alcancado (dist=%.1f), avancando para proximo", dist));
            wm.advanceWaypoint();
            return;
        }

        
        ctx.getPlayer().smoothWalkTo(ctx.getMemory(), ctx.getModuleBase(), ctx.getInput(), ctx.getPacketSender(),
                wp.getX(), wp.getY(),
                wp.getZ(), 7.0f);

        
        long now = System.currentTimeMillis();
        if (now - lastMoveTime >= MOVE_INTERVAL_MS) {
            lastMoveTime = now;
            ctx.getPacketSender().moveTowards(px, 0, pz, wp.getX(), wp.getY(), wp.getZ(), 7.0f);
        }
    }

    private Entity findBestTarget(BotContext ctx) {
        List<Entity> materials = ctx.getEntityManager().getMaterials();
        for (Entity e : materials) {
            if (!ctx.isBlacklisted(e.getBaseAddress())) {
                return e;
            }
        }
        return null;
    }

    private void log(String msg) {
        System.out.println(msg);
        BotSettings.logToUi(msg);
    }
}

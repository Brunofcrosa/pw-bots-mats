package com.bot.logic.states;

import com.bot.logic.BotContext;
import com.bot.model.Entity;
import com.bot.constants.BotSettings;

import java.util.List;


public class IdleState implements BotState {

    private long enterTime = 0;
    private static final long SCAN_COOLDOWN_MS = 2000; 

    @Override
    public void execute(BotContext ctx) {
        if (enterTime == 0) {
            enterTime = System.currentTimeMillis();
        }

        
        if (System.currentTimeMillis() - enterTime < SCAN_COOLDOWN_MS) {
            return;
        }

        
        Entity target = findBestTarget(ctx);
        if (target != null) {
            String name = target.getName() != null ? target.getName() : "Material";
            log(String.format("[IDLE] Material encontrado: %s dist=%.1fm, indo coletar",
                    name, target.getDistance()));
            ctx.setTargetEntity(target);
            ctx.setState(new CollectionState(target));
            return;
        }

        
        if (ctx.getWaypointManager() != null && ctx.getWaypointManager().getCurrentTarget() != null) {
            log("[IDLE] Nenhum material proximo, iniciando patrulha");
            ctx.setState(new PatrolState());
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

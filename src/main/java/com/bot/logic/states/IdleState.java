package com.bot.logic.states;

import com.bot.logic.BotContext;
import com.bot.model.Entity;
import java.util.List;

public class IdleState implements BotState {
    @Override
    public void execute(BotContext ctx) {
        List<Entity> materials = ctx.getEntityManager().getMaterials();
        if (!materials.isEmpty()) {
            Entity mat = materials.get(0);
            if (mat.getDistance() < 40.0f) {
                ctx.getPlayer().setTarget(ctx.getMemory(), ctx.getModuleBase(), mat.getId());
                ctx.setState(new MoveToTargetState(mat, true));
                return;
            }
        }

        Entity mob = ctx.getEntityManager().getNearestMob();
        if (mob != null && mob.getDistance() < 30.0f) {
            ctx.getPlayer().setTarget(ctx.getMemory(), ctx.getModuleBase(), mob.getId());
            ctx.setState(new MoveToTargetState(mob, false));
        }
    }
}
package com.bot.logic.states;

import com.bot.logic.BotContext;

public class IdleState implements BotState {
    @Override
    public void execute(BotContext ctx) {
        if (ctx.getWaypointManager() != null && ctx.getWaypointManager().getCurrentTarget() != null) {
            ctx.setState(new PatrolState());
        }
    }
}
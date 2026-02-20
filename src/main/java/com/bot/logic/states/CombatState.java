package com.bot.logic.states;

import com.bot.logic.BotContext;
import com.bot.constants.GameConstants;

public class CombatState implements BotState {
    @Override
    public void execute(BotContext ctx) {
        if (ctx.getPlayer().getTargetId() == 0) {
            ctx.setState(new IdleState());
            return;
        }
        ctx.getInput().sendKey(GameConstants.WINDOW_NAME, 0x70, 100);
    }
}
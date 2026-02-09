package com.bot.logic.states;

import com.bot.logic.BotContext;
import com.bot.constants.GameConstants;

public class CollectionState implements BotState {
    @Override
    public void execute(BotContext ctx) {
        ctx.getInput().sendKey(GameConstants.WINDOW_NAME, 0x58, 200);
        try { Thread.sleep(3000); } catch (Exception e) {}
        ctx.setState(new IdleState());
    }
}
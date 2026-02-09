package com.bot.logic.states;

import com.bot.logic.BotContext;

public interface BotState {
    void execute(BotContext ctx);
}
package com.bot.logic.states;

import com.bot.logic.BotContext;
import com.bot.model.Entity;
import com.bot.input.InputSimulator.Keys;
import com.bot.constants.GameConstants;

public class MoveToTargetState implements BotState {
    private final Entity target;
    private final boolean isMaterial;

    public MoveToTargetState(Entity target, boolean isMaterial) {
        this.target = target;
        this.isMaterial = isMaterial;
    }

    @Override
    public void execute(BotContext ctx) {
        target.calculateDistance(ctx.getPlayer());

        if (target.getDistance() > 3.5f) {
            ctx.getInput().sendKey(GameConstants.WINDOW_NAME, Keys.VK_W, 150);
        } else {
            if (isMaterial) {
                ctx.setState(new CollectionState());
            } else {
                ctx.setState(new CombatState());
            }
        }
    }
}
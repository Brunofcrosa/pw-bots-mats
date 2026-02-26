package com.bot.logic.states;

import com.bot.logic.BotContext;
import com.bot.model.Entity;
import com.bot.model.Vector3;
import com.bot.constants.BotSettings;
import com.bot.constants.GameConstants;


public class MoveToTargetState implements BotState {
    private final Entity target;
    private final boolean isMaterial;
    private long lastMoveTime = 0;
    private long enterTime = 0;

    private static final float INTERACTION_RANGE = 3.5f;
    private static final long MOVE_INTERVAL_MS = 800;
    private static final long MOVE_TIMEOUT_MS = 20000; 

    public MoveToTargetState(Entity target, boolean isMaterial) {
        this.target = target;
        this.isMaterial = isMaterial;
    }

    @Override
    public void execute(BotContext ctx) {
        if (enterTime == 0) enterTime = System.currentTimeMillis();

        if (target == null) {
            ctx.setState(new IdleState());
            return;
        }

        
        if (System.currentTimeMillis() - enterTime > MOVE_TIMEOUT_MS) {
            log(String.format("[MOVE] Timeout ao mover para %s, blacklistando",
                    target.getName() != null ? target.getName() : "alvo"));
            ctx.blacklist(target.getBaseAddress());
            ctx.setState(new IdleState());
            return;
        }

        target.calculateDistance(ctx.getPlayer());

        if (target.getDistance() <= INTERACTION_RANGE) {
            if (isMaterial) {
                ctx.setState(new CollectionState(target));
            } else {
                ctx.setState(new CombatState());
            }
            return;
        }

        
        long now = System.currentTimeMillis();
        if (now - lastMoveTime >= MOVE_INTERVAL_MS) {
            lastMoveTime = now;
            Vector3 dest = new Vector3(target.getX(), target.getY(), target.getZ());
            ctx.getPlayer().clickToMove(ctx.getMemory(), ctx.getModuleBase(), dest);
        }
    }

    private void log(String msg) {
        System.out.println(msg);
        BotSettings.logToUi(msg);
    }
}

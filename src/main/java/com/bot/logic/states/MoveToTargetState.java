package com.bot.logic.states;

import com.bot.logic.BotContext;
import com.bot.model.Entity;
import com.bot.constants.BotSettings;

public class MoveToTargetState implements BotState {
    private final Entity target;
    private final boolean isMaterial;
    private long lastMoveTime = 0;
    private long enterTime = 0;

    private static final float INTERACTION_RANGE = 3.5f;
    private static final long MOVE_INTERVAL_MS = 500;
    private static final long MOVE_TIMEOUT_MS = 20000;

    public MoveToTargetState(Entity target, boolean isMaterial) {
        this.target = target;
        this.isMaterial = isMaterial;
    }

    @Override
    public void execute(BotContext ctx) {
        if (enterTime == 0)
            enterTime = System.currentTimeMillis();

        if (target == null) {
            ctx.setState(new IdleState());
            return;
        }

        long now = System.currentTimeMillis();
        if (now - enterTime > MOVE_TIMEOUT_MS) {
            log(String.format("[MOVE] Timeout ao mover para %s, blacklistando",
                    target.getName() != null ? target.getName() : "alvo"));
            ctx.blacklist(target.getBaseAddress());
            ctx.setState(new IdleState());
            return;
        }

        
        float px = ctx.getPlayer().getX();
        float pz = ctx.getPlayer().getZ();
        float dx = target.getX() - px;
        float dz = target.getZ() - pz;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);

        if (distance <= INTERACTION_RANGE) {
            ctx.getPlayer().resetSmoothMove();
            if (isMaterial) {
                ctx.setState(new CollectionState(target));
            } else {
                ctx.setState(new CombatState());
            }
            return;
        }

        
        boolean arrived = ctx.getPlayer().smoothWalkTo(ctx.getMemory(), ctx.getModuleBase(), ctx.getInput(),
                ctx.getPacketSender(),
                target.getX(), target.getY(), target.getZ(), 6.0f);

        
        if (now - lastMoveTime >= MOVE_INTERVAL_MS) {
            lastMoveTime = now;
            ctx.getPacketSender().moveTowards(px, 0, pz, target.getX(), target.getY(), target.getZ(), 7.0f);
        }

        if (arrived) {
            ctx.getPlayer().resetSmoothMove();
            if (isMaterial) {
                ctx.setState(new CollectionState(target));
            } else {
                ctx.setState(new CombatState());
            }
        }
    }

    private void log(String msg) {
        System.out.println(msg);
        BotSettings.logToUi(msg);
    }
}

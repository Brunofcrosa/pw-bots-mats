package com.bot.logic;

import com.bot.model.Vector3;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class WaypointManager {
    private final Queue<Vector3> route;
    private Vector3 currentTarget;

    public WaypointManager(List<Vector3> waypoints) {
        this.route = new LinkedList<>(waypoints);
        this.currentTarget = this.route.poll();
    }

    public Vector3 getCurrentTarget() { return currentTarget; }

    public void advanceWaypoint() {
        if (currentTarget != null) route.offer(currentTarget);
        currentTarget = route.poll();
    }
}

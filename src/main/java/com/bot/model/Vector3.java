package com.bot.model;

public class Vector3 {
    private final float x, y, z;
    public Vector3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public float distanceTo(Vector3 other) {
        float dx = this.x - other.x, dy = this.y - other.y, dz = this.z - other.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

package com.bot.model;

public class Entity {
    private int id;
    private float x, y, z;
    private int type;
    private float distance;
    private int hp;
    private int maxHp;

    public Entity(int id, float x, float y, float z, int type) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
    }

    public void calculateDistance(Player player) {
        float dx = this.x - player.getX();
        float dy = this.y - player.getY();
        float dz = this.z - player.getZ();
        this.distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public float getDistance() { return distance; }
    public int getId() { return id; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }

    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }
    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
}
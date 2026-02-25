package com.bot.model;

public class Entity {
    private long baseAddress;
    private int id, type, hp, maxHp;
    private int templateId;
    private String name;
    private float x, y, z, distance;

    public Entity() {}

    public Entity(int id, float x, float y, float z, int type) {
        this.id = id; this.x = x; this.y = y; this.z = z; this.type = type;
    }

    public void calculateDistance(Player player) {
        float dx = this.x - player.getX(), dy = this.y - player.getY(), dz = this.z - player.getZ();
        this.distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public long getBaseAddress() { return baseAddress; }
    public void setBaseAddress(long baseAddress) { this.baseAddress = baseAddress; }
    public float getDistance() { return distance; }
    public void setDistance(float distance) { this.distance = distance; }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public float getX() { return x; }
    public void setX(float x) { this.x = x; }
    public float getY() { return y; }
    public void setY(float y) { this.y = y; }
    public float getZ() { return z; }
    public void setZ(float z) { this.z = z; }
    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }
    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    public int getTemplateId() { return templateId; }
    public void setTemplateId(int templateId) { this.templateId = templateId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

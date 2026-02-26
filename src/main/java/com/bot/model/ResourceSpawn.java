package com.bot.model;


public class ResourceSpawn {
    private final int templateId;
    private final String name;
    private final String category;
    private final int level;
    private final float x;
    private final float y;
    private final float z;

    public ResourceSpawn(int templateId, String name, String category, int level, float x, float y, float z) {
        this.templateId = templateId;
        this.name = name;
        this.category = category;
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getTemplateId() { return templateId; }
    public String getName()     { return name; }
    public String getCategory() { return category; }
    public int getLevel()       { return level; }
    public float getX()         { return x; }
    public float getY()         { return y; }
    public float getZ()         { return z; }

    public float distanceTo(float px, float py, float pz) {
        float dx = x - px, dy = y - py, dz = z - pz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s Lv%d (%.1f, %.1f, %.1f)", category, name, level, x, y, z);
    }
}

package com.bot.constants;

public class GameConstants {
    public static final String PROCESS_NAME = "elementclient.exe";
    public static final String WINDOW_NAME = "The Classic PW 1.2.6";
    public static final long BASE_OFFSET = 0xA4129C;
    public static final String MATTER_BASE_SIGNATURE = "8B 0D ? ? ? ? 8B 49 58 8B 51 3C";

    public static final int[] ENTITY_LIST_CHAIN = { 0x1C, 0x1C, 0x24 };
    public static final int OFFSET_NPC_COUNT = 0x70;
    public static final int OFFSET_NPC_ARRAY = 0x6C;

    public static final int[] MATTER_LIST_CHAIN = { 0x18, 0x58, 0x118 };
    public static final long MATTER_BASE_OFFSET = 0xA414B0;

    public static final int MATTER_TYPE = 8;
    public static final int OFFSET_MATTER_ID = 0x114;

    public static final int OFFSET_ID = 0x190;
    public static final int OFFSET_TYPE = 0xB4;
    public static final int OFFSET_X = 0x3C;
    public static final int OFFSET_Z = 0x44;
    public static final int OFFSET_Y = 0x40;

    public static final int TYPE_MATERIAL = 7;
    public static final int TYPE_MOB = 6;

    public static final int[] PLAYER_STRUCTURE_CHAIN = { 0x2C };
    public static final int OFFSET_LEVEL = 0x4F8;
    public static final int OFFSET_HP = 0x518;
    public static final int OFFSET_MP = 0x51C;
    public static final int OFFSET_MAX_HP = 0x4CC;
    public static final int OFFSET_MAX_MP = 0x4D0;
    public static final int OFFSET_NAME_PTR = 0x6E0;
    public static final int OFFSET_TARGET_ID = 0x510;
    public static final int OFFSET_ACTION_STRUCT = 0x11C;

    public static final java.util.Map<Integer, String> MATERIAL_NAMES;
    static {
        MATERIAL_NAMES = new java.util.LinkedHashMap<>();
    }

    public static final int[] TEMPLATE_ID_PROBE_OFFSETS = {
        OFFSET_MATTER_ID,
        0x08, 0x0C, 0x10, 0x14,
        OFFSET_TYPE,
        0xB8, 0xBC,
        OFFSET_ID
    };

    public static final int SNAPSHOT_SCAN_SIZE = 800;
    public static final int SNAPSHOT_PREFILTER_SIZE = 160;
    public static final int SNAPSHOT_MIN_HITS = 5;
    public static final int MATERIAL_ARRAY_SCAN_SIZE = 2048;
    public static final int CHAIN_SCAN_ARRAY_SIZE = 256;
    public static final float MATERIAL_CULL_DISTANCE = 200.0f;
    public static final float MATERIAL_SOFT_KEEP_DISTANCE = 30.0f;
    public static final float GPS_HIT_DISTANCE = 6.0f;
    public static final float GPS_FALLBACK_DISTANCE = 3.0f;
}

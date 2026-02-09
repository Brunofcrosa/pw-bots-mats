package com.bot.constants;

public class GameConstants {
    // Process and Window Names
    public static final String PROCESS_NAME = "elementclient.exe";
    public static final String WINDOW_NAME = "The Classic PW 1.2.6";
    public static final long BASE_OFFSET = 0xA4129C;
    public static final int[] PLAYER_STRUCTURE_CHAIN = { 0x2C };

    // Player Offsets
    public static final int OFFSET_HP = 0x518;
    public static final int OFFSET_MP = 0x51C;
    public static final int OFFSET_MAX_HP = 0x4E0;

    // Coordenadas (Padrão 1.2.6)
    public static final int OFFSET_X = 0x3C;
    public static final int OFFSET_Z = 0x40;
    public static final int OFFSET_Y = 0x44;

    public static final int[] ENTITY_LIST_CHAIN = { 0x1C, 0x24 };
}
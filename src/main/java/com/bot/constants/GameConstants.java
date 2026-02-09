package com.bot.constants;

public class GameConstants {
    public static final String PROCESS_NAME = "elementclient.exe";
    public static final String WINDOW_NAME = "The Classic PW 1.2.6";
    public static final long BASE_OFFSET = 0xA4129C;
    public static final int[] PLAYER_STRUCTURE_CHAIN = { 0x2C };
    public static final int OFFSET_NAME_PTR = 0x230;
    public static final int OFFSET_ID = 0x4EC;
    public static final int OFFSET_LEVEL = 0x4B0;
    public static final int OFFSET_TYPE = 0xB4;
    public static final int OFFSET_HP = 0x518;
    public static final int OFFSET_MP = 0x51C;
    public static final int OFFSET_MAX_HP = 0x4CC;
    public static final int OFFSET_MAX_MP = 0x4D0;
    public static final int OFFSET_TARGET_ID = 0x510;
    public static final int OFFSET_X = 0x3C;
    public static final int OFFSET_Y = 0x44;
    public static final int OFFSET_Z = 0x40;
    public static final int[] ENTITY_LIST_CHAIN = { 0x1C, 0x24 };
    public static final int TYPE_MOB = 6;
    public static final int TYPE_MATERIAL = 7;
}
package com.bot.memory;

import com.sun.jna.Structure;


@Structure.FieldOrder({"dwSize","cntUsage","th32ThreadID","th32OwnerProcessID","tpBasePri","tpDeltaPri","dwFlags"})
public class THREADENTRY32 extends Structure {
    public int dwSize;
    public int cntUsage;
    public int th32ThreadID;
    public int th32OwnerProcessID;
    public int tpBasePri;
    public int tpDeltaPri;
    public int dwFlags;

    public THREADENTRY32() { dwSize = size(); }

    public static class ByRef extends THREADENTRY32 implements Structure.ByReference {}
}

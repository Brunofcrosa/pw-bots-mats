package com.bot.memory;

import com.bot.constants.BotSettings;
import com.bot.constants.GameConstants;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.io.ByteArrayOutputStream;

public class PacketSender {

    private interface NtDll extends com.sun.jna.win32.StdCallLibrary {
        NtDll INSTANCE = Native.load("ntdll", NtDll.class);

        int NtCreateThreadEx(
                WinNT.HANDLEByReference hThread,
                int desiredAccess,
                Pointer objectAttributes,
                WinNT.HANDLE processHandle,
                Pointer startAddress,
                Pointer parameter,
                int createFlags,
                int zeroBits,
                int stackSize,
                int maximumStackSize,
                Pointer attributeList);
    }

    
    private interface Kernel32Ext extends StdCallLibrary {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class);

        boolean Thread32First(HANDLE hSnapshot, THREADENTRY32.ByRef te);

        boolean Thread32Next(HANDLE hSnapshot, THREADENTRY32.ByRef te);

        
        HANDLE OpenThread(int dwDesiredAccess, boolean bInheritHandle, int dwThreadId);

        
        boolean QueueUserAPC(Pointer pfnAPC, HANDLE hThread, Pointer dwData);
    }

    private final WinMemoryReader memory;
    private long sendPacketAddr;
    private long gameBaseAddr;
    private long moduleBase;
    private boolean initialized = false;
    
    private int gamePid = 0;
    
    private int mainThreadId = 0;
    
    private Pointer persistentApcMem = null;
    private static final int PERSISTENT_SHELLCODE_SIZE = 128; 
    private static final int PERSISTENT_PKT_SIZE = 256; 
    
    private int nextMoveSeq = 0;

    public void setNextMoveSeq(int seq) {
        this.nextMoveSeq = seq;
    }

    
    public void setGamePid(int pid) {
        this.gamePid = pid;
    }

    
    public void setGameHwnd(com.sun.jna.platform.win32.WinDef.HWND hwnd) {
        if (hwnd != null) {
            mainThreadId = com.sun.jna.platform.win32.User32.INSTANCE.GetWindowThreadProcessId(hwnd, null);
            log(String.format("[PKT] Main window thread ID: %d", mainThreadId));
        }
    }

    private static final int CONNECTION_OFFSET = 0x20;

    private static final String SIG_GAME_BASE = "8B 0D ? ? ? ? 8B 49 20";

    private static final String SIG_SEND_CALL = "6A ? 57 FF D0";

    public PacketSender(WinMemoryReader memory) {
        this.memory = memory;
    }

    public boolean initialize(long moduleBase) {
        this.moduleBase = moduleBase;
        log("[PKT] Iniciando busca de enderecos para envio de pacotes...");

        long codeStart = moduleBase + 0x1000;
        int scanSize = 0x800000;

        long sigAddr = memory.scanPattern(codeStart, scanSize, SIG_GAME_BASE);
        if (sigAddr != 0) {

            gameBaseAddr = memory.readInt(sigAddr + 2) & 0xFFFFFFFFL;
            log(String.format("[PKT] gameBase encontrado via signature: 0x%X (sigAddr=0x%X)", gameBaseAddr, sigAddr));
        } else {
            log("[PKT] WARN: Signature do gameBase nao encontrada. Tentando enderecos conhecidos...");

            long[] knownBases = { 0x0098657CL, 0x009C657CL, moduleBase + 0x5C657CL };
            for (long addr : knownBases) {
                long val = memory.readInt(addr) & 0xFFFFFFFFL;
                if (val > 0x10000 && val < 0x7FFFFFFFL) {
                    long session = memory.readInt(val + CONNECTION_OFFSET) & 0xFFFFFFFFL;
                    if (session > 0x10000 && session < 0x7FFFFFFFL) {
                        gameBaseAddr = addr;
                        log(String.format("[PKT] gameBase fallback: 0x%X (val=0x%X, session=0x%X)", addr, val,
                                session));
                        break;
                    }
                }
            }
        }

        if (gameBaseAddr == 0) {
            log("[PKT] ERRO: Nao foi possivel encontrar gameBase. Packet sender desativado.");
            return false;
        }

        sendPacketAddr = findSendPacketFunction(codeStart, scanSize);

        if (sendPacketAddr == 0) {
            log("[PKT] WARN: sendPacketFunction nao encontrada via scan. Tentando enderecos conhecidos...");
            long[] knownFuncs = { 0x005BD7B0L, 0x005BE7B0L, 0x005BC7B0L };
            for (long addr : knownFuncs) {
                int firstByte = memory.readInt(addr) & 0xFF;

                if (firstByte == 0x55 || firstByte == 0x56 || firstByte == 0x53 || firstByte == 0x8B) {
                    sendPacketAddr = addr;
                    log(String.format("[PKT] sendPacketFunction fallback: 0x%X (firstByte=0x%02X)", addr, firstByte));
                    break;
                }
            }
        }

        if (sendPacketAddr == 0) {
            log("[PKT] ERRO: Nao foi possivel encontrar sendPacketFunction. Packet sender desativado.");
            return false;
        }

        long base = memory.readInt(gameBaseAddr) & 0xFFFFFFFFL;
        long session = (base > 0x10000) ? (memory.readInt(base + CONNECTION_OFFSET) & 0xFFFFFFFFL) : 0;
        if (base < 0x10000 || session < 0x10000) {
            log(String.format("[PKT] WARN: Chain de conexao invalida base=0x%X session=0x%X", base, session));
            return false;
        }

        initialized = true;
        log(String.format("[PKT] OK - sendPacketFunc=0x%X gameBase=0x%X [base]=0x%X [session]=0x%X",
                sendPacketAddr, gameBaseAddr, base, session));

        
        HANDLE hProc0 = memory.getProcessHandle();
        if (hProc0 != null) {
            int totalPersist = PERSISTENT_SHELLCODE_SIZE + PERSISTENT_PKT_SIZE;
            persistentApcMem = Kernel32.INSTANCE.VirtualAllocEx(
                    hProc0, null,
                    new com.sun.jna.platform.win32.BaseTSD.SIZE_T(totalPersist),
                    WinNT.MEM_COMMIT | WinNT.MEM_RESERVE,
                    WinNT.PAGE_EXECUTE_READWRITE);
            if (persistentApcMem != null) {
                log(String.format("[PKT] Buffer APC persistente: 0x%X (%d bytes)",
                        Pointer.nativeValue(persistentApcMem), totalPersist));
            } else {
                log("[PKT] WARN: Buffer APC persistente falhou, alocando por pacote");
            }
        }
        return true;
    }

    public void setAddresses(long sendPacketAddr, long gameBaseAddr) {
        this.sendPacketAddr = sendPacketAddr;
        this.gameBaseAddr = gameBaseAddr;
        this.initialized = true;
        log(String.format("[PKT] Enderecos definidos manualmente: send=0x%X base=0x%X", sendPacketAddr, gameBaseAddr));
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean selectTarget(int targetId) {
        byte[] pkt = buildPacket(0x02, intBytes(targetId));
        return sendPacket(pkt);
    }

    public boolean deselectTarget() {
        return sendPacket(buildPacket(0x08));
    }

    public boolean regularAttack(int afterSkill) {
        byte[] pkt = new byte[3];
        pkt[0] = 0x03;
        pkt[1] = 0x00;
        pkt[2] = (byte) (afterSkill & 0xFF);
        return sendPacket(pkt);
    }

    public boolean pickUpItem(int uniqueItemId, int itemTypeId) {
        byte[] pkt = buildPacket(0x06, intBytes(uniqueItemId), intBytes(itemTypeId));
        return sendPacket(pkt);
    }

    public boolean startNpcDialogue(int npcId) {
        byte[] pkt = buildPacket(0x23, intBytes(npcId));
        return sendPacket(pkt);
    }

    public boolean gatherMaterial(int matterId, int toolPack, int toolIndex, int toolType, int taskId) {
        byte[] pkt = buildPacket(0x36,
                intBytes(matterId),
                shortBytes(toolPack),
                shortBytes(toolIndex),
                intBytes(toolType),
                intBytes(taskId));
        return sendPacket(pkt);
    }

    public boolean gatherMaterial(int matterId) {
        return gatherMaterial(matterId, 0, 0, 0, 0);
    }

    public boolean useItem(int invIndex, int itemTypeId) {
        byte[] pkt = new byte[10];
        pkt[0] = 0x28;
        pkt[1] = 0x00;
        pkt[2] = 0x00;
        pkt[3] = 0x00;
        pkt[4] = 0x01;
        pkt[5] = (byte) (invIndex & 0xFF);
        pkt[6] = 0x00;

        pkt[7] = (byte) (itemTypeId & 0xFF);

        byte[] pkt2 = new byte[10];
        pkt2[0] = 0x28;
        pkt2[1] = 0x00;
        pkt2[2] = 0x00;
        pkt2[3] = 0x01;
        pkt2[4] = (byte) (invIndex & 0xFF);
        pkt2[5] = 0x00;
        writeIntLE(pkt2, 6, itemTypeId);
        return sendPacket(pkt2);
    }

    public boolean useSkill(int skillId, int targetId) {
        byte[] pkt = new byte[12];
        pkt[0] = 0x29;
        pkt[1] = 0x00;
        writeIntLE(pkt, 2, skillId);
        pkt[6] = 0x00;
        pkt[7] = 0x01;
        writeIntLE(pkt, 8, targetId);
        return sendPacket(pkt);
    }

    public boolean cancelAction() {
        return sendPacket(buildPacket(0x2A));
    }

    
    public boolean moveTowards(float fromX, float fromY, float fromZ,
            float toX, float toY, float toZ,
            float speedMs) {
        int useTimeMs = 500;
        int speedEncoded = (int) (speedMs * 256.0f);

        byte[] pkt = new byte[33];
        pkt[0] = 0x00;
        pkt[1] = 0x00; 
        writeFloatLE(pkt, 2, fromX); 
        writeFloatLE(pkt, 6, fromY); 
        writeFloatLE(pkt, 10, fromZ); 
        writeFloatLE(pkt, 14, fromX); 
        writeFloatLE(pkt, 18, fromY); 
        writeFloatLE(pkt, 22, fromZ); 
        pkt[26] = (byte) (useTimeMs & 0xFF); 
        pkt[27] = (byte) ((useTimeMs >> 8) & 0xFF); 
        pkt[28] = (byte) (speedEncoded & 0xFF); 
        pkt[29] = (byte) ((speedEncoded >> 8) & 0xFF); 
        pkt[30] = 0x21; 
        pkt[31] = (byte) (nextMoveSeq & 0xFF); 
        pkt[32] = (byte) ((nextMoveSeq >> 8) & 0xFF); 
        nextMoveSeq++;
        return sendPacket(pkt);
    }

    
    public boolean stopMove(float atX, float atY, float atZ, float speedMs) {
        int speedEncoded = (int) (speedMs * 256.0f);
        byte[] pkt = new byte[22];
        pkt[0] = 0x07;
        pkt[1] = 0x00; 
        writeFloatLE(pkt, 2, atX); 
        writeFloatLE(pkt, 6, atY); 
        writeFloatLE(pkt, 10, atZ); 
        pkt[14] = (byte) (speedEncoded & 0xFF); 
        pkt[15] = (byte) ((speedEncoded >> 8) & 0xFF); 
        pkt[16] = 0x00; 
        pkt[17] = 0x21; 
        pkt[18] = (byte) (nextMoveSeq & 0xFF); 
        pkt[19] = (byte) ((nextMoveSeq >> 8) & 0xFF); 
        nextMoveSeq++;
        pkt[20] = 0x64;
        pkt[21] = 0x00; 
        return sendPacket(pkt);
    }

    
    @Deprecated
    public boolean requestMove(float x, float y, float z) {
        return moveTowards(x, 0f, z, x, 0f, z, 7.5f); 
    }

    public boolean startMeditating() {
        return sendPacket(buildPacket(0x2E));
    }

    public boolean stopMeditating() {
        return sendPacket(buildPacket(0x2F));
    }

    public boolean rezToTown() {
        return sendPacket(buildPacket(0x04));
    }

    public boolean rezWithScroll() {
        return sendPacket(buildPacket(0x05));
    }

    public boolean acceptRez() {
        return sendPacket(buildPacket(0x57));
    }

    public boolean logOut(int toAccount) {
        byte[] pkt = buildPacket(0x01, intBytes(toAccount));
        return sendPacket(pkt);
    }

    public boolean inviteParty(int playerId) {
        byte[] pkt = buildPacket(0x1B, intBytes(playerId));
        return sendPacket(pkt);
    }

    public boolean leaveParty() {
        return sendPacket(buildPacket(0x1E));
    }

    public boolean useGenieSkill(int skillId, int targetId) {
        byte[] pkt = new byte[10];
        pkt[0] = 0x74;
        pkt[1] = 0x00;

        pkt[2] = (byte) (skillId & 0xFF);
        pkt[3] = (byte) ((skillId >> 8) & 0xFF);
        pkt[4] = 0x00;
        pkt[5] = 0x01;
        writeIntLE(pkt, 6, targetId);
        return sendPacket(pkt);
    }

    public boolean increaseFlySpeed(boolean start) {
        byte[] pkt = buildPacket(0x5A, intBytes(start ? 1 : 0));
        return sendPacket(pkt);
    }

    public boolean toggleFashion() {
        return sendPacket(buildPacket(0x55));
    }

    public boolean summonPet(int petIndex) {
        byte[] pkt = buildPacket(0x64, intBytes(petIndex));
        return sendPacket(pkt);
    }

    public boolean recallPet() {
        return sendPacket(buildPacket(0x65));
    }

    public boolean repairAll() {
        byte[] pkt = new byte[16];
        pkt[0] = 0x25;
        pkt[1] = 0x00;
        writeIntLE(pkt, 2, 3);
        writeIntLE(pkt, 6, 6);
        pkt[10] = (byte) 0xFF;
        pkt[11] = (byte) 0xFF;
        pkt[12] = (byte) 0xFF;
        pkt[13] = (byte) 0xFF;
        pkt[14] = 0x00;
        pkt[15] = 0x00;
        return sendPacket(pkt);
    }

    public boolean sendPacket(byte[] packetData) {
        if (!initialized) {
            log("[PKT] ERRO: PacketSender nao inicializado");
            return false;
        }

        HANDLE hProc = memory.getProcessHandle();
        if (hProc == null) {
            log("[PKT] ERRO: Process handle nulo");
            return false;
        }

        
        if (gamePid != 0) {
            boolean apcOk = sendPacketAPC(hProc, packetData);
            if (apcOk)
                return true;
            log("[PKT] APC falhou, tentando CreateRemoteThread...");
        }

        return sendPacketCRT(hProc, packetData);
    }

    
    private boolean sendPacketAPC(HANDLE hProc, byte[] packetData) {
        
        byte[] shellcode = buildShellcodeAPC(packetData.length);

        if (shellcode.length > PERSISTENT_SHELLCODE_SIZE || packetData.length > PERSISTENT_PKT_SIZE) {
            log(String.format("[PKT-APC] WARN: tamanho excede buffer persistente (shell=%d pkt=%d)",
                    shellcode.length, packetData.length));
        }

        
        
        
        Pointer mem;
        boolean usingPersistent;
        if (persistentApcMem != null
                && shellcode.length <= PERSISTENT_SHELLCODE_SIZE
                && packetData.length <= PERSISTENT_PKT_SIZE) {
            mem = persistentApcMem;
            usingPersistent = true;
        } else {
            int totalSize = shellcode.length + packetData.length;
            mem = Kernel32.INSTANCE.VirtualAllocEx(hProc, null,
                    new com.sun.jna.platform.win32.BaseTSD.SIZE_T(totalSize),
                    WinNT.MEM_COMMIT | WinNT.MEM_RESERVE,
                    WinNT.PAGE_EXECUTE_READWRITE);
            usingPersistent = false;
            if (mem == null) {
                log("[PKT-APC] VirtualAllocEx falhou");
                return false;
            }
        }

        long codeAddr = Pointer.nativeValue(mem);
        long pktAddr = codeAddr + shellcode.length;

        
        
        int writeSize = shellcode.length + packetData.length;
        Memory buf = new Memory(writeSize);
        buf.write(0, shellcode, 0, shellcode.length);
        buf.write(shellcode.length, packetData, 0, packetData.length);
        if (!Kernel32.INSTANCE.WriteProcessMemory(hProc, mem, buf, writeSize, null)) {
            if (!usingPersistent) {
                Kernel32.INSTANCE.VirtualFreeEx(hProc, mem,
                        new com.sun.jna.platform.win32.BaseTSD.SIZE_T(0), WinNT.MEM_RELEASE);
            }
            log("[PKT-APC] WriteProcessMemory falhou");
            return false;
        }

        
        
        
        int queued = 0;
        final int THREAD_SET_CONTEXT = 0x0010;

        if (mainThreadId != 0) {
            
            HANDLE hThread = Kernel32Ext.INSTANCE.OpenThread(
                    THREAD_SET_CONTEXT, false, mainThreadId);
            if (hThread != null) {
                Pointer apcFunc = new Pointer(codeAddr);
                Pointer apcArg = new Pointer(pktAddr);
                boolean ok = Kernel32Ext.INSTANCE.QueueUserAPC(apcFunc, hThread, apcArg);
                if (ok)
                    queued++;
                Kernel32.INSTANCE.CloseHandle(hThread);
            }
            log(String.format("[PKT-APC] APC na main thread %d: %s", mainThreadId, queued > 0 ? "OK" : "FALHOU"));
        }

        
        if (queued == 0 && gamePid != 0) {
            log("[PKT-APC] Main thread falhou, tentando primeiras threads do processo...");
            final int TH32CS_SNAPTHREAD = 0x00000004;
            com.sun.jna.platform.win32.WinDef.DWORD snapPid = new com.sun.jna.platform.win32.WinDef.DWORD(0);
            HANDLE snap = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                    new com.sun.jna.platform.win32.WinDef.DWORD(TH32CS_SNAPTHREAD), snapPid);
            if (snap != null && snap != Kernel32.INVALID_HANDLE_VALUE) {
                THREADENTRY32.ByRef te = new THREADENTRY32.ByRef();
                if (Kernel32Ext.INSTANCE.Thread32First(snap, te)) {
                    do {
                        if (te.th32OwnerProcessID == gamePid && queued < 3) {
                            HANDLE hThread = Kernel32Ext.INSTANCE.OpenThread(
                                    THREAD_SET_CONTEXT, false, te.th32ThreadID);
                            if (hThread != null) {
                                Pointer apcFunc = new Pointer(codeAddr);
                                Pointer apcArg = new Pointer(pktAddr);
                                boolean ok = Kernel32Ext.INSTANCE.QueueUserAPC(apcFunc, hThread, apcArg);
                                if (ok)
                                    queued++;
                                Kernel32.INSTANCE.CloseHandle(hThread);
                            }
                        }
                    } while (Kernel32Ext.INSTANCE.Thread32Next(snap, te));
                }
                Kernel32.INSTANCE.CloseHandle(snap);
            }
        }

        if (queued == 0) {
            if (!usingPersistent) {
                Kernel32.INSTANCE.VirtualFreeEx(hProc, mem,
                        new com.sun.jna.platform.win32.BaseTSD.SIZE_T(0), WinNT.MEM_RELEASE);
            }
            log("[PKT-APC] Nenhuma thread com APC queued");
            return false;
        }

        
        
        
        
        
        log(String.format("[PKT-APC] APC enfileirado em %d thread(s). pkt=%d bytes [buf=%s]",
                queued, packetData.length, usingPersistent ? "persistente" : "temp"));
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return true;
    }

    
    private boolean sendPacketCRT(HANDLE hProc, byte[] packetData) {
        byte[] shellcode = buildShellcode(packetData.length);

        Pointer funcMem = Kernel32.INSTANCE.VirtualAllocEx(hProc, null,
                new com.sun.jna.platform.win32.BaseTSD.SIZE_T(shellcode.length),
                WinNT.MEM_COMMIT | WinNT.MEM_RESERVE,
                WinNT.PAGE_EXECUTE_READWRITE);
        if (funcMem == null) {
            log("[PKT] ERRO: VirtualAllocEx falhou para shellcode");
            return false;
        }

        Pointer pktMem = Kernel32.INSTANCE.VirtualAllocEx(hProc, null,
                new com.sun.jna.platform.win32.BaseTSD.SIZE_T(packetData.length),
                WinNT.MEM_COMMIT | WinNT.MEM_RESERVE,
                WinNT.PAGE_READWRITE);
        if (pktMem == null) {
            Kernel32.INSTANCE.VirtualFreeEx(hProc, funcMem,
                    new com.sun.jna.platform.win32.BaseTSD.SIZE_T(0), WinNT.MEM_RELEASE);
            log("[PKT] ERRO: VirtualAllocEx falhou para packet data");
            return false;
        }

        long pktAddr = Pointer.nativeValue(pktMem);

        byte[] finalCode = patchShellcodePacketAddr(shellcode, pktAddr);

        boolean success = false;
        try {

            Memory codeBuf = new Memory(finalCode.length);
            codeBuf.write(0, finalCode, 0, finalCode.length);
            if (!Kernel32.INSTANCE.WriteProcessMemory(hProc, funcMem, codeBuf, finalCode.length, null)) {
                log("[PKT] ERRO: WriteProcessMemory falhou para shellcode");
                return false;
            }

            Memory pktBuf = new Memory(packetData.length);
            pktBuf.write(0, packetData, 0, packetData.length);
            if (!Kernel32.INSTANCE.WriteProcessMemory(hProc, pktMem, pktBuf, packetData.length, null)) {
                log("[PKT] ERRO: WriteProcessMemory falhou para packet");
                return false;
            }

            HANDLE hThread = Kernel32.INSTANCE.CreateRemoteThread(hProc, null, 0,
                    funcMem, null, 0, null);
            if (hThread == null) {
                int crtErr = Kernel32.INSTANCE.GetLastError();
                log("[PKT] WARN: CreateRemoteThread falhou (erro=" + crtErr + "), tentando NtCreateThreadEx...");
                WinNT.HANDLEByReference hThreadRef = new WinNT.HANDLEByReference();
                int status = NtDll.INSTANCE.NtCreateThreadEx(
                        hThreadRef, 0x1FFFFF, null, hProc,
                        funcMem, null, 0, 0, 0, 0, null);
                if (status < 0) {
                    log(String.format("[PKT] ERRO: NtCreateThreadEx falhou (status=0x%X)", status));
                    return false;
                }
                hThread = hThreadRef.getValue();
                log("[PKT] NtCreateThreadEx OK");
            }

            int waitResult = Kernel32.INSTANCE.WaitForSingleObject(hThread, 5000);
            Kernel32.INSTANCE.CloseHandle(hThread);

            success = (waitResult == WinBase.WAIT_OBJECT_0);
            if (!success) {
                log(String.format("[PKT] WARN: Thread nao finalizou normalmente (wait=0x%X)", waitResult));
            }
        } finally {

            Kernel32.INSTANCE.VirtualFreeEx(hProc, funcMem,
                    new com.sun.jna.platform.win32.BaseTSD.SIZE_T(0), WinNT.MEM_RELEASE);
            Kernel32.INSTANCE.VirtualFreeEx(hProc, pktMem,
                    new com.sun.jna.platform.win32.BaseTSD.SIZE_T(0), WinNT.MEM_RELEASE);
        }

        return success;
    }

    
    private byte[] buildShellcodeAPC(int packetSize) {
        ByteArrayOutputStream code = new ByteArrayOutputStream();

        
        code.write(0x60);
        
        code.write(0xB8);
        writeLE(code, (int) sendPacketAddr);
        
        code.write(0x8B);
        code.write(0x0D);
        writeLE(code, (int) gameBaseAddr);
        
        code.write(0x8B);
        code.write(0x49);
        code.write(CONNECTION_OFFSET);
        
        code.write(0x8B);
        code.write(0x7C);
        code.write(0x24);
        code.write(0x24);
        
        if (packetSize <= 127) {
            code.write(0x6A);
            code.write(packetSize);
        } else {
            code.write(0x68);
            writeLE(code, packetSize);
        }
        
        code.write(0x57);
        
        code.write(0xFF);
        code.write(0xD0);
        
        code.write(0x61);
        
        code.write(0xC2);
        code.write(0x04);
        code.write(0x00);

        return code.toByteArray();
    }

    private byte[] buildShellcode(int packetSize) {
        ByteArrayOutputStream code = new ByteArrayOutputStream();

        code.write(0x60);

        code.write(0xB8);
        writeLE(code, (int) sendPacketAddr);

        code.write(0x8B);
        code.write(0x0D);
        writeLE(code, (int) gameBaseAddr);

        code.write(0x8B);
        code.write(0x49);
        code.write(CONNECTION_OFFSET);

        code.write(0xBF);
        writeLE(code, 0xDEADBEEF);

        if (packetSize <= 127) {
            code.write(0x6A);
            code.write(packetSize);
        } else {
            code.write(0x68);
            writeLE(code, packetSize);
        }

        code.write(0x57);

        code.write(0xFF);
        code.write(0xD0);

        code.write(0x61);

        code.write(0xC3);

        return code.toByteArray();
    }

    private byte[] patchShellcodePacketAddr(byte[] shellcode, long pktAddr) {
        byte[] result = shellcode.clone();
        byte[] marker = { (byte) 0xEF, (byte) 0xBE, (byte) 0xAD, (byte) 0xDE };
        for (int i = 0; i < result.length - 3; i++) {
            if (result[i] == marker[0] && result[i + 1] == marker[1] &&
                    result[i + 2] == marker[2] && result[i + 3] == marker[3]) {
                result[i] = (byte) (pktAddr & 0xFF);
                result[i + 1] = (byte) ((pktAddr >> 8) & 0xFF);
                result[i + 2] = (byte) ((pktAddr >> 16) & 0xFF);
                result[i + 3] = (byte) ((pktAddr >> 24) & 0xFF);
                break;
            }
        }
        return result;
    }

    private long findSendPacketFunction(long codeStart, int scanSize) {

        String[] patterns = {

                "B8 ? ? ? ? 8B 0D ? ? ? ? 8B 49 20",

                "FF 15 ? ? ? ? 8B 0D ? ? ? ? 8B 49 20",
        };

        for (String pattern : patterns) {
            long addr = memory.scanPattern(codeStart, scanSize, pattern);
            if (addr != 0) {

                int firstByte = memory.readInt(addr) & 0xFF;
                if (firstByte == 0xB8) {

                    long funcAddr = memory.readInt(addr + 1) & 0xFFFFFFFFL;
                    if (funcAddr > 0x400000 && funcAddr < 0x1000000) {
                        log(String.format("[PKT] sendPacketFunction via pattern: 0x%X (from 0x%X)", funcAddr, addr));
                        return funcAddr;
                    }
                } else if (firstByte == 0xFF) {

                    long ptrAddr = memory.readInt(addr + 2) & 0xFFFFFFFFL;
                    long funcAddr = memory.readInt(ptrAddr) & 0xFFFFFFFFL;
                    if (funcAddr > 0x400000 && funcAddr < 0x1000000) {
                        log(String.format("[PKT] sendPacketFunction via indirect pattern: 0x%X (from 0x%X)", funcAddr,
                                addr));
                        return funcAddr;
                    }
                }
            }
        }

        return 0;
    }

    private static byte[] buildPacket(int opcode, byte[]... payloads) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(opcode & 0xFF);
        out.write((opcode >> 8) & 0xFF);
        for (byte[] p : payloads) {
            out.write(p, 0, p.length);
        }
        return out.toByteArray();
    }

    private static byte[] intBytes(int val) {
        return new byte[] {
                (byte) (val & 0xFF),
                (byte) ((val >> 8) & 0xFF),
                (byte) ((val >> 16) & 0xFF),
                (byte) ((val >> 24) & 0xFF)
        };
    }

    private static byte[] shortBytes(int val) {
        return new byte[] {
                (byte) (val & 0xFF),
                (byte) ((val >> 8) & 0xFF)
        };
    }

    
    private static void writeFloatLE(byte[] arr, int offset, float val) {
        writeIntLE(arr, offset, Float.floatToRawIntBits(val));
    }

    private static void writeIntLE(byte[] arr, int offset, int val) {
        arr[offset] = (byte) (val & 0xFF);
        arr[offset + 1] = (byte) ((val >> 8) & 0xFF);
        arr[offset + 2] = (byte) ((val >> 16) & 0xFF);
        arr[offset + 3] = (byte) ((val >> 24) & 0xFF);
    }

    private static void writeLE(ByteArrayOutputStream out, int val) {
        out.write(val & 0xFF);
        out.write((val >> 8) & 0xFF);
        out.write((val >> 16) & 0xFF);
        out.write((val >> 24) & 0xFF);
    }

    private static void log(String msg) {
        System.out.println(msg);
        BotSettings.logToUi(msg);
    }
}

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

import java.io.ByteArrayOutputStream;


public class PacketSender {

    private final WinMemoryReader memory;
    private long sendPacketAddr;
    private long gameBaseAddr;
    private long moduleBase;
    private boolean initialized = false;

    
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
                        log(String.format("[PKT] gameBase fallback: 0x%X (val=0x%X, session=0x%X)", addr, val, session));
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
        return true;
    }

    
    public void setAddresses(long sendPacketAddr, long gameBaseAddr) {
        this.sendPacketAddr = sendPacketAddr;
        this.gameBaseAddr = gameBaseAddr;
        this.initialized = true;
        log(String.format("[PKT] Enderecos definidos manualmente: send=0x%X base=0x%X", sendPacketAddr, gameBaseAddr));
    }

    public boolean isInitialized() { return initialized; }

    

    
    public boolean selectTarget(int targetId) {
        byte[] pkt = buildPacket(0x02, intBytes(targetId));
        return sendPacket(pkt);
    }

    
    public boolean deselectTarget() {
        return sendPacket(buildPacket(0x08));
    }

    
    public boolean regularAttack(int afterSkill) {
        byte[] pkt = new byte[3];
        pkt[0] = 0x03; pkt[1] = 0x00;
        pkt[2] = (byte)(afterSkill & 0xFF);
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
        pkt[0] = 0x28; pkt[1] = 0x00;
        pkt[2] = 0x00; pkt[3] = 0x00; 
        pkt[4] = 0x01;
        pkt[5] = (byte)(invIndex & 0xFF);
        pkt[6] = 0x00;
        
        pkt[7] = (byte)(itemTypeId & 0xFF);
        
        
        
        
        
        byte[] pkt2 = new byte[10];
        pkt2[0] = 0x28; pkt2[1] = 0x00;
        pkt2[2] = 0x00; 
        pkt2[3] = 0x01;
        pkt2[4] = (byte)(invIndex & 0xFF);
        pkt2[5] = 0x00;
        writeIntLE(pkt2, 6, itemTypeId);
        return sendPacket(pkt2);
    }

    
    public boolean useSkill(int skillId, int targetId) {
        byte[] pkt = new byte[12];
        pkt[0] = 0x29; pkt[1] = 0x00;
        writeIntLE(pkt, 2, skillId);
        pkt[6] = 0x00; pkt[7] = 0x01;
        writeIntLE(pkt, 8, targetId);
        return sendPacket(pkt);
    }

    
    public boolean cancelAction() {
        return sendPacket(buildPacket(0x2A));
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
        pkt[0] = 0x74; pkt[1] = 0x00;
        
        pkt[2] = (byte)(skillId & 0xFF);
        pkt[3] = (byte)((skillId >> 8) & 0xFF);
        pkt[4] = 0x00; pkt[5] = 0x01;
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
        pkt[0] = 0x25; pkt[1] = 0x00;
        writeIntLE(pkt, 2, 3);      
        writeIntLE(pkt, 6, 6);      
        pkt[10] = (byte)0xFF; pkt[11] = (byte)0xFF; pkt[12] = (byte)0xFF; pkt[13] = (byte)0xFF;
        pkt[14] = 0x00; pkt[15] = 0x00;
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

        Pointer ptrProc = hProc.getPointer();

        
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
                log("[PKT] ERRO: CreateRemoteThread falhou (erro=" + Kernel32.INSTANCE.GetLastError() + ")");
                return false;
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
                result[i]     = (byte) (pktAddr & 0xFF);
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
                        log(String.format("[PKT] sendPacketFunction via indirect pattern: 0x%X (from 0x%X)", funcAddr, addr));
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
        return new byte[]{
            (byte) (val & 0xFF),
            (byte) ((val >> 8) & 0xFF),
            (byte) ((val >> 16) & 0xFF),
            (byte) ((val >> 24) & 0xFF)
        };
    }

    
    private static byte[] shortBytes(int val) {
        return new byte[]{
            (byte) (val & 0xFF),
            (byte) ((val >> 8) & 0xFF)
        };
    }

    
    private static void writeIntLE(byte[] arr, int offset, int val) {
        arr[offset]     = (byte) (val & 0xFF);
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

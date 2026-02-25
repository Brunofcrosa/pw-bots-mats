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

/**
 * Sends game packets by injecting x86 shellcode into the PW client process.
 *
 * Translated from AutoIt sendPacket code (Smurfin / Prophet bot).
 * Uses VirtualAllocEx + WriteProcessMemory + CreateRemoteThread to call
 * the game's internal SendPacket function.
 *
 * The shellcode executed in the game process:
 *   PUSHAD
 *   MOV EAX, sendPacketFunction    ; address of the send function
 *   MOV ECX, DWORD PTR [gameBase]  ; load game base pointer
 *   MOV ECX, DWORD PTR [ECX+0x20] ; get the connection/session object
 *   MOV EDI, packetAddress         ; pointer to our packet data
 *   PUSH packetSize                ; size of the packet
 *   PUSH EDI                       ; pointer to packet
 *   CALL EAX                       ; call SendPacket
 *   POPAD
 *   RET
 */
public class PacketSender {

    private final WinMemoryReader memory;
    private long sendPacketAddr;
    private long gameBaseAddr;
    private long moduleBase;
    private boolean initialized = false;

    /* x86 connection chain offset: [gameBase] + CONNECTION_OFFSET → session obj (ECX) */
    private static final int CONNECTION_OFFSET = 0x20;

    /* Signature patterns to auto-discover addresses for this client version */
    // Pattern for the code that calls SendPacket: MOV ECX, [gameBase]; MOV ECX, [ECX+20]
    private static final String SIG_GAME_BASE = "8B 0D ? ? ? ? 8B 49 20";
    // Alternate pattern that references the send function
    private static final String SIG_SEND_CALL = "6A ? 57 FF D0";

    public PacketSender(WinMemoryReader memory) {
        this.memory = memory;
    }

    /**
     * Initialize the packet sender by discovering the required addresses.
     * Must be called after the game process is attached and module base is known.
     *
     * @param moduleBase the base address of elementclient.exe
     * @return true if addresses were discovered successfully
     */
    public boolean initialize(long moduleBase) {
        this.moduleBase = moduleBase;
        log("[PKT] Iniciando busca de enderecos para envio de pacotes...");

        // Try to find gameBase via signature scan
        // The pattern "8B 0D XX XX XX XX 8B 49 20" appears when the game loads
        // the connection object before calling send.
        long codeStart = moduleBase + 0x1000;  // skip PE header
        int scanSize = 0x800000; // 8MB scan range

        long sigAddr = memory.scanPattern(codeStart, scanSize, SIG_GAME_BASE);
        if (sigAddr != 0) {
            // The 4 bytes after "8B 0D" are the gameBase address (little-endian)
            gameBaseAddr = memory.readInt(sigAddr + 2) & 0xFFFFFFFFL;
            log(String.format("[PKT] gameBase encontrado via signature: 0x%X (sigAddr=0x%X)", gameBaseAddr, sigAddr));
        } else {
            log("[PKT] WARN: Signature do gameBase nao encontrada. Tentando enderecos conhecidos...");
            // Try known addresses for various PW versions
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

        // Try to find sendPacketFunction via signature scan
        // Look for the actual function (commonly starts with a recognizable prologue)
        // We scan for a CALL pattern that references our connection chain
        sendPacketAddr = findSendPacketFunction(codeStart, scanSize);

        if (sendPacketAddr == 0) {
            log("[PKT] WARN: sendPacketFunction nao encontrada via scan. Tentando enderecos conhecidos...");
            long[] knownFuncs = { 0x005BD7B0L, 0x005BE7B0L, 0x005BC7B0L };
            for (long addr : knownFuncs) {
                int firstByte = memory.readInt(addr) & 0xFF;
                // Check if it looks like a function start (push ebp, sub esp, etc.)
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

        // Validate the chain: gameBase → [ptr] → [ptr+0x20] should be valid
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

    /**
     * Force-set the addresses manually (for testing or when auto-discovery fails).
     */
    public void setAddresses(long sendPacketAddr, long gameBaseAddr) {
        this.sendPacketAddr = sendPacketAddr;
        this.gameBaseAddr = gameBaseAddr;
        this.initialized = true;
        log(String.format("[PKT] Enderecos definidos manualmente: send=0x%X base=0x%X", sendPacketAddr, gameBaseAddr));
    }

    public boolean isInitialized() { return initialized; }

    // ─── High-level game actions ─────────────────────────────────────────

    /** Select a target (NPC, mob, resource, player) by its world ID */
    public boolean selectTarget(int targetId) {
        byte[] pkt = buildPacket(0x02, intBytes(targetId));
        return sendPacket(pkt);
    }

    /** Deselect current target */
    public boolean deselectTarget() {
        return sendPacket(buildPacket(0x08));
    }

    /** Regular/auto attack. afterSkill=1 if starting after a skill cast. */
    public boolean regularAttack(int afterSkill) {
        byte[] pkt = new byte[3];
        pkt[0] = 0x03; pkt[1] = 0x00;
        pkt[2] = (byte)(afterSkill & 0xFF);
        return sendPacket(pkt);
    }

    /** Pick up an item from the ground */
    public boolean pickUpItem(int uniqueItemId, int itemTypeId) {
        byte[] pkt = buildPacket(0x06, intBytes(uniqueItemId), intBytes(itemTypeId));
        return sendPacket(pkt);
    }

    /** Open NPC dialogue (also used to interact with gather nodes) */
    public boolean startNpcDialogue(int npcId) {
        byte[] pkt = buildPacket(0x23, intBytes(npcId));
        return sendPacket(pkt);
    }

    /** Use an item from inventory */
    public boolean useItem(int invIndex, int itemTypeId) {
        byte[] pkt = new byte[10];
        pkt[0] = 0x28; pkt[1] = 0x00;
        pkt[2] = 0x00; pkt[3] = 0x00; // equip=0 (inventory)
        pkt[4] = 0x01;
        pkt[5] = (byte)(invIndex & 0xFF);
        pkt[6] = 0x00;
        // itemTypeId (4 bytes LE)
        pkt[7] = (byte)(itemTypeId & 0xFF);
        // Wait, packet is 10 bytes. Let me recalculate.
        // 2800 + 0000(equip) + 01 + invIndex(1byte) + 00 + itemTypeId(4 bytes) = 2+2+1+1+1+4 = 11? 
        // AutoIt: packetSize=10, packet = 2800 + hex(equip,2) + 01 + hex(index,2) + 00 + hex(itemTypeId)
        // hex(x,2) = 1 byte, hex(x) = 4 bytes (default size=8 nybbles = 4 bytes)
        // So: 2 + 1 + 1 + 1 + 1 + 4 = 10 bytes
        byte[] pkt2 = new byte[10];
        pkt2[0] = 0x28; pkt2[1] = 0x00;
        pkt2[2] = 0x00; // equip=0
        pkt2[3] = 0x01;
        pkt2[4] = (byte)(invIndex & 0xFF);
        pkt2[5] = 0x00;
        writeIntLE(pkt2, 6, itemTypeId);
        return sendPacket(pkt2);
    }

    /** Use a skill on a target */
    public boolean useSkill(int skillId, int targetId) {
        byte[] pkt = new byte[12];
        pkt[0] = 0x29; pkt[1] = 0x00;
        writeIntLE(pkt, 2, skillId);
        pkt[6] = 0x00; pkt[7] = 0x01;
        writeIntLE(pkt, 8, targetId);
        return sendPacket(pkt);
    }

    /** Cancel current action (skill cast, etc.) */
    public boolean cancelAction() {
        return sendPacket(buildPacket(0x2A));
    }

    /** Start meditating */
    public boolean startMeditating() {
        return sendPacket(buildPacket(0x2E));
    }

    /** Stop meditating */
    public boolean stopMeditating() {
        return sendPacket(buildPacket(0x2F));
    }

    /** Respawn in town after death */
    public boolean rezToTown() {
        return sendPacket(buildPacket(0x04));
    }

    /** Respawn using rez scroll */
    public boolean rezWithScroll() {
        return sendPacket(buildPacket(0x05));
    }

    /** Accept rez from priest */
    public boolean acceptRez() {
        return sendPacket(buildPacket(0x57));
    }

    /** Log out (toAccount=1 → character select, toAccount=0 → exit) */
    public boolean logOut(int toAccount) {
        byte[] pkt = buildPacket(0x01, intBytes(toAccount));
        return sendPacket(pkt);
    }

    /** Invite player to party */
    public boolean inviteParty(int playerId) {
        byte[] pkt = buildPacket(0x1B, intBytes(playerId));
        return sendPacket(pkt);
    }

    /** Leave current party */
    public boolean leaveParty() {
        return sendPacket(buildPacket(0x1E));
    }

    /** Use genie skill */
    public boolean useGenieSkill(int skillId, int targetId) {
        byte[] pkt = new byte[10];
        pkt[0] = 0x74; pkt[1] = 0x00;
        // skillId as 2 bytes (short LE)
        pkt[2] = (byte)(skillId & 0xFF);
        pkt[3] = (byte)((skillId >> 8) & 0xFF);
        pkt[4] = 0x00; pkt[5] = 0x01;
        writeIntLE(pkt, 6, targetId);
        return sendPacket(pkt);
    }

    /** Increase fly speed (start=1) / stop (start=0) */
    public boolean increaseFlySpeed(boolean start) {
        byte[] pkt = buildPacket(0x5A, intBytes(start ? 1 : 0));
        return sendPacket(pkt);
    }

    /** Toggle fashion display */
    public boolean toggleFashion() {
        return sendPacket(buildPacket(0x55));
    }

    /** Summon pet at index (0-9) */
    public boolean summonPet(int petIndex) {
        byte[] pkt = buildPacket(0x64, intBytes(petIndex));
        return sendPacket(pkt);
    }

    /** Recall current pet */
    public boolean recallPet() {
        return sendPacket(buildPacket(0x65));
    }

    /** Repair all equipped items (must have NPC dialogue open) */
    public boolean repairAll() {
        byte[] pkt = new byte[16];
        pkt[0] = 0x25; pkt[1] = 0x00;
        writeIntLE(pkt, 2, 3);      // repair action
        writeIntLE(pkt, 6, 6);      // nBytes
        pkt[10] = (byte)0xFF; pkt[11] = (byte)0xFF; pkt[12] = (byte)0xFF; pkt[13] = (byte)0xFF;
        pkt[14] = 0x00; pkt[15] = 0x00;
        return sendPacket(pkt);
    }

    // ─── Core packet send mechanism ─────────────────────────────────────

    /**
     * Send a raw packet to the game server via code injection.
     * This is the Java equivalent of the AutoIt sendPacket function.
     */
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

        // Build the x86 shellcode
        byte[] shellcode = buildShellcode(packetData.length);

        // Allocate memory for shellcode in the target process
        Pointer funcMem = Kernel32.INSTANCE.VirtualAllocEx(hProc, null,
                new com.sun.jna.platform.win32.BaseTSD.SIZE_T(shellcode.length),
                WinNT.MEM_COMMIT | WinNT.MEM_RESERVE,
                WinNT.PAGE_EXECUTE_READWRITE);
        if (funcMem == null) {
            log("[PKT] ERRO: VirtualAllocEx falhou para shellcode");
            return false;
        }

        // Allocate memory for packet data in the target process
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

        // Patch the shellcode with the actual packet address
        byte[] finalCode = patchShellcodePacketAddr(shellcode, pktAddr);

        boolean success = false;
        try {
            // Write shellcode to allocated memory
            Memory codeBuf = new Memory(finalCode.length);
            codeBuf.write(0, finalCode, 0, finalCode.length);
            if (!Kernel32.INSTANCE.WriteProcessMemory(hProc, funcMem, codeBuf, finalCode.length, null)) {
                log("[PKT] ERRO: WriteProcessMemory falhou para shellcode");
                return false;
            }

            // Write packet data to allocated memory
            Memory pktBuf = new Memory(packetData.length);
            pktBuf.write(0, packetData, 0, packetData.length);
            if (!Kernel32.INSTANCE.WriteProcessMemory(hProc, pktMem, pktBuf, packetData.length, null)) {
                log("[PKT] ERRO: WriteProcessMemory falhou para packet");
                return false;
            }

            // Create remote thread to execute the shellcode
            HANDLE hThread = Kernel32.INSTANCE.CreateRemoteThread(hProc, null, 0,
                    funcMem, null, 0, null);
            if (hThread == null) {
                log("[PKT] ERRO: CreateRemoteThread falhou (erro=" + Kernel32.INSTANCE.GetLastError() + ")");
                return false;
            }

            // Wait for the thread to complete (max 5 seconds)
            int waitResult = Kernel32.INSTANCE.WaitForSingleObject(hThread, 5000);
            Kernel32.INSTANCE.CloseHandle(hThread);

            success = (waitResult == WinBase.WAIT_OBJECT_0);
            if (!success) {
                log(String.format("[PKT] WARN: Thread nao finalizou normalmente (wait=0x%X)", waitResult));
            }
        } finally {
            // Free allocated memory
            Kernel32.INSTANCE.VirtualFreeEx(hProc, funcMem,
                    new com.sun.jna.platform.win32.BaseTSD.SIZE_T(0), WinNT.MEM_RELEASE);
            Kernel32.INSTANCE.VirtualFreeEx(hProc, pktMem,
                    new com.sun.jna.platform.win32.BaseTSD.SIZE_T(0), WinNT.MEM_RELEASE);
        }

        return success;
    }

    // ─── Shellcode builder ──────────────────────────────────────────────

    /**
     * Build x86 shellcode for calling the game's SendPacket function.
     * The packet address placeholder (0xDEADBEEF) will be patched later.
     *
     * Equivalent AutoIt opcodes:
     *   60                PUSHAD
     *   B8 XX XX XX XX    MOV EAX, sendPacketFunction
     *   8B 0D XX XX XX XX MOV ECX, DWORD PTR [gameBase]
     *   8B 49 20          MOV ECX, DWORD PTR [ECX+0x20]
     *   BF XX XX XX XX    MOV EDI, packetAddress
     *   6A XX             PUSH packetSize
     *   57                PUSH EDI
     *   FF D0             CALL EAX
     *   61                POPAD
     *   C3                RET
     */
    private byte[] buildShellcode(int packetSize) {
        ByteArrayOutputStream code = new ByteArrayOutputStream();

        // PUSHAD
        code.write(0x60);

        // MOV EAX, sendPacketAddr
        code.write(0xB8);
        writeLE(code, (int) sendPacketAddr);

        // MOV ECX, DWORD PTR [gameBaseAddr]
        code.write(0x8B);
        code.write(0x0D);
        writeLE(code, (int) gameBaseAddr);

        // MOV ECX, DWORD PTR [ECX + CONNECTION_OFFSET]
        code.write(0x8B);
        code.write(0x49);
        code.write(CONNECTION_OFFSET);

        // MOV EDI, packetAddress (placeholder 0xDEADBEEF, patched later)
        code.write(0xBF);
        writeLE(code, 0xDEADBEEF);

        // PUSH packetSize
        if (packetSize <= 127) {
            code.write(0x6A);
            code.write(packetSize);
        } else {
            code.write(0x68);
            writeLE(code, packetSize);
        }

        // PUSH EDI
        code.write(0x57);

        // CALL EAX
        code.write(0xFF);
        code.write(0xD0);

        // POPAD
        code.write(0x61);

        // RET
        code.write(0xC3);

        return code.toByteArray();
    }

    /** Patch 0xDEADBEEF placeholder in shellcode with actual packet address */
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

    // ─── Address discovery ──────────────────────────────────────────────

    /**
     * Try to find the SendPacket function address by scanning for known patterns.
     */
    private long findSendPacketFunction(long codeStart, int scanSize) {
        // Strategy 1: Look for the signature pattern right before a known CALL
        // In many PW versions, the send function is referenced via
        // MOV EAX, [sendFunc]; ... CALL EAX or CALL [sendFunc]

        // Strategy 2: Search for function prologue patterns near known offsets
        // The send function typically has: PUSH EBP; MOV EBP, ESP; SUB ESP, ...
        // or: PUSH ESI; PUSH EDI; ...

        // Try multiple signature patterns
        String[] patterns = {
            // Pattern from the calling code (references sendPacketFunction)
            "B8 ? ? ? ? 8B 0D ? ? ? ? 8B 49 20",
            // Alternative calling convention
            "FF 15 ? ? ? ? 8B 0D ? ? ? ? 8B 49 20",
        };

        for (String pattern : patterns) {
            long addr = memory.scanPattern(codeStart, scanSize, pattern);
            if (addr != 0) {
                // Extract the function address from the MOV EAX instruction
                int firstByte = memory.readInt(addr) & 0xFF;
                if (firstByte == 0xB8) {
                    // MOV EAX, imm32 → the function address is at addr+1
                    long funcAddr = memory.readInt(addr + 1) & 0xFFFFFFFFL;
                    if (funcAddr > 0x400000 && funcAddr < 0x1000000) {
                        log(String.format("[PKT] sendPacketFunction via pattern: 0x%X (from 0x%X)", funcAddr, addr));
                        return funcAddr;
                    }
                } else if (firstByte == 0xFF) {
                    // CALL [imm32] → indirect
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

    // ─── Utility methods ────────────────────────────────────────────────

    /** Build a simple packet: opcode (2 bytes LE) + optional payload segments */
    private static byte[] buildPacket(int opcode, byte[]... payloads) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(opcode & 0xFF);
        out.write((opcode >> 8) & 0xFF);
        for (byte[] p : payloads) {
            out.write(p, 0, p.length);
        }
        return out.toByteArray();
    }

    /** Convert int to 4 bytes little-endian */
    private static byte[] intBytes(int val) {
        return new byte[]{
            (byte) (val & 0xFF),
            (byte) ((val >> 8) & 0xFF),
            (byte) ((val >> 16) & 0xFF),
            (byte) ((val >> 24) & 0xFF)
        };
    }

    /** Write int as 4 bytes LE into array at offset */
    private static void writeIntLE(byte[] arr, int offset, int val) {
        arr[offset]     = (byte) (val & 0xFF);
        arr[offset + 1] = (byte) ((val >> 8) & 0xFF);
        arr[offset + 2] = (byte) ((val >> 16) & 0xFF);
        arr[offset + 3] = (byte) ((val >> 24) & 0xFF);
    }

    /** Write int as 4 bytes LE to stream */
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

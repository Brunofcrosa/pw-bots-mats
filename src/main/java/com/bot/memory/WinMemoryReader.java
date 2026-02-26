package com.bot.memory;

import com.bot.constants.BotSettings;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

public class WinMemoryReader {
    private HANDLE processHandle;
    private int pid;
    private final java.util.Set<Long> bulkReadWarned = new java.util.HashSet<>();

    public WinMemoryReader() {}

    public WinMemoryReader(String windowName) {
        openProcess(windowName);
    }

    public boolean openProcess(String windowName) {
        HWND hwnd = User32.INSTANCE.FindWindow(null, windowName);
        if (hwnd == null) return false;

        IntByReference pidRef = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
        this.pid = pidRef.getValue();

        this.processHandle = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_ALL_ACCESS,
                false, pid);
        return this.processHandle != null;
    }

    public long getModuleBaseAddress(String moduleName) {
        WinDef.DWORD dwFlags = new WinDef.DWORD(Tlhelp32.TH32CS_SNAPMODULE.longValue() | Tlhelp32.TH32CS_SNAPMODULE32.longValue());
        HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(dwFlags, new WinDef.DWORD(pid));
        if (snapshot == Kernel32.INVALID_HANDLE_VALUE) return 0L;
        try {
            Tlhelp32.MODULEENTRY32W moduleEntry = new Tlhelp32.MODULEENTRY32W();
            if (Kernel32.INSTANCE.Module32FirstW(snapshot, moduleEntry)) {
                do {
                    if (Native.toString(moduleEntry.szModule).equalsIgnoreCase(moduleName)) {
                        return Pointer.nativeValue(moduleEntry.modBaseAddr);
                    }
                } while (Kernel32.INSTANCE.Module32NextW(snapshot, moduleEntry));
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        return 0L;
    }

    public long scanPattern(long startAddress, int searchSize, String signature) {
        String[] tokens = signature.split(" ");
        int[] pattern = new int[tokens.length];

        for (int i = 0; i < tokens.length; i++) {
            pattern[i] = (tokens[i].equals("?") || tokens[i].equals("??")) ? -1 : Integer.parseInt(tokens[i], 16);
        }

        Memory buffer = new Memory(searchSize);
        if (!Kernel32.INSTANCE.ReadProcessMemory(processHandle, new Pointer(startAddress), buffer, searchSize, null)) return 0L;

        byte[] data = buffer.getByteArray(0, searchSize);

        for (int i = 0; i < data.length - pattern.length; i++) {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++) {
                if (pattern[j] != -1 && pattern[j] != (data[i + j] & 0xFF)) {
                    found = false;
                    break;
                }
            }
            if (found) return startAddress + i;
        }
        return 0L;
    }

    public String discoverSignatureForAddress(long startAddress, int searchSize, long targetAddress) {
        Memory buffer = new Memory(searchSize);
        if (!Kernel32.INSTANCE.ReadProcessMemory(processHandle, new Pointer(startAddress), buffer, searchSize, null)) return null;

        byte[] data = buffer.getByteArray(0, searchSize);

        byte[] targetBytes = new byte[] {
                (byte) (targetAddress & 0xFF),
                (byte) ((targetAddress >> 8) & 0xFF),
                (byte) ((targetAddress >> 16) & 0xFF),
                (byte) ((targetAddress >> 24) & 0xFF)
        };

        for (int i = 2; i < data.length - 12; i++) {
            if (data[i] == targetBytes[0] && data[i+1] == targetBytes[1] &&
                    data[i+2] == targetBytes[2] && data[i+3] == targetBytes[3]) {

                int opcode1 = data[i-2] & 0xFF;
                int opcode2 = data[i-1] & 0xFF;
                StringBuilder sig = new StringBuilder();

                if (opcode2 == 0xA1 || opcode2 == 0xA2 || opcode2 == 0xA3) {
                    sig.append(String.format("%02X ? ? ? ? ", opcode2));
                    for(int j = 4; j < 10; j++) sig.append(String.format("%02X ", data[i+j] & 0xFF));
                    return sig.toString().trim();
                } else {
                    sig.append(String.format("%02X %02X ? ? ? ? ", opcode1, opcode2));
                    for(int j = 4; j < 8; j++) sig.append(String.format("%02X ", data[i+j] & 0xFF));
                    return sig.toString().trim();
                }
            }
        }
        return null;
    }

    public int readInt(long address) {
        Memory mem = new Memory(4);
        if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, new Pointer(address), mem, 4, null)) return mem.getInt(0);
        return 0;
    }

    public int[] readIntArray(long address, int length) {
        Memory mem = new Memory(length * 4L);
        if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, new Pointer(address), mem, (int) mem.size(), null)) {
            int[] result = new int[length];
            for (int i = 0; i < length; i++) {
                result[i] = mem.getInt(i * 4L);
            }
            return result;
        }

        if (bulkReadWarned.add(address)) {
            String warn = String.format("[WARN] Bulk readIntArray falhou em 0x%X, usando leitura individual (aviso unico)", address);
            System.out.println(warn);
            BotSettings.logToUi(warn);
        }
        int[] fallback = new int[length];
        for (int i = 0; i < length; i++) {
            fallback[i] = readInt(address + (long) i * 4L);
        }
        return fallback;
    }

    public boolean writeInt(long address, int value) {
        Memory mem = new Memory(4);
        mem.setInt(0, value);
        return Kernel32.INSTANCE.WriteProcessMemory(processHandle, new Pointer(address), mem, 4, null);
    }

    public float readFloat(long address) {
        Memory mem = new Memory(4);
        if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, new Pointer(address), mem, 4, null)) return mem.getFloat(0);
        return 0.0f;
    }

    public boolean writeFloat(long address, float value) {
        Memory mem = new Memory(4);
        mem.setFloat(0, value);
        return Kernel32.INSTANCE.WriteProcessMemory(processHandle, new Pointer(address), mem, 4, null);
    }

    public String readString(long address, int maxLength) {
        Memory mem = new Memory(maxLength * 2L);
        if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, new Pointer(address), mem, (int) mem.size(), null)) return mem.getWideString(0);
        return "Unknown";
    }

    public String readStringFromPointer(long baseAddress, int offset) {
        long namePtr = readInt(baseAddress + offset) & 0xFFFFFFFFL;
        if (namePtr == 0) return "Unknown";
        return readString(namePtr, 64);
    }

    public long readPointerAddress(long baseAddress, int[] offsets) {
        long pointerVal = readInt(baseAddress) & 0xFFFFFFFFL;
        if (pointerVal == 0) return 0;
        for (int i = 0; i < offsets.length - 1; i++) {
            pointerVal = readInt(pointerVal + offsets[i]) & 0xFFFFFFFFL;
            if (pointerVal == 0) return 0;
        }
        return pointerVal + offsets[offsets.length - 1];
    }

    public void close() {
        if (this.processHandle != null) Kernel32.INSTANCE.CloseHandle(this.processHandle);
    }

    public HANDLE getProcessHandle() { return processHandle; }
    public int getPid() { return pid; }
}

package com.bot.memory;

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

    public boolean openProcess(String windowName) {
        HWND hwnd = User32.INSTANCE.FindWindow(null, windowName);
        if (hwnd == null) return false;

        IntByReference pidRef = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
        this.pid = pidRef.getValue();

        this.processHandle = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_VM_READ | WinNT.PROCESS_VM_OPERATION | WinNT.PROCESS_QUERY_INFORMATION,
                false,
                pid
        );
        return this.processHandle != null;
    }

    public long getModuleBaseAddress(String moduleName) {
        WinDef.DWORD dwFlags = new WinDef.DWORD(
                Tlhelp32.TH32CS_SNAPMODULE.longValue() | Tlhelp32.TH32CS_SNAPMODULE32.longValue()
        );

        HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                dwFlags,
                new WinDef.DWORD(pid)
        );

        if (snapshot == Kernel32.INVALID_HANDLE_VALUE) return 0L;

        try {
            Tlhelp32.MODULEENTRY32W moduleEntry = new Tlhelp32.MODULEENTRY32W();
            if (Kernel32.INSTANCE.Module32FirstW(snapshot, moduleEntry)) {
                do {
                    String discoveredName = Native.toString(moduleEntry.szModule);
                    if (discoveredName.equalsIgnoreCase(moduleName)) {
                        return Pointer.nativeValue(moduleEntry.modBaseAddr);
                    }
                } while (Kernel32.INSTANCE.Module32NextW(snapshot, moduleEntry));
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        return 0L;
    }

    public int readInt(long address) {
        Memory mem = new Memory(4);
        if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, new Pointer(address), mem, 4, null)) {
            return mem.getInt(0);
        }
        return 0;
    }

    public boolean writeInt(long address, int value) {
        Memory mem = new Memory(4);
        mem.setInt(0, value);
        return Kernel32.INSTANCE.WriteProcessMemory(processHandle, new Pointer(address), mem, 4, null);
    }

    public float readFloat(long address) {
        Memory mem = new Memory(4);
        if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, new Pointer(address), mem, 4, null)) {
            return mem.getFloat(0);
        }
        return 0.0f;
    }

    public String readString(long address, int maxLength) {
        Memory mem = new Memory(maxLength * 2L);
        if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, new Pointer(address), mem, (int) mem.size(), null)) {
            return mem.getWideString(0);
        }
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
        if (this.processHandle != null) {
            Kernel32.INSTANCE.CloseHandle(this.processHandle);
        }
    }
}
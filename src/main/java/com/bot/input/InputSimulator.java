package com.bot.input;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.win32.StdCallLibrary;

public class InputSimulator {

    public interface User32Ext extends StdCallLibrary {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class);
        boolean PostMessageA(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam);
        int MapVirtualKeyA(int code, int mapType);
    }

    private static final int WM_KEYDOWN = 0x0100;
    private static final int WM_KEYUP = 0x0101;

    public void sendKey(String windowName, int keyCode, int durationMs) {
        HWND hwnd = User32.INSTANCE.FindWindow(null, windowName);
        if (hwnd == null) return;

        int scanCode = User32Ext.INSTANCE.MapVirtualKeyA(keyCode, 0);
        LPARAM lDown = new LPARAM(1 | (scanCode << 16));
        LPARAM lUp = new LPARAM(1 | (scanCode << 16) | (1 << 30) | (1 << 31));

        User32Ext.INSTANCE.PostMessageA(hwnd, WM_KEYDOWN, new WPARAM(keyCode), lDown);

        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        User32Ext.INSTANCE.PostMessageA(hwnd, WM_KEYUP, new WPARAM(keyCode), lUp);
    }

    public static class Keys {
        public static final int VK_W = 0x57;
        public static final int VK_A = 0x41;
        public static final int VK_S = 0x53;
        public static final int VK_D = 0x44;
        public static final int VK_TAB = 0x09;
        public static final int VK_F1 = 0x70;
    }
}
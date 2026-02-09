package com.bot.input;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.win32.StdCallLibrary;

public class InputSimulator {
    
    // Custom interface allowing PostMessage
    public interface User32Ext extends StdCallLibrary {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class);
        boolean PostMessageA(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam);
        int SendMessageA(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam);
    }

    private static final int WM_KEYDOWN = 0x0100;
    private static final int WM_KEYUP = 0x0101;

    public void sendKey(String windowName, int keyCode, int durationMs) {
        HWND hwnd = User32.INSTANCE.FindWindow(null, windowName);
        if (hwnd == null) return;

        WPARAM wKey = new WPARAM(keyCode);
        LPARAM lDown = new LPARAM(0x00000001); // Standard down
        LPARAM lUp = new LPARAM(0xC0000001);   // Standard up

        User32Ext.INSTANCE.PostMessageA(hwnd, WM_KEYDOWN, wKey, lDown);
        
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        User32Ext.INSTANCE.PostMessageA(hwnd, WM_KEYUP, wKey, lUp);
    }
    
    // Helper constants for keys
    public static class Keys {
        public static final int VK_W = 0x57;
        public static final int VK_A = 0x41;
        public static final int VK_S = 0x53;
        public static final int VK_D = 0x44;
        public static final int VK_SPACE = 0x20;
        public static final int VK_TAB = 0x09;
        public static final int VK_F1 = 0x70;
    }
}

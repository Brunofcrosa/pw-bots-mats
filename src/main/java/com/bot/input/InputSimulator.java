package com.bot.input;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

public class InputSimulator {

    public interface User32Ext extends StdCallLibrary {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class);
        boolean PostMessageA(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam);
        boolean SendMessageA(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam);
        int  MapVirtualKeyA(int code, int mapType);
        int  SendInput(int nInputs, Memory pInputs, int cbSize);
        void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);
        boolean GetWindowRect(HWND hWnd, RECT rect);
        boolean GetClientRect(HWND hWnd, RECT rect);
        boolean SetForegroundWindow(HWND hWnd);
        boolean SetCursorPos(int x, int y);
        HWND  GetForegroundWindow();
        boolean ShowWindow(HWND hWnd, int nCmdShow);
        boolean IsWindow(HWND hWnd);
        boolean IsWindowVisible(HWND hWnd);
        
        boolean EnumWindows(EnumWindowsProc lpEnumFunc, Pointer lParam);
        
        boolean AttachThreadInput(int idAttach, int idAttachTo, boolean fAttach);
    }

    
    public interface EnumWindowsProc extends StdCallLibrary.StdCallCallback {
        boolean callback(HWND hwnd, Pointer lParam);
    }

    
    private HWND cachedHwnd = null;
    
    private int gamePid = 0;
    private volatile int enumSearchPid = 0;
    private volatile HWND enumFoundVisible = null;
    private volatile HWND enumFoundAny = null;
    
    private final EnumWindowsProc enumCallback = (hwnd, data) -> {
        IntByReference winPid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, winPid);
        if (winPid.getValue() == enumSearchPid) {
            
            if (User32Ext.INSTANCE.IsWindowVisible(hwnd)) {
                enumFoundVisible = hwnd;
                return false; 
            }
            if (enumFoundAny == null) enumFoundAny = hwnd;
        }
        return true;
    };

    
    public void setGameHwnd(HWND hwnd) { this.cachedHwnd = hwnd; }
    
    public void setGamePid(int pid) { this.gamePid = pid; }

    
    private HWND findWindowByPid(int pid) {
        enumSearchPid = pid;
        enumFoundVisible = null;
        enumFoundAny = null;
        User32Ext.INSTANCE.EnumWindows(enumCallback, null);
        return (enumFoundVisible != null) ? enumFoundVisible : enumFoundAny;
    }

    private static final int WM_KEYDOWN       = 0x0100;
    private static final int WM_KEYUP         = 0x0101;
    private static final int WM_MOUSEMOVE     = 0x0200;
    private static final int WM_LBUTTONDOWN   = 0x0201;
    private static final int WM_LBUTTONUP     = 0x0202;
    private static final int WM_RBUTTONDOWN   = 0x0204;
    private static final int WM_RBUTTONUP     = 0x0205;
    private static final int KEYEVENTF_KEYUP  = 0x0002;

    
    private static final int INPUT_SIZE = 40;
    private static final int INPUT_KEYBOARD = 1;

    
    public void sendKey(String windowName, int keyCode, int durationMs) {
        HWND hwnd = User32.INSTANCE.FindWindow(null, windowName);
        if (hwnd == null) return;
        int scanCode = User32Ext.INSTANCE.MapVirtualKeyA(keyCode, 0);
        LPARAM lDown = new LPARAM(1 | (scanCode << 16));
        LPARAM lUp   = new LPARAM(1 | (scanCode << 16) | (1 << 30) | (1 << 31));
        User32Ext.INSTANCE.PostMessageA(hwnd, WM_KEYDOWN, new WPARAM(keyCode), lDown);
        try { Thread.sleep(durationMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        User32Ext.INSTANCE.PostMessageA(hwnd, WM_KEYUP, new WPARAM(keyCode), lUp);
    }

    
    public void sendKeySync(String windowName, int keyCode, int durationMs) {
        HWND hwnd = User32.INSTANCE.FindWindow(null, windowName);
        if (hwnd == null) return;
        int scanCode = User32Ext.INSTANCE.MapVirtualKeyA(keyCode, 0);
        LPARAM lDown = new LPARAM(1 | (scanCode << 16));
        LPARAM lUp   = new LPARAM(1 | (scanCode << 16) | (1 << 30) | (1 << 31));
        User32Ext.INSTANCE.SendMessageA(hwnd, WM_KEYDOWN, new WPARAM(keyCode), lDown);
        try { Thread.sleep(durationMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        User32Ext.INSTANCE.SendMessageA(hwnd, WM_KEYUP, new WPARAM(keyCode), lUp);
    }

    
    public void sendKeyInput(int keyCode, int durationMs) {
        int scanCode = User32Ext.INSTANCE.MapVirtualKeyA(keyCode, 0);

        
        Memory down = new Memory(INPUT_SIZE);
        down.clear();
        down.setInt(0, INPUT_KEYBOARD);
        
        down.setShort(8, (short) keyCode);   
        down.setShort(10, (short) scanCode); 
        down.setInt(12, 0);                  
        User32Ext.INSTANCE.SendInput(1, down, INPUT_SIZE);

        try { Thread.sleep(durationMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        
        Memory up = new Memory(INPUT_SIZE);
        up.clear();
        up.setInt(0, INPUT_KEYBOARD);
        up.setShort(8, (short) keyCode);
        up.setShort(10, (short) scanCode);
        up.setInt(12, KEYEVENTF_KEYUP);
        User32Ext.INSTANCE.SendInput(1, up, INPUT_SIZE);
    }

    
    public boolean bringToForeground(String windowName) {
        HWND hwnd = resolveGameHwnd(windowName);

        if (hwnd == null) {
            
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            hwnd = resolveGameHwnd(windowName);
        }

        if (hwnd == null) {
            System.out.println("[INPUT] bringToForeground: janela nao encontrada PID=" + gamePid + " titulo='" + windowName + "'");
            return false;
        }

        
        HWND fg = User32Ext.INSTANCE.GetForegroundWindow();
        if (fg != null && fg.equals(hwnd)) return true;

        
        
        int myThread = Kernel32.INSTANCE.GetCurrentThreadId();
        int fgThread = 0;
        if (fg != null) {
            fgThread = User32.INSTANCE.GetWindowThreadProcessId(fg, null);
        }
        boolean attached = false;
        if (fgThread != 0 && fgThread != myThread) {
            attached = User32Ext.INSTANCE.AttachThreadInput(myThread, fgThread, true);
        }

        
        User32Ext.INSTANCE.ShowWindow(hwnd, 9);
        boolean ok = User32Ext.INSTANCE.SetForegroundWindow(hwnd);

        if (attached) {
            User32Ext.INSTANCE.AttachThreadInput(myThread, fgThread, false);
        }

        try { Thread.sleep(60); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return ok;
    }

    
    private HWND resolveGameHwnd(String windowName) {
        
        if (cachedHwnd != null && User32Ext.INSTANCE.IsWindow(cachedHwnd)) {
            return cachedHwnd;
        }

        
        if (gamePid != 0) {
            HWND hwnd = findWindowByPid(gamePid);
            if (hwnd != null) {
                cachedHwnd = hwnd; 
                System.out.println("[INPUT] bringToForeground: janela encontrada por PID=" + gamePid);
                return hwnd;
            }
        }

        
        HWND hwnd = User32.INSTANCE.FindWindow(null, windowName);
        if (hwnd != null) {
            cachedHwnd = hwnd;
            System.out.println("[INPUT] bringToForeground: janela encontrada por titulo");
            return hwnd;
        }
        return null;
    }

    
    public void sendKeyEvent(int keyCode, int durationMs) {
        int scanCode = User32Ext.INSTANCE.MapVirtualKeyA(keyCode, 0);
        User32Ext.INSTANCE.keybd_event((byte) keyCode, (byte) scanCode, 0, 0);
        try { Thread.sleep(durationMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        User32Ext.INSTANCE.keybd_event((byte) keyCode, (byte) scanCode, KEYEVENTF_KEYUP, 0);
    }

    
    public void sendKeyAll(String windowName, int keyCode, int durationMs) {
        sendKey(windowName, keyCode, durationMs);
        sendKeySync(windowName, keyCode, durationMs);
    }

    
    public void clickAt(String windowName, int x, int y) {
        HWND hwnd = User32.INSTANCE.FindWindow(null, windowName);
        if (hwnd == null) return;
        long coord = ((long)(y & 0xFFFF) << 16) | (x & 0xFFFF);
        User32Ext.INSTANCE.PostMessageA(hwnd, WM_LBUTTONDOWN, new WPARAM(1), new LPARAM(coord));
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        User32Ext.INSTANCE.PostMessageA(hwnd, WM_LBUTTONUP,   new WPARAM(0), new LPARAM(coord));
    }

    
    public void postClickAtClientCenter(String windowName, int offsetX, int offsetY, boolean rightClick) {
        HWND hwnd = User32.INSTANCE.FindWindow(null, windowName);
        if (hwnd == null) {
            System.out.println("[INPUT] postClick: janela nao encontrada: " + windowName);
            return;
        }
        RECT cr = new RECT();
        User32Ext.INSTANCE.GetClientRect(hwnd, cr);
        int cx = (cr.right - cr.left) / 2 + offsetX;
        int cy = (cr.bottom - cr.top)  / 2 + offsetY;
        long coord = ((long)(cy & 0xFFFF) << 16) | (cx & 0xFFFF);

        
        User32Ext.INSTANCE.PostMessageA(hwnd, WM_MOUSEMOVE, new WPARAM(0), new LPARAM(coord));
        try { Thread.sleep(40); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        if (rightClick) {
            User32Ext.INSTANCE.PostMessageA(hwnd, WM_RBUTTONDOWN, new WPARAM(2), new LPARAM(coord));
            try { Thread.sleep(80); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            User32Ext.INSTANCE.PostMessageA(hwnd, WM_RBUTTONUP, new WPARAM(0), new LPARAM(coord));
        } else {
            User32Ext.INSTANCE.PostMessageA(hwnd, WM_LBUTTONDOWN, new WPARAM(1), new LPARAM(coord));
            try { Thread.sleep(80); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            User32Ext.INSTANCE.PostMessageA(hwnd, WM_LBUTTONUP, new WPARAM(0), new LPARAM(coord));
        }
        System.out.println(String.format("[INPUT] postClick client(%d,%d) off=(%d,%d) %s",
                cx, cy, offsetX, offsetY, rightClick ? "RIGHT" : "LEFT"));
    }

    
    public boolean clickAtScreenPos(String windowName, int screenX, int screenY) {
        HWND hwnd = User32.INSTANCE.FindWindow(null, windowName);
        if (hwnd == null) { System.out.println("[INPUT] janela nao encontrada: " + windowName); return false; }

        User32Ext.INSTANCE.SetForegroundWindow(hwnd);
        try { Thread.sleep(80); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        User32Ext.INSTANCE.SetCursorPos(screenX, screenY);
        try { Thread.sleep(40); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        
        final int MOUSEEVENTF_LEFTDOWN  = 0x0002;
        final int MOUSEEVENTF_LEFTUP    = 0x0004;
        final int MOUSEEVENTF_MOVE      = 0x0001;
        final int MOUSEEVENTF_ABSOLUTE  = 0x8000;
        final int INPUT_MOUSE = 0;
        final int INPUT_SIZE  = 40;

        
        int screenW = com.sun.jna.platform.win32.User32.INSTANCE.GetSystemMetrics(0);
        int screenH = com.sun.jna.platform.win32.User32.INSTANCE.GetSystemMetrics(1);
        int absX = (int)((long)screenX * 65535 / Math.max(1, screenW - 1));
        int absY = (int)((long)screenY * 65535 / Math.max(1, screenH - 1));

        Memory down = buildMouseInput(INPUT_SIZE, absX, absY, MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTDOWN);
        Memory up   = buildMouseInput(INPUT_SIZE, absX, absY, MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTUP);

        User32Ext.INSTANCE.SendInput(1, down, INPUT_SIZE);
        try { Thread.sleep(60); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        User32Ext.INSTANCE.SendInput(1, up, INPUT_SIZE);

        System.out.println(String.format("[INPUT] clickAtScreen(%d,%d) absInput=(%d,%d)", screenX, screenY, absX, absY));
        return true;
    }

    private Memory buildMouseInput(int size, int x, int y, int flags) {
        Memory m = new Memory(size);
        m.clear();
        m.setInt(0, 0);  
        
        m.setInt(8,  x);     
        m.setInt(12, y);     
        m.setInt(16, 0);     
        m.setInt(20, flags); 
        m.setInt(24, 0);     
        return m;
    }

    
    public int[] clickAtWindowCenter(String windowName) {
        HWND hwnd = User32.INSTANCE.FindWindow(null, windowName);
        if (hwnd == null) return null;
        RECT r = new RECT();
        User32Ext.INSTANCE.GetWindowRect(hwnd, r);
        int cx = r.left + (r.right  - r.left) / 2;
        int cy = r.top  + (r.bottom - r.top)  / 2;
        clickAtScreenPos(windowName, cx, cy);
        return new int[]{cx, cy};
    }

    
    public int[] getWindowRect(String windowName) {
        HWND hwnd = User32.INSTANCE.FindWindow(null, windowName);
        if (hwnd == null) return null;
        RECT r = new RECT();
        User32Ext.INSTANCE.GetWindowRect(hwnd, r);
        return new int[]{r.left, r.top, r.right, r.bottom};
    }

    
    public void clickAtWindowOffset(String windowName, int offsetX, int offsetY) {
        HWND hwnd = User32.INSTANCE.FindWindow(null, windowName);
        if (hwnd == null) return;
        RECT r = new RECT();
        User32Ext.INSTANCE.GetWindowRect(hwnd, r);
        int cx = r.left + (r.right  - r.left) / 2 + offsetX;
        int cy = r.top  + (r.bottom - r.top)  / 2 + offsetY;
        clickAtScreenPos(windowName, cx, cy);
    }

    public static class Keys {
        public static final int VK_W      = 0x57;
        public static final int VK_A      = 0x41;  
        public static final int VK_S      = 0x53;
        public static final int VK_D      = 0x44;  
        public static final int VK_LEFT   = 0x25;  
        public static final int VK_RIGHT  = 0x27;  
        public static final int VK_UP     = 0x26;
        public static final int VK_DOWN   = 0x28;
        public static final int VK_F      = 0x46;
        public static final int VK_T      = 0x54;
        public static final int VK_RETURN = 0x0D;
        public static final int VK_SPACE  = 0x20;
        public static final int VK_TAB    = 0x09;
        public static final int VK_F1     = 0x70;
    }
}

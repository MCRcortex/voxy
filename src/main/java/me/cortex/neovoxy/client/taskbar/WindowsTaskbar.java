package me.cortex.neovoxy.client.taskbar;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.COMInvoker;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.PointerByReference;
import org.lwjgl.glfw.GLFWNativeWin32;

public class WindowsTaskbar extends COMInvoker implements Taskbar.ITaskbar {
    private final WinDef.HWND hwnd;
    WindowsTaskbar(long windowId) {
        var itaskbar3res = new PointerByReference();

        if (W32Errors.FAILED(Ole32.INSTANCE.CoCreateInstance(new Guid.GUID("56FDF344-FD6D-11d0-958A-006097C9A090"),
                null,
                WTypes.CLSCTX_SERVER,
                new Guid.GUID("EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF"),
                itaskbar3res))) {
            throw new IllegalStateException("Failed to create ITaskbar3");
        }

        this.setPointer(itaskbar3res.getValue());
        this.hwnd = new WinDef.HWND(new Pointer(GLFWNativeWin32.glfwGetWin32Window(windowId)));

        this.invokeNative(3); // HrInit
    }

    private void invokeNative(int ventry, Object... objects) {
        Object[] args = new Object[objects.length+1];
        args[0] = this.getPointer();
        System.arraycopy(objects, 0, args, 1, objects.length);
        if (W32Errors.FAILED((WinNT.HRESULT) this._invokeNativeObject(ventry, args, WinNT.HRESULT.class))) {
            throw new IllegalStateException("Failed to invoke vtable: " + ventry);
        }
    }

    public void close() {
        this.invokeNative(10, this.hwnd, 0); // SetProgressState TBPF_NOPROGRESS (0x00000000)
        this.invokeNative(2); // Release
        this.setPointer(null);
    }

    @Override
    public void setIsNone() {
        this.invokeNative(10, this.hwnd, 0); // SetProgressState TBPF_NOPROGRESS (0x00000000)
    }

    @Override
    public void setProgress(long count, long outOf) {
        this.invokeNative(9, this.hwnd, count, outOf); // SetProgressValue
    }

    @Override
    public void setIsPaused() {
        this.invokeNative(10, this.hwnd, 8); // SetProgressState TBPF_PAUSED (0x00000008)
    }

    @Override
    public void setIsProgression() {
        this.invokeNative(10, this.hwnd, 2); // SetProgressState TBPF_NORMAL (0x00000002)
    }

    @Override
    public void setIsError() {
        this.invokeNative(10, this.hwnd, 2); // SetProgressState TBPF_ERROR (0x00000004)
    }
}

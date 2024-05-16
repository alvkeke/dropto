package cn.alvkeke.dropto.service;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

public class CoreServiceConnection implements ServiceConnection {

    private CoreService.CoreSrvBinder binder = null;
    private CoreService service;
    private final Task.ResultListener listener;
    private Bundle bundleAfterConnected;

    public CoreServiceConnection(Task.ResultListener listener) {
        this.listener = listener;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        binder = (CoreService.CoreSrvBinder) iBinder;
        service = binder.getService();
        if (listener != null)
            service.addTaskListener(listener);
        execOnServiceConnected(componentName, bundleAfterConnected);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        if (binder == null) return;
        if (listener != null)
            service.delTaskListener(listener);
        execOnServiceDisconnected();
        service = null;
        binder = null;
    }

    public void setBundleAfterConnected(Bundle bundleAfterConnected) {
        this.bundleAfterConnected = bundleAfterConnected;
    }

    public void execOnServiceConnected(ComponentName componentName, Bundle bundleAfterConnected) { }

    public void execOnServiceDisconnected() { }

    @SuppressWarnings("unused")
    public CoreService.CoreSrvBinder getBinder() {
        return binder;
    }

    public CoreService getService() {
        return service;
    }

}

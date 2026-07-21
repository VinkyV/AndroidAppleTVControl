package dev.vinkyv.tvcontrol;

import android.util.Log;

import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

public class CompanionListener implements ServiceListener {
    @Override
    public void serviceAdded(ServiceEvent event) {
        Log.i("CompanionListener","Service added: " + event.getInfo());
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
        Log.i("CompanionListener","Service removed: " + event.getInfo());
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        Log.i("CompanionListener","Service resolved: " + event.getInfo());
    }
}

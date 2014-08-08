package nz.co.cloudstore.serialbot.transport;

/*
 * SerialBot: adds Airconsole support to ConnectBot app
 * Copyright 2013 Cloudstore Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.util.HashMap;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

public class AirconsoleDiscovery implements ServiceListener {

    private static final String TAG = "ConnectBot.AirconsoleDiscovery";

    private static AirconsoleDevice defaultDevice = new AirconsoleDevice(Airconsole.DEFAULT_HOSTNAME, AirconsoleIP.DEFAULT_IP, AirconsoleIP.DEFAULT_PORT, true);
    private static AirconsoleDiscovery instance = null;
    private static final String SERVICE = "_telnetcpcd._tcp.local.";

    public static synchronized AirconsoleDiscovery getInstance() {
        if (instance == null) {
            instance = new AirconsoleDiscovery();
        }
        return instance;
    }

    private AirconsoleDevice currentDevice = defaultDevice;
    private HashMap<String,AirconsoleDevice> deviceMap = new HashMap<String,AirconsoleDevice>();
    private JmDNS jmdns = null;

    public void startDiscovery() {
        if (jmdns == null) {
            try {
                Log.d(TAG, "Starting service discovery");
                jmdns = JmDNS.create();
                jmdns.addServiceListener(SERVICE, this);
            } catch (IOException ioe) {
                Log.e(TAG, "Error starting service discovery", ioe);
            }
        }
    }

    public void stopDiscovery() {
        if (jmdns != null) {
            Log.d(TAG, "Stopping service discovery");
            jmdns.removeServiceListener(SERVICE, this);
            try {
                jmdns.close();
            } catch (IOException ioe) {
                Log.d(TAG, "Error stopping service discovery", ioe);
            }
            jmdns = null;
        }
    }

    public AirconsoleDevice getDeviceNamed(String name) {
        AirconsoleDevice result = currentDevice;
        if ((name != null) && (name.length() > 0)) {
            name = name.toLowerCase();
            if (deviceMap.containsKey(name)) {
                result = deviceMap.get(name);
            }
        }
        return result;
    }

    public void serviceAdded(ServiceEvent event) {
        Log.d(TAG, "MDNS service added: " + event.getName());
        if (jmdns != null) {
            long timeout = 1000;
            jmdns.requestServiceInfo(event.getType(), event.getName(), timeout);
        }
    }
    public void serviceRemoved(ServiceEvent event) {
        Log.d(TAG, "MDNS service removed: "+event.getName());
        String name = event.getName();
        if ((name != null) && (name.length() > 0)) {
            name = name.toLowerCase();
            if (deviceMap.containsKey(name)) {
                deviceMap.remove(name);
            }
        }
    }
    public void serviceResolved(ServiceEvent event) {
        Log.d(TAG, "MDNS service resolved: "+event.getName());
        ServiceInfo info = event.getInfo();
        if (info != null && info.getInet4Addresses() != null) {
            Inet4Address[] addresses = info.getInet4Addresses();
            if (addresses.length > 0) {
                String ip = addresses[0].getHostAddress();
                int port = info.getPort();
                Log.d(TAG, "Airconsole discovered on: "+ip+":"+port);
                String name = event.getName();
                if ((name == null) || (name.length() == 0)) {
                    name = Airconsole.DEFAULT_HOSTNAME;
                }
                name = name.toLowerCase();

                AirconsoleDevice device = new AirconsoleDevice(name, ip, port, false);
                // Set this to the most recently discovered device
                currentDevice = device;
                // and put into the hashmap for discovering named devices
                deviceMap.put(name, device);
            }
        }
    }


    public static class AirconsoleDevice {
        public String name;
        public String ip;
        public int port;
        public boolean defaultDevice;

        public AirconsoleDevice(String name, String ip, int port, boolean defaultDevice) {
            this.name = name;
            this.ip = ip;
            this.port = port;
            this.defaultDevice = defaultDevice;
        }
    }
}

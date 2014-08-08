package nz.co.cloudstore.serialbot.transport;

/*
 * SerialBot: adds Airconsole support to ConnectBot app
 * Copyright 2014 Cloudstore Ltd
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

import android.net.Uri;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AirconsoleIP extends Airconsole {

    private static final String TAG = "ConnectBot.AirconsoleIP";

    public static final String DEFAULT_IP = "192.168.10.1";
    public static final int DEFAULT_PORT = 3696;

    private static final String PROTOCOL = "serial-wifi";
    private static final String ALT_PROTOCOL = "serial";

    static final Pattern ipv4Pattern;

    private Socket netSocket;

    static {
        ipv4Pattern = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

        // Start discovery on background thread
        Runnable r = new Runnable() {
            public void run() {
                AirconsoleDiscovery.getInstance().startDiscovery();
            }
        };
        (new Thread(r)).start();
    }


    public AirconsoleIP() {
        super(PROTOCOL);
    }

    protected void connectImpl() throws IOException {
        String hostname = host.getHostname();
        if (hostname == null) {
            hostname = "";
        }

        // We need to determine IP and port to connect to - logic is below
        String ip;
        int port;


        // Is the hostname already an IP?
        Matcher matcher = ipv4Pattern.matcher(hostname);
        if (matcher.matches()) {
            // The user has entered an IP address manually - we should honour it (and the port)
            ip = hostname;
            port = host.getPort();
        } else {
            // The hostname looks like it is actually a hostname
            // Do an MDNS lookup (if enabled) - discovery will fallback to default device if mdns fails or name is not found
            AirconsoleDiscovery.AirconsoleDevice device = AirconsoleDiscovery.getInstance().getDeviceNamed(hostname);
            ip = device.ip;
            port = device.port;
        }

        // Check the user hasn't entered an invalid port...
        if ((port < 1) || (port > 65535)) {
            port = DEFAULT_PORT;
        }

        Log.d(TAG, "Connecting to " + ip + " on port " + port);


        netSocket = new Socket(ip, port);
    }

    protected Closeable getSocket() {
        return netSocket;
    }

    protected InputStream getInputStream() throws IOException {
        return netSocket.getInputStream();
    }

    protected OutputStream getOutputStream() throws IOException {
        return netSocket.getOutputStream();
    }

    public static String getProtocolName() {
        return PROTOCOL;
    }

    public static String getAltProtocolName() {
        return ALT_PROTOCOL;
    }

    @Override
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    @Override
    public boolean usesNetwork() {
        return true;
    }

    public static Uri getUri(String input) {
        return Airconsole.getUri(PROTOCOL, input);
    }
}

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


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.net.Uri;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AirconsoleBT extends Airconsole {

    private static final String TAG = "ConnectBot.AirconsoleBT";

    private static final String PROTOCOL = "serial-bt";

    //private static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";
    private static final String TELNETCPCD_UUID = "0F1E4B13-16D2-4396-BF26-000000000000";


    private BluetoothSocket btSocket;

    static final Pattern bdaddrPattern;

    static {
        bdaddrPattern = Pattern.compile("^([0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f])$", Pattern.CASE_INSENSITIVE);
    }

    public AirconsoleBT() {
        super(PROTOCOL);
    }

    public static String getProtocolName() {
        return PROTOCOL;
    }

    private List<BluetoothDevice> getBestDevices(Set<BluetoothDevice> devices) {
        // Note: currently hostname is not set when parsing the URI input by the user - this is for future enhancement
        // Hostname could be a.) a bluetooth address, b.) a bluetooth device name, c.) anything else (e.g. "airconsole")
        String hostname = host.getHostname();
        if (hostname == null) {
            hostname = "";
        }

        List<BluetoothDevice> result = new ArrayList<BluetoothDevice>();

        String address = null;

        Matcher matcher = bdaddrPattern.matcher(hostname);
        if (matcher.matches()) {
            address = hostname;
        }

        List<BluetoothDevice> potentials = new ArrayList<BluetoothDevice>();

        for (BluetoothDevice device : devices) {
            if (address != null) {
                // User has specified a BD address - only accept that device
                if (device.getAddress().equals(address)) {
                    result.add(device);
                    break;
                }
            } else {
                String deviceName = device.getName();
                if (deviceName != null) {
                    if (deviceName.equals(hostname)) {
                        // The device's name matches the hostname exactly - this is the only one to use
                        result.add(device);
                        break;
                    }
                    if (deviceName.toLowerCase().startsWith("airconsole")) {
                        // Build up a list of potential Airconsole devices to connect to
                        potentials.add(device);
                    }
                }
            }
        }
        if (result.size() == 0) {
            result = potentials;
        }
        return result;
    }


    protected void connectImpl() throws IOException {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            if (adapter.isEnabled()) {

                Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
                List<BluetoothDevice> devices = null;
                if (pairedDevices != null) {
                    devices = getBestDevices(pairedDevices);
                }

                if ((devices != null) && (devices.size() > 0)) {
                    IOException ex = null;
                    for (BluetoothDevice device : devices) {
                        Log.d(TAG, "Checking device: " + device.getName() + " (" + device.getAddress() + ")");

                        bridge.outputLine("Connecting to "+device.getName()+" ("+device.getAddress()+")");
                        // We create an insecure socket as there is no MITM protection with 'just-works' SSP
                        try {
                            ex = null;
                            btSocket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(TELNETCPCD_UUID));
                            btSocket.connect();
                            if (btSocket.isConnected()) {
                                // Leave loop - we are connected!
                                bridge.outputLine("Connected");
                                break;
                            } else {
                                btSocket.close();
                                btSocket = null;
                            }
                        } catch (IOException ioe) {
                            bridge.outputLine("Failed to connect to "+device.getName()+" ("+device.getAddress()+")");
                            ex = ioe;
                        }
                    }
                    if (ex != null) {
                        throw ex;
                    }
                } else {
                    Log.d(TAG, "Cannot find suitable paired bluetooth device");
                    bridge.outputLine("Could not find a paired Airconsole. Visit Bluetooth settings to pair");
                    throw new IOException("Cannot find suitable paired bluetooth device");
                }
            } else {
                Log.d(TAG, "Bluetooth is disabled");
                bridge.outputLine("Bluetooth is disabled on this device");
                throw new IOException("Bluetooth is disabled on this device");
            }
        } else {
            Log.d(TAG, "Device does not support bluetooth");
            bridge.outputLine("This device does not support bluetooth");
            throw new IOException("This device does not support bluetooth");
        }
    }
    protected Closeable getSocket() {
        return btSocket;
    }

    protected InputStream getInputStream() throws IOException {
        return btSocket.getInputStream();
    }

    protected OutputStream getOutputStream() throws IOException {
        return btSocket.getOutputStream();
    }

    @Override
    public int getDefaultPort() {
        return 0;
    }

    @Override
    public boolean usesNetwork() {
        return false;
    }

    public static Uri getUri(String input) {
        return Airconsole.getUri(PROTOCOL, input);
    }

}

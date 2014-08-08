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

package nz.co.cloudstore.serialbot.transport;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import de.mud.telnet.TelnetProtocolHandler;
import nz.co.cloudstore.serialbot.R;
import nz.co.cloudstore.serialbot.bean.HostBean;
import nz.co.cloudstore.serialbot.service.TerminalBridge;
import nz.co.cloudstore.serialbot.service.TerminalManager;
import nz.co.cloudstore.serialbot.util.HostDatabase;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Airconsole extends AbsTransport {
    private static final String TAG = "ConnectBot.Airconsole";

    private final static byte TELNET_IAC  = (byte)255;
    private final static byte TELNET_SB  = (byte)250;
    private final static byte TELNET_SE  = (byte)240;

    private final static byte TELOPT_COM_PORT_OPTION = 44;

    private final static byte TELOPT_COM_PORT_SET_BAUDRATE = 1;
    private final static byte TELOPT_COM_PORT_SET_DATASIZE = 2;
    private final static byte TELOPT_COM_PORT_SET_PARITY   = 3;
    private final static byte TELOPT_COM_PORT_SET_STOPSIZE = 4;
    private final static byte TELOPT_COM_PORT_SET_CONTROL  = 5;

    private final static byte TELOPT_COM_PORT_DATASIZE7 = 7;
    private final static byte TELOPT_COM_PORT_DATASIZE8 = 8;

    private final static byte TELOPT_COM_PORT_PARITY_NONE = 1;
    private final static byte TELOPT_COM_PORT_PARITY_ODD  = 2;
    private final static byte TELOPT_COM_PORT_PARITY_EVEN = 3;

    private final static byte TELOPT_COM_PORT_STOP_1 = 1;
    private final static byte TELOPT_COM_PORT_STOP_2 = 2;

    private final static byte TELOPT_COM_PORT_FLOW_NONE      = 1;
    private final static byte TELOPT_COM_PORT_FLOW_SOFTWARE  = 2;
    private final static byte TELOPT_COM_PORT_FLOW_HARDWARE  = 3;

    private final static byte TELOPT_COM_PORT_BREAK_ON  = 5;
    private final static byte TELOPT_COM_PORT_BREAK_OFF = 6;

    public static final String DEFAULT_HOSTNAME = "airconsole";

    private TelnetProtocolHandler handler;
    private Closeable socket;

    private InputStream is;
    private OutputStream os;
    private int width;
    private int height;

    private boolean connected = false;
    private String protocol;

    static final Pattern inputPattern;

    static {
        inputPattern = Pattern.compile("^([0-9]+)(-(\\d)([N|O|E])(\\d)(SW|HW)?)?$", Pattern.CASE_INSENSITIVE);
    }

    public Airconsole(String protocol) {
        this.protocol = protocol;

        handler = new TelnetProtocolHandler() {
            /** get the current terminal type */
            @Override
            public String getTerminalType() {
                return getEmulation();
            }

            /** get the current window size */
            @Override
            public int[] getWindowSize() {
                return new int[] { width, height };
            }

            /** notify about local echo */
            @Override
            public void setLocalEcho(boolean echo) {
				/* EMPTY */
            }

            /** write data to our back end */
            @Override
            public void write(byte[] b) throws IOException {
                if (os != null)
                    os.write(b);
            }

            /** sent on IAC EOR (prompt terminator for remote access systems). */
            @Override
            public void notifyEndOfRecord() {
            }

            @Override
            protected String getCharsetName() {
                Charset charset = bridge.getCharset();
                if (charset != null)
                    return charset.name();
                else
                    return "";
            }
        };
    }

    /**
     * @param host
     * @param bridge
     * @param manager
     */
    public Airconsole(HostBean host, TerminalBridge bridge, TerminalManager manager) {
        super(host, bridge, manager);
    }

    private void sendByteSubOption(byte option, byte suboption, byte value) {
        byte[] b = new byte[8];
        int n;
        b[0] = TELNET_IAC;
        b[1] = TELNET_SB;
        b[2] = option;
        b[3] = suboption;
        n = 4;
        if (value == (byte)255) {
            b[n++] = TELNET_IAC;
        }
        b[n++] = value;
        b[n++] = TELNET_IAC;
        b[n++] = TELNET_SE;
        try {
            Log.d(TAG, "Sending option="+option+", suboption="+suboption+", value="+value);
            write(b, 0, n);
        } catch (IOException e) {
            Log.e(TAG, "Couldn't send option="+option+", suboption="+suboption, e);
        }
    }

    private void sendIntSubOption(byte option, byte suboption, int value) {
        byte[] b = new byte[14];
        byte[] num = new byte[4];

        num[0] = (byte)((value & 0xFF000000) >> 24);
        num[1] = (byte)((value & 0x00FF0000) >> 16);
        num[2] = (byte)((value & 0x0000FF00) >> 8);
        num[3] = (byte)(value & 0x000000FF);

        int n;
        b[0] = TELNET_IAC;
        b[1] = TELNET_SB;
        b[2] = option;
        b[3] = suboption;
        n = 4;
        for (int x=0; x < 4; x++) {
            if (num[x] == (byte)255) {
                b[n++] = TELNET_IAC;
            }
            b[n++] = num[x];
        }
        b[n++] = TELNET_IAC;
        b[n++] = TELNET_SE;
        try {
            Log.d(TAG, "Sending option="+option+", suboption="+suboption+", value="+value);
            write(b, 0, n);
        } catch (IOException e) {
            Log.e(TAG, "Couldn't send option="+option+", suboption="+suboption, e);
        }
    }

    protected abstract void connectImpl() throws IOException;
    protected abstract Closeable getSocket();
    protected abstract InputStream getInputStream() throws IOException;
    protected abstract OutputStream getOutputStream() throws IOException;

    @Override
    public void connect() {

        try {
            connectImpl();
            socket = getSocket();
            is = getInputStream();
            os = getOutputStream();
            connected = true;

            bridge.onConnected();

            // We really should have some form of telnet negotiation before sending these...!
            sendIntSubOption(TELOPT_COM_PORT_OPTION, TELOPT_COM_PORT_SET_BAUDRATE, host.getBaudrate());
            sendByteSubOption(TELOPT_COM_PORT_OPTION, TELOPT_COM_PORT_SET_DATASIZE, rfc2217DataBits(host.getDatabits()));
            sendByteSubOption(TELOPT_COM_PORT_OPTION, TELOPT_COM_PORT_SET_PARITY, rfc2217Parity(host.getParity()));
            sendByteSubOption(TELOPT_COM_PORT_OPTION, TELOPT_COM_PORT_SET_STOPSIZE, rfc2217StopBits(host.getStopbits()));
            sendByteSubOption(TELOPT_COM_PORT_OPTION, TELOPT_COM_PORT_SET_CONTROL, rfc2217FlowControl(host.getFlowcontrol()));

        } catch (UnknownHostException e) {
            Log.d(TAG, "IO Exception connecting to host", e);
        } catch (IOException e) {
            Log.d(TAG, "IO Exception connecting to host", e);
        }
    }

    @Override
    public void close() {
        connected = false;
        if (socket != null)
            try {
                socket.close();
                socket = null;
            } catch (IOException e) {
                Log.d(TAG, "Error closing telnet socket.", e);
            }
    }

    @Override
    public void flush() throws IOException {
        os.flush();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isSessionOpen() {
        return connected;
    }

    @Override
    public int read(byte[] buffer, int start, int len) throws IOException {
		/* process all already read bytes */
        int n = 0;

        do {
            n = handler.negotiate(buffer, start);
            if (n > 0)
                return n;
        } while (n == 0);

        while (n <= 0) {
            do {
                n = handler.negotiate(buffer, start);
                if (n > 0)
                    return n;
            } while (n == 0);
            n = is.read(buffer, start, len);
            if (n < 0) {
                bridge.dispatchDisconnect(false);
                throw new IOException("Remote end closed connection.");
            }

            handler.inputfeed(buffer, start, n);
            n = handler.negotiate(buffer, start);
        }
        return n;
    }

    private void write(byte[] buffer, int offset, int count) throws IOException {
        try {
            if (os != null) {
                os.write(buffer, offset, count);
            }
        } catch (IOException e) {
            bridge.dispatchDisconnect(false);
        }
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        try {
            if (os != null)
                os.write(buffer);
        } catch (IOException e) {
            bridge.dispatchDisconnect(false);
        }
    }

    @Override
    public void write(int c) throws IOException {
        try {
            if (os != null)
                os.write(c);
        } catch (IOException e) {
            bridge.dispatchDisconnect(false);
        }
    }

    @Override
    public void setDimensions(int columns, int rows, int width, int height) {
        this.width = columns;
        this.height = rows;
        try {
            handler.setWindowSize(columns, rows);
        } catch (IOException e) {
            Log.e(TAG, "Couldn't resize remote terminal", e);
        }
    }

    @Override
    public void setSerialParameters(int baudrate, int databits, String parity, int stopbits, String flowcontrol) {
        Log.d(TAG, "Updating serial settings, baudrate="+baudrate+" "+databits+parity+stopbits+" flow="+flowcontrol);
        if (host.getBaudrate() != baudrate) {
            host.setBaudrate(baudrate);
            sendIntSubOption(TELOPT_COM_PORT_OPTION, TELOPT_COM_PORT_SET_BAUDRATE, baudrate);
        }
        if (host.getDatabits() != databits) {
            host.setDatabits(databits);
            sendByteSubOption(TELOPT_COM_PORT_OPTION, TELOPT_COM_PORT_SET_DATASIZE, rfc2217DataBits(databits));
        }
        if ((parity != null) && !host.getParity().equals(parity)) {
            host.setParity(parity);
            sendByteSubOption(TELOPT_COM_PORT_OPTION, TELOPT_COM_PORT_SET_PARITY, rfc2217Parity(parity));
        }
        if (host.getStopbits() != stopbits) {
            host.setStopbits(stopbits);
            sendByteSubOption(TELOPT_COM_PORT_OPTION, TELOPT_COM_PORT_SET_STOPSIZE, rfc2217StopBits(stopbits));
        }
        if ((flowcontrol != null) && !host.getFlowcontrol().equals(flowcontrol)) {
            host.setFlowcontrol(flowcontrol);
            sendByteSubOption(TELOPT_COM_PORT_OPTION, TELOPT_COM_PORT_SET_CONTROL, rfc2217FlowControl(flowcontrol));
        }
    }

    @Override
    public String getDefaultNickname(String username, String hostname, int port) {
        return "Airconsole";
    }

    protected static boolean isValidBaudRate(int baud) {
        switch (baud) {
            case 1200:
            case 2400:
            case 4800:
            case 9600:
            case 19200:
            case 38400:
            case 57600:
            case 115200:
                return true;
            default:
                return false;
        }
    }

    protected static byte rfc2217DataBits(int databits) {
        switch (databits) {
            case 8:
            default:
                return TELOPT_COM_PORT_DATASIZE8;
            case 7:
                return TELOPT_COM_PORT_DATASIZE7;
        }
    }

    protected static boolean isValidDataBits(int databits) {
        switch (databits) {
            case 7:
            case 8:
                return true;
            default:
                return false;
        }
    }

    protected static byte rfc2217Parity(String parity) {
        if (parity != null) {
            if (parity.equals(HostDatabase.PARITY_ODD)) {
                return TELOPT_COM_PORT_PARITY_ODD;
            } else if (parity.equals(HostDatabase.PARITY_EVEN)) {
                return TELOPT_COM_PORT_PARITY_EVEN;
            }
        }
        return TELOPT_COM_PORT_PARITY_NONE;
    }

    protected static boolean isValidParity(String parity) {
        return ((parity != null) &&
                (parity.equals(HostDatabase.PARITY_NONE) ||
                 parity.equals(HostDatabase.PARITY_ODD) ||
                 parity.equals(HostDatabase.PARITY_EVEN)));
    }

    protected static byte rfc2217StopBits(int stopbits) {
        switch (stopbits) {
            case 1:
            default:
                return TELOPT_COM_PORT_STOP_1;
            case 2:
                return TELOPT_COM_PORT_STOP_2;
        }
    }

    protected static boolean isValidStopbits(int stopbits) {
        switch (stopbits) {
            case 1:
            case 2:
            // don't support 1.5 stopbits..
                return true;
            default:
                return false;
        }
    }

    protected static byte rfc2217FlowControl(String flowcontrol) {
        if (flowcontrol != null) {
            if (flowcontrol.equals(HostDatabase.FLOWCONTROL_SOFTWARE)) {
                return TELOPT_COM_PORT_FLOW_SOFTWARE;
            } else if (flowcontrol.equals(HostDatabase.FLOWCONTROL_HARDWARE)) {
                return TELOPT_COM_PORT_FLOW_HARDWARE;
            }
        }
        return TELOPT_COM_PORT_FLOW_NONE;
    }

    protected static boolean isValidFlowControl(String flowcontrol) {
        return ((flowcontrol != null) &&
                (flowcontrol.equals(HostDatabase.FLOWCONTROL_NONE) ||
                 flowcontrol.equals(HostDatabase.FLOWCONTROL_SOFTWARE) ||
                 flowcontrol.equals(HostDatabase.FLOWCONTROL_HARDWARE)));
    }


    protected static int parseInt(String s) {
        int result = 0;
        if (s != null) {
            try {
                result = Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                // ignored
            }
        }
        return result;
    }

    protected SerialParameters parseURI(Uri uri) {
        SerialParameters result = new SerialParameters();
        result.baudrate = parseInt(uri.getQueryParameter("baudrate"));
        if (!isValidBaudRate(result.baudrate)) {
            result.baudrate = HostDatabase.DEFAULT_BAUDRATE;
        }
        result.databits = parseInt(uri.getQueryParameter("databits"));
        if (!isValidDataBits(result.databits)) {
            result.databits= HostDatabase.DEFAULT_DATABITS;
        }

        result.parity = uri.getQueryParameter("parity");
        if (!isValidParity(result.parity)) {
            result.parity= HostDatabase.DEFAULT_PARITY;
        }
        result.stopbits = parseInt(uri.getQueryParameter("stopbits"));
        if (!isValidStopbits(result.stopbits)) {
            result.stopbits= HostDatabase.DEFAULT_STOPBITS;
        }
        result.flowcontrol = uri.getQueryParameter("flowcontrol");
        if (!isValidFlowControl(result.flowcontrol)) {
            result.flowcontrol= HostDatabase.DEFAULT_FLOW_CONTROL;
        }
        return result;
    }

    protected static String getNicknameFromBaud(int baudrate) {
        return baudrate + " Baud";
    }

    @Override
    public HostBean createHost(Uri uri) {
        SerialParameters p = parseURI(uri);

        HostBean host = new HostBean();

        host.setProtocol(protocol);
        host.setHostname(uri.getHost());

        int port = uri.getPort();
        if ((port < 1) || (port > 65535)) {
            port = getDefaultPort();
        }
        host.setPort(port);

        String nickname = uri.getFragment();
        if (nickname == null || nickname.length() == 0) {
            nickname = getNicknameFromBaud(p.baudrate);
        }
        host.setNickname(nickname);

        host.setBaudrate(p.baudrate);
        host.setDatabits(p.databits);
        host.setParity(p.parity);
        host.setStopbits(p.stopbits);
        host.setFlowcontrol(p.flowcontrol);

        return host;
    }

    @Override
    public void getSelectionArgs(Uri uri, Map<String, String> selection) {
        // This is for SQL look-ups
        selection.put(HostDatabase.FIELD_HOST_PROTOCOL, protocol);
        selection.put(HostDatabase.FIELD_HOST_NICKNAME, uri.getFragment());
    }

    @Override
    public boolean canSendBreak() {
        return true;
    }

    public void sendBreak() {
        sendByteSubOption(TELOPT_COM_PORT_OPTION, TELOPT_COM_PORT_SET_CONTROL, TELOPT_COM_PORT_BREAK_ON);
        sendByteSubOption(TELOPT_COM_PORT_OPTION, TELOPT_COM_PORT_SET_CONTROL, TELOPT_COM_PORT_BREAK_OFF);
    }

    protected class SerialParameters {
        public int baudrate = HostDatabase.DEFAULT_BAUDRATE;
        public int databits = HostDatabase.DEFAULT_DATABITS;
        public String parity = HostDatabase.DEFAULT_PARITY;
        public int stopbits = HostDatabase.DEFAULT_STOPBITS;
        public String flowcontrol = HostDatabase.DEFAULT_FLOW_CONTROL;
    }

    public static Uri getUri(String protocol, String input) {
        // User will input in format "baudrate-8N1SW"
        // piece following - is optional, if not supplied then baudrate is optional

        Matcher matcher = inputPattern.matcher(input);

        StringBuilder sb = new StringBuilder();

        // URI format
        // serial://airconsole/?baudrate=9600&databits=8#nickname

        int baudrate = HostDatabase.DEFAULT_BAUDRATE;
        int databits = HostDatabase.DEFAULT_DATABITS;
        String parity = HostDatabase.DEFAULT_PARITY;
        int stopbits = HostDatabase.DEFAULT_STOPBITS;
        String flowcontrol = HostDatabase.DEFAULT_FLOW_CONTROL;

        if (matcher.matches()) {
            // There is a match of some sort
            baudrate = parseInt(matcher.group(1));
            if (!isValidBaudRate(baudrate)) {
                baudrate = HostDatabase.DEFAULT_BAUDRATE;
            }
            databits = parseInt(matcher.group(3));
            if (!isValidDataBits(databits)) {
                databits = HostDatabase.DEFAULT_DATABITS;
            }
            parity = matcher.group(4);
            if (!isValidParity(parity)) {
                parity = HostDatabase.DEFAULT_PARITY;
            }
            stopbits = parseInt(matcher.group(5));
            if (!isValidStopbits(stopbits)) {
                stopbits = HostDatabase.DEFAULT_STOPBITS;
            }
            flowcontrol = matcher.group(6);
            if (!isValidFlowControl(flowcontrol)) {
                flowcontrol = HostDatabase.DEFAULT_FLOW_CONTROL;
            }
        } else {
            return null;
        }

        String nickname = getNicknameFromBaud(baudrate);

        // Error here...
        sb.append(protocol)
                .append("://") .append(DEFAULT_HOSTNAME) .append("/")
                .append("?baudrate=").append(baudrate)
                .append("&databits=").append(databits)
                .append("&parity=").append(parity)
                .append("&stopbits=").append(stopbits)
                .append("&flowcontrol=").append(flowcontrol)
                .append("#")
                .append(nickname);

        Uri uri = Uri.parse(sb.toString());

        return uri;
    }

    public static String getFormatHint(Context context) {
        return String.format("%s-8N1", context.getString(R.string.format_baudrate));
    }

}

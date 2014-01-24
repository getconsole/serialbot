/**
 *
 */
package nz.co.cloudstore.nfc;

import java.io.UnsupportedEncodingException;

import nz.co.cloudstore.serialbot.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * @author Hamid
 */
public class WiFiUtils {

    private static String DEFAULT_SSID = String.format("\"%s\"", "AirConsole-XX");
    private static String DEFAULT_PRE_SHARED_KEY = String.format("\"%s\"", "12345678");
    private static String DEFAULT_PROTOCOL_NAME = "";
    private static String DEFAULT_PROTOCOL = "";

    private String ssid = DEFAULT_SSID;
    private String preSharedKey = DEFAULT_PRE_SHARED_KEY;
    private String protocolName = DEFAULT_PROTOCOL_NAME;
    private String protocol = DEFAULT_PROTOCOL;
    private boolean nfc;

    public static final int DIALOG_PROGRESS = 5237;
    private ProgressDialog mProgressDialog;
    public static Context mContext;
    private static String MIME_TEXT_PLAIN = "text/plain";
    public static final String TAG = "Nfc-SerialBot";
    protected Handler mHandler;
    boolean isAirConsoleON = false;
    boolean isRuning = false;
    NdefReaderTask.WifiReceiver mReceiver;
    WifiManager wifiManager;
    protected Handler mlocalHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    dismissDialog(DIALOG_PROGRESS);
                    displayErrorDialog(mContext, "Error", msg.obj.toString());
                    return;
                case 2:
                    Log.d(TAG, "State:: " + msg.obj.toString());
                    if (mProgressDialog != null && mProgressDialog.isShowing())
                        mProgressDialog.setMessage((String) msg.obj);
                    return;
            }

        }

    };


    public WiFiUtils(Context context, Handler updateHandler) {
        mContext = context;
        mHandler = updateHandler;
        MIME_TEXT_PLAIN = "application/" + context.getPackageName();
        wifiManager = (WifiManager) mContext.getSystemService(mContext.WIFI_SERVICE);
    }

    public boolean isNfc() {
        return nfc;
    }

    public void setNfc(boolean nfc) {
        this.nfc = nfc;
    }

    public String getProtocolName() {
        return protocolName;
    }

    public void setProtocolName(String protocolName) {
        this.protocolName = protocolName;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * @param Progress dialog id
     */
    private void dismissDialog(int id) {
        try {
            if (mProgressDialog != null && mProgressDialog.isShowing())
                mProgressDialog.dismiss();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * @param Progress dialog id
     */
    private void showDialog(int id) {
        try {
            switch (id) {
                case DIALOG_PROGRESS:
                    if (mProgressDialog == null)
                        mProgressDialog = new ProgressDialog(mContext);
                    if (mProgressDialog != null) {
                        mProgressDialog.setMessage("Please Wait");
                        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                        mProgressDialog.setCancelable(false);
                        if (!mProgressDialog.isShowing())
                            mProgressDialog.show();
                    }

            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    //Handling NFC intent
    public void handleIntent(Intent intent) {
        try {
            if (isRuning)
                return;
            isRuning = true;
            String action = intent.getAction();
            if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {

                String type = intent.getType();
                if (MIME_TEXT_PLAIN.equals(type)) {

                    Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                    // Reading Tag data
                    new NdefReaderTask().execute(tag);

                } else {
                    Message mMessage = new Message();
                    mMessage.what = 1;
                    mMessage.obj = "Unsupported Encoding";
                    mlocalHandler.sendMessage(mMessage);

                    Log.d(TAG, "Wrong mime type: " + type);
                    isRuning = false;
                }
            } else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

                // In case we would still use the Tech Discovered Intent
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                String[] techList = tag.getTechList();
                String searchedTech = Ndef.class.getName();

                for (String tech : techList) {
                    if (searchedTech.equals(tech)) {
                        new NdefReaderTask().execute(tag);
                        break;
                    }
                }
            }
        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    /**
     * Background task for reading the data. Do not block the UI thread while reading.
     */
    private class NdefReaderTask extends AsyncTask<Tag, Integer, String> {

        //Connecting to WIFI
        public void connectToWiFi() {
            try {
                Log.i(TAG, "* connectToWiFi");

                if (!wifiManager.isWifiEnabled()) {
                    publishProgress(2);
                    wifiManager.setWifiEnabled(true);
                    int i = 0;
                    while (!wifiManager.isWifiEnabled()) {

                        Log.d("WifiPreference", "enableNetwork returned " + wifiManager.isWifiEnabled() + "-- " + i);
                        i++;
                        if (i > 1000)
                            break;
                    }
                }

                wifiManager.startScan();
                publishProgress(15);
                int i = 0;
                while (wifiManager.getScanResults() == null) {
                    Log.d(TAG, "Scanning WiFi-- " + i);
                    i++;
                    if (i > 1000)
                        break;
                }

                if (!wifiManager.isWifiEnabled()) {
                    Message mMessage = new Message();
                    mMessage.what = 1;
                    mMessage.obj = "Could Not Enabled the WiFi";
                    mlocalHandler.sendMessage(mMessage);
                    Log.d(TAG, "*** Could Not Enabled the WiFi");
                }

                WifiConfiguration wifiConfiguration = new WifiConfiguration();
                String networkSSID = ssid;
                String networkPass = preSharedKey; //"87654321";//
                Log.d(TAG, "# password " + networkPass);
                for (ScanResult result : wifiManager.getScanResults()) {
                    if (result.SSID.equals(networkSSID)) {
                        isAirConsoleON = true;
                        String securityMode = getScanResultSecurity(result);

                        if (securityMode.equalsIgnoreCase("OPEN")) {

                            wifiConfiguration.SSID = "\"" + networkSSID + "\"";
                            wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                            int res = wifiManager.addNetwork(wifiConfiguration);
                            Log.d(TAG, "# add Network returned " + res);

                            boolean b = wifiManager.enableNetwork(res, true);
                            Log.d(TAG, "# enableNetwork returned " + b);

                            wifiManager.setWifiEnabled(true);

                        } else if (securityMode.equalsIgnoreCase("WEP")) {

                            wifiConfiguration.SSID = "\"" + networkSSID + "\"";
                            wifiConfiguration.wepKeys[0] = "\"" + networkPass + "\"";
                            wifiConfiguration.wepTxKeyIndex = 0;
                            wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                            wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                            int res = wifiManager.addNetwork(wifiConfiguration);
                            Log.d(TAG, "### 1 ### add Network returned " + res);

                            boolean b = wifiManager.enableNetwork(res, true);
                            Log.d(TAG, "# enableNetwork returned " + b);

                            wifiManager.setWifiEnabled(true);

                        }


                        publishProgress(3);
                        wifiConfiguration.SSID = "\"" + networkSSID + "\"";
                        wifiConfiguration.preSharedKey = "\"" + networkPass + "\"";
                        wifiConfiguration.hiddenSSID = true;
                        wifiConfiguration.priority = 100;
                        wifiConfiguration.status = WifiConfiguration.Status.ENABLED;
                        wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                        wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                        wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                        wifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                        wifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                        wifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                        wifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.WPA);

                        int res = wifiManager.addNetwork(wifiConfiguration);
                        Log.d(TAG, "### 2 ### add Network returned " + res);

                        wifiManager.enableNetwork(res, true);

                        wifiManager.reconnect();

                        IntentFilter mIntentFilter = new IntentFilter();
                        mIntentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
                        mReceiver = new WifiReceiver() {

                        };
                        mContext.registerReceiver(mReceiver, mIntentFilter);

                        boolean changeHappen = wifiManager.saveConfiguration();

                        if (res != -1 && changeHappen) {
                            Log.d(TAG, "### Change happen");
                        } else {
                            Message mMessage = new Message();
                            mMessage.what = 1;
                            mMessage.obj = "Could Not add Network";
                            mlocalHandler.sendMessage(mMessage);
                            Log.d(TAG, "*** Change NOT happen");
                        }

                        wifiManager.setWifiEnabled(true);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /* (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
            showDialog(DIALOG_PROGRESS);

        }

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];
            publishProgress(0);
            Ndef ndef = Ndef.get(tag);
            Message mMessage;
            if (ndef == null) {
                mMessage = new Message();
                mMessage.what = 1;
                mMessage.obj = "NDEF is not supported by this Tag.";
                mlocalHandler.sendMessage(mMessage);

                return null;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();

            NdefRecord[] records = ndefMessage.getRecords();
            for (NdefRecord ndefRecord : records) {
                if (ndefRecord.getTnf() == NdefRecord.TNF_MIME_MEDIA /*&& Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)*/) {
                    try {
                        return readText(ndefRecord);
                    } catch (UnsupportedEncodingException e) {
                        mMessage = new Message();
                        mMessage.what = 1;
                        mMessage.obj = "Unsupported Encoding";
                        mlocalHandler.sendMessage(mMessage);
                        Log.e(TAG, "Unsupported Encoding", e);
                    }
                }
            }

            return null;
        }

        private String readText(NdefRecord record) throws UnsupportedEncodingException {

            byte[] payload = record.getPayload();
            // Get the Text Encoding
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
            // Get the Text
            return new String(payload, 0, payload.length, textEncoding);
        }

        /* (non-Javadoc)
         * @see android.os.AsyncTask#onProgressUpdate(Progress[])
         */
        @Override
        protected void onProgressUpdate(Integer... values) {
            String msg = "Please Wait";
            switch (values[0]) {
                case 0:
                    msg = "Reading Tag";
                    break;
                case 1:
                    msg = "Checking WiFi";
                    break;
                case 2:
                    msg = "Enabling WiFi";
                    break;
                case 3:
                    msg = "Trying to Join " + ssid; //
                    break;
                case 4:
                    msg = "Trying to Join " + ssid;//ASSOCIATED
                    break;
                case 5:
                    msg = "Trying to Join " + ssid;//ASSOCIATING
                    break;
                case 6:
                    msg = "Authenticating...";
                    break;
                case 7:
                    msg = "Connected to " + ssid;//
                    break;
                case 8:
                    msg = "Disconnected";
                    break;
                case 9:
                    msg = "Trying to Join " + ssid;////DORMANT
                    break;
                case 10:
                    msg = "Trying to Join " + ssid;////FOUR_WAY_HANDSHAKE
                    break;
                case 11:
                    msg = "Trying to Join " + ssid;////GROUP_HANDSHAKE
                    break;
                case 12:
                    msg = "Trying to Join " + ssid;////INACTIVE
                    break;
                case 13:
                    msg = "Trying to Join " + ssid;////INTERFACE_DISABLED
                    break;
                case 14:
                    msg = "Trying to Join " + ssid;////INVALID
                    break;
                case 15:
                    msg = "Scanning WiFi";
                    break;
                case 16:
                    msg = "Trying to Join " + ssid;///UNINITIALIZED
                    break;
                case 17:
                    msg = "Unknown Error";
                    break;
                case 18:
                    msg = "Authentication error..";//ERROR_AUTHENTICATING
                    break;

            }

            Log.d(TAG, "State:: " + msg);
            if (mProgressDialog != null && mProgressDialog.isShowing())
                mProgressDialog.setMessage(msg);
        }


        @Override
        protected void onPostExecute(String result) {
            try {
                if (result != null) {

                    String[] ar = result.split("[_]");
                    if (ar.length == 4) {
                        ssid = ar[0];
                        preSharedKey = ar[1];
                        protocolName = ar[2];
                        protocol = ar[3];

                        publishProgress(1);
                     /*List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
						for( WifiConfiguration i : list ) {
							wifiManager.removeNetwork(i.networkId);
						}*/
                        connectToWiFi();


                    } else {
                        Message mMessage = new Message();
                        mMessage.what = 1;
                        mMessage.obj = "Unsupported Data" + result;
                        mlocalHandler.sendMessage(mMessage);
                        Log.e(TAG, "Unsupported Data" + result);
                    }

                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            try {
                                if (isAirConsoleON) {
                                    if ((wifiManager.getConnectionInfo().getSSID().equals(ssid) || wifiManager.getConnectionInfo().getSSID().equals("\"" + ssid + "\"")) && wifiManager.getConnectionInfo().getSupplicantState().equals(SupplicantState.COMPLETED)) {
                                        setNfc(true);
                                        mHandler.sendEmptyMessage(2);
                                        dismissDialog(DIALOG_PROGRESS);
                                        Log.i("SupplicantState", "Connected");
                                    } else {
                                        Message mMessage = new Message();
                                        mMessage.what = 1;
                                        mMessage.obj = "Could not connect to '" + ssid + "'. Password could be wrong";
                                        mlocalHandler.sendMessage(mMessage);
                                        Log.i("SupplicantState", "Disconnected");
                                    }


                                } else {
                                    Message mMessage = new Message();
                                    mMessage.what = 1;
                                    mMessage.obj = ssid + " switched off or still booting";
                                    mlocalHandler.sendMessage(mMessage);
                                }
                                if (mReceiver != null)
                                    mContext.unregisterReceiver(mReceiver);
                            } catch (Exception e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                    }, 3000);
                }
                //if Tag is Empty
                else {
                    Message mMessage = new Message();
                    mMessage.what = 1;
                    mMessage.obj = "Tag is Empty";
                    mlocalHandler.sendMessage(mMessage);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isRuning = false;
                Log.d(TAG, "isRuning:: " + isRuning);
            }

        }

        //Checking WIFI States
        class WifiReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context c, Intent intent) {
                try {

                    String action = intent.getAction();
                    if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                        Log.d("WifiReceiver", ">>>>SUPPLICANT_STATE_CHANGED_ACTION<<<<<<");
                        SupplicantState supl_state = ((SupplicantState) intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE));
                        switch (supl_state) {
                            case ASSOCIATED:
                                publishProgress(4);
                                Log.i("SupplicantState", "ASSOCIATED");
                                break;
                            case ASSOCIATING:
                                publishProgress(5);
                                Log.i("SupplicantState", "ASSOCIATING");
                                break;
                            case AUTHENTICATING:
                                publishProgress(6);
                                Log.i("SupplicantState", "Authenticating...");
                                break;
                            case COMPLETED:
                                publishProgress(7);
                                Log.i("SupplicantState", "Connected");
                                break;
                            case DISCONNECTED:
                                publishProgress(8);
                                Log.i("SupplicantState", "Disconnected");
                                break;
                            case DORMANT:
                                publishProgress(9);
                                Log.i("SupplicantState", "DORMANT");
                                break;
                            case FOUR_WAY_HANDSHAKE:
                                publishProgress(10);
                                Log.i("SupplicantState", "FOUR_WAY_HANDSHAKE");
                                break;
                            case GROUP_HANDSHAKE:
                                publishProgress(11);
                                Log.i("SupplicantState", "GROUP_HANDSHAKE");
                                break;
                            case INACTIVE:
                                publishProgress(12);
                                Log.i("SupplicantState", "INACTIVE");
                                break;
                            case INTERFACE_DISABLED:
                                publishProgress(13);
                                Log.i("SupplicantState", "INTERFACE_DISABLED");
                                break;
                            case INVALID:
                                publishProgress(14);
                                Log.i("SupplicantState", "INVALID");
                                break;
                            case SCANNING:
                                publishProgress(15);
                                Log.i("SupplicantState", "SCANNING");
                                break;
                            case UNINITIALIZED:
                                publishProgress(16);
                                Log.i("SupplicantState", "UNINITIALIZED");
                                break;
                            default:
                                publishProgress(17);
                                Log.i("SupplicantState", "Unknown");
                                break;

                        }
                        int supl_error = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1);
                        if (supl_error == WifiManager.ERROR_AUTHENTICATING) {
                            publishProgress(18);
                            Log.i("ERROR_AUTHENTICATING", "ERROR_AUTHENTICATING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }
    }


    public String getScanResultSecurity(ScanResult scanResult) {
        try {
            Log.i(TAG, "* getScanResultSecurity");

            final String cap = scanResult.capabilities;
            final String[] securityModes = {"WEP", "PSK", "EAP"};

            for (int i = securityModes.length - 1; i >= 0; i--) {
                if (cap.contains(securityModes[i])) {
                    return securityModes[i];
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "OPEN";
    }


    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param adapter  The {@link NfcAdapter} used for the foreground dispatch.
     */
    public void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);
        try {
            filters[0].addDataType(MIME_TEXT_PLAIN);
        } catch (MalformedMimeTypeException e) {
            throw new RuntimeException("Check your mime type.");
        }

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    /**
     * @param activity The corresponding {@link BaseActivity} requesting to stop the foreground dispatch.
     * @param adapter  The {@link NfcAdapter} used for the foreground dispatch.
     */
    public void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }


    public static void displayErrorDialog(Context context, String title, String message) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setIcon(R.drawable.error_icon)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();
    }
}

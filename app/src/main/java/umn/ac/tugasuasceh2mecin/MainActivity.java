package umn.ac.tugasuasceh2mecin;

import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.*;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import umn.ac.tugasuasceh2mecin.parser.NdefMessageParser;
import umn.ac.tugasuasceh2mecin.record.ParsedNdefRecord;

public class MainActivity extends AppCompatActivity {

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private TextView text;
    private Button btnWrite;
    private ArrayList<byte[]> savedData;
    private Tag cardTag;

    final private String target_server = "http://10.20.10.115";
    private boolean writeMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        text = findViewById(R.id.text);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        btnWrite = findViewById(R.id.btnWrite);
        savedData = new ArrayList<>();

        btnWrite.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                if(cardTag == null || savedData.isEmpty()) {
                    Toast.makeText(MainActivity.this, "No NFC Tag Detected!", Toast.LENGTH_LONG).show();
                } else {
                    if(!writeMode){
                        writeMode = true;
                        Toast.makeText(MainActivity.this, "Switched to Write Mode", Toast.LENGTH_LONG).show();
                        btnWrite.setText("Switch to Read Mode");
                    } else {
                        writeMode = false;
                        Toast.makeText(MainActivity.this, "Switched to Read Mode", Toast.LENGTH_LONG).show();
                        btnWrite.setText("Switch to Write Mode");
                    }
                }
            }
        });

        if (nfcAdapter == null) {
            Toast.makeText(this, "No NFC", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, this.getClass())
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (nfcAdapter != null) {
            if (!nfcAdapter.isEnabled())
                showWirelessSettings();

            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        nfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        resolveIntent(intent);
    }

    private void resolveIntent(Intent intent) {
        String action = intent.getAction();

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            if(!writeMode){
                savedData = new ArrayList<>();
                Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                NdefMessage[] msgs;

                if (rawMsgs != null) {
                    msgs = new NdefMessage[rawMsgs.length];

                    for (int i = 0; i < rawMsgs.length; i++) {
                        msgs[i] = (NdefMessage) rawMsgs[i];
                        rawMsgs.toString();
                    }
                } else {
                    byte[] empty = new byte[0];
                    byte[] id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
                    cardTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

                    byte[] payload = dumpTagData().getBytes();
                    NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, id, payload);
                    NdefMessage msg = new NdefMessage(new NdefRecord[] {record});
                    msgs = new NdefMessage[] {msg};
                }

                displayMsgs(msgs);
            }
            else{
                Tag temp = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                MifareClassic mifareTag = MifareClassic.get(temp);

                try{
                    mifareTag.connect();
                    Log.d("TEST", String.valueOf(mifareTag.isConnected()));
                    if (mifareTag.isConnected()){
                        Log.d("TEST", "CONNECTED, WRITING ...");
                        int s_len = mifareTag.getSectorCount();

                        for(int i = 0; i < s_len; i++){
                            boolean isAuthenticated = false;

                            if(mifareTag.authenticateSectorWithKeyA(i, MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY) ||
                                    mifareTag.authenticateSectorWithKeyA(i, MifareClassic.KEY_DEFAULT) ||
                                    mifareTag.authenticateSectorWithKeyA(i, MifareClassic.KEY_NFC_FORUM)) {
                                isAuthenticated = true;
                                Log.d("TEST", "Authenticated with Key A");
                            }
                            else if(mifareTag.authenticateSectorWithKeyB(i, MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY) ||
                                    mifareTag.authenticateSectorWithKeyB(i, MifareClassic.KEY_DEFAULT) ||
                                    mifareTag.authenticateSectorWithKeyB(i,MifareClassic.KEY_NFC_FORUM)){
                                isAuthenticated = true;
                                Log.d("TEST", "Authenticated with Key B");
                            }
                            else
                                Log.d("TAG", "Authorization denied ");

                            if(isAuthenticated) {
                                //TODO: Investigate This (Probably Hardware Issue, try other phones
                                int block_index = mifareTag.sectorToBlock(i);

                                Log.d("TEST", savedData.get(block_index).toString());

                                mifareTag.writeBlock(block_index, savedData.get(block_index));
//                                mifareTag.transceive(savedData.get(block_index));
                                Log.d("DATA", "written " + savedData.get(block_index).toString() + " to block " + block_index);
                            }
                        }
                    }

                    mifareTag.close();
                    Log.d("TEST", "WRITING COMPLETE");
                } catch (IOException e){
                    e.printStackTrace();
                } finally {
                    if (mifareTag != null){
                        try{
                            mifareTag.close();
                        } catch (IOException e){
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private void displayMsgs(NdefMessage[] msgs) {
        if (msgs == null || msgs.length == 0)
            return;

        StringBuilder builder = new StringBuilder();
        List<ParsedNdefRecord> records = NdefMessageParser.parse(msgs[0]);
        final int size = records.size();

        for (int i = 0; i < size; i++) {
            ParsedNdefRecord record = records.get(i);
            String str = record.str();
            builder.append(str).append("\n");
        }

        text.setText(builder.toString());
    }

    // Open Settings to allow users to enable NFC
    private void showWirelessSettings() {
        Toast.makeText(this, "You need to enable NFC", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        startActivity(intent);
    }

    private String dumpTagData() {
        StringBuilder sb = new StringBuilder();
        byte[] id = cardTag.getId();

        sb.append("ID (hex): ").append(toHex(id)).append('\n');
        sb.append("ID (dec): ").append(toDec(id)).append('\n');

        String prefix = "android.nfc.tech.";
        sb.append("Technologies: ");
        for (String tech : cardTag.getTechList()) {
            sb.append(tech.substring(prefix.length()));
            sb.append(", ");
        }

        sb.delete(sb.length() - 2, sb.length());

        for (String tech : cardTag.getTechList()) {
            if (tech.equals(MifareClassic.class.getName())) {
                sb.append('\n');
                String type = "Unknown";

                try {
                    MifareClassic mifareTag = MifareClassic.get(cardTag);

                    try{
                        mifareTag.connect();
                        int s_len = mifareTag.getSectorCount();
                        if (mifareTag.isConnected()){

                            List<String> temp = new ArrayList<>();
                            JSONObject holder = new JSONObject();

                            for(int i=0; i < s_len; i++){
                                boolean isAuthenticated = false;

                                if (mifareTag.authenticateSectorWithKeyA(i, MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY) ||
                                        mifareTag.authenticateSectorWithKeyA(i, MifareClassic.KEY_DEFAULT) ||
                                        mifareTag.authenticateSectorWithKeyA(i,MifareClassic.KEY_NFC_FORUM)){
                                    isAuthenticated = true;
                                }
                                else if(mifareTag.authenticateSectorWithKeyB(i, MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY) ||
                                        mifareTag.authenticateSectorWithKeyB(i, MifareClassic.KEY_DEFAULT) ||
                                        mifareTag.authenticateSectorWithKeyB(i,MifareClassic.KEY_NFC_FORUM)){
                                    isAuthenticated = true;
                                }
                                else
                                    Log.d("TAG", "Authorization denied ");

                                if(isAuthenticated) {
                                    int block_index = mifareTag.sectorToBlock(i);

                                    byte[] block = mifareTag.readBlock(block_index);
                                    Log.d("DATA", "Sector : " + i);
                                    Log.d("DATA", toReversedHex(block));

                                    savedData.add(block);

                                    temp.add(toHex(block));
                                }
                            }

                            holder.put("uid", toHex(id));
                            holder.put("sectorCount", mifareTag.getSectorCount());
                            holder.put("blockCount", mifareTag.getBlockCount());
                            holder.put("data", new JSONArray(temp));

                            Log.d("JSON", holder.toString());
                            new sendToServer().execute(holder);
                        }

                        mifareTag.close();
                    } catch (IOException e){
                        e.printStackTrace();
                    } catch(JSONException err) {
                        err.printStackTrace();
                    } finally {
                        if (mifareTag != null){
                            try{
                                mifareTag.close();
                            } catch (IOException e){
                                e.printStackTrace();
                            }
                        }
                    }

                    switch (mifareTag.getType()) {
                        case MifareClassic.TYPE_CLASSIC:
                            type = "Classic";
                            break;
                        case MifareClassic.TYPE_PLUS:
                            type = "Plus";
                            break;
                        case MifareClassic.TYPE_PRO:
                            type = "Pro";
                            break;
                    }
                    sb.append("Mifare Classic type: ");
                    sb.append(type);
                    sb.append('\n');

                    sb.append("Mifare size: ");
                    sb.append(mifareTag.getSize() + " bytes");
                    sb.append('\n');

                    sb.append("Mifare sectors: ");
                    sb.append(mifareTag.getSectorCount());
                    sb.append('\n');

                    sb.append("Mifare blocks: ");
                    sb.append(mifareTag.getBlockCount());
                } catch (Exception e) {
                    sb.append("Mifare classic error: " + e.getMessage());
                }
            }

            if (tech.equals(MifareUltralight.class.getName())) {
                sb.append('\n');
                MifareUltralight mifareUlTag = MifareUltralight.get(cardTag);
                String type = "Unknown";
                switch (mifareUlTag.getType()) {
                    case MifareUltralight.TYPE_ULTRALIGHT:
                        type = "Ultralight";
                        break;
                    case MifareUltralight.TYPE_ULTRALIGHT_C:
                        type = "Ultralight C";
                        break;
                }
                sb.append("Mifare Ultralight type: ");
                sb.append(type);
            }
        }

        return sb.toString();
    }

    class sendToServer extends AsyncTask<JSONObject, String, String>{
        @Override
        protected String doInBackground(JSONObject... jsonObjects) {
            JSONObject payload = jsonObjects[0];

            HttpURLConnection conn = null;
            InputStream is = null;

            try{
                URL url = new URL(target_server);
                conn = (HttpURLConnection) url.openConnection();
                conn.setChunkedStreamingMode(0);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                OutputStream out = conn.getOutputStream();
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));

                bw.write("testing=" + payload.toString());
                bw.flush();
                bw.close();

                out.close();

                conn.connect();

                Log.d("TEST", conn.getResponseCode() + " : " + conn.getResponseMessage());

                if(conn.getResponseCode() == HttpURLConnection.HTTP_OK){
                    Log.d("TEST", "Input Stream Get!");
                    is = conn.getInputStream();
                } else {
                    Log.d("TEST", "ERROR STREAM!");
                    is = conn.getErrorStream();
                }

            } catch (MalformedURLException eURL){
                eURL.printStackTrace();
            } catch (IOException eIO){
                eIO.printStackTrace();
            }

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        is, "UTF-8"), 8);
                StringBuilder sb = new StringBuilder();
                String line = "";
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }
                is.close();
                String response = sb.toString();
            } catch (Exception e) {
                Log.e("Buffer Error", "Error converting result " + e.toString());
            } finally {
                if(conn != null)
                    conn.disconnect();
            }


            return null;
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; --i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
            if (i > 0) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private String toReversedHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; ++i) {
            if (i > 0) {
                sb.append(" ");
            }
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
        }
        return sb.toString();
    }

    private long toDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = 0; i < bytes.length; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

}
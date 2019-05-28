package umn.ac.tugasuasceh2mecin;

import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
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
            Toast.makeText(this, "No NFC Detected, quitting...", Toast.LENGTH_LONG).show();
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

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) ||
            NfcAdapter.ACTION_TECH_DISCOVERED.equals(action) ||
            NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            if(!writeMode){
                savedData = new ArrayList<>();
                Parcelable[] rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                NdefMessage[] messages;

                if (rawMessages != null) {
                    messages = new NdefMessage[rawMessages.length];

                    for (int i = 0; i < rawMessages.length; i++)
                        messages[i] = (NdefMessage) rawMessages[i];
                }
                else {
                    byte[] emptyByte = new byte[0];
                    byte[] cardId = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
                    cardTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

                    byte[] payload = dumpTagData().getBytes();
                    NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, emptyByte, cardId, payload);
                    NdefMessage message = new NdefMessage(new NdefRecord[] {record});
                    messages = new NdefMessage[] {message};
                }

                displayMsgs(messages);
            }
            else{
                Tag temp = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                MifareClassic mifareTag = MifareClassic.get(temp);

                // TODO: Reimplement R&W For All Blocks instead of Sectors
                try{
                    mifareTag.connect();
                    if (mifareTag.isConnected()){
                        int sectorCount = mifareTag.getSectorCount();

                        for(int i = 0; i < sectorCount; i++){
                            int blockPerSector = mifareTag.getBlockCountInSector(i);
                            boolean authOK = false;

                            if( mifareTag.authenticateSectorWithKeyA(i, MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY) ||
                                mifareTag.authenticateSectorWithKeyA(i, MifareClassic.KEY_DEFAULT) ||
                                mifareTag.authenticateSectorWithKeyA(i, MifareClassic.KEY_NFC_FORUM))
                                authOK = true;
                            else if(mifareTag.authenticateSectorWithKeyB(i, MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY) ||
                                    mifareTag.authenticateSectorWithKeyB(i, MifareClassic.KEY_DEFAULT) ||
                                    mifareTag.authenticateSectorWithKeyB(i, MifareClassic.KEY_NFC_FORUM))
                                authOK = true;
                            else
                                Toast.makeText(this, "Authorization Denied! Unable to Write Card", Toast.LENGTH_SHORT).show();

                            if(authOK)
                                for(int x = 0; x < blockPerSector; x++)
                                    mifareTag.writeBlock((i*blockPerSector) + x, savedData.get(i));

                        }
                    }

                    mifareTag.close();
                    Toast.makeText(this, "Writing Complete!", Toast.LENGTH_SHORT).show();
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
                        int sectorCount = mifareTag.getSectorCount();

                        if (mifareTag.isConnected()){
                            List<String> temp = new ArrayList<>();
                            JSONObject holder = new JSONObject();

                            for(int i = 0; i < sectorCount; i++){
                                int blockPerSector = mifareTag.getBlockCountInSector(i);
                                boolean authOK = false;

                                if (mifareTag.authenticateSectorWithKeyA(i, MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY) ||
                                    mifareTag.authenticateSectorWithKeyA(i, MifareClassic.KEY_DEFAULT) ||
                                    mifareTag.authenticateSectorWithKeyA(i, MifareClassic.KEY_NFC_FORUM)){
                                    authOK = true;
                                }
                                else if(mifareTag.authenticateSectorWithKeyB(i, MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY) ||
                                        mifareTag.authenticateSectorWithKeyB(i, MifareClassic.KEY_DEFAULT) ||
                                        mifareTag.authenticateSectorWithKeyB(i, MifareClassic.KEY_NFC_FORUM)){
                                    authOK = true;
                                }
                                else
                                    Toast.makeText(this, "Authorization Denied! Unable to Read Card", Toast.LENGTH_SHORT).show();

                                if(authOK) {
                                    for(int x = 0; x < blockPerSector; x++){
                                        byte[] block = mifareTag.readBlock((i*blockPerSector) + x);
                                        savedData.add(block);
                                        temp.add(toHex(block));
                                    }
                                }
                            }

                            holder.put("uid", toHex(id));
                            holder.put("sectorCount", mifareTag.getSectorCount());
                            holder.put("blockCount", mifareTag.getBlockCount());
                            holder.put("data", new JSONArray(temp));

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

    // Open Settings to allow users to enable NFC
    private void showWirelessSettings() {
        Toast.makeText(this, "You need to enable NFC", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        startActivity(intent);
    }

    class sendToServer extends AsyncTask<JSONObject, String, String>{
        @Override
        protected String doInBackground(JSONObject... jsonObjects) {
            JSONObject payload = jsonObjects[0];
            HttpURLConnection conn  ;

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
            } catch (MalformedURLException eURL){
                eURL.printStackTrace();
            } catch (IOException eIO){
                eIO.printStackTrace();
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
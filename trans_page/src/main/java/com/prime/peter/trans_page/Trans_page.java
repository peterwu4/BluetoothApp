package com.prime.peter.trans_page;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class Trans_page extends AppCompatActivity{
    private Button button_paired ;
    private Button button_disconnect ;
    private Button button_find ;
    private TextView show_data ;
    private ListView event_listView;
    private TextView show_count ;

    private BluetoothAdapter mBluetoothAdapter ;
    private ArrayAdapter<String> deviceName ;
    private ArrayAdapter<String> deviceID ;
    private Set<BluetoothDevice> pairedDevices ;
    private String[] pairedDeviceAddr = new String[10];
    private String choosedID ;
    private BluetoothDevice bluetoothDevice ;

    private BluetoothSocket bluesoccket ; //   becareful
    private InputStream mmInputStream ;
    private OutputStream mmOutputStream ;
    Thread workerThread ;
    volatile boolean stopWorker ;
    private int readBufferPosition ;
    private byte[] readBuffer ;
    private String uid ;
    private int count ;
    private LocationManager locMgr ;
    private String bestProv ;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trans_page);
        getView();
        setListener();
        deviceName = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1);
        deviceID = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1);
        count = 0;
    }
    private void getView(){
        button_paired = (Button)findViewById(R.id.btn_paired);
        button_disconnect=(Button)findViewById(R.id.btn_disconn);
        show_data = (TextView)findViewById(R.id.txtShow);
        event_listView=(ListView)findViewById(R.id.Show_B_List);
        button_find = (Button)findViewById(R.id.btn_conn);
        show_count = (TextView)findViewById(R.id.txt_count);
    }
    private void setListener(){
        button_paired.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                findPBT();
            }
        });
        button_disconnect.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v){
                try {
                    closeBT();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        button_find.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                findPBT();
            }
        });
        event_listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View view , int position, long id){
//              choosedID = deviceID.getItem(position);
                choosedID = (String) parent.getItemAtPosition(position);
                for (BluetoothDevice device : pairedDevices) {
                    if (pairedDeviceAddr[position] == device.getAddress()) {
                        uid = device.getAddress();
                        bluetoothDevice = device ;
                    }
                }
                Toast.makeText(Trans_page.this,"選擇了: "+ choosedID, Toast.LENGTH_SHORT).show();
                Log.e("BluetoothUtil", "選擇了: "+ choosedID);
                try{
                    openBT();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        });
    }

    private void findPBT(){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null){
            show_data.setText("No bluetooth adapter available");
        }
        if(!mBluetoothAdapter.isEnabled()){
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth,1);
            Log.e("BluetoothUtil", "Enable Bluetooth");
        }
        pairedDevices=mBluetoothAdapter.getBondedDevices();
        if (pairedDevices == null) {
            Log.e("BluetoothUtil", "get bondled devices error!");
            return;
        }
        if(pairedDevices.size()>0){
            deviceName.clear();
            int i = 0;
            for(BluetoothDevice device : pairedDevices){
                String str = // "已配對完成的裝置有: " +
                        device.getName() + "\n" +
                                device.getAddress() + "\n";
//                uid = device.getAddress();
//                bluetoothDevice = device ;
                pairedDeviceAddr[i++] = device.getAddress();
                Log.e("BluetoothUtil", str);
                deviceName.add(str);
            }
            event_listView.setAdapter(deviceName);
        }
    }

    private void openBT() throws IOException{
        // #藍牙串口服務
        // SerialPortServiceClass_UUID = '{00001101-0000-1000-8000-00805F9B34FB}'
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        if(bluetoothDevice != null){
//            bluesoccket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
            bluesoccket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);
            bluesoccket.connect();
            mmOutputStream = bluesoccket.getOutputStream();
            mmInputStream = bluesoccket.getInputStream();
            show_data.setText("Bluetooth Opened: " + bluetoothDevice.getName() + " " +
                    bluetoothDevice.getAddress());
            beginListenForData();
        }
    }

    private void beginListenForData(){
        final Handler handler = new Handler();
//        final byte delimiter = 10 ;
        final byte delimiter = 'z';

        stopWorker = false ;
        readBufferPosition = 0 ;
        readBuffer = new byte[1024];
        workerThread = new Thread((Runnable)()->{
            while(!Thread.currentThread().isInterrupted()&&!stopWorker)
            {
                try{
                    int bytesAvailable = mmInputStream.available();
                    if(bytesAvailable>0)
                    {
                        byte[] packetBytes = new byte[bytesAvailable];
                        mmInputStream.read(packetBytes);
                        for(int i = 0;i<bytesAvailable ; i++){
                            byte b = packetBytes[i];
                            if(b==delimiter){
                                byte[] encodedBytes = new byte[readBufferPosition];
                                System.arraycopy(readBuffer,0,encodedBytes,0,encodedBytes.length);
                                final String data = new String(encodedBytes,"US-ASCII");
                                readBufferPosition = 0;
                                count++;
                                handler.post(()->{
                                    String prevString = show_data.getText().toString();
                                    String dataText = String.format("%s\nCF=1,PM2.5=%sug/m3,收到了第%s筆資料",prevString,data,count);
                                    show_data.setText(dataText);
                                });
                            }else
                            {
                                readBuffer[readBufferPosition++]=b;
                            }
                        }
                    }
                } catch (IOException ex){
                    stopWorker = true ;
                }
            }
        });
        workerThread.start();
    }
    private void closeBT() throws  IOException{
        try {
            stopWorker = true;
            mmOutputStream.close();
            mmInputStream.close();
            bluesoccket.close();
            deviceName.clear();
            show_count.setText("");
            show_data.setText("Bluetooth Closed");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

/*
ServiceDiscoveryServerServiceClassID_UUID = '{00001000-0000-1000-8000-00805F9B34FB}'
BrowseGroupDescriptorServiceClassID_UUID = '{00001001-0000-1000-8000-00805F9B34FB}'
PublicBrowseGroupServiceClass_UUID = '{00001002-0000-1000-8000-00805F9B34FB}'
#藍牙串口服務
SerialPortServiceClass_UUID = '{00001101-0000-1000-8000-00805F9B34FB}'
LANAccessUsingPPPServiceClass_UUID = '{00001102-0000-1000-8000-00805F9B34FB}'
#撥號網路服務
DialupNetworkingServiceClass_UUID = '{00001103-0000-1000-8000-00805F9B34FB}'
#資訊同步服務
IrMCSyncServiceClass_UUID = '{00001104-0000-1000-8000-00805F9B34FB}'
SDP_OBEXObjectPushServiceClass_UUID = '{00001105-0000-1000-8000-00805F9B34FB}'
#檔案傳輸服務
OBEXFileTransferServiceClass_UUID = '{00001106-0000-1000-8000-00805F9B34FB}'
IrMCSyncCommandServiceClass_UUID = '{00001107-0000-1000-8000-00805F9B34FB}'
SDP_HeadsetServiceClass_UUID = '{00001108-0000-1000-8000-00805F9B34FB}'
CordlessTelephonyServiceClass_UUID = '{00001109-0000-1000-8000-00805F9B34FB}'
SDP_AudioSourceServiceClass_UUID = '{0000110A-0000-1000-8000-00805F9B34FB}'
SDP_AudioSinkServiceClass_UUID = '{0000110B-0000-1000-8000-00805F9B34FB}'
SDP_AVRemoteControlTargetServiceClass_UUID = '{0000110C-0000-1000-8000-00805F9B34FB}'
SDP_AdvancedAudioDistributionServiceClass_UUID = '{0000110D-0000-1000-8000-00805F9B34FB}'
SDP_AVRemoteControlServiceClass_UUID = '{0000110E-0000-1000-8000-00805F9B34FB}'
VideoConferencingServiceClass_UUID = '{0000110F-0000-1000-8000-00805F9B34FB}'
IntercomServiceClass_UUID = '{00001110-0000-1000-8000-00805F9B34FB}'
#藍牙傳真服務
FaxServiceClass_UUID = '{00001111-0000-1000-8000-00805F9B34FB}'
HeadsetAudioGatewayServiceClass_UUID = '{00001112-0000-1000-8000-00805F9B34FB}'
WAPServiceClass_UUID = '{00001113-0000-1000-8000-00805F9B34FB}'
WAPClientServiceClass_UUID = '{00001114-0000-1000-8000-00805F9B34FB}'
#個人局域網服務
PANUServiceClass_UUID = '{00001115-0000-1000-8000-00805F9B34FB}'
#個人局域網服務
NAPServiceClass_UUID = '{00001116-0000-1000-8000-00805F9B34FB}'
#個人局域網服務
GNServiceClass_UUID = '{00001117-0000-1000-8000-00805F9B34FB}'
DirectPrintingServiceClass_UUID = '{00001118-0000-1000-8000-00805F9B34FB}'
ReferencePrintingServiceClass_UUID = '{00001119-0000-1000-8000-00805F9B34FB}'
ImagingServiceClass_UUID = '{0000111A-0000-1000-8000-00805F9B34FB}'
ImagingResponderServiceClass_UUID = '{0000111B-0000-1000-8000-00805F9B34FB}'
ImagingAutomaticArchiveServiceClass_UUID = '{0000111C-0000-1000-8000-00805F9B34FB}'
ImagingReferenceObjectsServiceClass_UUID = '{0000111D-0000-1000-8000-00805F9B34FB}'
SDP_HandsfreeServiceClass_UUID = '{0000111E-0000-1000-8000-00805F9B34FB}'
HandsfreeAudioGatewayServiceClass_UUID = '{0000111F-0000-1000-8000-00805F9B34FB}'
DirectPrintingReferenceObjectsServiceClass_UUID = '{00001120-0000-1000-8000-00805F9B34FB}'
ReflectedUIServiceClass_UUID = '{00001121-0000-1000-8000-00805F9B34FB}'
BasicPringingServiceClass_UUID = '{00001122-0000-1000-8000-00805F9B34FB}'
PrintingStatusServiceClass_UUID = '{00001123-0000-1000-8000-00805F9B34FB}'
#人機輸入服務
HumanInterfaceDeviceServiceClass_UUID = '{00001124-0000-1000-8000-00805F9B34FB}'
HardcopyCableReplacementServiceClass_UUID = '{00001125-0000-1000-8000-00805F9B34FB}'
#藍牙列印服務
HCRPrintServiceClass_UUID = '{00001126-0000-1000-8000-00805F9B34FB}'
HCRScanServiceClass_UUID = '{00001127-0000-1000-8000-00805F9B34FB}'
CommonISDNAccessServiceClass_UUID = '{00001128-0000-1000-8000-00805F9B34FB}'
VideoConferencingGWServiceClass_UUID = '{00001129-0000-1000-8000-00805F9B34FB}'
UDIMTServiceClass_UUID = '{0000112A-0000-1000-8000-00805F9B34FB}'
UDITAServiceClass_UUID = '{0000112B-0000-1000-8000-00805F9B34FB}'
AudioVideoServiceClass_UUID = '{0000112C-0000-1000-8000-00805F9B34FB}'
SIMAccessServiceClass_UUID = '{0000112D-0000-1000-8000-00805F9B34FB}'
PnPInformationServiceClass_UUID = '{00001200-0000-1000-8000-00805F9B34FB}'
GenericNetworkingServiceClass_UUID = '{00001201-0000-1000-8000-00805F9B34FB}'
GenericFileTransferServiceClass_UUID = '{00001202-0000-1000-8000-00805F9B34FB}'
GenericAudioServiceClass_UUID = '{00001203-0000-1000-8000-00805F9B34FB}'
GenericTelephonyServiceClass_UUID = '{00001204-0000-1000-8000-00805F9B34FB}'
 */
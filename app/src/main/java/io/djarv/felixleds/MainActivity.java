package io.djarv.felixleds;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.skydoves.colorpickerview.ColorEnvelope;
import com.skydoves.colorpickerview.ColorPickerView;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.LinkedBlockingQueue;

public class MainActivity extends AppCompatActivity implements ColorEnvelopeListener, View.OnClickListener {
    private static final int MSG_RGB = 0x01;
    private static final int MSG_BRIGHTNESS = 0x02;
    private static final String TAG = "MainActivity";
    private final LinkedBlockingQueue<ColorEnvelope> outgoing = new LinkedBlockingQueue<>();
    private SharedPreferences preferences;
    private ColorPickerView colorPickerView;
    private Button offButton;
    private Thread sendColorThread;
    private ColorEnvelope envelope;
    private InetAddress address;
    private DatagramSocket socket;
    private final Runnable sendColor = () -> {
        Log.i(TAG, "sendColor: starting sending queue");
        while (true) {
            try {
                ColorEnvelope envelope = outgoing.take();
                int[] argb = envelope.getArgb();
                byte[] buffer = new byte[]{MSG_RGB, Integer.valueOf(argb[1]).byteValue(), Integer.valueOf(argb[2]).byteValue(), Integer.valueOf(argb[3]).byteValue()};

                Log.i(TAG, String.format("sendColor: %x,%x,%x", buffer[1], buffer[2], buffer[3]));

                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, 4210);
                try {
                    socket.send(packet);
//                    packet = new DatagramPacket(buffer, buffer.length);
//                    socket.receive(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "sendColor: failed to send packet: " + e.getMessage());
                }

            } catch (InterruptedException e) {
                Log.i(TAG, "sendColor: going to sleep");
                break;
            }
        }
    };
    private boolean isOn;

    @Override
    protected void onResume() {
        super.onResume();
        sendColorThread = new Thread(sendColor);
        sendColorThread.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sendColorThread.interrupt();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        isOn = true;

        preferences = getPreferences(Context.MODE_PRIVATE);

        try {
            address = InetAddress.getByName("192.168.1.170");
            socket = new DatagramSocket();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "onCreate: Unable to configure network: " + e.getMessage());
        }

        String initialColor = preferences.getString("color", "ffffffff");
        BrightnessSlideBar brightnessSlideBar = findViewById(R.id.brightnessSlideBar);

        colorPickerView = findViewById(R.id.colorPickerView);
        colorPickerView.setInitialColor(Color.parseColor("#" + initialColor));
        colorPickerView.setColorListener(this);
        colorPickerView.attachBrightnessSlider(brightnessSlideBar);

        offButton = findViewById(R.id.offButton);
        offButton.setText(R.string.buttonOff);
        offButton.setOnClickListener(this);
    }

    @Override
    public void onColorSelected(ColorEnvelope envelope, boolean fromUser) {
        this.envelope = envelope;
        preferences.edit().putString("color", envelope.getHexCode()).apply();
        outgoing.offer(this.envelope);
    }

    @Override
    public void onClick(View v) {
        isOn = !isOn;

        colorPickerView.setEnabled(isOn);
        if (!isOn) {
            offButton.setText(R.string.buttonOn);
            outgoing.offer(new ColorEnvelope(Color.rgb(0, 0, 0)));
        } else {
            offButton.setText(R.string.buttonOff);
            colorPickerView.setInitialColor(envelope.getColor());
        }
    }
}
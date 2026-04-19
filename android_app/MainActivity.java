package com.example.chat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private EditText etIp, etPort, etInput;
    private Button btnConnect, btnSend;
    private TextView tvChat;
    private ScrollView scrollView;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isConnected = false;

    // UI'ı güncellemek için ana thread handler'ı
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etIp = findViewById(R.id.etIp);
        etPort = findViewById(R.id.etPort);
        etInput = findViewById(R.id.etInput);
        btnConnect = findViewById(R.id.btnConnect);
        btnSend = findViewById(R.id.btnSend);
        tvChat = findViewById(R.id.tvChat);
        scrollView = findViewById(R.id.scrollView);

        btnConnect.setOnClickListener(v -> {
            if (!isConnected) {
                String ip = etIp.getText().toString();
                int port = Integer.parseInt(etPort.getText().toString());
                connectToServer(ip, port);
            } else {
                disconnectServer();
            }
        });

        btnSend.setOnClickListener(v -> {
            if (isConnected) {
                String message = etInput.getText().toString();
                if (!message.isEmpty()) {
                    sendMessage(message);
                    etInput.setText("");
                }
            } else {
                Toast.makeText(this, "Önce sunucuya bağlanın", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void connectToServer(String ip, int port) {
        new Thread(() -> {
            try {
                socket = new Socket(ip, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                isConnected = true;
                
                mainHandler.post(() -> {
                    btnConnect.setText("Bağlantıyı Kes");
                    appendChat("Sistem: Bağlantı kuruldu!");
                });

                // Dinleme döngüsü
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    mainHandler.post(() -> appendChat(msg));
                }

                // Baglanti koptuysa
                disconnectServer();

            } catch (Exception e) {
                Log.e("SocketErr", "Bağlantı hatası", e);
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Bağlantı başarısız: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void sendMessage(String message) {
        new Thread(() -> {
            if (out != null) {
                out.println(message);
            }
        }).start();
    }

    private void disconnectServer() {
        new Thread(() -> {
            try {
                isConnected = false;
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null) socket.close();
                
                mainHandler.post(() -> {
                    btnConnect.setText("Bağlan");
                    appendChat("Sistem: Bağlantı kesildi.");
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void appendChat(String message) {
        tvChat.append(message + "\n");
        // Otomatik aşağı kaydır
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectServer();
    }
}

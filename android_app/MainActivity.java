package com.example.chat;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private static final String DEFAULT_HOST = "0.tcp.eu.ngrok.io";
    private static final int    DEFAULT_PORT = 18294;

    private String serverHost;
    private int    serverPort;

    private EditText     etInput;
    private Button       btnSend, btnSettings;
    private TextView     tvStatus;
    private LinearLayout chatContainer;
    private ScrollView   scrollView;

    private Socket         socket;
    private PrintWriter    out;
    private BufferedReader in;
    private boolean isConnected = false;
    private String username = "Kullanıcı";

    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        prefs = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE);
        serverHost = prefs.getString("server_host", DEFAULT_HOST);
        serverPort = prefs.getInt("server_port", DEFAULT_PORT);

        etInput       = findViewById(R.id.etInput);
        btnSend       = findViewById(R.id.btnSend);
        btnSettings   = findViewById(R.id.btnSettings);
        tvStatus      = findViewById(R.id.tvStatus);
        chatContainer = findViewById(R.id.chatContainer);
        scrollView    = findViewById(R.id.scrollView);

        // Ayarlar butonu
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        // Gönder butonu
        btnSend.setOnClickListener(v -> sendUserMessage());

        // Klavyeden "Gönder" tuşu
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendUserMessage();
                return true;
            }
            return false;
        });

        // İlk açılışta ad sor, sonra otomatik bağlan
        askUsername();
    }

    // ─── Ayarlar Dialogu ───────────────────────────────────────────────────
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sunucu Ayarlari");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(8));

        TextView hostLabel = new TextView(this);
        hostLabel.setText("Sunucu Adresi (Host):");
        hostLabel.setTextSize(14);
        hostLabel.setTextColor(Color.parseColor("#555555"));
        layout.addView(hostLabel);

        EditText hostInput = new EditText(this);
        hostInput.setText(serverHost);
        hostInput.setSingleLine(true);
        hostInput.setTextSize(15);
        layout.addView(hostInput);

        TextView portLabel = new TextView(this);
        portLabel.setText("Port Numarasi:");
        portLabel.setTextSize(14);
        portLabel.setTextColor(Color.parseColor("#555555"));
        LinearLayout.LayoutParams portLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        portLabelParams.setMargins(0, dpToPx(12), 0, 0);
        portLabel.setLayoutParams(portLabelParams);
        layout.addView(portLabel);

        EditText portInput = new EditText(this);
        portInput.setText(String.valueOf(serverPort));
        portInput.setSingleLine(true);
        portInput.setTextSize(15);
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(portInput);

        TextView info = new TextView(this);
        info.setText("\nngrok tcp baslatinca cikan adresi ve\nport numarasini buraya yaz, Kaydet'e bas.");
        info.setTextSize(12);
        info.setTextColor(Color.parseColor("#999999"));
        layout.addView(info);

        builder.setView(layout);

        builder.setPositiveButton("Kaydet", (dialog, which) -> {
            String newHost = hostInput.getText().toString().trim();
            String portStr = portInput.getText().toString().trim();
            if (!newHost.isEmpty() && !portStr.isEmpty()) {
                serverHost = newHost;
                try {
                    serverPort = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Gecersiz port!", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit()
                     .putString("server_host", serverHost)
                     .putInt("server_port", serverPort)
                     .apply();
                addSystemMessage("Ayarlar kaydedildi: " + serverHost + ":" + serverPort);

                // Eski bağlantıyı kes ve yeniden bağlan
                disconnectServer();
                mainHandler.postDelayed(() -> connectToServer(serverHost, serverPort), 500);
            }
        });

        builder.setNegativeButton("Iptal", null);
        builder.show();
    }

    // ─── Kullanıcı Adı Dialog ───────────────────────────────────────────────
    private void askUsername() {
        // Kayıtlı ad varsa direkt bağlan
        String savedName = prefs.getString("username", "");
        if (!savedName.isEmpty()) {
            username = savedName;
            addSystemMessage("Tekrar hosgeldin, " + username + "!");
            connectToServer(serverHost, serverPort);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Hos Geldin!");
        builder.setMessage("Adini gir:");

        final EditText input = new EditText(this);
        input.setHint("Adin...");
        input.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12));
        input.setSingleLine(true);
        builder.setView(input);
        builder.setCancelable(false);

        builder.setPositiveButton("Basla", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                username = name;
                prefs.edit().putString("username", username).apply();
            }
            addSystemMessage("Hosgeldin, " + username + "! Baglaniliyor...");
            connectToServer(serverHost, serverPort);
        });

        builder.show();
    }

    // ─── Kullanıcı Mesaj Gönderme ──────────────────────────────────────────
    private void sendUserMessage() {
        if (!isConnected) {
            Toast.makeText(this, "Sunucuya bagli degil! Ayarlardan kontrol et.", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;

        sendRaw(text);
        addMessageBubble(text, true, username);
        etInput.setText("");
    }

    private void sendRaw(String message) {
        new Thread(() -> {
            if (out != null) out.println(message);
        }).start();
    }

    // ─── Sunucuya Bağlan ───────────────────────────────────────────────────
    private void connectToServer(String host, int port) {
        addSystemMessage("Baglaniliyor: " + host + ":" + port);
        tvStatus.setText("Baglaniliyor...");
        tvStatus.setTextColor(Color.parseColor("#FFF59D"));

        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                isConnected = true;

                // Sunucuya adımızı gönder
                out.println("NAME:" + username);

                mainHandler.post(() -> {
                    tvStatus.setText("Baglandi");
                    tvStatus.setTextColor(Color.parseColor("#A5D6A7"));
                    addSystemMessage("Baglanti kuruldu!");
                });

                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    mainHandler.post(() -> handleIncoming(msg));
                }

                // Bağlantı koptu
                mainHandler.post(() -> {
                    isConnected = false;
                    tvStatus.setText("Baglanti kesildi");
                    tvStatus.setTextColor(Color.parseColor("#EF9A9A"));
                    addSystemMessage("Baglanti kesildi. Ayarlardan tekrar baglanabilirsin.");
                });

            } catch (Exception e) {
                Log.e("SocketErr", "Baglanti hatasi", e);
                mainHandler.post(() -> {
                    isConnected = false;
                    tvStatus.setText("Baglanamadi");
                    tvStatus.setTextColor(Color.parseColor("#EF9A9A"));
                    addSystemMessage("Baglanamadi: " + e.getMessage());
                    addSystemMessage("Ayar butonundan adresi kontrol et.");
                });
            }
        }).start();
    }

    private void handleIncoming(String msg) {
        // Kendi mesajımızı tekrar gösterme
        if (msg.startsWith(username + ": ")) return;

        int colonIdx = msg.indexOf(": ");
        if (colonIdx > 0) {
            String sender  = msg.substring(0, colonIdx);
            String content = msg.substring(colonIdx + 2);
            addMessageBubble(content, false, sender);
        } else {
            addSystemMessage(msg);
        }
    }

    private void disconnectServer() {
        new Thread(() -> {
            try {
                isConnected = false;
                if (out    != null) out.close();
                if (in     != null) in.close();
                if (socket != null) socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ─── Mesaj Balonu ──────────────────────────────────────────────────────
    private void addMessageBubble(String message, boolean isSent, String senderName) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wParams.setMargins(0, dpToPx(3), 0, dpToPx(3));
        wrapper.setLayoutParams(wParams);

        if (!isSent) {
            TextView nameView = new TextView(this);
            nameView.setText(senderName);
            nameView.setTextSize(11);
            nameView.setTextColor(Color.parseColor("#757575"));
            nameView.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams nParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            nParams.setMargins(dpToPx(16), 0, dpToPx(72), dpToPx(2));
            nameView.setLayoutParams(nParams);
            wrapper.addView(nameView);
        }

        TextView bubble = new TextView(this);
        bubble.setText(message);
        bubble.setTextSize(15);
        bubble.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);

        LinearLayout.LayoutParams bParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        if (isSent) {
            bubble.setTextColor(Color.WHITE);
            bg.setColor(Color.parseColor("#1A73E8"));
            bg.setCornerRadii(new float[]{28, 28, 28, 28, 4, 4, 28, 28});
            bParams.gravity = Gravity.END;
            bParams.setMargins(dpToPx(64), 0, dpToPx(8), 0);
        } else {
            bubble.setTextColor(Color.parseColor("#212121"));
            bg.setColor(Color.WHITE);
            bg.setCornerRadii(new float[]{4, 4, 28, 28, 28, 28, 28, 28});
            bParams.gravity = Gravity.START;
            bParams.setMargins(dpToPx(8), 0, dpToPx(64), 0);
        }

        bubble.setBackground(bg);
        bubble.setLayoutParams(bParams);
        wrapper.addView(bubble);
        chatContainer.addView(wrapper);
        scrollToBottom();
    }

    private void addSystemMessage(String message) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(12);
        tv.setTextColor(Color.parseColor("#9E9E9E"));
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dpToPx(6), 0, dpToPx(6));
        tv.setLayoutParams(params);
        chatContainer.addView(tv);
        scrollToBottom();
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectServer();
    }
}

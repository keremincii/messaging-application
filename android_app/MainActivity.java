package com.example.chat;

import android.app.AlertDialog;
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

    // =====================================================
    //  SUNUCU BİLGİLERİ — ngrok her açılışta değişebilir!
    //  ngrok tcp çalıştırınca çıkan HOST ve PORT'u buraya yaz.
    // =====================================================
    private static final String SERVER_HOST = "booted-equation-iguana.ngrok-free.dev";
    private static final int    SERVER_PORT = 12345;

    // UI elemanları
    private EditText    etInput;
    private Button      btnConnect, btnSend;
    private TextView    tvStatus;
    private LinearLayout chatContainer;
    private ScrollView  scrollView;

    // Soket
    private Socket       socket;
    private PrintWriter  out;
    private BufferedReader in;
    private boolean isConnected = false;

    // Kullanıcı adı (giriş ekranından alınır)
    private String username = "Kullanıcı";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Klavye açıkken layout'u küçült
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // View bağlantıları
        etInput       = findViewById(R.id.etInput);
        btnConnect    = findViewById(R.id.btnConnect);
        btnSend       = findViewById(R.id.btnSend);
        tvStatus      = findViewById(R.id.tvStatus);
        chatContainer = findViewById(R.id.chatContainer);
        scrollView    = findViewById(R.id.scrollView);

        // Kullanıcı adı sor, sonra beklet
        askUsername();

        // Bağlan / Kes butonu
        btnConnect.setOnClickListener(v -> {
            if (!isConnected) {
                connectToServer(SERVER_HOST, SERVER_PORT);
            } else {
                disconnectServer();
            }
        });

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
    }

    // ─── Kullanıcı Adı Dialog ───────────────────────────────────────────────
    private void askUsername() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("👋 Hoş Geldin!");
        builder.setMessage("ChatApp'e bağlanmadan önce adını gir:");

        final EditText input = new EditText(this);
        input.setHint("Adın...");
        input.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12));
        input.setSingleLine(true);
        builder.setView(input);
        builder.setCancelable(false);

        builder.setPositiveButton("Devam ➜", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                username = name;
            }
            addSystemMessage("Merhaba, " + username + "! 'Bağlan' butonuna bas.");
        });

        builder.show();
    }

    // ─── Mesaj Gönder ──────────────────────────────────────────────────────
    private void sendUserMessage() {
        if (!isConnected) {
            Toast.makeText(this, "Önce sunucuya bağlanın!", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;

        // Sunucuya "Ad: mesaj" formatında gönder
        String fullMessage = username + ": " + text;
        sendRaw(fullMessage);

        // Kendi ekranında sağ tarafta göster
        addMessageBubble(text, true, username);
        etInput.setText("");
    }

    // ─── Sunucuya Ham Metin Gönder ─────────────────────────────────────────
    private void sendRaw(String message) {
        new Thread(() -> {
            if (out != null) {
                out.println(message);
            }
        }).start();
    }

    // ─── Sunucuya Bağlan ───────────────────────────────────────────────────
    private void connectToServer(String host, int port) {
        addSystemMessage("Bağlanılıyor...");

        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                isConnected = true;

                mainHandler.post(() -> {
                    btnConnect.setText("Kes");
                    tvStatus.setText("● Bağlandı");
                    tvStatus.setTextColor(Color.parseColor("#A5D6A7"));
                    addSystemMessage("✅ Bağlantı kuruldu!");
                });

                // Gelen mesajları dinle
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    mainHandler.post(() -> handleIncoming(msg));
                }

                // Bağlantı koptu
                disconnectServer();

            } catch (Exception e) {
                Log.e("SocketErr", "Bağlantı hatası", e);
                mainHandler.post(() -> {
                    addSystemMessage("❌ Bağlantı başarısız: " + e.getMessage());
                    Toast.makeText(MainActivity.this,
                            "Sunucuya ulaşılamadı!", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ─── Gelen Mesajı İşle ─────────────────────────────────────────────────
    private void handleIncoming(String msg) {
        // Kendi gönderdiğimiz mesaj sunucudan geri dönüyorsa (echo), atla
        if (msg.startsWith(username + ": ")) {
            return;
        }

        // "Ad: mesaj" formatını ayır
        int colonIdx = msg.indexOf(": ");
        if (colonIdx > 0) {
            String sender  = msg.substring(0, colonIdx);
            String content = msg.substring(colonIdx + 2);
            addMessageBubble(content, false, sender);
        } else {
            // Format yoksa sistem mesajı olarak göster
            addSystemMessage(msg);
        }
    }

    // ─── Bağlantıyı Kes ────────────────────────────────────────────────────
    private void disconnectServer() {
        new Thread(() -> {
            try {
                isConnected = false;
                if (out    != null) out.close();
                if (in     != null) in.close();
                if (socket != null) socket.close();

                mainHandler.post(() -> {
                    btnConnect.setText("Bağlan");
                    tvStatus.setText("Bağlı değil");
                    tvStatus.setTextColor(Color.parseColor("#BBDEFB"));
                    addSystemMessage("Bağlantı kesildi.");
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ─── Mesaj Balonu Ekle ─────────────────────────────────────────────────
    private void addMessageBubble(String message, boolean isSent, String senderName) {
        // Dış wrapper (hizalama için)
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wParams.setMargins(0, dpToPx(3), 0, dpToPx(3));
        wrapper.setLayoutParams(wParams);

        // Gönderen adı (sadece alınan mesajlarda göster)
        if (!isSent) {
            TextView nameView = new TextView(this);
            nameView.setText(senderName);
            nameView.setTextSize(11);
            nameView.setTextColor(Color.parseColor("#757575"));
            nameView.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams nParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            nParams.setMarginStart(dpToPx(16));
            nParams.setMarginEnd(dpToPx(72));
            nParams.setMargins(dpToPx(16), 0, dpToPx(72), dpToPx(2));
            nameView.setLayoutParams(nParams);
            wrapper.addView(nameView);
        }

        // Balon
        TextView bubble = new TextView(this);
        bubble.setText(message);
        bubble.setTextSize(15);
        bubble.setLineSpacing(dpToPx(2), 1f);
        bubble.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);

        LinearLayout.LayoutParams bParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        if (isSent) {
            // Sağ taraf — mavi balon
            bubble.setTextColor(Color.WHITE);
            bg.setColor(Color.parseColor("#1A73E8"));
            bg.setCornerRadii(new float[]{28, 28, 28, 28, 4, 4, 28, 28});
            bParams.gravity = Gravity.END;
            bParams.setMargins(dpToPx(64), 0, dpToPx(8), 0);
        } else {
            // Sol taraf — beyaz balon
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

    // ─── Sistem Mesajı (ortalı, gri) ──────────────────────────────────────
    private void addSystemMessage(String message) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(12);
        tv.setTextColor(Color.parseColor("#9E9E9E"));
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dpToPx(6), 0, dpToPx(6));
        tv.setLayoutParams(params);
        tv.setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(4));
        chatContainer.addView(tv);
        scrollToBottom();
    }

    // ─── Aşağı Kaydır ─────────────────────────────────────────────────────
    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    // ─── dp → px Yardımcı ─────────────────────────────────────────────────
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

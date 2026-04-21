package com.example.chat;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
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
import android.widget.ViewFlipper;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    private static final String DEFAULT_HOST = "0.tcp.eu.ngrok.io";
    private static final int    DEFAULT_PORT = 18294;
    private static final String TAG = "ChatApp";

    // Avatar renkleri
    private static final int[] AVATAR_COLORS = {
        Color.parseColor("#E53935"), Color.parseColor("#8E24AA"),
        Color.parseColor("#3949AB"), Color.parseColor("#039BE5"),
        Color.parseColor("#00897B"), Color.parseColor("#43A047"),
        Color.parseColor("#F4511E"), Color.parseColor("#6D4C41"),
        Color.parseColor("#757575"), Color.parseColor("#1E88E5")
    };

    private String serverHost;
    private int    serverPort;

    // UI - Genel
    private ViewFlipper viewFlipper;
    private Button      btnSettings;

    // UI - Kisi Listesi (Ekran 0)
    private LinearLayout contactsList;
    private TextView     tvContactsStatus;

    // UI - Sohbet (Ekran 1)
    private LinearLayout chatContainer;
    private ScrollView   chatScrollView;
    private EditText     etInput;
    private Button       btnSend, btnBack;
    private TextView     tvChatName, tvChatStatus, tvChatAvatar;

    // Soket
    private Socket         socket;
    private PrintWriter    out;
    private BufferedReader in;
    private volatile boolean isConnected = false;

    // Otomatik yeniden baglanti
    private boolean shouldReconnect = true;
    private int reconnectDelay = 3000; // 3 saniye
    private static final int MAX_RECONNECT_DELAY = 30000; // 30 saniye max
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private Runnable reconnectRunnable;

    // Keepalive (baglanti kopmamasi icin)
    private Handler keepaliveHandler = new Handler(Looper.getMainLooper());
    private static final int KEEPALIVE_INTERVAL = 25000; // 25 saniye

    // Kullanici bilgileri
    private String username = "Kullanici";
    private String currentChatUser = null;

    // Kisi listesi ve mesaj gecmisi
    private List<String> onlineUsers = new ArrayList<>();
    private Map<String, List<ChatMessage>> chatHistory = new HashMap<>();

    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Mesaj modeli
    static class ChatMessage {
        String text;
        boolean isSent;
        long timestamp;
        ChatMessage(String text, boolean isSent) {
            this.text = text;
            this.isSent = isSent;
            this.timestamp = System.currentTimeMillis();
        }
        ChatMessage(String text, boolean isSent, long timestamp) {
            this.text = text;
            this.isSent = isSent;
            this.timestamp = timestamp;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        prefs = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE);
        serverHost = prefs.getString("server_host", DEFAULT_HOST);
        serverPort = prefs.getInt("server_port", DEFAULT_PORT);

        // View baglantilari
        viewFlipper     = findViewById(R.id.viewFlipper);
        btnSettings     = findViewById(R.id.btnSettings);
        contactsList    = findViewById(R.id.contactsList);
        tvContactsStatus= findViewById(R.id.tvContactsStatus);
        chatContainer   = findViewById(R.id.chatContainer);
        chatScrollView  = findViewById(R.id.chatScrollView);
        etInput         = findViewById(R.id.etInput);
        btnSend         = findViewById(R.id.btnSend);
        btnBack         = findViewById(R.id.btnBack);
        tvChatName      = findViewById(R.id.tvChatName);
        tvChatStatus    = findViewById(R.id.tvChatStatus);
        tvChatAvatar    = findViewById(R.id.tvChatAvatar);

        // Mesaj gecmisini yukle
        loadChatHistoryFromStorage();

        // Ayarlar butonu
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        // Geri butonu
        btnBack.setOnClickListener(v -> showContactsList());

        // Gonder butonu - baslangicta soluk
        btnSend.setAlpha(0.4f);
        btnSend.setOnClickListener(v -> sendUserMessage());

        // Mesaj yazilinca gonder butonu canlansin
        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSend.setAlpha(s.toString().trim().length() > 0 ? 1.0f : 0.4f);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Klavyeden gonder
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendUserMessage();
                return true;
            }
            return false;
        });

        // Ilk acilista ad sor, sonra otomatik baglan
        askUsername();
    }

    @Override
    public void onBackPressed() {
        if (viewFlipper.getDisplayedChild() == 1) {
            showContactsList();
        } else {
            super.onBackPressed();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MESAJ GECMISI KAYDETME / YUKLEME (SharedPreferences + JSON)
    // ═══════════════════════════════════════════════════════════════════════

    private void saveChatHistoryToStorage() {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, List<ChatMessage>> entry : chatHistory.entrySet()) {
                JSONArray arr = new JSONArray();
                for (ChatMessage msg : entry.getValue()) {
                    JSONObject m = new JSONObject();
                    m.put("text", msg.text);
                    m.put("sent", msg.isSent);
                    m.put("time", msg.timestamp);
                    arr.put(m);
                }
                root.put(entry.getKey(), arr);
            }
            prefs.edit().putString("chat_history", root.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Mesaj gecmisi kaydedilemedi", e);
        }
    }

    private void loadChatHistoryFromStorage() {
        try {
            String json = prefs.getString("chat_history", "");
            if (json.isEmpty()) return;

            JSONObject root = new JSONObject(json);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String contact = keys.next();
                JSONArray arr = root.getJSONArray(contact);
                List<ChatMessage> messages = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.getJSONObject(i);
                    messages.add(new ChatMessage(
                        m.getString("text"),
                        m.getBoolean("sent"),
                        m.optLong("time", 0)
                    ));
                }
                chatHistory.put(contact, messages);
            }
        } catch (Exception e) {
            Log.e(TAG, "Mesaj gecmisi yuklenemedi", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EKRAN GECISLERI
    // ═══════════════════════════════════════════════════════════════════════

    private void showContactsList() {
        currentChatUser = null;
        viewFlipper.setDisplayedChild(0);
        refreshContactsUI();
    }

    private void openChat(String contactName) {
        currentChatUser = contactName;
        viewFlipper.setDisplayedChild(1);

        tvChatName.setText(contactName);
        boolean isOnline = onlineUsers.contains(contactName);
        tvChatStatus.setText(isOnline ? "Online" : "Offline");
        tvChatStatus.setTextColor(isOnline ?
            Color.parseColor("#A5D6A7") : Color.parseColor("#EF9A9A"));

        setupAvatar(tvChatAvatar, contactName);
        loadChatMessages(contactName);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // KISI LISTESI UI
    // ═══════════════════════════════════════════════════════════════════════

    private void refreshContactsUI() {
        contactsList.removeAllViews();

        // Oncelik: online kullanicilar + gecmisi olan offline kullanicilar
        List<String> allContacts = new ArrayList<>(onlineUsers);
        for (String contact : chatHistory.keySet()) {
            if (!allContacts.contains(contact)) {
                allContacts.add(contact);
            }
        }

        if (allContacts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Henuz kimse online degil.\nBaska bir cihazdan baglanmayi dene!");
            empty.setTextSize(14);
            empty.setTextColor(Color.parseColor("#999999"));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dpToPx(32), dpToPx(48), dpToPx(32), dpToPx(48));
            contactsList.addView(empty);
            return;
        }

        for (String user : allContacts) {
            boolean isOnline = onlineUsers.contains(user);
            addContactItem(user, isOnline);
        }
    }

    private void addContactItem(String name, boolean isOnline) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        item.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        itemParams.setMargins(0, 0, 0, dpToPx(1));
        item.setLayoutParams(itemParams);

        // Avatar
        TextView avatar = new TextView(this);
        avatar.setWidth(dpToPx(48));
        avatar.setHeight(dpToPx(48));
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextSize(20);
        avatar.setTextColor(Color.WHITE);
        avatar.setTypeface(null, Typeface.BOLD);
        setupAvatar(avatar, name);
        item.addView(avatar);

        // Ad, durum ve son mesaj
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dpToPx(14), 0, 0, 0);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        textCol.setLayoutParams(textParams);

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextSize(16);
        nameView.setTextColor(Color.parseColor("#212121"));
        nameView.setTypeface(null, Typeface.BOLD);
        textCol.addView(nameView);

        // Son mesaj preview
        List<ChatMessage> msgs = chatHistory.get(name);
        String lastMsg = "";
        if (msgs != null && !msgs.isEmpty()) {
            ChatMessage last = msgs.get(msgs.size() - 1);
            lastMsg = last.isSent ? "Sen: " + last.text : last.text;
            if (lastMsg.length() > 30) lastMsg = lastMsg.substring(0, 30) + "...";
        } else {
            lastMsg = isOnline ? "Online" : "Offline";
        }

        TextView previewView = new TextView(this);
        previewView.setText(lastMsg);
        previewView.setTextSize(13);
        previewView.setTextColor(Color.parseColor("#757575"));
        previewView.setSingleLine(true);
        textCol.addView(previewView);

        item.addView(textCol);

        // Online noktasi
        TextView dot = new TextView(this);
        dot.setWidth(dpToPx(12));
        dot.setHeight(dpToPx(12));
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(isOnline ? Color.parseColor("#4CAF50") : Color.parseColor("#BDBDBD"));
        dot.setBackground(dotBg);
        item.addView(dot);

        item.setOnClickListener(v -> openChat(name));
        item.setClickable(true);
        item.setFocusable(true);

        contactsList.addView(item);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AVATAR
    // ═══════════════════════════════════════════════════════════════════════

    private void setupAvatar(TextView tv, String name) {
        String initial = name.substring(0, 1).toUpperCase();
        tv.setText(initial);
        int colorIndex = Math.abs(name.hashCode()) % AVATAR_COLORS.length;
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(AVATAR_COLORS[colorIndex]);
        tv.setBackground(bg);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SOHBET MESAJLARINI YUKLE
    // ═══════════════════════════════════════════════════════════════════════

    private void loadChatMessages(String contactName) {
        chatContainer.removeAllViews();
        List<ChatMessage> messages = chatHistory.get(contactName);
        if (messages != null) {
            for (ChatMessage msg : messages) {
                addBubbleToChat(msg.text, msg.isSent);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AYARLAR
    // ═══════════════════════════════════════════════════════════════════════

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sunucu Ayarlari");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(8));

        TextView hostLabel = new TextView(this);
        hostLabel.setText("Sunucu Adresi (Host):");
        hostLabel.setTextSize(14);
        layout.addView(hostLabel);

        EditText hostInput = new EditText(this);
        hostInput.setText(serverHost);
        hostInput.setSingleLine(true);
        layout.addView(hostInput);

        TextView portLabel = new TextView(this);
        portLabel.setText("Port Numarasi:");
        portLabel.setTextSize(14);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.setMargins(0, dpToPx(12), 0, 0);
        portLabel.setLayoutParams(plp);
        layout.addView(portLabel);

        EditText portInput = new EditText(this);
        portInput.setText(String.valueOf(serverPort));
        portInput.setSingleLine(true);
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(portInput);

        builder.setView(layout);

        builder.setPositiveButton("Kaydet", (dialog, which) -> {
            String newHost = hostInput.getText().toString().trim();
            String portStr = portInput.getText().toString().trim();
            if (!newHost.isEmpty() && !portStr.isEmpty()) {
                serverHost = newHost;
                try { serverPort = Integer.parseInt(portStr); }
                catch (NumberFormatException e) { return; }
                prefs.edit()
                     .putString("server_host", serverHost)
                     .putInt("server_port", serverPort).apply();
                Toast.makeText(this, "Kaydedildi! Yeniden baglaniliyor...", Toast.LENGTH_SHORT).show();
                disconnectServer();
                mainHandler.postDelayed(() -> connectToServer(serverHost, serverPort), 500);
            }
        });
        builder.setNegativeButton("Iptal", null);
        builder.show();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // KULLANICI ADI
    // ═══════════════════════════════════════════════════════════════════════

    private void askUsername() {
        String savedName = prefs.getString("username", "");
        if (!savedName.isEmpty()) {
            username = savedName;
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
            connectToServer(serverHost, serverPort);
        });
        builder.show();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MESAJ GONDER
    // ═══════════════════════════════════════════════════════════════════════

    private void sendUserMessage() {
        if (!isConnected || currentChatUser == null) {
            Toast.makeText(this, "Bagli degil veya kisi secilmedi!", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;

        // Mesaji sifrele ve gonder
        String encrypted = aesEncrypt(text);
        sendRaw("TO:" + currentChatUser + ":ENC:" + encrypted);
        addBubbleToChat(text, true);
        saveChatMessage(currentChatUser, text, true);
        etInput.setText("");
    }

    private void sendRaw(String message) {
        new Thread(() -> {
            try {
                if (out != null) out.println(message);
            } catch (Exception e) {
                Log.e(TAG, "Gonderme hatasi", e);
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MESAJ GECMISI (HAFIZADA)
    // ═══════════════════════════════════════════════════════════════════════

    private void saveChatMessage(String contact, String text, boolean isSent) {
        if (!chatHistory.containsKey(contact)) {
            chatHistory.put(contact, new ArrayList<>());
        }
        chatHistory.get(contact).add(new ChatMessage(text, isSent));
        // Kalici olarak kaydet
        saveChatHistoryToStorage();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SUNUCUYA BAGLAN + OTO YENIDEN BAGLANTI + KEEPALIVE
    // ═══════════════════════════════════════════════════════════════════════

    private void connectToServer(String host, int port) {
        shouldReconnect = true;
        reconnectDelay = 3000;
        tvContactsStatus.setText("Baglaniliyor...");
        tvContactsStatus.setTextColor(Color.parseColor("#FFF59D"));

        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                socket.setKeepAlive(true);
                socket.setSoTimeout(0); // sonsuz bekle
                out = new PrintWriter(socket.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                isConnected = true;
                reconnectDelay = 3000; // basarili baglantida delay'i sifirla

                out.println("NAME:" + username);

                mainHandler.post(() -> {
                    tvContactsStatus.setText("Baglandi - " + username);
                    tvContactsStatus.setTextColor(Color.parseColor("#A5D6A7"));
                });

                // Keepalive basla
                startKeepalive();

                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    mainHandler.post(() -> handleIncoming(msg));
                }

                // Buraya geldiyse baglanti kopmu demek
                mainHandler.post(() -> {
                    isConnected = false;
                    tvContactsStatus.setText("Baglanti kesildi");
                    tvContactsStatus.setTextColor(Color.parseColor("#EF9A9A"));
                    stopKeepalive();
                    scheduleReconnect();
                });

            } catch (Exception e) {
                Log.e(TAG, "Baglanti hatasi", e);
                mainHandler.post(() -> {
                    isConnected = false;
                    tvContactsStatus.setText("Baglanamadi - Tekrar deneniyor...");
                    tvContactsStatus.setTextColor(Color.parseColor("#EF9A9A"));
                    stopKeepalive();
                    scheduleReconnect();
                });
            }
        }).start();
    }

    // Otomatik yeniden baglanti zamanlayici
    private void scheduleReconnect() {
        if (!shouldReconnect) return;

        if (reconnectRunnable != null) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
        }

        final int currentDelay = reconnectDelay;
        tvContactsStatus.setText("Tekrar deneniyor (" + (currentDelay / 1000) + "s)...");

        reconnectRunnable = () -> {
            if (shouldReconnect && !isConnected) {
                connectToServer(serverHost, serverPort);
            }
        };
        reconnectHandler.postDelayed(reconnectRunnable, currentDelay);

        // Exponential backoff: 3s -> 6s -> 12s -> 24s -> 30s max
        reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY);
    }

    // Keepalive: ngrok baglantinin idle kalmasini onler
    private void startKeepalive() {
        keepaliveHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnected && out != null) {
                    new Thread(() -> {
                        try {
                            out.println("PING");
                        } catch (Exception e) {
                            Log.e(TAG, "Keepalive hatasi", e);
                        }
                    }).start();
                }
                if (isConnected) {
                    keepaliveHandler.postDelayed(this, KEEPALIVE_INTERVAL);
                }
            }
        }, KEEPALIVE_INTERVAL);
    }

    private void stopKeepalive() {
        keepaliveHandler.removeCallbacksAndMessages(null);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GELEN MESAJLARI ISLE
    // ═══════════════════════════════════════════════════════════════════════

    private void handleIncoming(String msg) {
        if (msg.startsWith("USERLIST:")) {
            String list = msg.substring(9);
            onlineUsers.clear();
            if (!list.isEmpty()) {
                String[] users = list.split(",");
                for (String u : users) {
                    String trimmed = u.trim();
                    if (!trimmed.isEmpty()) onlineUsers.add(trimmed);
                }
            }
            refreshContactsUI();

        } else if (msg.startsWith("ONLINE:")) {
            String user = msg.substring(7).trim();
            if (!onlineUsers.contains(user)) {
                onlineUsers.add(user);
            }
            refreshContactsUI();
            if (user.equals(currentChatUser)) {
                tvChatStatus.setText("Online");
                tvChatStatus.setTextColor(Color.parseColor("#A5D6A7"));
            }

        } else if (msg.startsWith("OFFLINE:")) {
            String user = msg.substring(8).trim();
            onlineUsers.remove(user);
            refreshContactsUI();
            if (user.equals(currentChatUser)) {
                tvChatStatus.setText("Offline");
                tvChatStatus.setTextColor(Color.parseColor("#EF9A9A"));
            }

        } else if (msg.startsWith("MSG:")) {
            String content = msg.substring(4);
            int colonIdx = content.indexOf(':');
            if (colonIdx > 0) {
                String sender = content.substring(0, colonIdx);
                String rawText = content.substring(colonIdx + 1);

                // Sifreli mesajlari coz
                String text;
                if (rawText.startsWith("ENC:")) {
                    text = aesDecrypt(rawText.substring(4));
                } else {
                    text = rawText;
                }

                saveChatMessage(sender, text, false);

                if (sender.equals(currentChatUser)) {
                    addBubbleToChat(text, false);
                } else {
                    Toast.makeText(this, sender + ": " + text, Toast.LENGTH_SHORT).show();
                    refreshContactsUI();
                }

                if (!onlineUsers.contains(sender)) {
                    onlineUsers.add(sender);
                    refreshContactsUI();
                }
            }

        } else if (msg.startsWith("SYS:")) {
            Toast.makeText(this, msg.substring(4), Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectServer() {
        shouldReconnect = false;
        stopKeepalive();
        if (reconnectRunnable != null) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
        }
        new Thread(() -> {
            try {
                isConnected = false;
                if (out    != null) out.close();
                if (in     != null) in.close();
                if (socket != null) socket.close();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MESAJ BALONU
    // ═══════════════════════════════════════════════════════════════════════

    private void addBubbleToChat(String message, boolean isSent) {
        TextView bubble = new TextView(this);
        bubble.setText(message);
        bubble.setTextSize(15);
        bubble.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        if (isSent) {
            bubble.setTextColor(Color.WHITE);
            bg.setColor(Color.parseColor("#1A73E8"));
            bg.setCornerRadii(new float[]{28, 28, 28, 28, 4, 4, 28, 28});
            params.gravity = Gravity.END;
            params.setMargins(dpToPx(64), dpToPx(3), dpToPx(8), dpToPx(3));
        } else {
            bubble.setTextColor(Color.parseColor("#212121"));
            bg.setColor(Color.WHITE);
            bg.setCornerRadii(new float[]{4, 4, 28, 28, 28, 28, 28, 28});
            params.gravity = Gravity.START;
            params.setMargins(dpToPx(8), dpToPx(3), dpToPx(64), dpToPx(3));
        }

        bubble.setBackground(bg);
        bubble.setLayoutParams(params);
        chatContainer.addView(bubble);
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // YARDIMCI
    // ═══════════════════════════════════════════════════════════════════════

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AES-128-CBC SIFRELEME (Sunucu ile ayni anahtar)
    // ═══════════════════════════════════════════════════════════════════════

    private static final byte[] AES_KEY = "ChatApp2024Key!!".getBytes();
    private static final byte[] AES_IV  = "ChatAppInitVec!!".getBytes();

    private String aesEncrypt(String plaintext) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(AES_IV);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Sifreleme hatasi", e);
            return plaintext;
        }
    }

    private String aesDecrypt(String base64Encrypted) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(AES_IV);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decoded = Base64.decode(base64Encrypted, Base64.NO_WRAP);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "Sifre cozme hatasi", e);
            return base64Encrypted;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveChatHistoryToStorage();
        disconnectServer();
    }
}

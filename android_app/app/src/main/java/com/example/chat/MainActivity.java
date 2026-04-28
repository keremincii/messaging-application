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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    private static final String DEFAULT_HOST = "178.104.241.34";
    private static final int    DEFAULT_PORT = 8080;
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

    // UI - Kisi Listesi (Ekran 0)
    private LinearLayout contactsList;
    private TextView     tvContactsStatus;

    // UI - Sohbet (Ekran 1)
    private LinearLayout chatContainer;
    private ScrollView   chatScrollView;
    private EditText     etInput;
    private Button       btnSend;
    private TextView     btnBack, btnImage, btnFile, tvChatName, tvChatStatus, tvChatAvatar;

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

    // Dosya gonderme / alma
    private static final int  FILE_CHUNK_SIZE       = 30 * 1024;        // 30 KB ham veri
    private static final long MAX_FILE_SIZE          = 5 * 1024 * 1024L; // 5 MB limit
    private static final int  REQUEST_FILE           = 200;
    private final Map<String, Map<Integer, String>> incomingFileChunks     = new HashMap<>();
    private final Map<String, Integer>              incomingFileTotalChunks = new HashMap<>();

    // Kullanici bilgileri
    private String username = "Kullanici";
    private String currentChatUser = null;
    private String deviceToken = "";

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
        serverHost = DEFAULT_HOST;
        serverPort = DEFAULT_PORT;

        // View baglantilari
        viewFlipper     = findViewById(R.id.viewFlipper);
        contactsList    = findViewById(R.id.contactsList);
        tvContactsStatus= findViewById(R.id.tvContactsStatus);
        chatContainer   = findViewById(R.id.chatContainer);
        chatScrollView  = findViewById(R.id.chatScrollView);
        etInput         = findViewById(R.id.etInput);
        btnSend         = findViewById(R.id.btnSend);
        btnImage        = findViewById(R.id.btnImage);
        btnBack         = findViewById(R.id.btnBack);

        // Dosya butonu: btnImage'in soluna programatik olarak ekle (XML degisikligi gerektirmez)
        LinearLayout inputBar = (LinearLayout) btnImage.getParent();
        btnFile = new TextView(this);
        btnFile.setText("📎");
        btnFile.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        btnFile.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44));
        fileParams.setMarginStart(dpToPx(4));
        btnFile.setLayoutParams(fileParams);
        TypedValue tvRipple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tvRipple, true);
        btnFile.setBackgroundResource(tvRipple.resourceId);
        inputBar.addView(btnFile, inputBar.indexOfChild(btnImage));
        tvChatName      = findViewById(R.id.tvChatName);
        tvChatStatus    = findViewById(R.id.tvChatStatus);
        tvChatAvatar    = findViewById(R.id.tvChatAvatar);

        // Mesaj gecmisini yukle
        loadChatHistoryFromStorage();

        // Geri butonu
        btnBack.setOnClickListener(v -> showContactsList());

        // Gonder butonu - baslangicta soluk
        btnSend.setAlpha(0.4f);
        btnSend.setOnClickListener(v -> sendUserMessage());

        btnImage.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, 100);
        });

        btnFile.setOnClickListener(v -> openFilePicker());

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
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            try {
                android.net.Uri uri = data.getData();
                android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                float ratio = Math.min(800.0f / width, 800.0f / height);
                if (ratio < 1.0) {
                    bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, (int)(width * ratio), (int)(height * ratio), true);
                }
                
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos);
                byte[] imageBytes = baos.toByteArray();
                String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
                
                String encrypted = aesEncrypt("[IMG]" + base64Image);
                String rawMsg = "TO:" + currentChatUser + ":ENC:" + encrypted;
                
                addBubbleToChat("[IMG]" + base64Image, true);
                saveChatMessage(currentChatUser, "[IMG]" + base64Image, true);
                
                if (isConnected) {
                    sendRaw(rawMsg);
                } else {
                    saveToOfflineQueue(rawMsg);
                    Toast.makeText(this, "Çevrimdışısınız, fotoğraf kuyruğa eklendi.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Fotograf secilemedi", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_FILE && resultCode == RESULT_OK && data != null) {
            java.util.List<android.net.Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                android.content.ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    uris.add(clipData.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) sendFilesSequentially(uris, 0);
        }
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
        // Kendi adimizi ve tekrarlari filtrele
        List<String> allContacts = new ArrayList<>();
        for (String user : onlineUsers) {
            if (!user.equals(username) && !allContacts.contains(user)) {
                allContacts.add(user);
            }
        }
        for (String contact : chatHistory.keySet()) {
            if (!contact.equals(username) && !allContacts.contains(contact)) {
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
            String prefix = last.isSent ? "Sen: " : "";
            if (last.text != null && last.text.startsWith("[IMG]")) {
                lastMsg = prefix + "📷 Fotoğraf";
            } else if (last.text != null && last.text.startsWith("[FILE:")) {
                String inner = last.text.substring(6);
                int colonEnd = inner.indexOf(':');
                String fname = colonEnd > 0 ? inner.substring(0, colonEnd) : "Dosya";
                lastMsg = prefix + "📎 " + fname;
            } else {
                lastMsg = prefix + (last.text != null ? last.text : "");
                if (lastMsg.length() > 30) lastMsg = lastMsg.substring(0, 30) + "...";
            }
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
    // KULLANICI ADI
    // ═══════════════════════════════════════════════════════════════════════

    private void askUsername() {
        deviceToken = prefs.getString("device_token", "");
        if (deviceToken.isEmpty()) {
            deviceToken = java.util.UUID.randomUUID().toString();
            prefs.edit().putString("device_token", deviceToken).apply();
        }

        String savedName = prefs.getString("username", "");
        if (!savedName.isEmpty()) {
            username = savedName;
            refreshContactsUI();
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
            refreshContactsUI();
            connectToServer(serverHost, serverPort);
        });
        builder.show();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MESAJ GONDER
    // ═══════════════════════════════════════════════════════════════════════

    private void sendUserMessage() {
        if (currentChatUser == null) {
            Toast.makeText(this, "Kisi secilmedi!", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;

        addBubbleToChat(text, true);
        saveChatMessage(currentChatUser, text, true);
        etInput.setText("");

        String encrypted = aesEncrypt(text);
        String rawMsg = "TO:" + currentChatUser + ":ENC:" + encrypted;

        if (isConnected) {
            sendRaw(rawMsg);
        } else {
            // Çevrimdışıysak kuyruğa ekle
            saveToOfflineQueue(rawMsg);
            Toast.makeText(this, "Çevrimdışısınız, mesajınız kuyruğa eklendi.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToOfflineQueue(String rawMsg) {
        try {
            String qStr = prefs.getString("offline_queue", "[]");
            JSONArray arr = new JSONArray(qStr);
            arr.put(rawMsg);
            prefs.edit().putString("offline_queue", arr.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void flushOfflineQueue() {
        try {
            String qStr = prefs.getString("offline_queue", "[]");
            JSONArray arr = new JSONArray(qStr);
            if (arr.length() > 0) {
                for (int i = 0; i < arr.length(); i++) {
                    sendRaw(arr.getString(i));
                }
                prefs.edit().remove("offline_queue").apply();
            }
        } catch (Exception e) { e.printStackTrace(); }
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

                out.println("AUTH:" + username + ":" + deviceToken);

                mainHandler.post(() -> {
                    tvContactsStatus.setText("Baglandi - " + username);
                    tvContactsStatus.setTextColor(Color.parseColor("#A5D6A7"));
                    // Baglanir baglanmaz kuyruktaki mesajlari gonder
                    flushOfflineQueue();
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
        if (msg.startsWith("AUTH_FAIL")) {
            shouldReconnect = false;
            disconnectServer();
            prefs.edit().remove("username").apply();
            username = "";
            Toast.makeText(this, "Bu isim baska bir cihaza kayitli! Lutfen farkli bir isim secin.", Toast.LENGTH_LONG).show();
            askUsername();
        } else if (msg.startsWith("AUTH_OK")) {
            // Yetkilendirme basarili
        } else if (msg.startsWith("USERLIST:")) {
            String list = msg.substring(9);
            onlineUsers.clear();
            if (!list.isEmpty()) {
                String[] users = list.split(",");
                for (String u : users) {
                    String trimmed = u.trim();
                    // Kendi adimizi ve boslari filtrele
                    if (!trimmed.isEmpty() && !trimmed.equals(username)
                            && !onlineUsers.contains(trimmed)) {
                        onlineUsers.add(trimmed);
                    }
                }
            }
            refreshContactsUI();

        } else if (msg.startsWith("ONLINE:")) {
            String user = msg.substring(7).trim();
            // Kendi adimizi ekleme, tekrardan kacin
            if (!user.equals(username) && !onlineUsers.contains(user)) {
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

                // Dosya chunk'i mi? Birleştirme işlemini devret, buble gösterme
                if (text.startsWith("[FILECHUNK:")) {
                    handleIncomingFileChunk(sender, text);
                    return;
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
        if (message != null && message.startsWith("[IMG]")) {
            try {
                String base64 = message.substring(5);
                byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                
                android.widget.ImageView iv = new android.widget.ImageView(this);
                iv.setImageBitmap(bmp);
                iv.setAdjustViewBounds(true);
                iv.setMaxWidth(dpToPx(240));
                iv.setMaxHeight(dpToPx(300));
                
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                if (isSent) {
                    bg.setColor(Color.parseColor("#1A73E8"));
                    bg.setCornerRadii(new float[]{28, 28, 28, 28, 4, 4, 28, 28});
                    params.gravity = Gravity.END;
                    params.setMargins(dpToPx(64), dpToPx(3), dpToPx(8), dpToPx(3));
                } else {
                    bg.setColor(Color.WHITE);
                    bg.setCornerRadii(new float[]{4, 4, 28, 28, 28, 28, 28, 28});
                    params.gravity = Gravity.START;
                    params.setMargins(dpToPx(8), dpToPx(3), dpToPx(64), dpToPx(3));
                }
                iv.setBackground(bg);
                iv.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
                iv.setLayoutParams(params);
                chatContainer.addView(iv);
                chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
            } catch (Exception e) { e.printStackTrace(); }
            return;
        }

        if (message != null && message.startsWith("[FILE:")) {
            addFileBubble(message, isSent);
            return;
        }

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

    // ═══════════════════════════════════════════════════════════════════════
    // DOSYA GONDERME
    // ═══════════════════════════════════════════════════════════════════════

    private void openFilePicker() {
        if (currentChatUser == null) {
            Toast.makeText(this, "Önce bir kişi seçin.", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES, new String[]{
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        });
        intent.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_FILE);
    }

    private void sendFilesSequentially(java.util.List<android.net.Uri> uris, int index) {
        if (index >= uris.size() || currentChatUser == null) return;
        sendFile(uris.get(index), () -> sendFilesSequentially(uris, index + 1));
    }

    private void sendFile(android.net.Uri uri, Runnable onComplete) {
        new Thread(() -> {
            try {
                String filename = getFileName(uri);
                long fileSize   = getFileSize(uri);

                if (fileSize > 0 && fileSize > MAX_FILE_SIZE) {
                    mainHandler.post(() -> Toast.makeText(this,
                        "\"" + filename + "\" çok büyük! Maksimum 5 MB.", Toast.LENGTH_LONG).show());
                    if (onComplete != null) mainHandler.post(onComplete);
                    return;
                }

                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) {
                    mainHandler.post(() -> Toast.makeText(this, "Dosya okunamadı.", Toast.LENGTH_SHORT).show());
                    if (onComplete != null) mainHandler.post(onComplete);
                    return;
                }
                byte[] allBytes;
                try {
                    allBytes = readAllBytes(is);
                } catch (OutOfMemoryError oom) {
                    mainHandler.post(() -> Toast.makeText(this,
                        "\"" + filename + "\" çok büyük, bellek yetersiz.", Toast.LENGTH_LONG).show());
                    if (onComplete != null) mainHandler.post(onComplete);
                    return;
                } finally {
                    is.close();
                }

                if (allBytes.length > MAX_FILE_SIZE) {
                    mainHandler.post(() -> Toast.makeText(this,
                        "\"" + filename + "\" çok büyük! Maksimum 5 MB.", Toast.LENGTH_LONG).show());
                    if (onComplete != null) mainHandler.post(onComplete);
                    return;
                }
                if (allBytes.length == 0) {
                    mainHandler.post(() -> Toast.makeText(this, "Dosya boş.", Toast.LENGTH_SHORT).show());
                    if (onComplete != null) mainHandler.post(onComplete);
                    return;
                }

                int totalChunks = (allBytes.length + FILE_CHUNK_SIZE - 1) / FILE_CHUNK_SIZE;
                mainHandler.post(() -> Toast.makeText(this,
                    "\"" + filename + "\" gönderiliyor...", Toast.LENGTH_SHORT).show());

                for (int i = 0; i < totalChunks; i++) {
                    PrintWriter localOut = out;
                    if (!isConnected || localOut == null) {
                        mainHandler.post(() -> Toast.makeText(this,
                            "Bağlantı kesildi, dosya gönderilemedi.", Toast.LENGTH_LONG).show());
                        if (onComplete != null) mainHandler.post(onComplete);
                        return;
                    }
                    int    start   = i * FILE_CHUNK_SIZE;
                    int    end     = Math.min(start + FILE_CHUNK_SIZE, allBytes.length);
                    byte[] chunk   = Arrays.copyOfRange(allBytes, start, end);
                    String b64     = Base64.encodeToString(chunk, Base64.NO_WRAP);
                    String payload = "[FILECHUNK:" + filename + ":" + i + "/" + totalChunks + "]" + b64;
                    String enc     = aesEncrypt(payload);
                    localOut.println("TO:" + currentChatUser + ":ENC:" + enc);
                    Thread.sleep(50);
                }

                // Gonderici tarafinda da dosyayi yerel kaydet (gecmisten acilabilmesi icin)
                File saved   = saveFileLocally(allBytes, filename);
                String path  = saved != null ? saved.getAbsolutePath() : "";
                String tag   = "[FILE:" + filename + ":" + allBytes.length + ":" + path + "]";

                mainHandler.post(() -> {
                    addBubbleToChat(tag, true);
                    saveChatMessage(currentChatUser, tag, true);
                    Toast.makeText(this, "\"" + filename + "\" gönderildi.", Toast.LENGTH_SHORT).show();
                });

                if (onComplete != null) mainHandler.post(onComplete);

            } catch (Exception e) {
                Log.e(TAG, "Dosya gonderme hatasi", e);
                mainHandler.post(() -> Toast.makeText(this, "Dosya gönderilemedi.", Toast.LENGTH_SHORT).show());
                if (onComplete != null) mainHandler.post(onComplete);
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DOSYA ALMA (CHUNK BIRLESTIRME)
    // ═══════════════════════════════════════════════════════════════════════

    private void handleIncomingFileChunk(String sender, String text) {
        // Format: [FILECHUNK:filename:idx/total]base64data
        try {
            int headerEnd = text.indexOf(']');
            if (headerEnd < 0) return;
            String header  = text.substring(11, headerEnd); // "[FILECHUNK:" = 11 karakter
            String b64data = text.substring(headerEnd + 1);

            int lastColon = header.lastIndexOf(':');
            if (lastColon < 0) return;
            String filename = header.substring(0, lastColon);
            String idxInfo  = header.substring(lastColon + 1);

            int slashPos = idxInfo.indexOf('/');
            if (slashPos < 0) return;
            int idx   = Integer.parseInt(idxInfo.substring(0, slashPos));
            int total = Integer.parseInt(idxInfo.substring(slashPos + 1));
            if (total <= 0) return;

            // sender:filename anahtari - her ikisi de ':' icermez (protokol garantisi)
            String key = sender + ":" + filename;
            if (!incomingFileChunks.containsKey(key)) {
                incomingFileChunks.put(key, new HashMap<>());
                incomingFileTotalChunks.put(key, total);
            }
            incomingFileChunks.get(key).put(idx, b64data);

            if (incomingFileChunks.get(key).size() == total) {
                // Tum chunk'lar geldi - hemen HashMap'ten al ve birlestir
                Map<Integer, String> chunks = incomingFileChunks.remove(key);
                incomingFileTotalChunks.remove(key);
                reassembleFile(chunks, total, filename, sender);
            }
        } catch (Exception e) {
            Log.e(TAG, "Chunk isleme hatasi: " + e.getMessage());
        }
    }

    private void reassembleFile(Map<Integer, String> chunks, int total, String filename, String sender) {
        new Thread(() -> {
            try {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                for (int i = 0; i < total; i++) {
                    String b64 = chunks.get(i);
                    if (b64 == null) {
                        mainHandler.post(() -> Toast.makeText(this,
                            "\"" + filename + "\" eksik parça, alınamadı.", Toast.LENGTH_LONG).show());
                        return;
                    }
                    baos.write(Base64.decode(b64, Base64.NO_WRAP));
                }

                byte[] fileBytes = baos.toByteArray();
                File   saved     = saveFileLocally(fileBytes, filename);
                String path      = saved != null ? saved.getAbsolutePath() : "";
                String tag       = "[FILE:" + filename + ":" + fileBytes.length + ":" + path + "]";

                mainHandler.post(() -> {
                    saveChatMessage(sender, tag, false);
                    if (sender.equals(currentChatUser)) {
                        addBubbleToChat(tag, false);
                    } else {
                        Toast.makeText(this,
                            sender + " bir dosya gönderdi: " + filename, Toast.LENGTH_SHORT).show();
                        refreshContactsUI();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Dosya birlestirme hatasi", e);
                mainHandler.post(() -> Toast.makeText(this,
                    "\"" + filename + "\" alınamadı.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DOSYA BALONU (UI)
    // ═══════════════════════════════════════════════════════════════════════

    private void addFileBubble(String fileTag, boolean isSent) {
        // Format: [FILE:filename:size:filepath]
        String inner = fileTag.substring(6);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);
        String[] parts    = inner.split(":", 3);
        String   filename = parts.length > 0 ? parts[0] : "dosya";
        long     fileSize = parts.length > 1 ? parseLongSafe(parts[1]) : 0;
        String   filepath = parts.length > 2 ? parts[2] : "";

        // Uzantiya gore renk ve etiket
        String ext = getFileExtension(filename).toLowerCase();
        int    typeColor;
        String typeLabel;
        switch (ext) {
            case "pdf":  typeColor = Color.parseColor("#E53935"); typeLabel = "PDF"; break;
            case "doc":
            case "docx": typeColor = Color.parseColor("#1E88E5"); typeLabel = "DOC"; break;
            case "xls":
            case "xlsx": typeColor = Color.parseColor("#43A047"); typeLabel = "XLS"; break;
            case "ppt":
            case "pptx": typeColor = Color.parseColor("#FB8C00"); typeLabel = "PPT"; break;
            default:     typeColor = Color.parseColor("#757575");
                         typeLabel = ext.isEmpty() ? "FILE" : ext.toUpperCase(); break;
        }

        // Kart (balon)
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dpToPx(10), dpToPx(10), dpToPx(14), dpToPx(10));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (isSent) {
            cardParams.gravity = Gravity.END;
            cardParams.setMargins(dpToPx(64), dpToPx(3), dpToPx(8), dpToPx(3));
        } else {
            cardParams.gravity = Gravity.START;
            cardParams.setMargins(dpToPx(8), dpToPx(3), dpToPx(64), dpToPx(3));
        }
        card.setLayoutParams(cardParams);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setColor(isSent ? Color.parseColor("#1A73E8") : Color.WHITE);
        cardBg.setCornerRadii(isSent
                ? new float[]{28, 28, 28, 28, 4, 4, 28, 28}
                : new float[]{4, 4, 28, 28, 28, 28, 28, 28});
        card.setBackground(cardBg);

        // Renkli tür rozeti (PDF / DOC / XLS / PPT)
        TextView badge = new TextView(this);
        badge.setText(typeLabel);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(10);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dpToPx(42), dpToPx(52));
        badge.setLayoutParams(badgeParams);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.RECTANGLE);
        badgeBg.setColor(typeColor);
        badgeBg.setCornerRadius(dpToPx(6));
        badge.setBackground(badgeBg);
        card.addView(badge);

        // Dosya adi ve boyut
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dpToPx(10), 0, 0, 0);

        TextView nameView = new TextView(this);
        nameView.setText(filename);
        nameView.setTextSize(14);
        nameView.setTypeface(null, Typeface.BOLD);
        nameView.setTextColor(isSent ? Color.WHITE : Color.parseColor("#212121"));
        nameView.setMaxWidth(dpToPx(160));
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(nameView);

        TextView subView = new TextView(this);
        String sub = (fileSize > 0 ? formatFileSize(fileSize) + " • " : "") + "Aç";
        subView.setText(sub);
        subView.setTextSize(12);
        subView.setTextColor(isSent ? Color.parseColor("#BBDEFB") : Color.parseColor("#757575"));
        info.addView(subView);

        card.addView(info);

        final String finalPath = filepath;
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> openFile(finalPath, filename));

        chatContainer.addView(card);
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void openFile(String filepath, String filename) {
        if (filepath == null || filepath.isEmpty()) {
            Toast.makeText(this, "Dosya yolu bulunamadı.", Toast.LENGTH_SHORT).show();
            return;
        }
        File file = new File(filepath);
        if (!file.exists()) {
            Toast.makeText(this, "Dosya bulunamadı.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.net.Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".provider", file);
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uri, getFileMimeType(filename));
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(intent, "Aç"));
        } catch (Exception e) {
            Log.e(TAG, "Dosya acilamadi", e);
            Toast.makeText(this, "Bu dosyayı açacak uygulama bulunamadı.", Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DOSYA YARDIMCI METODLARI
    // ═══════════════════════════════════════════════════════════════════════

    private String getFileName(android.net.Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        if (result == null) result = uri.getLastPathSegment();
        if (result == null) result = "dosya";
        // ':' ve '[]' karakterleri protokolde özel anlam taşır, temizle
        return result.replaceAll("[:\\[\\]]", "_");
    }

    private long getFileSize(android.net.Uri uri) {
        android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return 0;
    }

    private byte[] readAllBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    private File saveFileLocally(byte[] data, String filename) {
        try {
            File extDir = getExternalFilesDir(null);
            if (extDir == null) extDir = getFilesDir();
            File dir = new File(extDir, "files");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, filename);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();
            return file;
        } catch (Exception e) {
            Log.e(TAG, "Dosya kaydedilemedi", e);
            return null;
        }
    }

    private String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private String getFileMimeType(String filename) {
        switch (getFileExtension(filename).toLowerCase()) {
            case "pdf":  return "application/pdf";
            case "doc":  return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":  return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":  return "application/vnd.ms-powerpoint";
            case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default:     return "*/*";
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024)             return bytes + " B";
        if (bytes < 1024 * 1024)      return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private long parseLongSafe(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveChatHistoryToStorage();
        disconnectServer();
    }
}

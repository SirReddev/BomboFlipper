package com.bomboflip.mod.api;

import com.bomboflip.mod.config.BomboFlipConfig;
import com.bomboflip.mod.flip.FlipAnalyzer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoflWebSocketClient implements WebSocket.Listener {

    // Using 127.0.0.1 bypasses Java's IPv6 localhost resolution bugs
    private static final String WS_URL = "ws://127.0.0.1:8080";

    private static CoflWebSocketClient instance;
    private WebSocket webSocket;
    private static final AtomicBoolean serverConnected = new AtomicBoolean(false);
    private final Gson gson = new Gson();

    public static boolean isServerConnected() {
        return serverConnected.get() && instance != null && instance.webSocket != null;
    }

    public static void connect() {
        if (serverConnected.get()) return;

        // Spawn a background Daemon thread that loops until it connects
        Thread connectThread = new Thread(() -> {
            while (!serverConnected.get()) {
                System.out.println("[BomboFlipper] Attempting connection to backend: " + WS_URL);
                try {
                    instance = new CoflWebSocketClient();
                    HttpClient httpClient = HttpClient.newHttpClient();

                    httpClient.newWebSocketBuilder()
                            .buildAsync(URI.create(WS_URL), instance)
                            .thenAccept(ws -> {
                                instance.webSocket = ws;
                                System.out.println("[BomboFlipper] WebSocket successfully connected!");
                                serverConnected.set(true);
                            })
                            .exceptionally(throwable -> {
                                System.err.println("[BomboFlipper] Connection refused. Retrying in 5s...");
                                return null;
                            }).join(); // Wait for the async connection attempt to finish

                    // If it failed, wait 5 seconds before looping again
                    if (!serverConnected.get()) {
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException ignored) {
                    break; // Exit safely if the thread is interrupted
                } catch (Exception e) {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        });
        connectThread.setName("BomboFlipper-Reconnect-Thread");
        connectThread.setDaemon(true); // Ensures this thread doesn't prevent Minecraft from closing
        connectThread.start();
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnecting");
        }
        serverConnected.set(false);
        instance = null;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println("[BomboFlipper] onOpen triggered! Connected to engine.");
        sendDebugMessage("§aConnected to backend WebSocket!");

        this.webSocket = webSocket;
        serverConnected.set(true);
        webSocket.request(1);
    }

    private final StringBuilder messageBuffer = new StringBuilder();

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);

        if (!last) {
            webSocket.request(1);
            return null;
        }

        String raw = messageBuffer.toString();
        messageBuffer.setLength(0); // Clear buffer for next message

        try {
            JsonObject json = gson.fromJson(raw, JsonObject.class);
            if (json.has("type")) {
                String type = json.get("type").getAsString();

                if ("ping".equals(type)) {
                    webSocket.request(1);
                    return null;
                }

                if ("chatMessage".equals(type)) {
                    String messageData = json.get("data").getAsString();

                    MinecraftClient.getInstance().execute(() -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player != null) {
                            mc.player.sendMessage(Text.literal(messageData), false);
                        }
                    });
                }

                if ("debugLog".equals(type)) {
                    if (BomboFlipConfig.getInstance().isDebugMode()) {
                        String debugMsg = json.has("message") ? json.get("message").getAsString() : "Unknown debug log";
                        sendDebugMessage(debugMsg);
                    }
                }

                if ("flip".equals(type)) {
                    FlipAnalyzer.processWebSocketFlip(json);
                }
            }
        } catch (Exception e) {
            System.err.println("[BomboFlipper] Error parsing websocket text: " + e.getMessage());
        }

        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println("[BomboFlipper] WebSocket closed: " + reason + " (Code: " + statusCode + ")");
        sendDebugMessage("§cDisconnected from backend WebSocket. Reason: " + reason);

        serverConnected.set(false);
        this.webSocket = null;

        // Auto-reconnect if the Node.js server shuts down
        connect();

        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println("[BomboFlipper] WebSocket error encountered. Reconnecting...");
        sendDebugMessage("§cWebSocket Error: " + error.getMessage());

        serverConnected.set(false);
        this.webSocket = null;

        // Auto-reconnect on crash
        connect();
    }

    private static void sendDebugMessage(String message) {
        if (BomboFlipConfig.getInstance().isDebugMode()) {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§7[BomboFlip Backend] " + message), false);
                }
            });
        }
    }
}
package com.example.desktopwindows;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatsController {

    @FXML private BorderPane rootPane;
    @FXML private VBox chatListBox;
    @FXML private Button tabAll;
    @FXML private Button tabPr;
    @FXML private Button tabGroups;

    private final HttpClient client = HttpClient.newHttpClient();

    private final List<String> chatIds = new ArrayList<>();
    private final List<String> chatNames = new ArrayList<>();
    private final List<String> chatTypes = new ArrayList<>();

    private final List<Long> allUserIds = new ArrayList<>();
    private final List<String> allUsernames = new ArrayList<>();

    private WebSocketClient currentWs = null;
    private String currentRoomId = null;
    private long currentUserId = -1;
    private int selectedChatIndex = -1;

    private enum Tab { ALL, PR, GROUPS }
    private Tab currentTab = Tab.ALL;

    private static final String ACTIVE_STYLE =
            "-fx-background-color: transparent; -fx-font-size: 14; " +
            "-fx-text-fill: #FAA030; -fx-font-weight: bold; " +
            "-fx-border-color: #FAA030; -fx-border-width: 0 0 2 0;";
    private static final String INACTIVE_STYLE =
            "-fx-background-color: transparent; -fx-font-size: 14; -fx-text-fill: #555;";

    @FXML
    public void initialize() {
        rootPane.setLeft(NavBar.build(NavBar.Page.CHATS,
                this::onNavHome, this::onNavCalendar, this::onNavChats, this::onNavSettings));
        Thread.ofVirtual().start(this::loadUsersAndChats);
        ContextMenu contextMenu = new ContextMenu();
        MenuItem openItem = new MenuItem("Открыть");
        MenuItem manageItem = new MenuItem("Управление группой");
        MenuItem deleteItem = new MenuItem("Удалить чат");

        openItem.setOnAction(e -> onOpenChatClick());
        manageItem.setOnAction(e -> onManageGroupChat());
        deleteItem.setOnAction(e -> onDeleteChatClick());

        contextMenu.getItems().addAll(openItem, manageItem, new SeparatorMenuItem(), deleteItem);

        // Добавляем меню к chatListBox (или к контейнеру со списком чатов)
        // chatListBox.setContextMenu(contextMenu);

        // Обработчик клика для выбора чата
      //  chatListBox.setOnMouseClicked(event -> {
            // Нужно определить какой чат выбран
            // Для этого нужно хранить список узлов или использовать lookup
       // });
    }


    private void loadUsersAndChats() {
        try {
            String username = extractUsernameFromToken();
            HttpRequest usersReq = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/user"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            HttpRequest chatsReq = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/chat/rooms"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();

            // Оба запроса параллельно
            var usersFuture = client.sendAsync(usersReq, HttpResponse.BodyHandlers.ofString());
            var chatsFuture = client.sendAsync(chatsReq, HttpResponse.BodyHandlers.ofString());

            String usersBody = usersFuture.get().body();
            String chatsBody = chatsFuture.get().body();

            // Парсим пользователей
            allUserIds.clear(); allUsernames.clear();
            Matcher m = Pattern.compile("\"id\":(\\d+),\"username\":\"([^\"]+)\"").matcher(usersBody);
            while (m.find()) {
                long uid = Long.parseLong(m.group(1));
                String uname = m.group(2);
                allUserIds.add(uid);
                allUsernames.add(uname);
                if (uname.equals(username)) currentUserId = uid;
            }

            // Парсим чаты
            chatIds.clear(); chatNames.clear(); chatTypes.clear();
            Pattern blockPat = Pattern.compile("\\{[^{}]*\"nameChat\"[^{}]*\\}", Pattern.DOTALL);
            Matcher blockM = blockPat.matcher(chatsBody);
            while (blockM.find()) {
                String block = blockM.group();
                Matcher idM   = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(block);
                Matcher nameM = Pattern.compile("\"nameChat\":\"([^\"]+)\"").matcher(block);
                Matcher typeM = Pattern.compile("\"type\":\"([^\"]+)\"").matcher(block);
                if (idM.find() && nameM.find()) {
                    chatIds.add(idM.group(1));
                    chatNames.add(nameM.group(1));
                    chatTypes.add(typeM.find() ? typeM.group(1) : "PRIVATE");
                }
            }

            Platform.runLater(this::renderChats);
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> showError("Ошибка загрузки: " + e.getMessage()));
        }
    }

    private void loadCurrentUser() {
        // Оставлен для совместимости, основная загрузка в loadUsersAndChats
    }

    private String extractUsernameFromToken() {
        try {
            String token = Session.getToken();
            String payload = token.split("\\.")[1];
            int pad = payload.length() % 4;
            if (pad != 0) payload += "=".repeat(4 - pad);
            String decoded = new String(java.util.Base64.getDecoder().decode(payload));
            Matcher m = Pattern.compile("\"sub\":\"([^\"]+)\"").matcher(decoded);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return "";
    }
    // ─── Загрузка проектов (чаты групп) ──────────────────────────────────────

    private void loadChats() {
        Thread.ofVirtual().start(() -> {
            chatIds.clear(); chatNames.clear(); chatTypes.clear();
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(Session.API_BASE + "/chat/rooms"))
                        .header("Authorization", "Bearer " + Session.getToken())
                        .GET().build();
                String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
                Pattern blockPat = Pattern.compile("\\{[^{}]*\"nameChat\"[^{}]*\\}", Pattern.DOTALL);
                Matcher blockM = blockPat.matcher(body);
                while (blockM.find()) {
                    String block = blockM.group();
                    Matcher idM   = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(block);
                    Matcher nameM = Pattern.compile("\"nameChat\":\"([^\"]+)\"").matcher(block);
                    Matcher typeM = Pattern.compile("\"type\":\"([^\"]+)\"").matcher(block);
                    if (idM.find() && nameM.find()) {
                        chatIds.add(idM.group(1));
                        chatNames.add(nameM.group(1));
                        chatTypes.add(typeM.find() ? typeM.group(1) : "PRIVATE");
                    }
                }
            } catch (Exception e) {
                Platform.runLater(() -> showError("Ошибка загрузки чатов: " + e.getMessage()));
                e.printStackTrace();
            }
            Platform.runLater(this::renderChats);
        });
    }

    // ─── Рендер списка чатов ──────────────────────────────────────────────────

    private void renderChats() {
        chatListBox.getChildren().clear();

        if (currentTab == Tab.ALL || currentTab == Tab.GROUPS) {
            for (int i = 0; i < chatIds.size(); i++) {
                if (chatTypes.get(i).equals("GROUP")) {
                    chatListBox.getChildren().add(buildGroupChatRow(i));
                }
            }
        }

        if (currentTab == Tab.ALL || currentTab == Tab.PR) {
            for (int i = 0; i < chatIds.size(); i++) {
                if (chatTypes.get(i).equals("PRIVATE")) {
                    chatListBox.getChildren().add(buildPlaceholderRow(i));
                }
            }
        }

        if (chatListBox.getChildren().isEmpty()) {
            Label empty = new Label("Чатов нет");
            empty.setStyle("-fx-text-fill: #888; -fx-font-size: 14; -fx-padding: 20;");
            chatListBox.getChildren().add(empty);
        }
    }

    private HBox buildGroupChatRow(int i) {
        // Иконка группы
        Label avatar = makeAvatar("👥", "#FAA030");

        VBox info = new VBox(1);
        Label nameLbl = new Label("Чат " + chatNames.get(i));
        nameLbl.setStyle("-fx-text-fill: black;-fx-font-size: 14; -fx-font-weight: bold;");
        info.getChildren().addAll(nameLbl);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox row = new HBox(12, avatar, info);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        row.setStyle("-fx-background-color: transparent; -fx-border-color: #EEEEEE;"
                + " -fx-border-width: 0 0 1 0; -fx-cursor: hand;");

        final String cid = chatIds.get(i);;
        final String cname = chatNames.get(i);
        row.setOnMouseClicked(e -> openChat(cid, cname, true));

        final int index = i;
        row.setOnMouseClicked(e -> {
            selectedChatIndex = index;
            openChat(cid, cname, true);
        });
        return row;
    }

    private HBox buildPlaceholderRow(int i) {
        Label avatar = makeAvatar("👤", "#AAAAAA");

        VBox info = new VBox(1);
        Label nameLbl = new Label(chatNames.get(i));
        nameLbl.setStyle("-fx-text-fill: black; -fx-font-size: 14; -fx-font-weight: bold;");
        info.getChildren().addAll(nameLbl);

        HBox row = new HBox(12, avatar, info);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        row.setStyle("-fx-background-color: transparent; -fx-border-color: #EEEEEE; -fx-border-width: 0 0 1 0;");
        final String cid = chatIds.get(i);
        final String cname = chatNames.get(i);
        row.setOnMouseClicked(e -> openChat(cid, cname, false));

        final int index = i;
        row.setOnMouseClicked(e -> {
            selectedChatIndex = index;
            openChat(cid, cname, false);
        });
        return row;
    }

    private Label makeAvatar(String icon, String color) {
        Label lbl = new Label(icon);
        lbl.setStyle("-fx-font-size: 18; -fx-background-color: " + color
                + "; -fx-background-radius: 50; -fx-padding: 8; -fx-alignment: center;");
        lbl.setMinSize(44, 44);
        lbl.setMaxSize(44, 44);
        lbl.setMouseTransparent(true);
        return lbl;
    }

    // ─── Открытие чата ────────────────────────────────────────────────────────

    private void openChat(String roomId, String roomName, boolean isGroup) {
        // Закрываем предыдущий WebSocket
        if (currentWs != null) {
            currentWs.close();
            currentWs = null;
        }
        currentRoomId = roomId;

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle((isGroup ? "Группа: " : "Чат: ") + roomName);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(550, 550);

        // История сообщений
        VBox messageBox = new VBox(8);
        messageBox.setPadding(new Insets(10));
        ScrollPane scroll = new ScrollPane(messageBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #F5F0EB;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        loadHistory(roomId, messageBox, scroll);

        // Панель управления (только для групп)
        HBox controlBar = null;
        if (isGroup) {
            controlBar = new HBox(8);
            controlBar.setAlignment(Pos.CENTER_LEFT);
            controlBar.setPadding(new Insets(8, 12, 8, 12));
            controlBar.setStyle("-fx-background-color: #EEEEEE; -fx-border-color: #CCCCCC; -fx-border-width: 0 0 1 0;");

            Button addMemberBtn = new Button("+ Добавить участника");
            addMemberBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-background-radius: 15; -fx-padding: 5 12;");
            addMemberBtn.setOnAction(e -> showAddMemberDialog(roomId, messageBox, scroll));

            Button removeMemberBtn = new Button("- Удалить участника");
            removeMemberBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-background-radius: 15; -fx-padding: 5 12;");
            removeMemberBtn.setOnAction(e -> showRemoveMemberDialog(roomId, messageBox, scroll));

            Button deleteChatBtn = new Button("🗑 Удалить чат");
            deleteChatBtn.setStyle("-fx-background-color: #FF5722; -fx-text-fill: white; -fx-background-radius: 15; -fx-padding: 5 12;");
            deleteChatBtn.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Удалить чат \"" + roomName + "\"? Все сообщения будут потеряны!",
                        ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText(null);
                confirm.showAndWait().ifPresent(btn -> {
                    if (btn == ButtonType.YES) {
                        deleteChat(roomId);
                        dialog.close();
                        loadChats();
                    }
                });
            });

            controlBar.getChildren().addAll(addMemberBtn, removeMemberBtn, deleteChatBtn);
        }

        // Поле ввода
        TextField inputField = new TextField();
        inputField.setPromptText("Сообщение...");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setStyle("-fx-background-radius: 20; -fx-padding: 8 12;");

        Button sendBtn = new Button("➤");
        sendBtn.setStyle("-fx-background-color: #FAA030; -fx-background-radius: 50;"
                + " -fx-font-size: 14; -fx-padding: 6 12; -fx-cursor: hand;");

        connectWebSocket(roomId, messageBox, scroll);

        Runnable sendAction = () -> {
            String text = inputField.getText().trim();
            if (text.isBlank()) return;

            if (currentWs != null && currentWs.isOpen()) {
                String escapedText = text.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n");
                String json = String.format("{\"roomId\":\"%s\",\"content\":\"%s\"}", roomId, escapedText);
                String sendFrame = "SEND\n" +
                        "destination:/app/chat\n" +
                        "content-type:application/json\n" +
                        "\n" +
                        json +
                        "\u0000";
                currentWs.send(sendFrame);
                inputField.clear();
            } else {
                showError("Нет подключения к серверу");
            }
        };

        sendBtn.setOnAction(e -> sendAction.run());
        inputField.setOnAction(e -> sendAction.run());

        HBox inputBar = new HBox(8, inputField, sendBtn);
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setPadding(new Insets(8));
        inputBar.setStyle("-fx-background-color: #EEEEEE;");

        VBox root = new VBox();
        if (isGroup && controlBar != null) {
            root.getChildren().addAll(controlBar, scroll, inputBar);
        } else {
            root.getChildren().addAll(scroll, inputBar);
        }
        root.setPrefHeight(500);
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setStyle("-fx-padding: 0;");

        dialog.setOnCloseRequest(e -> {
            if (currentWs != null) {
                currentWs.close();
                currentWs = null;
            }
        });

        dialog.showAndWait();
    }

    private void loadHistory(String roomId, VBox messageBox, ScrollPane scroll) {
        Thread.ofVirtual().start(() -> loadHistoryAsync(roomId, messageBox, scroll));
    }

    private void loadHistoryAsync(String roomId, VBox messageBox, ScrollPane scroll) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/chat/rooms/" + roomId + "/messages"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

            List<HBox> bubbles = new ArrayList<>();
            boolean hasMessages = false;

            if (body != null && !body.isBlank() && !body.equals("[]")) {
                Pattern blockPat = Pattern.compile("\\{[^{}]*\"content\"[^{}]*\\}", Pattern.DOTALL);
                Matcher blockM = blockPat.matcher(body);
                while (blockM.find()) {
                    String block = blockM.group();
                    Matcher contentM = Pattern.compile("\"content\":\"([^\"]+)\"").matcher(block);
                    Matcher senderM  = Pattern.compile("\"senderId\":(\\d+)").matcher(block);
                    Matcher nameM    = Pattern.compile("\"senderName\":\"([^\"]*)\"").matcher(block);
                    if (contentM.find()) {
                        hasMessages = true;
                        String content = contentM.group(1);
                        long senderId = senderM.find() ? Long.parseLong(senderM.group(1)) : -1;
                        String senderName = nameM.find() ? nameM.group(1) : "Неизвестный";
                        bubbles.add(buildBubble(content, senderId == currentUserId, senderName));
                    }
                }
            }

            boolean finalHasMessages = hasMessages;
            Platform.runLater(() -> {
                messageBox.getChildren().clear();
                if (finalHasMessages) {
                    messageBox.getChildren().addAll(bubbles);
                } else {
                    Label noMsg = new Label("Сообщений пока нет.\nБудьте первым!");
                    noMsg.setStyle("-fx-text-fill: #AAA; -fx-font-size: 13; -fx-padding: 20;");
                    noMsg.setWrapText(true);
                    messageBox.getChildren().add(noMsg);
                }
                scroll.setVvalue(1.0);
            });

        } catch (Exception e) {
            Platform.runLater(() -> {
                Label err = new Label("Ошибка загрузки сообщений: " + e.getMessage());
                err.setStyle("-fx-text-fill: #CC0000;");
                messageBox.getChildren().add(err);
            });
        }
    }

    // ─── WebSocket подключение ───────────────────────────────────────────────

    private void connectWebSocket(String roomId, VBox messageBox, ScrollPane scroll) {
        try {
            URI wsUri = new URI(Session.API_BASE.replace("https://", "wss://").replace("http://", "ws://") + "/ws");

            currentWs = new WebSocketClient(wsUri) {
                private boolean connected = false;

                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("WebSocket opened");

                    // 1. Отправляем CONNECT фрейм
                    String connectFrame = "CONNECT\n" +
                            "accept-version:1.2,1.1,1.0\n" +
                            "heart-beat:0,0\n" +
                            "Authorization:Bearer " + Session.getToken() + "\n" +
                            "\n" +
                            "\u0000";
                    send(connectFrame);
                    System.out.println(">>> CONNECT frame sent");
                }

                @Override
                public void onMessage(String message) {
                    System.out.println("\n=== RAW MESSAGE ===");
                    System.out.println(message);
                    System.out.println("=== END RAW ===\n");

                    if (message == null || message.trim().isEmpty()) {
                        return;
                    }

                    // Разбираем STOMP фрейм
                    String[] parts = message.split("\n\n", 2);
                    String headers = parts[0];
                    String body = parts.length > 1 ? parts[1] : "";

                    // Убираем null-символ из тела
                    if (body.endsWith("\u0000")) {
                        body = body.substring(0, body.length() - 1);
                    }

                    String[] headerLines = headers.split("\n");
                    String command = headerLines[0].trim();

                    System.out.println("Command: " + command);
                    System.out.println("Body: " + body);

                    // Обрабатываем CONNECTED ответ
                    if ("CONNECTED".equals(command)) {
                        connected = true;
                        System.out.println(">>> STOMP Connected successfully");

                        String subscribeFrame = "SUBSCRIBE\n" +
                                "id:sub-0\n" +
                                "destination:/topic/chat/" + roomId + "\n" +
                                "ack:auto\n" +
                                "\n" +
                                "\u0000";
                        send(subscribeFrame);
                        System.out.println(">>> SUBSCRIBE frame sent");
                        return;
                    }

                    // Обрабатываем MESSAGE фрейм
                    if ("MESSAGE".equals(command) && !body.isEmpty()) {
                        String content = extractJsonValue(body, "content");
                        String senderIdStr = extractJsonValue(body, "senderId");
                        String senderName = extractJsonValue(body, "senderName");
                        String msgRoomId = extractJsonValue(body, "roomId");

                        final String finalContent = content;
                        final String finalSenderIdStr = senderIdStr;
                        final String finalSenderName = senderName;
                        final String finalMsgRoomId = msgRoomId;

                        Platform.runLater(() -> {
                            if (finalContent == null || finalMsgRoomId == null) return;

                            if (!finalMsgRoomId.equals(roomId)) return;

                            long senderId = Long.parseLong(finalSenderIdStr);
                            boolean isMine = senderId == currentUserId;
                            String displayName = finalSenderName != null ? finalSenderName : "Неизвестный";

                            // Убираем заглушку "Сообщений пока нет"
                            messageBox.getChildren().removeIf(node ->
                                    node instanceof Label lbl &&
                                            lbl.getText() != null &&
                                            lbl.getText().contains("Сообщений пока нет"));

                            HBox bubble = buildBubble(finalContent, isMine, displayName);
                            messageBox.getChildren().add(bubble);
                            scroll.layout();
                            scroll.setVvalue(1.0);

                            System.out.println("Message added to UI from: " + displayName);
                        });
                    }
                }

                // Вспомогательный метод для парсинга JSON значений
                private String extractJsonValue(String json, String key) {
                    String pattern = "\"" + key + "\":";
                    int keyIndex = json.indexOf(pattern);
                    if (keyIndex == -1) return null;

                    int start = keyIndex + pattern.length();

                    // Пропускаем пробелы
                    while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
                        start++;
                    }

                    if (start >= json.length()) return null;

                    char firstChar = json.charAt(start);

                    if (firstChar == '"') {
                        // Строковое значение
                        int end = start + 1;
                        while (end < json.length() && json.charAt(end) != '"') {
                            if (json.charAt(end) == '\\') end++; // пропускаем экранированные символы
                            end++;
                        }
                        return json.substring(start + 1, end);
                    } else if (firstChar == '{' || firstChar == '[') {
                        // Объект или массив - не обрабатываем для простоты
                        return null;
                    } else {
                        // Числовое или булево значение
                        int end = start;
                        while (end < json.length() &&
                                (Character.isDigit(json.charAt(end)) ||
                                        json.charAt(end) == '.' ||
                                        json.charAt(end) == '-' ||
                                        json.charAt(end) == '+' ||
                                        json.charAt(end) == 'e' ||
                                        json.charAt(end) == 'E')) {
                            end++;
                        }
                        return json.substring(start, end);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("WebSocket closed: " + reason + " (code: " + code + ")");
                    connected = false;
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("WebSocket error: " + ex.getMessage());
                    ex.printStackTrace();
                }
            };

            if (wsUri.getScheme().equals("wss")) {
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
                sslContext.init(null, null, null);
                currentWs.setSocketFactory(sslContext.getSocketFactory());
            }
            currentWs.connect();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Ошибка подключения к WebSocket: " + e.getMessage());
        }
    }

    private HBox buildBubble(String text, boolean isMine, String senderName) {
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setMaxWidth(280);
        lbl.setPadding(new Insets(8, 12, 8, 12));
        lbl.setStyle("-fx-background-color: " + (isMine ? "#FAA030" : "#E0E0E0")
                + "; -fx-background-radius: 18; -fx-font-size: 13;");

        VBox bubble = new VBox(2);
        if (!isMine) {
            Label nameLbl = new Label(senderName);
            nameLbl.setStyle("-fx-font-size: 11; -fx-text-fill: #888;");
            bubble.getChildren().add(nameLbl);
        }
        bubble.getChildren().add(lbl);
        bubble.setMaxWidth(300);

        HBox box = new HBox(bubble);
        box.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private void loadAllUsers() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/user"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            allUserIds.clear();
            allUsernames.clear();

            Pattern pat = Pattern.compile("\"id\":(\\d+),\"username\":\"([^\"]+)\"");
            Matcher m = pat.matcher(resp.body());
            while (m.find()) {
                allUserIds.add(Long.parseLong(m.group(1)));
                allUsernames.add(m.group(2));
            }
        } catch (Exception e) {
            showError("Ошибка загрузки пользователей: " + e.getMessage());
        }
    }

    // ─── Добавить личную задачу ───────────────────────────────────────────────

    @FXML
    protected void onAddChatClick() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Новый чат");
        ButtonType createBtn = new ButtonType("Создать", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        Label titleLabel = new Label("Название чата:");
        TextField titleField = new TextField();
        titleField.setPromptText("Название чата...");
        titleField.setStyle("-fx-background-color: #E0E0E0; -fx-background-radius: 8; -fx-padding: 8;");

        RadioButton button1 = new RadioButton("Личные сообщения");
        RadioButton button2 = new RadioButton("Общий чат");
        ToggleGroup group = new ToggleGroup();
        button1.setToggleGroup(group);
        button2.setToggleGroup(group);
        button1.setSelected(true);

        // Поле названия скрыто для личных чатов
        titleLabel.setVisible(false);
        titleLabel.setManaged(false);
        titleField.setVisible(false);
        titleField.setManaged(false);

        button1.setOnAction(e -> {
            titleLabel.setVisible(false); titleLabel.setManaged(false);
            titleField.setVisible(false); titleField.setManaged(false);
        });
        button2.setOnAction(e -> {
            titleLabel.setVisible(true); titleLabel.setManaged(true);
            titleField.setVisible(true); titleField.setManaged(true);
        });

        ComboBox<String> userCombo = new ComboBox<>();
        userCombo.setPromptText("Выберите участника");
        userCombo.setPrefWidth(250);
        for (int i = 0; i < allUsernames.size(); i++) {
            if (!allUserIds.get(i).equals(currentUserId)) {
                userCombo.getItems().add(allUsernames.get(i));
            }
        }
        Button addBtn = new Button("Добавить");
        Label searchStatus = new Label();

        List<Long> selectedIds = new ArrayList<>();
        ObservableList<String> selectedNames = FXCollections.observableArrayList();
        ListView<String> selectedList = new ListView<>(selectedNames);
        selectedList.setPrefHeight(100);

        // Для группового чата — список участников; для личного — один ComboBox
        addBtn.setOnAction(e -> {
            String selected = userCombo.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            int i = allUsernames.indexOf(selected);
            if (i < 0) return;
            Long uid = allUserIds.get(i);
            if (selectedIds.contains(uid)) {
                searchStatus.setText("Уже добавлен");
                return;
            }
            selectedIds.add(uid);
            selectedNames.add(selected);
            userCombo.getSelectionModel().clearSelection();
            searchStatus.setText("");
        });

        VBox content = new VBox(8,
                new Label("Выберите тип чата:"), button1, button2,
                titleLabel, titleField,
                new Label("Добавить участников:"),
                new HBox(5, userCombo, addBtn),
                searchStatus,
                selectedList);
        content.setPrefWidth(340);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != createBtn) return;
            boolean isPrivate = button1.isSelected();
            if (isPrivate) {
                // Для личного чата нужен ровно один участник
                String selected = userCombo.getSelectionModel().getSelectedItem();
                if (selected == null && selectedIds.isEmpty()) {
                    showError("Выберите собеседника");
                    return;
                }
                // Если не нажали "Добавить" — берём текущий выбор в комбобоксе
                if (selectedIds.isEmpty() && selected != null) {
                    int i = allUsernames.indexOf(selected);
                    if (i >= 0) { selectedIds.add(allUserIds.get(i)); selectedNames.add(selected); }
                }
                if (selectedIds.size() != 1) {
                    showError("В личный чат можно добавить только одного собеседника");
                    return;
                }
                String chatName = selectedNames.get(0);
                Thread.ofVirtual().start(() -> { CreateChat(chatName, selectedIds, true); loadChats(); });
            } else {
                String t = titleField.getText().trim();
                if (t.isBlank()) { showError("Введите название группового чата"); return; }
                if (selectedIds.isEmpty()) { showError("Добавьте хотя бы одного участника"); return; }
                Thread.ofVirtual().start(() -> { CreateChat(t, selectedIds, false); loadChats(); });
            }
        });
    }

    private void CreateChat(String name, List<Long> memberIds, boolean isPrivate){
        try {
            String endpoint;
            String json = null;

            if (isPrivate) {
                // Личный чат: POST /chat/private?name=...&userId=...
                if (memberIds.isEmpty()) {
                    showError("Для личного чата нужен собеседник");
                    return;
                }
                String encodedName = java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8);
                endpoint = Session.API_BASE + "/chat/private?name=" + encodedName + "&userId=" + memberIds.get(0);
            } else {
                // Групповой чат: POST /chat/group с телом {name, members}
                endpoint = Session.API_BASE + "/chat/group";
                StringBuilder membersJson = new StringBuilder();
                for (int i = 0; i < memberIds.size(); i++) {
                    if (i > 0) membersJson.append(",");
                    membersJson.append(memberIds.get(i));
                }
                json = "{\"name\":\"" + name.replace("\"", "\\\"") + "\",\"members\":[" + membersJson + "]}";
            }
            System.out.println(">>> SENDING POST to: " + endpoint);  // ДОБАВЬ ЭТО
            HttpRequest.BodyPublisher body = isPrivate
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + Session.getToken())
                    .POST(body)
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println(">>> RESPONSE: " + resp.statusCode() + " " + resp.body());  // ДОБАВЬ ЭТО
            if (resp.statusCode() != 200) {
                showError("Ошибка создания чата (" + resp.statusCode() + "): " + resp.body());

            }
        } catch (Exception e) {
            showError("Ошибка: " + e.getMessage());
            e.printStackTrace();  // ДОБАВЬ ЭТО
        }
    }

    // ─── Управление чатом ───────────────────────────────────────────────

    private void showAddMemberDialog(String roomId, VBox messageBox, ScrollPane scroll) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Добавить участника");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> userCombo = new ComboBox<>();
        userCombo.setPromptText("Выберите пользователя");
        userCombo.setPrefWidth(250);

        for (int i = 0; i < allUsernames.size(); i++) {
            if (!allUserIds.get(i).equals(currentUserId)) {
                userCombo.getItems().add(allUsernames.get(i));
            }
        }

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(new Label("Выберите пользователя:"), userCombo);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                String selected = userCombo.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    int idx = allUsernames.indexOf(selected);
                    if (idx >= 0) {
                        addMemberToGroup(roomId, allUserIds.get(idx));
                        messageBox.getChildren().clear();
                        loadHistory(roomId, messageBox, scroll);
                    }
                }
            }
        });
    }

    private void showRemoveMemberDialog(String roomId, VBox messageBox, ScrollPane scroll) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Удалить участника");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> memberCombo = new ComboBox<>();
        memberCombo.setPromptText("Выберите участника");
        memberCombo.setPrefWidth(250);

        loadMembersForGroup(roomId, memberCombo);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(new Label("Выберите участника для удаления:"), memberCombo);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                String selected = memberCombo.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    int idx = allUsernames.indexOf(selected);
                    if (idx >= 0) {
                        removeMemberFromGroup(roomId, allUserIds.get(idx));
                        messageBox.getChildren().clear();
                        loadHistory(roomId, messageBox, scroll);
                    }
                }
            }
        });
    }

    @FXML
    protected void onOpenChatClick() {
        if (selectedChatIndex < 0 || selectedChatIndex >= chatIds.size()) {
            showError("Выберите чат");
            return;
        }
        openChat(chatIds.get(selectedChatIndex), chatNames.get(selectedChatIndex),
                chatTypes.get(selectedChatIndex).equals("GROUP"));
    }

    @FXML
    protected void onDeleteChatClick() {
        if (selectedChatIndex < 0 || selectedChatIndex >= chatIds.size()) {
            showError("Выберите чат");
            return;
        }

        String chatName = chatNames.get(selectedChatIndex);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить чат \"" + chatName + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                deleteChat(chatIds.get(selectedChatIndex));
                loadChats();
            }
        });
    }

    private void deleteChat(String roomId) {
        try {
            // DELETE запрос на удаление чата (нужно добавить на сервере)
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/chat/rooms/" + roomId))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .DELETE()
                    .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            showError("Ошибка удаления чата: " + e.getMessage());
        }
    }

    @FXML
    protected void onManageGroupChat() {
        if (selectedChatIndex < 0 || selectedChatIndex >= chatIds.size()) {
            showError("Выберите чат");
            return;
        }

        String type = chatTypes.get(selectedChatIndex);
        if (!type.equals("GROUP")) {
            showError("Управление доступно только для групповых чатов");
            return;
        }

        String roomId = chatIds.get(selectedChatIndex);
        String roomName = chatNames.get(selectedChatIndex);

        // Диалог управления группой
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Управление группой: " + roomName);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setPrefWidth(350);

        // Добавить участника
        TextField addField = new TextField();
        addField.setPromptText("Никнейм пользователя");
        Button addBtn = new Button("Добавить участника");
        addBtn.setOnAction(e -> {
            String username = addField.getText().trim();
            if (username.isBlank()) return;

            int idx = allUsernames.indexOf(username);
            if (idx < 0) {
                showError("Пользователь не найден");
                return;
            }
            addMemberToGroup(roomId, allUserIds.get(idx));
            addField.clear();
            loadChats();
            dialog.close();
        });

        // Удалить участника
        ComboBox<String> memberCombo = new ComboBox<>();
        Button removeBtn = new Button("Удалить участника");
        removeBtn.setOnAction(e -> {
            String selected = memberCombo.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            int idx = allUsernames.indexOf(selected);
            if (idx < 0) return;

            Long userId = allUserIds.get(idx);
            if (userId.equals(currentUserId)) {
                showError("Нельзя удалить себя");
                return;
            }

            removeMemberFromGroup(roomId, userId);
            loadChats();
            dialog.close();
        });

        loadMembersForGroup(roomId, memberCombo);

        content.getChildren().addAll(
                new Label("Добавить участника:"), addField, addBtn,
                new Separator(),
                new Label("Удалить участника:"), memberCombo, removeBtn
        );

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void addMemberToGroup(String roomId, Long userId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/chat/group/" + roomId + "/member?userId=" + userId))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                showError("Ошибка: " + resp.body());
            }
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void removeMemberFromGroup(String roomId, Long userId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/chat/group/" + roomId + "/member/" + userId))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .DELETE()
                    .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void loadMembersForGroup(String roomId, ComboBox<String> combo) {
        combo.getItems().clear();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/chat/rooms/" + roomId))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET()
                    .build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

            System.out.println("DEBUG room response: " + body);

            // Парсим memberIds из ответа
            Pattern memberPattern = Pattern.compile("\"memberIds\":\\[([^\\]]+)\\]");
            Matcher memberMatcher = memberPattern.matcher(body);

            if (memberMatcher.find()) {
                String membersStr = memberMatcher.group(1);
                String[] ids = membersStr.split(",");
                for (String idStr : ids) {
                    Long uid = Long.parseLong(idStr.trim());
                    int idx = allUserIds.indexOf(uid);
                    if (idx >= 0 && !uid.equals(currentUserId)) {
                        combo.getItems().add(allUsernames.get(idx));
                    }
                }
            }

            if (combo.getItems().isEmpty()) {
                combo.getItems().add("-- нет участников для удаления --");
            }

        } catch (Exception e) {
            e.printStackTrace();
            showError("Ошибка загрузки участников: " + e.getMessage());
        }
    }
    // ─── Вкладки ─────────────────────────────────────────────────────────────

    @FXML protected void onTabAll()    { setTab(Tab.ALL); }
    @FXML protected void onTabDm()     { setTab(Tab.PR); }
    @FXML protected void onTabGroups() { setTab(Tab.GROUPS); }

    private void setTab(Tab tab) {
        currentTab = tab;
        tabAll.setStyle(tab == Tab.ALL    ? ACTIVE_STYLE : INACTIVE_STYLE);
        tabPr.setStyle(tab == Tab.PR      ? ACTIVE_STYLE : INACTIVE_STYLE);
        tabGroups.setStyle(tab == Tab.GROUPS ? ACTIVE_STYLE : INACTIVE_STYLE);
        renderChats();
    }

    // ─── Навигация ────────────────────────────────────────────────────────────

    @FXML protected void onNavHome()     { navigate("main-view.fxml"); }
    @FXML protected void onNavCalendar() { navigate("calendar-view.fxml"); }
    @FXML protected void onNavChats()    { /* already here */ }
    @FXML protected void onNavSettings() { navigate("settings-view.fxml"); }

    private void navigate(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) chatListBox.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), stage.getWidth(), stage.getHeight()));
        } catch (Exception e) {
            showError("Ошибка навигации: " + e.getMessage());
        }
    }

    private void setSelectedChat(int index) {
        selectedChatIndex = index;
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}

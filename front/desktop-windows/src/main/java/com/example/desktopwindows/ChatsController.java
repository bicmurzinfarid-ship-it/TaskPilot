package com.example.desktopwindows;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatsController {

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

    private enum Tab { ALL, PR, GROUPS }
    private Tab currentTab = Tab.ALL;

    private static final String ACTIVE_STYLE =
            "-fx-background-color: transparent; -fx-font-size: 14; " +
            "-fx-text-fill: #FAA757; -fx-font-weight: bold; " +
            "-fx-border-color: #FAA757; -fx-border-width: 0 0 2 0;";
    private static final String INACTIVE_STYLE =
            "-fx-background-color: transparent; -fx-font-size: 14; -fx-text-fill: #555;";

    @FXML
    public void initialize() {
        loadChats();
        loadAllUsers();
    }

    // ─── Загрузка проектов (чаты групп) ──────────────────────────────────────

    private void loadChats() {
        chatIds.clear();
        chatNames.clear();
        chatTypes.clear();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/chat/rooms"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

            System.out.println(">>> CHATS BODY: " + body);  // отладка

            // Парсим каждый объект ChatRoom: от { до }
            Pattern blockPat = Pattern.compile("\\{[^{}]*\"nameChat\"[^{}]*\\}", Pattern.DOTALL);
            Matcher blockM = blockPat.matcher(body);
            while (blockM.find()) {
                String block = blockM.group();
                System.out.println(">>> BLOCK: " + block);  // отладка

                Matcher idM   = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(block);
                Matcher nameM = Pattern.compile("\"nameChat\":\"([^\"]+)\"").matcher(block);
                Matcher typeM = Pattern.compile("\"type\":\"([^\"]+)\"").matcher(block);

                if (idM.find() && nameM.find()) {
                    chatIds.add(idM.group(1));
                    chatNames.add(nameM.group(1));
                    chatTypes.add(typeM.find() ? typeM.group(1) : "PRIVATE");
                    System.out.println(">>> ADDED: " + idM.group(1) + " " + nameM.group(1));
                }
            }
        } catch (Exception e) {
            showError("Ошибка загрузки чатов: " + e.getMessage());
            e.printStackTrace();
        }
        renderChats();
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
        Label avatar = makeAvatar("👥", "#FAA757");

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

    private void openChat(String roomId, String projectName, boolean isGroup) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle((isGroup ? "Чат " : "") + projectName);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(500, 500);

        // История сообщений
        VBox messageBox = new VBox(8);
        messageBox.setPadding(new Insets(10));
        ScrollPane scroll = new ScrollPane(messageBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #F5F0EB;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Заглушка — сообщений нет
        Label noMsg = new Label("Сообщений пока нет.\nБудьте первым!");
        noMsg.setStyle("-fx-text-fill: #AAA; -fx-font-size: 13; -fx-padding: 20;");
        noMsg.setWrapText(true);
        messageBox.getChildren().add(noMsg);

        // Поле ввода
        TextField inputField = new TextField();
        inputField.setPromptText("Сообщение...");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setStyle("-fx-background-radius: 20; -fx-padding: 8 12;");

        Button sendBtn = new Button("➤");
        sendBtn.setStyle("-fx-background-color: #FAA757; -fx-background-radius: 50;"
                + " -fx-font-size: 14; -fx-padding: 6 12; -fx-cursor: hand;");

        Runnable sendAction = () -> {
            String text = inputField.getText().trim();
            if (text.isBlank()) return;

            // Убираем заглушку при первом сообщении
            messageBox.getChildren().remove(noMsg);

            // Пузырь "мои" сообщения
            HBox bubble = buildBubble(text, true);
            messageBox.getChildren().add(bubble);
            inputField.clear();

            // Прокрутка вниз
            scroll.layout();
            scroll.setVvalue(1.0);
        };

        sendBtn.setOnAction(e -> sendAction.run());
        inputField.setOnAction(e -> sendAction.run());

        HBox inputBar = new HBox(8, inputField, sendBtn);
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setPadding(new Insets(8));
        inputBar.setStyle("-fx-background-color: #EEEEEE;");

        VBox root = new VBox(scroll, inputBar);
        root.setPrefHeight(460);
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setStyle("-fx-padding: 0;");
        dialog.showAndWait();
    }

    private HBox buildBubble(String text, boolean isMine) {
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setMaxWidth(280);
        lbl.setPadding(new Insets(8, 12, 8, 12));
        lbl.setStyle("-fx-background-color: " + (isMine ? "#FAA757" : "#E0E0E0")
                + "; -fx-background-radius: 18; -fx-font-size: 13;");

        HBox box = new HBox(lbl);
        box.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private void loadAllUsers() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/user"))
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

        TextField titleField = new TextField();
        titleField.setPromptText("Название чата...");
        titleField.setStyle("-fx-background-color: #E0E0E0; -fx-background-radius: 8; -fx-padding: 8;");

        Label choiceType = new Label("Выберите тип чата");
        RadioButton button1 = new RadioButton("Личные сообщения");
        RadioButton button2 = new RadioButton("Общий чат");
        ToggleGroup group = new ToggleGroup();
        button1.setToggleGroup(group);
        button2.setToggleGroup(group);
        button1.setSelected(true);
        //descField.setPrefRowCount(3);
        //descField.setStyle("-fx-background-color: #E0E0E0; -fx-background-radius: 8;");

        TextField searchField = new TextField();
        searchField.setPromptText("Никнейм участника");
        Button addBtn = new Button("Добавить");
        Label searchStatus = new Label();

        List<Long> selectedIds = new ArrayList<>();
        ObservableList<String> selectedNames = FXCollections.observableArrayList();
        ListView<String> selectedList = new ListView<>(selectedNames);
        selectedList.setPrefHeight(100);

        addBtn.setOnAction(e -> {
            String query = searchField.getText().trim();
            if (query.isBlank()) return;
            int i = allUsernames.indexOf(query);

            if (i < 0) {
                searchStatus.setText("Пользователь «" + query + "» не найден");
                return;
            }
            Long uid = allUserIds.get(i);
            if (selectedIds.contains(uid)) {
                searchStatus.setText("Уже добавлен");
                return;
            }
            selectedIds.add(uid);
            selectedNames.add(query);
            searchField.clear();
            searchStatus.setText("");
        });

        VBox content = new VBox(8,
                new Label("Назовите свою задачу:"), titleField,
                new Label("Выберите тип чата"), button1, button2,
                new Label("Добавить участников:"),
                new HBox(5, searchField, addBtn),
                searchStatus,
                selectedList);
        content.setPrefWidth(340);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != createBtn) return;
            String t = titleField.getText().trim();
            if (t.isBlank()) { showError("Название не может быть пустым"); return; }
            if(selectedIds.size() != 1 && button1.isSelected() && !button2.isSelected()){
                showError("В личный чат можно добавить только одного собеседника");
                return;
            }
            CreateChat(t, selectedIds, button1.isSelected());
            loadChats();
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
                endpoint = "http://localhost:8080/chat/private?name=" + encodedName + "&userId=" + memberIds.get(0);
            } else {
                // Групповой чат: POST /chat/group с телом {name, members}
                endpoint = "http://localhost:8080/chat/group";
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

    private void navigate(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) chatListBox.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), stage.getWidth(), stage.getHeight()));
        } catch (Exception e) {
            showError("Ошибка навигации: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}

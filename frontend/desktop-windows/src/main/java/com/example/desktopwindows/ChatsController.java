package com.example.desktopwindows;

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
    @FXML private Button tabDm;
    @FXML private Button tabGroups;

    private final HttpClient client = HttpClient.newHttpClient();

    // Проекты — чаты групп
    private final List<Long>   projectIds   = new ArrayList<>();
    private final List<String> projectNames = new ArrayList<>();

    private enum Tab { ALL, DM, GROUPS }
    private Tab currentTab = Tab.ALL;

    private static final String ACTIVE_STYLE =
            "-fx-background-color: transparent; -fx-font-size: 14; " +
            "-fx-text-fill: #FAA757; -fx-font-weight: bold; " +
            "-fx-border-color: #FAA757; -fx-border-width: 0 0 2 0;";
    private static final String INACTIVE_STYLE =
            "-fx-background-color: transparent; -fx-font-size: 14; -fx-text-fill: #555;";

    @FXML
    public void initialize() {
        loadProjects();
    }

    // ─── Загрузка проектов (чаты групп) ──────────────────────────────────────

    private void loadProjects() {
        projectIds.clear();
        projectNames.clear();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/project"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

            Pattern blockPat = Pattern.compile("\\{(.*?)\"members\"", Pattern.DOTALL);
            Matcher blockM = blockPat.matcher(body);
            while (blockM.find()) {
                String block = blockM.group(1);
                if (!block.contains("\"creatorId\"")) continue;
                Matcher idM   = Pattern.compile("\"id\":(\\d+)").matcher(block);
                Matcher nameM = Pattern.compile("\"name\":\"([^\"]+)\"").matcher(block);
                if (idM.find() && nameM.find()) {
                    projectIds.add(Long.parseLong(idM.group(1)));
                    projectNames.add(nameM.group(1));
                }
            }
        } catch (Exception e) {
            showError("Ошибка загрузки проектов: " + e.getMessage());
        }
        renderChats();
    }

    // ─── Рендер списка чатов ──────────────────────────────────────────────────

    private void renderChats() {
        chatListBox.getChildren().clear();

        if (currentTab == Tab.ALL || currentTab == Tab.GROUPS) {
            for (int i = 0; i < projectIds.size(); i++) {
                chatListBox.getChildren().add(buildGroupChatRow(i));
            }
        }

        if (currentTab == Tab.ALL || currentTab == Tab.DM) {
            // ЛС — заглушка, пока бэкенда нет
            chatListBox.getChildren().add(buildPlaceholderRow());
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

        VBox info = new VBox(2);
        Label nameLbl = new Label("Чат " + projectNames.get(i));
        nameLbl.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        Label lastMsg = new Label("Нажмите чтобы открыть чат");
        lastMsg.setStyle("-fx-font-size: 12; -fx-text-fill: #888;");
        info.getChildren().addAll(nameLbl, lastMsg);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox row = new HBox(12, avatar, info);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        row.setStyle("-fx-background-color: transparent; -fx-border-color: #EEEEEE;"
                + " -fx-border-width: 0 0 1 0; -fx-cursor: hand;");

        final long pid = projectIds.get(i);
        final String pname = projectNames.get(i);
        row.setOnMouseClicked(e -> openChat(pid, pname, true));

        return row;
    }

    private HBox buildPlaceholderRow() {
        Label avatar = makeAvatar("👤", "#AAAAAA");

        VBox info = new VBox(2);
        Label nameLbl = new Label("Личные сообщения");
        nameLbl.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        Label lastMsg = new Label("Скоро будет доступно");
        lastMsg.setStyle("-fx-font-size: 12; -fx-text-fill: #AAA;");
        info.getChildren().addAll(nameLbl, lastMsg);

        HBox row = new HBox(12, avatar, info);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        row.setStyle("-fx-background-color: transparent; -fx-border-color: #EEEEEE; -fx-border-width: 0 0 1 0;");
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

    private void openChat(long projectId, String projectName, boolean isGroup) {
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

    // ─── Вкладки ─────────────────────────────────────────────────────────────

    @FXML protected void onTabAll()    { setTab(Tab.ALL); }
    @FXML protected void onTabDm()     { setTab(Tab.DM); }
    @FXML protected void onTabGroups() { setTab(Tab.GROUPS); }

    private void setTab(Tab tab) {
        currentTab = tab;
        tabAll.setStyle(tab == Tab.ALL    ? ACTIVE_STYLE : INACTIVE_STYLE);
        tabDm.setStyle(tab == Tab.DM      ? ACTIVE_STYLE : INACTIVE_STYLE);
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

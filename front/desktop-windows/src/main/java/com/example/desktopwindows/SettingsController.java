package com.example.desktopwindows;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsController {

    @FXML private javafx.scene.layout.BorderPane rootPane;
    @FXML private Label usernameLabel;
    @FXML private Label emailLabel;
    @FXML private Label avatarLabel;

    private final HttpClient client = HttpClient.newHttpClient();

    @FXML
    public void initialize() {
        rootPane.setLeft(NavBar.build(NavBar.Page.SETTINGS,
                this::onNavHome, this::onNavCalendar, this::onNavChats, null));
        Thread.ofVirtual().start(this::loadProfile);
    }

    private void loadProfile() {
        try {
            // Декодируем имя из JWT
            String username = extractUsername();

            // Загружаем список пользователей, ищем email по username
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/user"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

            // Ищем email по username в JSON
            String email = "";
            Pattern blockPat = Pattern.compile("\\{[^{}]*\"username\":\"" + Pattern.quote(username) + "\"[^{}]*\\}");
            Matcher bm = blockPat.matcher(body);
            if (bm.find()) {
                Matcher em = Pattern.compile("\"email\":\"([^\"]+)\"").matcher(bm.group());
                if (em.find()) email = em.group(1);
            }

            String finalUsername = username.isEmpty() ? "Пользователь" : username;
            String finalEmail    = email;
            String initial       = finalUsername.isEmpty() ? "?" : String.valueOf(finalUsername.charAt(0)).toUpperCase();

            Platform.runLater(() -> {
                usernameLabel.setText(finalUsername);
                emailLabel.setText(finalEmail);
                avatarLabel.setText(initial);
            });
        } catch (Exception e) {
            Platform.runLater(() -> usernameLabel.setText("Ошибка загрузки"));
        }
    }

    private String extractUsername() {
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

    @FXML
    protected void onLogoutClick() {
        Session.setToken(null);
        navigate("login-view.fxml");
    }

    private void onNavHome()     { navigate("main-view.fxml"); }
    private void onNavCalendar() { navigate("calendar-view.fxml"); }
    private void onNavChats()    { navigate("chats-view.fxml"); }

    private void navigate(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) rootPane.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), stage.getWidth(), stage.getHeight()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

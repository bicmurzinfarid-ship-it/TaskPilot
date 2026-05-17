package com.example.desktopwindows;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;
    @FXML private ImageView logoIcon;

    @FXML
    public void initialize() {
        try {
            Image img = new Image(getClass().getResourceAsStream("icon_tasks_section.png"));
            logoIcon.setImage(img);
        } catch (Exception ignored) {}
    }

    public void setSuccessMessage(String message) {
        if (successLabel != null) successLabel.setText(message);
    }

    @FXML
    protected void onLoginClick() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        if (username.isBlank() || password.isBlank()) {
            errorLabel.setText("Введите логин и пароль");
            return;
        }
        errorLabel.setText("");
        usernameField.setDisable(true);
        passwordField.setDisable(true);

        String json = "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}";
        Thread.ofVirtual().start(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(Session.API_BASE + "/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                HttpResponse<String> response = HttpClient.newHttpClient()
                        .send(request, HttpResponse.BodyHandlers.ofString());

                Platform.runLater(() -> {
                    usernameField.setDisable(false);
                    passwordField.setDisable(false);
                    if (response.statusCode() == 200) {
                        String body = response.body();
                        java.util.regex.Matcher tm = java.util.regex.Pattern
                                .compile("\"token\":\"([^\"]+)\"").matcher(body);
                        java.util.regex.Matcher um = java.util.regex.Pattern
                                .compile("\"userId\":(\\d+)").matcher(body);
                        String token = tm.find() ? tm.group(1) : body;
                        Session.setToken(token);
                        if (um.find()) Session.setUserId(Long.parseLong(um.group(1)));
                        try {
                            FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"));
                            Stage stage = (Stage) usernameField.getScene().getWindow();
                            stage.setScene(new Scene(loader.load(), stage.getWidth(), stage.getHeight()));
                        } catch (Exception e) {
                            errorLabel.setText("Ошибка загрузки: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        errorLabel.setText("Неверный логин или пароль");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    usernameField.setDisable(false);
                    passwordField.setDisable(false);
                    errorLabel.setText("Нет соединения с сервером");
                });
            }
        });
    }

    @FXML
    protected void onRegisterClick() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("register-view.fxml"));
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(loader.load()));
        } catch (Exception e) {
            errorLabel.setText("Ошибка: " + e.getMessage());
        }
    }
}

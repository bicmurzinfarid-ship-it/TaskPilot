package com.example.desktopwindows;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @FXML
    protected void onBackClick() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(loader.load()));
        } catch (Exception e) {
            errorLabel.setText("Ошибка: " + e.getMessage());
        }
    }

    @FXML
    protected void onRegisterClick() {
        String username = usernameField.getText().trim();
        String email    = emailField.getText().trim();
        String password = passwordField.getText().trim();
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            errorLabel.setText("Заполните все поля");
            return;
        }
        errorLabel.setText("");
        usernameField.setDisable(true);
        emailField.setDisable(true);
        passwordField.setDisable(true);

        String json = "{\"email\":\"" + email + "\",\"username\":\"" + username
                + "\",\"password\":\"" + password + "\"}";
        Thread.ofVirtual().start(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(Session.API_BASE + "/user"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                HttpResponse<String> response = HttpClient.newHttpClient()
                        .send(request, HttpResponse.BodyHandlers.ofString());

                Platform.runLater(() -> {
                    usernameField.setDisable(false);
                    emailField.setDisable(false);
                    passwordField.setDisable(false);
                    if (response.statusCode() == 200 || response.statusCode() == 201) {
                        try {
                            FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
                            Stage stage = (Stage) usernameField.getScene().getWindow();
                            Scene scene = new Scene(loader.load());
                            LoginController ctrl = loader.getController();
                            ctrl.setSuccessMessage("Регистрация прошла успешно! Войдите в аккаунт.");
                            stage.setScene(scene);
                        } catch (Exception ex) {
                            errorLabel.setText("Ошибка перехода: " + ex.getMessage());
                        }
                    } else {
                        errorLabel.setText("Ошибка регистрации (" + response.statusCode() + ")");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    usernameField.setDisable(false);
                    emailField.setDisable(false);
                    passwordField.setDisable(false);
                    errorLabel.setText("Нет соединения с сервером");
                });
            }
        });
    }
}

package com.example.desktopwindows;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RegisterController {
    @FXML
    private TextField usernameField;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;

    @FXML
    protected void onBackClick() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.setScene(new Scene(loader.load()));
    }

    @FXML
    protected void onRegisterClick() throws IOException, InterruptedException {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String email = emailField.getText();
        String json = "{\"email\": \"" + email + "\",\"username\": \"" + username + "\", \"password\": \"" + password + "\"}";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/user"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> responce = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (responce.statusCode() == 200) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
                Stage stage = (Stage) usernameField.getScene().getWindow();
                Scene scene = new Scene(loader.load());
                LoginController controller = loader.getController();
                controller.setSuccessMessage("Регистрация прошла успешно!");
                stage.setScene(scene);
            } else {
                errorLabel.setText("неверные данные");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    }


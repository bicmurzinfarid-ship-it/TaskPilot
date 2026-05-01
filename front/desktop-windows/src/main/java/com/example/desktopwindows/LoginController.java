package com.example.desktopwindows;

import javafx.scene.control.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;


import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    @FXML
    private Label successLabel;
    @FXML
    public void setSuccessMessage(String message) {
        successLabel.setText(message);
    }
    @FXML
    protected void onLoginClick() throws IOException, InterruptedException {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String json = "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> responce = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (responce.statusCode() == 200){
                String body = responce.body();
                String token = body.replace("{\"token\":\"", "").replace("\"}", "");
                Session.setToken(token);

                FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"));
                Stage stage = (Stage) usernameField.getScene().getWindow();
                stage.setScene(new Scene(loader.load()));
            }else{
                errorLabel.setText("неверный логин или пароль");
            }


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @FXML
    protected void onRegisterClick() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("register-view.fxml"));
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.setScene(new Scene(loader.load()));
    }
}

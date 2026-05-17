package com.example.desktopwindows;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsController {

    @FXML private BorderPane rootPane;
    @FXML private Label usernameLabel;
    @FXML private Label emailLabel;
    @FXML private Label avatarLabel;
    @FXML private StackPane avatarPane;

    private final HttpClient client = HttpClient.newHttpClient();
    private String currentUsername = "";

    @FXML
    public void initialize() {
        rootPane.setLeft(NavBar.build(NavBar.Page.SETTINGS,
                this::onNavHome, this::onNavCalendar, this::onNavChats, null));
        Thread.ofVirtual().start(this::loadProfile);
    }

    private void loadProfile() {
        try {
            String username = extractUsername();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/user"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

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
            currentUsername = finalUsername;

            // Загружаем аватар
            Image avatar = AvatarLoader.loadSync(Session.getUserId());

            Platform.runLater(() -> {
                usernameLabel.setText(finalUsername);
                emailLabel.setText(finalEmail);
                if (avatar != null) {
                    showAvatarImage(avatar);
                } else {
                    avatarLabel.setText(initial);
                }
            });
        } catch (Exception e) {
            Platform.runLater(() -> usernameLabel.setText("Ошибка загрузки"));
        }
    }

    private void showAvatarImage(Image img) {
        ImageView iv = new ImageView(img);
        iv.setFitWidth(56);
        iv.setFitHeight(56);
        iv.setPreserveRatio(false);
        Circle clip = new Circle(28, 28, 28);
        iv.setClip(clip);
        avatarPane.getChildren().setAll(iv);
    }

    @FXML
    protected void onChangeAvatarClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите фото");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        File file = chooser.showOpenDialog(rootPane.getScene().getWindow());
        if (file == null) return;
        if (file.length() > 5L * 1024 * 1024) {
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                    "Файл слишком большой (макс. 5 МБ)").showAndWait();
            return;
        }
        Thread.ofVirtual().start(() -> uploadAvatar(file));
    }

    private void uploadAvatar(File file) {
        try {
            String boundary = "----AvatarBoundary" + System.currentTimeMillis();
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String mime = URLConnection.guessContentTypeFromName(file.getName());
            if (mime == null) mime = "image/jpeg";

            String header = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\""
                    + file.getName().replace("\"", "") + "\"\r\n"
                    + "Content-Type: " + mime + "\r\n\r\n";
            byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(fileBytes);
            out.write(footer);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/user/me/avatar"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                AvatarLoader.invalidate(Session.getUserId());
                Image img = AvatarLoader.loadSync(Session.getUserId());
                Platform.runLater(() -> {
                    if (img != null) showAvatarImage(img);
                });
            } else {
                Platform.runLater(() -> new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.ERROR,
                        "Ошибка загрузки: " + resp.body()).showAndWait());
            }
        } catch (Exception e) {
            Platform.runLater(() -> new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR,
                    "Ошибка: " + e.getMessage()).showAndWait());
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

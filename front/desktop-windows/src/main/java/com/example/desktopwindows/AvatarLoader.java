package com.example.desktopwindows;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AvatarLoader {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Map<Long, Image> cache = new ConcurrentHashMap<>();

    private static final String[] COLORS = {
        "#E53935", "#8E24AA", "#1E88E5", "#00897B",
        "#F4511E", "#6D4C41", "#3949AB", "#039BE5"
    };

    /** Загружает аватар синхронно (вызывать только из фонового потока). */
    public static Image loadSync(Long userId) {
        if (userId == null) return null;
        if (cache.containsKey(userId)) return cache.get(userId);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/user/" + userId + "/avatar"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body().length > 0) {
                Image img = new Image(new ByteArrayInputStream(resp.body()));
                if (!img.isError()) {
                    cache.put(userId, img);
                    return img;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Сбрасывает кэш для пользователя (после смены аватара). */
    public static void invalidate(Long userId) {
        cache.remove(userId);
    }

    /**
     * Создаёт круглый аватар: сначала показывает инициал,
     * затем асинхронно заменяет на фото если оно есть.
     */
    public static StackPane make(Long userId, String name, double size) {
        StackPane pane = new StackPane();
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);

        String initial = (name != null && !name.isEmpty())
                ? String.valueOf(name.charAt(0)).toUpperCase() : "?";
        String color = COLORS[Math.abs(name != null ? name.hashCode() : 0) % COLORS.length];

        Circle bg = new Circle(size / 2);
        bg.setFill(Color.web(color));

        Label lbl = new Label(initial);
        lbl.setStyle("-fx-font-size: " + (int)(size * 0.38)
                + "; -fx-text-fill: white; -fx-font-weight: bold;");

        pane.getChildren().addAll(bg, lbl);

        if (userId != null) {
            Thread.ofVirtual().start(() -> {
                Image img = loadSync(userId);
                if (img != null) {
                    Platform.runLater(() -> {
                        ImageView iv = new ImageView(img);
                        iv.setFitWidth(size);
                        iv.setFitHeight(size);
                        iv.setPreserveRatio(false);
                        Circle clip = new Circle(size / 2, size / 2, size / 2);
                        iv.setClip(clip);
                        pane.getChildren().setAll(iv);
                    });
                }
            });
        }
        return pane;
    }
}

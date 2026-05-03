package com.example.desktopwindows;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

/**
 * Левая боковая панель навигации.
 * Каждая кнопка: иконка сверху + подпись снизу.
 */
public class NavBar {

    public enum Page { HOME, TASKS, CALENDAR, CHATS, SETTINGS }

    public static VBox build(Page activePage, Runnable onHome, Runnable onCalendar,
                             Runnable onChats, Runnable onSettings) {
        VBox bar = new VBox(4);
        bar.setAlignment(Pos.TOP_CENTER);
        bar.setStyle("-fx-background-color: #FF9A2E; -fx-padding: 10 4;");
        bar.setMinWidth(82);
        bar.setMaxWidth(82);

        bar.getChildren().addAll(
                makeBtn("nav_home.png",     "Главная",   activePage == Page.HOME,     onHome),
                makeBtn("nav_calendar.png", "Календарь", activePage == Page.CALENDAR, onCalendar),
                makeBtn("nav_chats.png",    "Чаты",      activePage == Page.CHATS,    onChats),
                makeBtn("nav_settings.png", "Настройки", activePage == Page.SETTINGS, onSettings)
        );

        return bar;
    }

    private static Button makeBtn(String iconFile, String label, boolean active, Runnable action) {
        ImageView icon = loadIcon(iconFile, 28);

        Button btn = new Button(label);
        btn.setGraphic(icon);
        btn.setContentDisplay(ContentDisplay.TOP);
        btn.setGraphicTextGap(4);
        btn.setStyle(
                "-fx-background-color: " + (active ? "rgba(0,0,0,0.15)" : "transparent") + ";" +
                "-fx-font-size: 11px;" +
                "-fx-font-weight: " + (active ? "bold" : "normal") + ";" +
                "-fx-text-fill: #3D1A00;" +
                "-fx-cursor: hand;" +
                "-fx-background-radius: 10;" +
                "-fx-padding: 8 4;" +
                "-fx-min-width: 74;" +
                "-fx-max-width: 74;" +
                "-fx-alignment: center;"
        );

        if (!active) {
            btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle()
                    .replace("-fx-background-color: transparent;", "-fx-background-color: rgba(0,0,0,0.12);")));
            btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle()
                    .replace("-fx-background-color: rgba(0,0,0,0.12);", "-fx-background-color: transparent;")));
        }

        if (action != null) btn.setOnAction(e -> action.run());
        return btn;
    }

    public static ImageView loadIcon(String filename, int size) {
        try {
            Image img = new Image(NavBar.class.getResourceAsStream(filename));
            ImageView iv = new ImageView(img);
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            return iv;
        } catch (Exception e) {
            return new ImageView();
        }
    }
}

package com.example.desktopwindows;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TasksController {

    @FXML private BorderPane rootPane;
    @FXML private VBox taskListBox;
    @FXML private Button tabRecent;
    @FXML private Button tabAssignedToMe;
    @FXML private Button tabAssignedByMe;

    private final HttpClient client = HttpClient.newHttpClient();

    // Все задачи
    private final List<Long>   ids        = new ArrayList<>();
    private final List<String> titles     = new ArrayList<>();
    private final List<String> statuses   = new ArrayList<>();
    private final List<String> deadlines  = new ArrayList<>();
    private final List<Long>   assigneeIds  = new ArrayList<>();
    private final List<Long>   creatorIds   = new ArrayList<>();
    private final List<Integer> importances = new ArrayList<>();
    private final List<String>  quadrants   = new ArrayList<>();

    private Long currentUserId = null;

    private enum Tab { RECENT, TO_ME, BY_ME }
    private Tab currentTab = Tab.RECENT;

    // Стили вкладок
    private static final String ACTIVE_STYLE =
            "-fx-background-color: transparent; -fx-font-size: 14; " +
            "-fx-text-fill: #FAA030; -fx-font-weight: bold; " +
            "-fx-border-color: #FAA030; -fx-border-width: 0 0 2 0;";
    private static final String INACTIVE_STYLE =
            "-fx-background-color: transparent; -fx-font-size: 14; -fx-text-fill: #555;";

    @FXML
    public void initialize() {
        rootPane.setLeft(NavBar.build(NavBar.Page.TASKS,
                this::onNavHome, this::onNavCalendar, this::onNavChats, this::onNavSettings));
        loadCurrentUser();
        loadTasks();
    }

    // ─── Загрузка текущего пользователя ──────────────────────────────────────

    private void loadCurrentUser() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/user"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
            // Берём первый id — это не текущий пользователь.
            // Нужно сравнивать по токену. Пока загрузим всех и найдём совпадение через /user/{id} —
            // проще: в Session храним username, а здесь найдём по нему
            String username = extractUsername();
            Matcher m = Pattern.compile("\"id\":(\\d+),\"username\":\"([^\"]+)\"").matcher(body);
            while (m.find()) {
                if (m.group(2).equals(username)) {
                    currentUserId = Long.parseLong(m.group(1));
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    private String extractUsername() {
        // JWT payload — вторая часть токена base64
        try {
            String token = Session.getToken();
            String payload = token.split("\\.")[1];
            // Дополняем до кратного 4
            int pad = payload.length() % 4;
            if (pad != 0) payload += "=".repeat(4 - pad);
            String decoded = new String(java.util.Base64.getDecoder().decode(payload));
            Matcher m = Pattern.compile("\"sub\":\"([^\"]+)\"").matcher(decoded);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return "";
    }

    // ─── Загрузка задач ───────────────────────────────────────────────────────

    private void loadTasks() {
        ids.clear(); titles.clear(); statuses.clear();
        deadlines.clear(); assigneeIds.clear(); creatorIds.clear(); importances.clear(); quadrants.clear();

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/task"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
            parseTasksFromBody(body);
        } catch (Exception e) {
            showError("Ошибка загрузки задач: " + e.getMessage());
        }
        renderTasks();
    }

    private void parseTasksFromBody(String body) {
        Pattern blockPat = Pattern.compile("\\{[^{}]*\"title\"[^{}]*\\}", Pattern.DOTALL);
        Matcher blockM = blockPat.matcher(body);
        while (blockM.find()) {
            String block = blockM.group();
            Matcher idM       = Pattern.compile("\"id\":(\\d+)").matcher(block);
            Matcher titleM    = Pattern.compile("\"title\":\"([^\"]+)\"").matcher(block);
            Matcher statusM   = Pattern.compile("\"status\":\"([^\"]+)\"").matcher(block);
            Matcher deadlineM = Pattern.compile("\"deadline\":\"([^\"]+)\"").matcher(block);
            Matcher assigneeM = Pattern.compile("\"assigneeId\":(\\d+)").matcher(block);
            Matcher creatorM  = Pattern.compile("\"creatorId\":(\\d+)").matcher(block);

            Matcher importanceM = Pattern.compile("\"importance\":(\\d+)").matcher(block);
            Matcher quadrantM   = Pattern.compile("\"eisenhowerQuadrant\":\"([^\"]+)\"").matcher(block);
            if (!idM.find() || !titleM.find()) continue;
            ids.add(Long.parseLong(idM.group(1)));
            titles.add(titleM.group(1));
            statuses.add(statusM.find() ? statusM.group(1) : "WAITING");
            deadlines.add(deadlineM.find() ? deadlineM.group(1) : null);
            assigneeIds.add(assigneeM.find() ? Long.parseLong(assigneeM.group(1)) : null);
            creatorIds.add(creatorM.find() ? Long.parseLong(creatorM.group(1)) : null);
            importances.add(importanceM.find() ? Integer.parseInt(importanceM.group(1)) : 3);
            quadrants.add(quadrantM.find() ? quadrantM.group(1) : null);
        }
    }

    // ─── Рендер списка ────────────────────────────────────────────────────────

    private void renderTasks() {
        taskListBox.getChildren().clear();

        for (int i = 0; i < ids.size(); i++) {
            Long assignee = assigneeIds.get(i);
            Long creator  = creatorIds.get(i);

            boolean show = switch (currentTab) {
                case RECENT -> currentUserId == null
                        || currentUserId.equals(creator)
                        || currentUserId.equals(assignee);
                case TO_ME  -> currentUserId != null && currentUserId.equals(assignee);
                case BY_ME  -> currentUserId != null && currentUserId.equals(creator)
                                && !currentUserId.equals(assignee);
            };
            if (!show) continue;

            taskListBox.getChildren().add(buildCard(i));
        }

        if (taskListBox.getChildren().isEmpty()) {
            Label empty = new Label("Задач нет");
            empty.setStyle("-fx-text-fill: #888; -fx-font-size: 14; -fx-padding: 20;");
            taskListBox.getChildren().add(empty);
        }
    }

    private HBox buildCard(int i) {
        String status   = statuses.get(i);
        String deadline = deadlines.get(i);
        String quadrant = i < quadrants.size() ? quadrants.get(i) : null;
        boolean overdue = false;
        if (deadline != null && !"READY".equals(status)) {
            try {
                overdue = LocalDateTime.parse(deadline, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                        .isBefore(LocalDateTime.now());
            } catch (Exception ignored) {}
        }

        String icon = overdue ? "✖"
                : switch (status) {
                    case "READY"       -> "✔";
                    case "DEVELOPMENT" -> "◉";
                    default            -> "○";
                };

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 20; -fx-min-width: 28;");
        iconLbl.setAlignment(Pos.CENTER);

        Label titleLbl = new Label(titles.get(i));
        titleLbl.setStyle("-fx-font-size: 15;");
        titleLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLbl, Priority.ALWAYS);

        final int idx = i;

        // READY — весь фон зелёный; просроченная — весь фон очень тёмно-красный
        if ("READY".equals(status) || overdue) {
            String fullBg = overdue ? "#B71C1C" : "#90EE90";
            if (overdue) {
                iconLbl.setStyle("-fx-font-size: 20; -fx-min-width: 28; -fx-text-fill: white;");
                titleLbl.setStyle("-fx-font-size: 15; -fx-text-fill: white;");
            }
            HBox card = new HBox(12, iconLbl, titleLbl);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setPadding(new Insets(12, 16, 12, 16));
            card.setStyle("-fx-background-color: " + fullBg + "; -fx-background-radius: 10; -fx-cursor: hand;");
            card.setMaxWidth(Double.MAX_VALUE);
            card.setOnMouseClicked(e -> { if (e.getClickCount() == 2) showTaskDetail(idx); });
            return card;
        }

        // Левая полоска — цвет статуса
        String statusColor = "DEVELOPMENT".equals(status) ? "#FFA726" : "#9E9E9E";
        Region statusStripe = new Region();
        statusStripe.setMinWidth(6); statusStripe.setMaxWidth(6);
        statusStripe.setStyle("-fx-background-color: " + statusColor
                + "; -fx-background-radius: 10 0 0 10;");

        // Фон карточки — квадрант Эйзенхауэра
        HBox inner = new HBox(12, iconLbl, titleLbl);
        inner.setAlignment(Pos.CENTER_LEFT);
        inner.setPadding(new Insets(12, 12, 12, 16));
        inner.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(inner, Priority.ALWAYS);
        inner.setStyle("-fx-background-color: " + eisenhowerColor(quadrant)
                + "; -fx-background-radius: 0 10 10 0;");

        HBox card = new HBox(0, statusStripe, inner);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-radius: 10; -fx-cursor: hand;"
                + " -fx-border-color: #E0E0E0; -fx-border-radius: 10; -fx-border-width: 1;");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setOnMouseClicked(e -> { if (e.getClickCount() == 2) showTaskDetail(idx); });
        return card;
    }

    private String eisenhowerColor(String quadrant) {
        if (quadrant == null) return "#E0E0E0";
        return switch (quadrant) {
            case "URGENT_IMPORTANT"               -> "#EF9A9A"; // срочно + важно
            case "URGENT_SOMEWHAT_IMPORTANT"      -> "#FFCC80"; // срочно + не очень важно
            case "URGENT_NOT_IMPORTANT"           -> "#FFF59D"; // срочно + не важно
            case "NOT_URGENT_IMPORTANT"           -> "#90CAF9"; // не срочно + важно
            case "NOT_URGENT_SOMEWHAT_IMPORTANT"  -> "#B3E5FC"; // не срочно + не очень важно
            default                               -> "#E0E0E0"; // не срочно + не важно
        };
    }

    // ─── Детали задачи ────────────────────────────────────────────────────────

    private void showTaskDetail(int i) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Задача: " + titles.get(i));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setPrefWidth(380);

        // Статус
        Label statusLbl = new Label("Статус: " + statuses.get(i));
        statusLbl.setStyle("-fx-font-size: 13;");

        // Дедлайн
        String dl = deadlines.get(i);
        Label deadlineLbl = new Label("Дедлайн: " + (dl != null ? dl.replace("T", "  ") : "не задан"));
        deadlineLbl.setStyle("-fx-font-size: 13;");

        // Смена статуса
        Label changeLabel = new Label("Изменить статус:");
        changeLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold;");
        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("WAITING", "DEVELOPMENT", "READY");
        statusBox.setValue(statuses.get(i));

        Button saveBtn = new Button("Сохранить статус");
        saveBtn.setStyle("-fx-background-color: #FAA030; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 6 14;");
        saveBtn.setOnAction(e -> {
            updateStatus(ids.get(i), statusBox.getValue());
            statuses.set(i, statusBox.getValue());
            renderTasks();
            dialog.close();
        });

        box.getChildren().addAll(statusLbl, deadlineLbl,
                new Separator(), changeLabel, statusBox, saveBtn);
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait();
    }

    private void updateStatus(Long taskId, String status) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/task/" + taskId + "/status?status=" + status))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .method("PATCH", HttpRequest.BodyPublishers.noBody())
                    .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            showError("Ошибка обновления статуса: " + e.getMessage());
        }
    }

    // ─── Добавить личную задачу ───────────────────────────────────────────────

    @FXML
    protected void onAddTaskClick() {
        Stage dialog = new Stage();
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setResizable(false);

        // ─── Шапка ───────────────────────────────────────────────────────────
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #FAA030; -fx-padding: 18 16;");
        header.setAlignment(Pos.CENTER_LEFT);
        Label headerLbl = new Label("Новая Задача");
        headerLbl.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: white;");
        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"
                + " -fx-font-size: 15; -fx-cursor: hand; -fx-padding: 0 4;");
        closeBtn.setOnAction(e -> dialog.close());
        header.getChildren().addAll(headerLbl, hSpacer, closeBtn);

        // ─── Поля ────────────────────────────────────────────────────────────
        TextField titleField = new TextField();
        titleField.setPromptText("Назовите свою задачу...");
        titleField.setStyle("-fx-background-color: #E8E8E8; -fx-background-radius: 10;"
                + " -fx-border-color: transparent; -fx-padding: 10 14; -fx-font-size: 14;");
        titleField.setMaxWidth(Double.MAX_VALUE);

        TextArea descField = new TextArea();
        descField.setPromptText("Опишите её...");
        descField.setPrefRowCount(3);
        descField.setStyle("-fx-background-color: #E8E8E8; -fx-background-radius: 10;"
                + " -fx-border-color: transparent; -fx-padding: 10 14; -fx-font-size: 13;");

        // ─── Дедлайн: дата + часы + минуты ───────────────────────────────────
        DatePicker deadlinePicker = new DatePicker();
        deadlinePicker.setPromptText("Дата");
        HBox.setHgrow(deadlinePicker, Priority.ALWAYS);

        Spinner<Integer> hoursSpinner = new Spinner<>(0, 23, 23);
        hoursSpinner.setEditable(true);
        hoursSpinner.setPrefWidth(68);

        Label colonLbl = new Label(":");
        colonLbl.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-padding: 0 0 2 0;");

        Spinner<Integer> minsSpinner = new Spinner<>(0, 59, 59);
        minsSpinner.setEditable(true);
        minsSpinner.setPrefWidth(68);

        HBox deadlineRow = new HBox(8, deadlinePicker, hoursSpinner, colonLbl, minsSpinner);
        deadlineRow.setAlignment(Pos.CENTER_LEFT);

        // ─── Звёзды важности (1-3) ───────────────────────────────────────────
        int[] importanceHolder = {2};
        Label[] stars = new Label[3];
        HBox starsBox = new HBox(6);
        starsBox.setAlignment(Pos.CENTER_LEFT);
        Runnable refreshStars = () -> {
            for (int i = 0; i < 3; i++) {
                stars[i].setText(i < importanceHolder[0] ? "★" : "☆");
                stars[i].setStyle("-fx-font-size: 28; -fx-text-fill: "
                        + (i < importanceHolder[0] ? "#FAA030" : "#CCCCCC") + "; -fx-cursor: hand;");
            }
        };
        for (int i = 0; i < 3; i++) {
            stars[i] = new Label();
            final int val = i + 1;
            stars[i].setOnMouseClicked(e -> { importanceHolder[0] = val; refreshStars.run(); });
            starsBox.getChildren().add(stars[i]);
        }
        refreshStars.run();

        // ─── Кнопка ДОБАВИТЬ ─────────────────────────────────────────────────
        Button addBtn = new Button("ДОБАВИТЬ");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setStyle("-fx-background-color: #FAA030; -fx-text-fill: white; -fx-font-weight: bold;"
                + " -fx-font-size: 16; -fx-background-radius: 25; -fx-padding: 13 0; -fx-cursor: hand;");
        addBtn.setOnAction(e -> {
            String t = titleField.getText().trim();
            if (t.isBlank()) { showError("Название не может быть пустым"); return; }
            String dl = null;
            if (deadlinePicker.getValue() != null) {
                dl = deadlinePicker.getValue()
                        + "T" + String.format("%02d", hoursSpinner.getValue())
                        + ":" + String.format("%02d", minsSpinner.getValue()) + ":00";
            }
            createPersonalTask(t, descField.getText().trim(), dl, importanceHolder[0]);
            dialog.close();
            loadTasks();
        });

        VBox content = new VBox(8,
                formLabel("Назовите свою задачу"), titleField,
                formLabel("Опишите её"), descField,
                formLabel("Дедлайн"), deadlineRow,
                formLabel("Важность"), starsBox,
                addBtn
        );
        content.setPadding(new Insets(16, 20, 20, 20));
        content.setStyle("-fx-background-color: #F5F0EB;");

        VBox root = new VBox(header, content);
        root.setStyle("-fx-background-color: #F5F0EB;");

        dialog.setScene(new Scene(root, 400, 460));
        dialog.showAndWait();
    }

    private Label formLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 13; -fx-text-fill: #666; -fx-font-weight: bold;");
        return lbl;
    }

    private void createPersonalTask(String title, String desc, String deadline, int importance) {
        try {
            Long personalProjectId = getOrCreatePersonalProject();
            if (personalProjectId == null) return;

            StringBuilder json = new StringBuilder("{");
            json.append("\"title\":\"").append(title.replace("\"", "\\\"")).append("\"");
            if (!desc.isBlank())
                json.append(",\"description\":\"").append(desc.replace("\"", "\\\"")).append("\"");
            if (deadline != null)
                json.append(",\"deadline\":\"").append(deadline).append("\"");
            json.append(",\"importance\":").append(importance);
            json.append("}");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/project/" + personalProjectId + "/task"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + Session.getToken())
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200)
                showError("Ошибка создания задачи (" + resp.statusCode() + "): " + resp.body());
        } catch (Exception e) {
            showError("Ошибка: " + e.getMessage());
        }
    }

    /** Находит проект "Личные" или создаёт его. Возвращает id. */
    private Long getOrCreatePersonalProject() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/project"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

            // Brace-depth parser: each top-level object is one project
            int depth = 0, start = -1;
            for (int i = 0; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c == '{') { if (depth++ == 0) start = i; }
                else if (c == '}' && --depth == 0 && start >= 0) {
                    String obj = body.substring(start, i + 1);
                    Matcher nameM = Pattern.compile("\"name\":\"([^\"]+)\"").matcher(obj);
                    Matcher idM   = Pattern.compile("\"id\":(\\d+)").matcher(obj);
                    if (nameM.find() && nameM.group(1).equals("Личные") && idM.find())
                        return Long.parseLong(idM.group(1));
                }
            }

            // Не нашли — создаём
            HttpRequest createReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/project"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + Session.getToken())
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Личные\"}"))
                    .build();
            String createBody = client.send(createReq, HttpResponse.BodyHandlers.ofString()).body();
            Matcher idM = Pattern.compile("\"id\":(\\d+)").matcher(createBody);
            if (idM.find()) return Long.parseLong(idM.group(1));

            showError("Не удалось создать проект «Личные»");
        } catch (Exception e) {
            showError("Ошибка: " + e.getMessage());
        }
        return null;
    }

    // ─── Переключение вкладок ─────────────────────────────────────────────────

    @FXML protected void onTabRecent()       { setTab(Tab.RECENT); }
    @FXML protected void onTabAssignedToMe() { setTab(Tab.TO_ME); }
    @FXML protected void onTabAssignedByMe() { setTab(Tab.BY_ME); }

    private void setTab(Tab tab) {
        currentTab = tab;
        tabRecent.setStyle(tab == Tab.RECENT ? ACTIVE_STYLE : INACTIVE_STYLE);
        tabAssignedToMe.setStyle(tab == Tab.TO_ME ? ACTIVE_STYLE : INACTIVE_STYLE);
        tabAssignedByMe.setStyle(tab == Tab.BY_ME ? ACTIVE_STYLE : INACTIVE_STYLE);
        renderTasks();
    }

    // ─── Навигация ────────────────────────────────────────────────────────────

    @FXML protected void onNavHome()     { navigate("main-view.fxml"); }
    @FXML protected void onNavCalendar() { navigate("calendar-view.fxml"); }
    @FXML protected void onNavChats()    { navigate("chats-view.fxml"); }
    @FXML protected void onNavSettings() { navigate("settings-view.fxml"); }

    private void navigate(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) taskListBox.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), stage.getWidth(), stage.getHeight()));
        } catch (Exception e) {
            showError("Ошибка навигации: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}

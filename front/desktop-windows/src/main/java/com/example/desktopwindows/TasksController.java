package com.example.desktopwindows;

import javafx.application.Platform;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final List<Long>   assigneeIds = new ArrayList<>();
    private final List<Long>   creatorIds  = new ArrayList<>();

    private Long currentUserId = null;

    private enum Tab { RECENT, TO_ME, BY_ME }
    private Tab currentTab = Tab.RECENT;

    // Стили вкладок
    private static final String ACTIVE_STYLE =
            "-fx-background-color: transparent; -fx-font-size: 14; " +
            "-fx-text-fill: #FF9A2E; -fx-font-weight: bold; " +
            "-fx-border-color: #FF9A2E; -fx-border-width: 0 0 2 0;";
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
        deadlines.clear(); assigneeIds.clear(); creatorIds.clear();

        try {
            // Получаем все проекты пользователя
            HttpRequest projReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/project"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            String projBody = client.send(projReq, HttpResponse.BodyHandlers.ofString()).body();

            // Собираем id проектов
            List<Long> myProjectIds = new ArrayList<>();
            Pattern blockPat = Pattern.compile("\\{(.*?)\"members\"", Pattern.DOTALL);
            Matcher blockM = blockPat.matcher(projBody);
            while (blockM.find()) {
                String block = blockM.group(1);
                if (!block.contains("\"creatorId\"")) continue;
                Matcher idM = Pattern.compile("\"id\":(\\d+)").matcher(block);
                if (idM.find()) myProjectIds.add(Long.parseLong(idM.group(1)));
            }

            // Загружаем задачи из каждого проекта
            for (Long pid : myProjectIds) {
                HttpRequest taskReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/project/" + pid + "/task"))
                        .header("Authorization", "Bearer " + Session.getToken())
                        .GET().build();
                String taskBody = client.send(taskReq, HttpResponse.BodyHandlers.ofString()).body();
                parseTasksFromBody(taskBody);
            }
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

            if (!idM.find() || !titleM.find()) continue;
            ids.add(Long.parseLong(idM.group(1)));
            titles.add(titleM.group(1));
            statuses.add(statusM.find() ? statusM.group(1) : "WAITING");
            deadlines.add(deadlineM.find() ? deadlineM.group(1) : null);
            assigneeIds.add(assigneeM.find() ? Long.parseLong(assigneeM.group(1)) : null);
            creatorIds.add(creatorM.find() ? Long.parseLong(creatorM.group(1)) : null);
        }
    }

    // ─── Рендер списка ────────────────────────────────────────────────────────

    private void renderTasks() {
        taskListBox.getChildren().clear();

        for (int i = 0; i < ids.size(); i++) {
            Long assignee = assigneeIds.get(i);
            Long creator  = creatorIds.get(i);

            boolean show = switch (currentTab) {
                case RECENT   -> true;
                case TO_ME    -> currentUserId != null && currentUserId.equals(assignee);
                case BY_ME    -> currentUserId != null && currentUserId.equals(creator)
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
        boolean overdue = false;
        if (deadline != null) {
            try {
                LocalDate d = LocalDateTime.parse(deadline,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")).toLocalDate();
                overdue = d.isBefore(LocalDate.now()) && !"READY".equals(status);
            } catch (Exception ignored) {}
        }

        String bg = overdue ? "#FFB3B3"
                : switch (status) {
                    case "READY"       -> "#90EE90";
                    case "DEVELOPMENT" -> "#FFD580";
                    default            -> "#E0E0E0";
                };
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

        HBox card = new HBox(12, iconLbl, titleLbl);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 10; -fx-cursor: hand;");
        card.setMaxWidth(Double.MAX_VALUE);

        // Двойной клик — детали задачи
        final int idx = i;
        card.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) showTaskDetail(idx);
        });

        return card;
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
        saveBtn.setStyle("-fx-background-color: #FF9A2E; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 6 14;");
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
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Новая задача");
        ButtonType addBtn = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        TextField titleField = new TextField();
        titleField.setPromptText("Название задачи...");
        titleField.setStyle("-fx-background-color: #E0E0E0; -fx-background-radius: 8; -fx-padding: 8;");

        TextArea descField = new TextArea();
        descField.setPromptText("Описание...");
        descField.setPrefRowCount(3);
        descField.setStyle("-fx-background-color: #E0E0E0; -fx-background-radius: 8;");

        TextField deadlineField = new TextField();
        deadlineField.setPromptText("2026-04-20T12:00:00  (необязательно)");

        Spinner<Integer> importanceSpinner = new Spinner<>(1, 5, 3);
        importanceSpinner.setEditable(true);
        importanceSpinner.setPrefWidth(70);

        VBox content = new VBox(8,
                new Label("Назовите свою задачу:"), titleField,
                new Label("Опишите её:"), descField,
                new Label("Дедлайн:"), deadlineField,
                new HBox(8, new Label("Важность (1–5):"), importanceSpinner)
        );
        content.setPrefWidth(340);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != addBtn) return;
            String t = titleField.getText().trim();
            if (t.isBlank()) { showError("Название не может быть пустым"); return; }

            String dl = deadlineField.getText().trim().isEmpty() ? null : deadlineField.getText().trim();
            createPersonalTask(t, descField.getText().trim(), dl, importanceSpinner.getValue());
            loadTasks();
        });
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

            Pattern blockPat = Pattern.compile("\\{(.*?)\"members\"", Pattern.DOTALL);
            Matcher blockM = blockPat.matcher(body);
            while (blockM.find()) {
                String block = blockM.group(1);
                if (!block.contains("\"creatorId\"")) continue;
                Matcher nameM = Pattern.compile("\"name\":\"([^\"]+)\"").matcher(block);
                Matcher idM   = Pattern.compile("\"id\":(\\d+)").matcher(block);
                if (nameM.find() && nameM.group(1).equals("Личные") && idM.find()) {
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

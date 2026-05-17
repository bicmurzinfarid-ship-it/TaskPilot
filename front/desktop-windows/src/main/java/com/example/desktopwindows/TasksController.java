package com.example.desktopwindows;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TasksController {

    @FXML private BorderPane rootPane;
    @FXML private VBox taskListBox;
    @FXML private Button tabRecent;
    @FXML private Button tabAssignedToMe;
    @FXML private Button tabAssignedByMe;

    private final HttpClient client = HttpClient.newHttpClient();

    private final List<Long>    ids          = new ArrayList<>();
    private final List<String>  titles       = new ArrayList<>();
    private final List<String>  statuses     = new ArrayList<>();
    private final List<String>  deadlines    = new ArrayList<>();
    private final List<Long>    assigneeIds  = new ArrayList<>();
    private final List<Long>    creatorIds   = new ArrayList<>();
    private final List<Integer> importances  = new ArrayList<>();
    private final List<String>  quadrants    = new ArrayList<>();
    private final List<Long>    taskProjectIds = new ArrayList<>();

    // projectId → [creatorId, teamLeadId] (кэш чтобы не ходить в сеть каждый раз)
    private final Map<Long, long[]> projectInfoMap = new HashMap<>();

    private enum Tab { RECENT, TO_ME, BY_ME }
    private Tab currentTab = Tab.RECENT;

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
        loadTasks();
    }

    // ─── Загрузка задач ───────────────────────────────────────────────────────

    private void loadTasks() {
        ids.clear(); titles.clear(); statuses.clear(); deadlines.clear();
        assigneeIds.clear(); creatorIds.clear(); importances.clear();
        quadrants.clear(); taskProjectIds.clear();

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/task"))
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
            Matcher idM        = Pattern.compile("\"id\":(\\d+)").matcher(block);
            Matcher titleM     = Pattern.compile("\"title\":\"([^\"]+)\"").matcher(block);
            Matcher statusM    = Pattern.compile("\"status\":\"([^\"]+)\"").matcher(block);
            Matcher deadlineM  = Pattern.compile("\"deadline\":\"([^\"]+)\"").matcher(block);
            Matcher assigneeM  = Pattern.compile("\"assigneeId\":(\\d+)").matcher(block);
            Matcher creatorM   = Pattern.compile("\"creatorId\":(\\d+)").matcher(block);
            Matcher importanceM= Pattern.compile("\"importance\":(\\d+)").matcher(block);
            Matcher quadrantM  = Pattern.compile("\"eisenhowerQuadrant\":\"([^\"]+)\"").matcher(block);
            Matcher projectM   = Pattern.compile("\"projectId\":(\\d+)").matcher(block);

            if (!idM.find() || !titleM.find()) continue;
            ids.add(Long.parseLong(idM.group(1)));
            titles.add(titleM.group(1));
            statuses.add(statusM.find() ? statusM.group(1) : "WAITING");
            deadlines.add(deadlineM.find() ? deadlineM.group(1) : null);
            assigneeIds.add(assigneeM.find() ? Long.parseLong(assigneeM.group(1)) : null);
            creatorIds.add(creatorM.find() ? Long.parseLong(creatorM.group(1)) : null);
            importances.add(importanceM.find() ? Integer.parseInt(importanceM.group(1)) : 3);
            quadrants.add(quadrantM.find() ? quadrantM.group(1) : null);
            taskProjectIds.add(projectM.find() ? Long.parseLong(projectM.group(1)) : null);
        }
    }

    // ─── Рендер ───────────────────────────────────────────────────────────────

    private void renderTasks() {
        taskListBox.getChildren().clear();
        Long me = Session.getUserId();

        for (int i = 0; i < ids.size(); i++) {
            Long assignee = assigneeIds.get(i);
            Long creator  = creatorIds.get(i);
            boolean show = switch (currentTab) {
                case RECENT -> me == null || me.equals(creator) || me.equals(assignee);
                case TO_ME  -> me != null && me.equals(assignee);
                case BY_ME  -> me != null && me.equals(creator) && !me.equals(assignee);
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
        String quadrant = quadrants.get(i);
        boolean overdue = false;
        if (deadline != null && !"READY".equals(status) && !"PENDING_REVIEW".equals(status)) {
            try {
                overdue = LocalDateTime.parse(deadline,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                        .isBefore(LocalDateTime.now());
            } catch (Exception ignored) {}
        }

        String icon = overdue ? "✖"
                : switch (status) {
                    case "READY"          -> "✔";
                    case "PENDING_REVIEW" -> "⏳";
                    case "DEVELOPMENT"    -> "◉";
                    default               -> "○";
                };

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 20; -fx-min-width: 28;");
        iconLbl.setAlignment(Pos.CENTER);

        Label titleLbl = new Label(titles.get(i));
        titleLbl.setStyle("-fx-font-size: 15;");
        titleLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLbl, Priority.ALWAYS);

        final int idx = i;

        if ("READY".equals(status) || overdue) {
            String bg = overdue ? "#B71C1C" : "#90EE90";
            if (overdue) {
                iconLbl.setStyle("-fx-font-size: 20; -fx-min-width: 28; -fx-text-fill: white;");
                titleLbl.setStyle("-fx-font-size: 15; -fx-text-fill: white;");
            }
            HBox card = new HBox(12, iconLbl, titleLbl);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setPadding(new Insets(12, 16, 12, 16));
            card.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 10; -fx-cursor: hand;");
            card.setMaxWidth(Double.MAX_VALUE);
            card.setOnMouseClicked(e -> { if (e.getClickCount() == 2) showTaskDetail(idx); });
            return card;
        }

        if ("PENDING_REVIEW".equals(status)) {
            HBox card = new HBox(12, iconLbl, titleLbl);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setPadding(new Insets(12, 16, 12, 16));
            card.setStyle("-fx-background-color: #FFC966; -fx-background-radius: 10; -fx-cursor: hand;"
                    + " -fx-border-color: #E0E0E0; -fx-border-radius: 10; -fx-border-width: 1;");
            card.setMaxWidth(Double.MAX_VALUE);
            card.setOnMouseClicked(e -> { if (e.getClickCount() == 2) showTaskDetail(idx); });
            return card;
        }

        String statusColor = "DEVELOPMENT".equals(status) ? "#FFA726" : "#9E9E9E";
        Region statusStripe = new Region();
        statusStripe.setMinWidth(6); statusStripe.setMaxWidth(6);
        statusStripe.setStyle("-fx-background-color: " + statusColor
                + "; -fx-background-radius: 10 0 0 10;");

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
            case "URGENT_IMPORTANT"              -> "#EF9A9A";
            case "URGENT_SOMEWHAT_IMPORTANT"     -> "#FFCC80";
            case "URGENT_NOT_IMPORTANT"          -> "#FFF59D";
            case "NOT_URGENT_IMPORTANT"          -> "#90CAF9";
            case "NOT_URGENT_SOMEWHAT_IMPORTANT" -> "#B3E5FC";
            default                              -> "#E0E0E0";
        };
    }

    // ─── Детали задачи ────────────────────────────────────────────────────────

    private void showTaskDetail(int i) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Задача: " + titles.get(i));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setPrefWidth(400);

        Label statusLbl = new Label("Статус: " + humanStatus(statuses.get(i)));
        statusLbl.setStyle("-fx-font-size: 13;");

        String dl = deadlines.get(i);
        Label deadlineLbl = new Label("Дедлайн: " + (dl != null ? dl.replace("T", "  ") : "не задан"));
        deadlineLbl.setStyle("-fx-font-size: 13;");

        // Определяем роль
        Long me = Session.getUserId();
        Long taskProjId = taskProjectIds.get(i);
        long[] projInfo = fetchProjectInfo(taskProjId);

        boolean isManager = projInfo != null && me != null
                && (me.equals(projInfo[0]) || me.equals(projInfo[1]));
        boolean isAssignee = me != null && me.equals(assigneeIds.get(i));

        // Комбобокс статуса — опции зависят от роли
        Label changeLabel = new Label("Изменить статус:");
        changeLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold;");
        ComboBox<String> statusBox = new ComboBox<>();

        if (isManager) {
            statusBox.getItems().addAll("WAITING", "DEVELOPMENT", "PENDING_REVIEW", "READY");
        } else if (isAssignee) {
            statusBox.getItems().addAll("WAITING", "DEVELOPMENT", "PENDING_REVIEW");
        } else {
            statusBox.getItems().addAll("WAITING", "DEVELOPMENT", "PENDING_REVIEW", "READY");
            statusBox.setDisable(true);
        }
        statusBox.setValue(statuses.get(i));

        Button saveBtn = new Button("Сохранить статус");
        saveBtn.setStyle("-fx-background-color: #FAA030; -fx-text-fill: white;"
                + " -fx-background-radius: 8; -fx-padding: 6 14;");
        saveBtn.setDisable(!isAssignee && !isManager);
        saveBtn.setOnAction(e -> {
            if (updateStatus(ids.get(i), statusBox.getValue())) {
                statuses.set(i, statusBox.getValue());
                renderTasks();
                dialog.close();
            }
        });

        // Вложения
        VBox attachPanel = buildAttachmentPanel(ids.get(i), isAssignee || isManager);

        box.getChildren().addAll(
                statusLbl, deadlineLbl,
                new Separator(), changeLabel, statusBox, saveBtn,
                new Separator(), attachPanel);

        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(500);
        scroll.setStyle("-fx-background: white; -fx-background-color: white;");

        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    private String humanStatus(String s) {
        return switch (s) {
            case "WAITING"        -> "Ожидает";
            case "DEVELOPMENT"    -> "В работе";
            case "PENDING_REVIEW" -> "На проверке ⏳";
            case "READY"          -> "Выполнено ✔";
            default               -> s;
        };
    }

    // ─── Статус ───────────────────────────────────────────────────────────────

    /** Отправляет новый статус. Возвращает true если сервер принял. */
    private boolean updateStatus(Long taskId, String status) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/task/" + taskId + "/status?status=" + status))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .method("PATCH", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) return true;
            Matcher m = Pattern.compile("\"error\":\"([^\"]+)\"").matcher(resp.body());
            showError("Не удалось изменить статус:\n" + (m.find() ? m.group(1) : "код " + resp.statusCode()));
            return false;
        } catch (Exception e) {
            showError("Ошибка: " + e.getMessage());
            return false;
        }
    }

    // ─── Информация о проекте ─────────────────────────────────────────────────

    /** Возвращает [creatorId, teamLeadId] для проекта. Кэширует результат. */
    private long[] fetchProjectInfo(Long projectId) {
        if (projectId == null) return null;
        if (projectInfoMap.containsKey(projectId)) return projectInfoMap.get(projectId);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/project/" + projectId))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
            Matcher cM  = Pattern.compile("\"creatorId\":(\\d+)").matcher(body);
            Matcher tlM = Pattern.compile("\"teamLeadId\":(\\d+)").matcher(body);
            long cid  = cM.find()  ? Long.parseLong(cM.group(1))  : -1;
            long tlid = tlM.find() ? Long.parseLong(tlM.group(1)) : -1;
            long[] info = {cid, tlid};
            projectInfoMap.put(projectId, info);
            return info;
        } catch (Exception ignored) {}
        return null;
    }

    // ─── Вложения ─────────────────────────────────────────────────────────────

    private VBox buildAttachmentPanel(Long taskId, boolean canUpload) {
        VBox panel = new VBox(5);
        List<String[]> attachments = fetchAttachments(taskId);

        Label header = new Label("Вложения (" + attachments.size() + "/5, до 10 МБ):");
        header.setStyle("-fx-font-size: 13; -fx-font-weight: bold;");
        panel.getChildren().add(header);

        for (String[] att : attachments) {
            long kb = Long.parseLong(att[2]) / 1024;
            Label lbl = new Label("📎 " + att[1] + " (" + kb + " КБ)");
            lbl.setStyle("-fx-font-size: 12; -fx-text-fill: #444;");
            lbl.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(lbl, Priority.ALWAYS);

            Button dlBtn = new Button("⬇");
            dlBtn.setStyle("-fx-background-color: #E8E8E8; -fx-background-radius: 5;"
                    + " -fx-padding: 2 7; -fx-font-size: 11; -fx-cursor: hand;");
            dlBtn.setTooltip(new Tooltip("Скачать " + att[1]));
            final String attId   = att[0];
            final String attName = att[1];
            dlBtn.setOnAction(ev -> downloadAttachment(attId, attName));

            HBox row = new HBox(8, lbl, dlBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            panel.getChildren().add(row);
        }

        if (!canUpload) return panel;

        if (attachments.size() < 5) {
            Button addFileBtn = new Button("Прикрепить файл");
            addFileBtn.setStyle("-fx-background-color: #E8E8E8; -fx-background-radius: 6;"
                    + " -fx-padding: 4 12; -fx-font-size: 12;");
            addFileBtn.setOnAction(ev -> {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Выберите файл (до 10 МБ)");
                File chosen = chooser.showOpenDialog(null);
                if (chosen == null) return;
                if (chosen.length() > 10L * 1024 * 1024) {
                    showError("Файл превышает 10 МБ");
                    return;
                }
                uploadFilesToTask(taskId, List.of(chosen));
                panel.getChildren().setAll(buildAttachmentPanel(taskId, true).getChildren());
            });
            panel.getChildren().add(addFileBtn);
        } else {
            Label limitLbl = new Label("Лимит файлов достигнут (5/5)");
            limitLbl.setStyle("-fx-font-size: 11; -fx-text-fill: #999;");
            panel.getChildren().add(limitLbl);
        }
        return panel;
    }

    private List<String[]> fetchAttachments(Long taskId) {
        List<String[]> result = new ArrayList<>();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/task/" + taskId + "/attachments"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
            Pattern objPat = Pattern.compile("\\{[^{}]+\\}");
            Matcher objM = objPat.matcher(body);
            while (objM.find()) {
                String obj = objM.group();
                Matcher idM   = Pattern.compile("\"id\":(\\d+)").matcher(obj);
                Matcher nameM = Pattern.compile("\"fileName\":\"([^\"]+)\"").matcher(obj);
                Matcher sizeM = Pattern.compile("\"fileSize\":(\\d+)").matcher(obj);
                if (idM.find() && nameM.find() && sizeM.find())
                    result.add(new String[]{idM.group(1), nameM.group(1), sizeM.group(1)});
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void downloadAttachment(String attId, String attName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить файл");
        chooser.setInitialFileName(attName);
        File dest = chooser.showSaveDialog(rootPane.getScene().getWindow());
        if (dest == null) return;
        Thread.ofVirtual().start(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(Session.API_BASE + "/attachment/" + attId + "/download"))
                        .header("Authorization", "Bearer " + Session.getToken())
                        .GET().build();
                HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() == 200) {
                    Files.write(dest.toPath(), resp.body());
                    javafx.application.Platform.runLater(() ->
                            new Alert(Alert.AlertType.INFORMATION,
                                    "Файл сохранён:\n" + dest.getAbsolutePath(),
                                    ButtonType.OK).showAndWait());
                } else {
                    javafx.application.Platform.runLater(() ->
                            showError("Ошибка скачивания: код " + resp.statusCode()));
                }
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        showError("Ошибка скачивания: " + e.getMessage()));
            }
        });
    }

    private void uploadFilesToTask(Long taskId, List<File> files) {
        for (File file : files) {
            if (file.length() > 10L * 1024 * 1024) {
                showError("«" + file.getName() + "» превышает 10 МБ — пропущен");
                continue;
            }
            try {
                String boundary = "----TaskPilotBoundary" + System.currentTimeMillis();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(Session.API_BASE + "/task/" + taskId + "/attachment"))
                        .header("Authorization", "Bearer " + Session.getToken())
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(buildMultipartBody(file, boundary))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200)
                    showError("Ошибка загрузки «" + file.getName() + "»: " + resp.body());
            } catch (Exception e) {
                showError("Ошибка загрузки «" + file.getName() + "»: " + e.getMessage());
            }
        }
    }

    private HttpRequest.BodyPublisher buildMultipartBody(File file, String boundary) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        String mime = URLConnection.guessContentTypeFromName(file.getName());
        if (mime == null) mime = "application/octet-stream";
        String headerStr = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + file.getName().replace("\"", "") + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n";
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(headerStr.getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write(footer);
        return HttpRequest.BodyPublishers.ofByteArray(out.toByteArray());
    }

    // ─── Добавить личную задачу ───────────────────────────────────────────────

    @FXML
    protected void onAddTaskClick() {
        Stage dialog = new Stage();
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setResizable(false);

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

        DatePicker deadlinePicker = new DatePicker();
        deadlinePicker.setPromptText("Дата");
        HBox.setHgrow(deadlinePicker, Priority.ALWAYS);

        Spinner<Integer> hoursSpinner = new Spinner<>(0, 23, 23);
        hoursSpinner.setEditable(true); hoursSpinner.setPrefWidth(68);
        Label colonLbl = new Label(":");
        colonLbl.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-padding: 0 0 2 0;");
        Spinner<Integer> minsSpinner = new Spinner<>(0, 59, 59);
        minsSpinner.setEditable(true); minsSpinner.setPrefWidth(68);
        HBox deadlineRow = new HBox(8, deadlinePicker, hoursSpinner, colonLbl, minsSpinner);
        deadlineRow.setAlignment(Pos.CENTER_LEFT);

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

        // Файлы
        List<File> selectedFiles = new ArrayList<>();
        Label filesLbl = new Label("Не выбраны");
        filesLbl.setStyle("-fx-font-size: 11; -fx-text-fill: #888;");
        Button pickBtn = new Button("Выбрать файлы");
        pickBtn.setStyle("-fx-background-color: #E8E8E8; -fx-background-radius: 8;"
                + " -fx-padding: 5 12; -fx-font-size: 13;");
        pickBtn.setOnAction(ev -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Файлы (до 10 МБ каждый, макс. 5)");
            List<File> chosen = chooser.showOpenMultipleDialog(dialog);
            if (chosen == null || chosen.isEmpty()) return;
            long oversize = 0;
            for (File f : chosen) {
                if (selectedFiles.size() >= 5) break;
                if (f.length() > 10L * 1024 * 1024) { oversize++; continue; }
                if (!selectedFiles.contains(f)) selectedFiles.add(f);
            }
            filesLbl.setText(selectedFiles.size() + " файл(ов)"
                    + (oversize > 0 ? " (" + oversize + " >10МБ пропущено)" : ""));
        });

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
            Long newTaskId = createPersonalTask(t, descField.getText().trim(), dl, importanceHolder[0]);
            if (newTaskId != null && !selectedFiles.isEmpty())
                uploadFilesToTask(newTaskId, selectedFiles);
            dialog.close();
            loadTasks();
        });

        VBox content = new VBox(8,
                formLabel("Назовите свою задачу"), titleField,
                formLabel("Опишите её"), descField,
                formLabel("Дедлайн"), deadlineRow,
                formLabel("Важность"), starsBox,
                formLabel("Прикрепить файлы (макс. 5, до 10 МБ)"),
                pickBtn, filesLbl,
                addBtn);
        content.setPadding(new Insets(16, 20, 20, 20));
        content.setStyle("-fx-background-color: #F5F0EB;");

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #F5F0EB; -fx-background-color: #F5F0EB;");

        VBox root = new VBox(header, sp);
        root.setStyle("-fx-background-color: #F5F0EB;");

        dialog.setScene(new Scene(root, 400, 520));
        dialog.showAndWait();
    }

    private Label formLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 13; -fx-text-fill: #666; -fx-font-weight: bold;");
        return lbl;
    }

    /** Создаёт личную задачу. Возвращает id или null при ошибке. */
    private Long createPersonalTask(String title, String desc, String deadline, int importance) {
        try {
            Long pid = getOrCreatePersonalProject();
            if (pid == null) return null;

            StringBuilder json = new StringBuilder("{");
            json.append("\"title\":\"").append(title.replace("\"", "\\\"")).append("\"");
            if (!desc.isBlank())
                json.append(",\"description\":\"").append(desc.replace("\"", "\\\"")).append("\"");
            if (deadline != null)
                json.append(",\"deadline\":\"").append(deadline).append("\"");
            json.append(",\"importance\":").append(importance);
            json.append("}");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/project/" + pid + "/task"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + Session.getToken())
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                showError("Ошибка создания задачи (" + resp.statusCode() + ")");
                return null;
            }
            Matcher idM = Pattern.compile("\"id\":(\\d+)").matcher(resp.body());
            return idM.find() ? Long.parseLong(idM.group(1)) : null;
        } catch (Exception e) {
            showError("Ошибка: " + e.getMessage());
            return null;
        }
    }

    private Long getOrCreatePersonalProject() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/project"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

            int depth = 0, start = -1;
            for (int i = 0; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c == '{') { if (depth++ == 0) start = i; }
                else if (c == '}' && --depth == 0 && start >= 0) {
                    String obj = body.substring(start, i + 1);
                    Matcher nameM = Pattern.compile("\"name\":\"([^\"]+)\"").matcher(obj);
                    Matcher idM   = Pattern.compile("\"id\":(\\d+)").matcher(obj);
                    if (nameM.find() && "Личные".equals(nameM.group(1)) && idM.find())
                        return Long.parseLong(idM.group(1));
                }
            }

            HttpRequest createReq = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/project"))
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

    // ─── Вкладки ──────────────────────────────────────────────────────────────

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

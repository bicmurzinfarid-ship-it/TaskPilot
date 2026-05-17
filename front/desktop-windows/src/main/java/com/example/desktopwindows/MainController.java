package com.example.desktopwindows;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.DatePicker;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainController {

    @FXML private javafx.scene.layout.BorderPane rootPane;
    @FXML private ListView<String> projectListView;
    @FXML private ImageView tasksIcon;
    @FXML private ImageView folderOutlineIcon;

    private final List<Long> projectIds = new ArrayList<>();

    // Все пользователи, загруженные из GET /user
    private final List<Long> allUserIds = new ArrayList<>();
    private final List<String> allUsernames = new ArrayList<>();

    private final HttpClient client = HttpClient.newHttpClient();

    @FXML
    public void initialize() {
        rootPane.setLeft(NavBar.build(NavBar.Page.HOME,
                this::onNavHome, this::onNavCalendar, this::onNavChats, this::onNavSettings));

        setIcon(tasksIcon, "icon_tasks_section.png");
        setIcon(folderOutlineIcon, "icon_folder_outline.png");

        Image folderImg = loadImage("icon_folder_orange.png");
        projectListView.setCellFactory(lv -> new ListCell<>() {
            private final ImageView iv = new ImageView(folderImg);
            private final Label nameLbl = new Label();
            private final HBox row = new HBox(10, iv, nameLbl);
            {
                iv.setFitWidth(20); iv.setFitHeight(20); iv.setPreserveRatio(true);
                nameLbl.setStyle("-fx-font-size: 14;");
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(4, 0, 4, 8));
                setGraphic(row); setText(null);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) row.setVisible(false);
                else { row.setVisible(true); nameLbl.setText(item); }
            }
        });

        Thread.ofVirtual().start(this::loadProjects);
        projectListView.setOnMouseClicked(event -> {
            int idx = projectListView.getSelectionModel().getSelectedIndex();
            if (idx < 0) return;
            if (event.getClickCount() == 2)
                showProjectDialog(projectIds.get(idx), projectListView.getItems().get(idx));
        });

        ContextMenu contextMenu = new ContextMenu();
        MenuItem openItem = new MenuItem("Открыть");
        MenuItem manageItem = new MenuItem("Управление");
        MenuItem deleteItem = new MenuItem("Удалить");
        openItem.setOnAction(e -> onOpenProjectClick());
        manageItem.setOnAction(e -> onManageProjectClick());
        deleteItem.setOnAction(e -> onDeleteProjectClick());
        contextMenu.getItems().addAll(openItem, manageItem, new SeparatorMenuItem(), deleteItem);
        projectListView.setContextMenu(contextMenu);
    }

    // ─── Загрузка проектов ────────────────────────────────────────────────────

    private void loadProjects() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/project"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                Platform.runLater(() -> showError("Ошибка загрузки проектов (" + resp.statusCode() + ")"));
                return;
            }
            List<Long> newIds = new ArrayList<>();
            List<String> newNames = new ArrayList<>();
            // Brace-depth parser: extracts each top-level JSON object correctly,
            // immune to cross-contamination from nested member arrays
            String body = resp.body();
            int depth = 0, start = -1;
            for (int i = 0; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c == '{') { if (depth++ == 0) start = i; }
                else if (c == '}' && --depth == 0 && start >= 0) {
                    String obj = body.substring(start, i + 1);
                    Matcher idM   = Pattern.compile("\"id\":(\\d+)").matcher(obj);
                    Matcher nameM = Pattern.compile("\"name\":\"([^\"]+)\"").matcher(obj);
                    if (idM.find() && nameM.find()) {
                        newIds.add(Long.parseLong(idM.group(1)));
                        newNames.add(nameM.group(1));
                    }
                }
            }
            Platform.runLater(() -> {
                projectIds.clear();
                projectIds.addAll(newIds);
                projectListView.getItems().setAll(newNames);
            });
        } catch (Exception e) {
            Platform.runLater(() -> showError("Ошибка загрузки проектов: " + e.getMessage()));
        }
    }

    // ─── Создание проекта с добавлением участников ───────────────────────────

    @FXML
    protected void onAddProjectClick() {
        loadAllUsers();
        showCreateProjectDialog();
    }

    private void loadAllUsers() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/user"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            allUserIds.clear();
            allUsernames.clear();

            Pattern pat = Pattern.compile("\"id\":(\\d+),\"username\":\"([^\"]+)\"");
            Matcher m = pat.matcher(resp.body());
            while (m.find()) {
                allUserIds.add(Long.parseLong(m.group(1)));
                allUsernames.add(m.group(2));
            }
        } catch (Exception e) {
            showError("Ошибка загрузки пользователей: " + e.getMessage());
        }
    }

    private void showCreateProjectDialog() {
        Stage dialog = new Stage();
        dialog.initOwner(projectListView.getScene().getWindow());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setResizable(false);

        // ─── Шапка ───────────────────────────────────────────────────────────
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #FAA030; -fx-padding: 18 16;");
        header.setAlignment(Pos.CENTER_LEFT);
        Label headerLbl = new Label("Новый проект");
        headerLbl.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: white;");
        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"
                + " -fx-font-size: 15; -fx-cursor: hand; -fx-padding: 0 4;");
        closeBtn.setOnAction(e -> dialog.close());
        header.getChildren().addAll(headerLbl, hSpacer, closeBtn);

        // ─── Название ────────────────────────────────────────────────────────
        TextField nameField = new TextField();
        nameField.setPromptText("Назовите проект...");
        nameField.setStyle("-fx-background-color: #E8E8E8; -fx-background-radius: 10;"
                + " -fx-border-color: transparent; -fx-padding: 10 14; -fx-font-size: 14;");
        nameField.setMaxWidth(Double.MAX_VALUE);

        // ─── Участники с чипами ───────────────────────────────────────────────
        List<Long>   selIds   = new ArrayList<>();
        List<String> selNames = new ArrayList<>();
        FlowPane chips = new FlowPane(6, 6);

        TextField searchField = new TextField();
        searchField.setPromptText("Поиск пользователя...");
        searchField.setStyle("-fx-background-color: #E8E8E8; -fx-background-radius: 10;"
                + " -fx-border-color: transparent; -fx-padding: 8 12; -fx-font-size: 13;");
        searchField.setMaxWidth(Double.MAX_VALUE);

        ContextMenu suggestionsPopup = new ContextMenu();

        Runnable[] rebuildChips = {null};
        rebuildChips[0] = () -> {
            chips.getChildren().clear();
            for (int i = 0; i < selNames.size(); i++) {
                final int fi = i;
                Label nameLbl = new Label(selNames.get(i));
                nameLbl.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
                Button rm = new Button("✕");
                rm.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"
                        + " -fx-font-size: 10; -fx-padding: 0 2; -fx-cursor: hand;");
                rm.setOnAction(ev -> { selIds.remove(fi); selNames.remove(fi); rebuildChips[0].run(); });
                HBox chip = new HBox(4, nameLbl, rm);
                chip.setStyle("-fx-background-color: #FAA030; -fx-background-radius: 12; -fx-padding: 4 8;");
                chip.setAlignment(Pos.CENTER_LEFT);
                chips.getChildren().add(chip);
            }
        };

        searchField.textProperty().addListener((obs, old, newVal) -> {
            suggestionsPopup.getItems().clear();
            String q = newVal.trim().toLowerCase();
            if (q.isEmpty()) { suggestionsPopup.hide(); return; }
            for (String name : allUsernames) {
                if (name.toLowerCase().contains(q) && !selNames.contains(name)) {
                    MenuItem mi = new MenuItem(name);
                    mi.setOnAction(ev -> {
                        int i = allUsernames.indexOf(name);
                        if (i >= 0 && !selIds.contains(allUserIds.get(i))) {
                            selIds.add(allUserIds.get(i));
                            selNames.add(name);
                            rebuildChips[0].run();
                        }
                        searchField.clear();
                        suggestionsPopup.hide();
                    });
                    suggestionsPopup.getItems().add(mi);
                }
            }
            if (!suggestionsPopup.getItems().isEmpty())
                suggestionsPopup.show(searchField, javafx.geometry.Side.BOTTOM, 0, 0);
            else
                suggestionsPopup.hide();
        });

        VBox membersBox = new VBox(6, styledLabel("Участники"), searchField, chips);
        membersBox.setStyle("-fx-background-color: #F0F4FF; -fx-background-radius: 10;"
                + " -fx-padding: 10 12; -fx-border-color: #C8D4F8; -fx-border-radius: 10; -fx-border-width: 1;");

        // ─── Кнопка СОЗДАТЬ ───────────────────────────────────────────────────
        Button createBtn = new Button("СОЗДАТЬ");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setStyle("-fx-background-color: #FAA030; -fx-text-fill: white; -fx-font-weight: bold;"
                + " -fx-font-size: 16; -fx-background-radius: 25; -fx-padding: 13 0; -fx-cursor: hand;");
        createBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isBlank()) { showError("Название не может быть пустым"); return; }
            Long projectId = createProject(name);
            if (projectId == null) return;
            for (Long uid : selIds) addMember(projectId, uid);
            dialog.close();
            Thread.ofVirtual().start(this::loadProjects);
        });

        VBox content = new VBox(12,
                styledLabel("Название проекта"), nameField,
                membersBox,
                createBtn
        );
        content.setPadding(new Insets(16, 20, 20, 20));
        content.setStyle("-fx-background-color: #F5F0EB;");

        VBox root = new VBox(header, content);
        root.setStyle("-fx-background-color: #F5F0EB;");

        dialog.setScene(new Scene(root, 420, 380));
        dialog.showAndWait();
    }

    private Long createProject(String name) {
        try {
            String json = "{\"name\": \"" + name + "\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/project"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + Session.getToken())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                showError("Ошибка создания проекта (" + resp.statusCode() + "): " + resp.body());
                return null;
            }
            Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(resp.body());
            if (m.find()) return Long.parseLong(m.group(1));
            showError("Сервер не вернул ID проекта: " + resp.body());
        } catch (Exception e) {
            showError("Ошибка создания проекта: " + e.getMessage());
        }
        return null;
    }

    private void addMember(Long projectId, Long userId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/project/" + projectId + "/member/" + userId))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            showError("Ошибка добавления участника " + userId + ": " + e.getMessage());
        }
    }

    // ─── Открытие проекта ────────────────────────────────────────────────────

    @FXML
    protected void onOpenProjectClick() {
        int idx = projectListView.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            showError("Выберите проект для открытия");
            return;
        }
        showProjectDialog(projectIds.get(idx), projectListView.getItems().get(idx));
    }

    private void showProjectDialog(Long projectId, String projectName) {
        ProjectDetails details = loadProjectDetails(projectId);
        boolean isCreator = details.creatorId() != null
                && details.creatorId().equals(Session.getUserId());

        // ─── Вкладка Информация ───────────────────────────────────────────────
        ScrollPane infoScroll = new ScrollPane();
        infoScroll.setFitToWidth(true);
        infoScroll.setStyle("-fx-background-color: #F5F0EB; -fx-background: #F5F0EB; -fx-border-color: transparent;");

        VBox infoTab = buildInfoTab(projectId, details, isCreator);
        infoScroll.setContent(infoTab);

        // ─── Вкладка Доска ────────────────────────────────────────────────────
        VBox boardTab = buildBoardTab(projectId, details);

        // ─── Вкладка Чат (заглушка) ───────────────────────────────────────────
        VBox chatTab = new VBox();
        chatTab.setStyle("-fx-background-color: #F5F0EB; -fx-alignment: CENTER;");
        chatTab.getChildren().add(new Label("Чат будет реализован позже"));

        // ─── TabPane ──────────────────────────────────────────────────────────
        Tab tabInfo = new Tab("Информация", infoScroll);
        tabInfo.setClosable(false);
        Tab tabBoard = new Tab("Доска", boardTab);
        tabBoard.setClosable(false);
        Tab tabChat = new Tab("Чат", chatTab);
        tabChat.setClosable(false);

        TabPane tabPane = new TabPane(tabInfo, tabBoard, tabChat);
        tabPane.setPrefSize(480, 420);
        tabPane.setStyle("-fx-tab-min-width: 100;");

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(projectName);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(tabPane);
        dialog.getDialogPane().setStyle("-fx-padding: 0;");
        dialog.showAndWait();
    }

    private VBox buildInfoTab(Long projectId, ProjectDetails details, boolean isCreator) {
        VBox tab = new VBox(0);
        tab.setStyle("-fx-background-color: #F5F0EB;");

        // ─── Тимлид ───────────────────────────────────────────────────────────
        String teamLeadName = "не назначен";
        if (details.teamLeadId() != null) {
            int tlIdx = details.memberIds().indexOf(details.teamLeadId());
            if (tlIdx >= 0) teamLeadName = details.memberNames().get(tlIdx);
        }
        tab.getChildren().add(makeInfoRow("Лидер", teamLeadName, details.teamLeadId()));
        tab.getChildren().add(new Separator());

        // Управление тимлидом — только для создателя
        if (isCreator) {
            ComboBox<String> tlCombo = new ComboBox<>();
            tlCombo.getItems().addAll(details.memberNames());
            if (details.teamLeadId() != null) {
                int cur = details.memberIds().indexOf(details.teamLeadId());
                if (cur >= 0) tlCombo.getSelectionModel().select(cur);
            }
            tlCombo.setPromptText("Выбрать тимлида...");
            tlCombo.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(tlCombo, Priority.ALWAYS);

            Button setBtn = new Button("Назначить");
            setBtn.setStyle("-fx-background-color: #FAA030; -fx-text-fill: white;"
                    + " -fx-background-radius: 8; -fx-padding: 6 12; -fx-cursor: hand;");

            Button removeBtn = new Button("Снять");
            removeBtn.setStyle("-fx-background-color: #E53935; -fx-text-fill: white;"
                    + " -fx-background-radius: 8; -fx-padding: 6 12; -fx-cursor: hand;");
            removeBtn.setDisable(details.teamLeadId() == null);

            setBtn.setOnAction(e -> {
                int i = tlCombo.getSelectionModel().getSelectedIndex();
                if (i < 0) return;
                setTeamLead(projectId, details.memberIds().get(i));
                removeBtn.setDisable(false);
                // обновить строку "Лидер" вверху
                HBox leaderRow = (HBox) tab.getChildren().get(0);
                Label nameLbl = (Label) ((HBox) leaderRow).getChildren().get(1);
                nameLbl.setText(details.memberNames().get(i));
            });

            removeBtn.setOnAction(e -> {
                removeTeamLead(projectId);
                removeBtn.setDisable(true);
                HBox leaderRow = (HBox) tab.getChildren().get(0);
                Label nameLbl = (Label) ((HBox) leaderRow).getChildren().get(1);
                nameLbl.setText("не назначен");
            });

            HBox tlRow = new HBox(8, tlCombo, setBtn, removeBtn);
            tlRow.setAlignment(Pos.CENTER_LEFT);
            tlRow.setStyle("-fx-padding: 8 12;");
            tab.getChildren().add(tlRow);
            tab.getChildren().add(new Separator());
        }

        // ─── Участники ────────────────────────────────────────────────────────
        for (int mi = 0; mi < details.memberNames().size(); mi++) {
            tab.getChildren().add(makeInfoRow("",
                    details.memberNames().get(mi),
                    details.memberIds().get(mi)));
        }
        tab.getChildren().add(new Separator());

        // ─── Описание ─────────────────────────────────────────────────────────
        Label descLabel = new Label("Описание");
        descLabel.setStyle("-fx-font-size: 14; -fx-padding: 8 12 4 12;");
        TextArea descArea = new TextArea();
        descArea.setPromptText("Описание проекта...");
        descArea.setPrefRowCount(3);
        descArea.setEditable(false);
        descArea.setStyle("-fx-background-color: #E0E0E0; -fx-background-radius: 8; -fx-padding: 6;");
        VBox.setMargin(descArea, new javafx.geometry.Insets(0, 12, 12, 12));
        tab.getChildren().addAll(descLabel, descArea);

        return tab;
    }

    private HBox makeInfoRow(String role, String name, Long userId) {
        HBox row = new HBox(12);
        row.setStyle("-fx-padding: 10 16;");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        row.getChildren().add(AvatarLoader.make(userId, name, 36));

        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-size: 14;");
        row.getChildren().add(nameLbl);

        if (!role.isBlank()) {
            Label roleLbl = new Label(role);
            roleLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #888; -fx-padding: 0 0 0 4;");
            HBox.setHgrow(nameLbl, javafx.scene.layout.Priority.ALWAYS);
            row.getChildren().add(roleLbl);
        }
        return row;
    }

    private VBox buildBoardTab(Long projectId, ProjectDetails details) {
        VBox board = new VBox(8);
        board.setStyle("-fx-background-color: #F5F0EB; -fx-padding: 10;");

        // Кнопки
        Button addTaskBtn = new Button("+ Добавить задачу");
        addTaskBtn.setStyle("-fx-background-color: #FAA030; -fx-background-radius: 20;"
                + " -fx-font-size: 13; -fx-padding: 6 16; -fx-text-fill: white;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        VBox taskList = new VBox(8);
        taskList.setStyle("-fx-padding: 4;");
        scrollPane.setContent(taskList);
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        Runnable refreshTasks = () -> {
            taskList.getChildren().clear();
            taskIds.clear();
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(Session.API_BASE + "/project/" + projectId + "/task"))
                        .header("Authorization", "Bearer " + Session.getToken())
                        .GET().build();
                String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

                Pattern blockPat = Pattern.compile("\\{[^{}]*\"title\"[^{}]*\\}", Pattern.DOTALL);
                Matcher blockM = blockPat.matcher(body);
                while (blockM.find()) {
                    String block = blockM.group();
                    Matcher idM = Pattern.compile("\"id\":(\\d+)").matcher(block);
                    Matcher titleM = Pattern.compile("\"title\":\"([^\"]+)\"").matcher(block);
                    Matcher statusM = Pattern.compile("\"status\":\"([^\"]+)\"").matcher(block);
                    if (!idM.find() || !titleM.find()) continue;
                    String status = statusM.find() ? statusM.group(1) : "WAITING";
                    Long tid = Long.parseLong(idM.group(1));
                    taskIds.add(tid);
                    taskList.getChildren().add(buildTaskCard(block, titleM.group(1), status, tid, taskList));
                }
                if (taskList.getChildren().isEmpty()) {
                    Label empty = new Label("Задач нет");
                    empty.setStyle("-fx-text-fill: #888; -fx-font-size: 13;");
                    taskList.getChildren().add(empty);
                }
            } catch (Exception e) {
                taskList.getChildren().add(new Label("Ошибка: " + e.getMessage()));
            }
        };
        refreshTasks.run();

        addTaskBtn.setOnAction(e -> {
            showAddTaskDialog(projectId, details.memberIds(), details.memberNames(), null);
            refreshTasks.run();
        });

        board.getChildren().addAll(addTaskBtn, scrollPane);
        return board;
    }

    private HBox buildTaskCard(String block, String title, String status, Long taskId, VBox taskList) {
        String deadlineStr = null;
        Matcher dlM = Pattern.compile("\"deadline\":\"([^\"]+)\"").matcher(block);
        if (dlM.find()) deadlineStr = dlM.group(1);

        String quadrant = null;
        Matcher qM = Pattern.compile("\"eisenhowerQuadrant\":\"([^\"]+)\"").matcher(block);
        if (qM.find()) quadrant = qM.group(1);

        int importance = 2;
        Matcher impM = Pattern.compile("\"importance\":(\\d+)").matcher(block);
        if (impM.find()) importance = Integer.parseInt(impM.group(1));

        boolean overdue = false;
        if (deadlineStr != null) {
            try {
                overdue = LocalDateTime.parse(deadlineStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .isBefore(LocalDateTime.now());
            } catch (Exception ignored) {}
        }

        // Фон карточки: READY — зелёный, просрочено — тёмно-красный, иначе — Eisenhower
        String cardBg;
        String textColor;
        if ("READY".equals(status)) {
            cardBg = "#43A047"; textColor = "white";
        } else if (overdue) {
            cardBg = "#B71C1C"; textColor = "white";
        } else {
            cardBg = boardEisenhowerColor(quadrant); textColor = "#1A1A1A";
        }

        // Полоска статуса слева
        String stripeColor = switch (status) {
            case "READY"        -> "#2E7D32";
            case "DEVELOPMENT"  -> "#1565C0";
            default             -> "#757575";
        };
        Rectangle stripe = new Rectangle(5, 50);
        stripe.setFill(Color.web(stripeColor));

        int impClamped = Math.min(Math.max(importance, 1), 3);
        Label starsLbl = new Label("★".repeat(impClamped) + "☆".repeat(3 - impClamped));
        starsLbl.setStyle("-fx-font-size: 10; -fx-text-fill: "
                + ("white".equals(textColor) ? "rgba(255,255,255,0.8)" : "#FAA030") + ";");

        Label titleLbl = new Label(title);
        titleLbl.setMaxWidth(Double.MAX_VALUE);
        titleLbl.setWrapText(false);
        titleLbl.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");

        Label deadlineLbl = new Label(formatDeadline(deadlineStr));
        deadlineLbl.setStyle("-fx-font-size: 11; -fx-text-fill: "
                + ("white".equals(textColor) ? "rgba(255,255,255,0.75)" : "#888888") + ";");

        VBox textCol = new VBox(2, titleLbl, starsLbl, deadlineLbl);
        textCol.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        Button delBtn = new Button("✕");
        delBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: "
                + ("white".equals(textColor) ? "rgba(255,255,255,0.7)" : "#AAAAAA")
                + "; -fx-font-size: 12; -fx-cursor: hand;");
        delBtn.setOnAction(e -> {
            deleteTask(taskId);
            taskIds.remove(taskId);
            taskList.getChildren().removeIf(node ->
                node instanceof HBox hb && hb.getUserData() != null && hb.getUserData().equals(taskId));
        });

        HBox inner = new HBox(10, textCol, delBtn);
        inner.setAlignment(Pos.CENTER_LEFT);
        inner.setPadding(new Insets(8, 12, 8, 10));
        inner.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(inner, Priority.ALWAYS);

        HBox card = new HBox(0, stripe, inner);
        card.setUserData(taskId);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 10;"
                + " -fx-border-color: rgba(0,0,0,0.08); -fx-border-radius: 10; -fx-border-width: 1;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 4, 0, 0, 1); -fx-cursor: hand;");
        return card;
    }

    private String boardEisenhowerColor(String quadrant) {
        if (quadrant == null || quadrant.isEmpty()) return "#F5F5F5";
        return switch (quadrant) {
            case "URGENT_IMPORTANT"              -> "#FFCDD2";
            case "URGENT_SOMEWHAT_IMPORTANT"     -> "#FFE0B2";
            case "URGENT_NOT_IMPORTANT"          -> "#FFF9C4";
            case "NOT_URGENT_IMPORTANT"          -> "#BBDEFB";
            case "NOT_URGENT_SOMEWHAT_IMPORTANT" -> "#E1F5FE";
            default                              -> "#F5F5F5";
        };
    }

    private String formatDeadline(String deadlineStr) {
        if (deadlineStr == null) return "";
        try {
            LocalDateTime dl = LocalDateTime.parse(deadlineStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            long hours = ChronoUnit.HOURS.between(LocalDateTime.now(), dl);
            if (hours < 0) return "Просрочено";
            if (hours < 24) return "Осталось " + hours + " ч";
            return "Осталось " + (hours / 24) + " д";
        } catch (Exception e) { return deadlineStr; }
    }

    private void showAddTaskDialog(Long projectId,
                                   List<Long> memberIds, List<String> memberNames,
                                   ListView<String> tasksView) {
        loadAllUsers();

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

        // ─── Название / Описание ──────────────────────────────────────────────
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

        // ─── Исполнители (множественный выбор с чипами) ───────────────────────
        List<Long>   selIds   = new ArrayList<>();
        List<String> selNames = new ArrayList<>();
        FlowPane chips = new FlowPane(6, 6);

        TextField searchField = new TextField();
        searchField.setPromptText("Поиск пользователя...");
        searchField.setStyle("-fx-background-color: #E8E8E8; -fx-background-radius: 10;"
                + " -fx-border-color: transparent; -fx-padding: 8 12; -fx-font-size: 13;");
        searchField.setMaxWidth(Double.MAX_VALUE);

        ContextMenu assigneesPopup = new ContextMenu();

        Runnable[] rebuildChips = {null};
        rebuildChips[0] = () -> {
            chips.getChildren().clear();
            for (int i = 0; i < selNames.size(); i++) {
                final int fi = i;
                Label nameLbl = new Label(selNames.get(i));
                nameLbl.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
                Button rm = new Button("✕");
                rm.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"
                        + " -fx-font-size: 10; -fx-padding: 0 2; -fx-cursor: hand;");
                rm.setOnAction(ev -> { selIds.remove(fi); selNames.remove(fi); rebuildChips[0].run(); });
                HBox chip = new HBox(4, nameLbl, rm);
                chip.setStyle("-fx-background-color: #FAA030; -fx-background-radius: 12; -fx-padding: 4 8;");
                chip.setAlignment(Pos.CENTER_LEFT);
                chips.getChildren().add(chip);
            }
        };

        searchField.textProperty().addListener((obs, old, newVal) -> {
            assigneesPopup.getItems().clear();
            String q = newVal.trim().toLowerCase();
            if (q.isEmpty()) { assigneesPopup.hide(); return; }
            for (String name : allUsernames) {
                if (name.toLowerCase().contains(q) && !selNames.contains(name)) {
                    MenuItem mi = new MenuItem(name);
                    mi.setOnAction(ev -> {
                        int i = allUsernames.indexOf(name);
                        if (i >= 0 && !selIds.contains(allUserIds.get(i))) {
                            selIds.add(allUserIds.get(i));
                            selNames.add(name);
                            rebuildChips[0].run();
                        }
                        searchField.clear();
                        assigneesPopup.hide();
                    });
                    assigneesPopup.getItems().add(mi);
                }
            }
            if (!assigneesPopup.getItems().isEmpty())
                assigneesPopup.show(searchField, javafx.geometry.Side.BOTTOM, 0, 0);
            else
                assigneesPopup.hide();
        });

        VBox assigneesBox = new VBox(6, styledLabel("Исполнители"), searchField, chips);
        assigneesBox.setStyle("-fx-background-color: #F0F4FF; -fx-background-radius: 10;"
                + " -fx-padding: 10 12; -fx-border-color: #C8D4F8; -fx-border-radius: 10; -fx-border-width: 1;");

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

        // ─── Звёзды важности ─────────────────────────────────────────────────
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
            String title = titleField.getText().trim();
            if (title.isBlank()) { showError("Название не может быть пустым"); return; }

            // Дедлайн с часами и минутами
            String deadline = null;
            if (deadlinePicker.getValue() != null) {
                deadline = deadlinePicker.getValue()
                        + "T" + String.format("%02d", hoursSpinner.getValue())
                        + ":" + String.format("%02d", minsSpinner.getValue()) + ":00";
            }

            String desc = descField.getText().trim();
            int imp = importanceHolder[0];
            if (selIds.isEmpty()) {
                createProjectTask(projectId, title, desc, null, deadline, imp);
            } else {
                for (Long aid : selIds) {
                    createProjectTask(projectId, title, desc, aid, deadline, imp);
                }
            }
            dialog.close();
        });

        VBox content = new VBox(10,
                styledLabel("Назовите свою задачу"), titleField,
                styledLabel("Опишите её"), descField,
                assigneesBox,
                styledLabel("Дедлайн"), deadlineRow,
                styledLabel("Важность"), starsBox,
                addBtn
        );
        content.setPadding(new Insets(16, 20, 20, 20));
        content.setStyle("-fx-background-color: #F5F0EB;");

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: #F5F0EB; -fx-background: #F5F0EB; -fx-border-color: transparent;");

        VBox root = new VBox(header, sp);
        root.setStyle("-fx-background-color: #F5F0EB;");

        dialog.setScene(new Scene(root, 420, 640));
        dialog.showAndWait();
    }

    private Label styledLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 13; -fx-text-fill: #666; -fx-font-weight: bold;");
        return lbl;
    }


private void createProjectTask(Long projectId, String title, String description,
                                   Long assigneeId, String deadline, Integer importance) {
        try {
            StringBuilder json = new StringBuilder("{");
            json.append("\"title\":\"").append(title.replace("\"", "\\\"")).append("\"");
            if (!description.isBlank())
                json.append(",\"description\":\"").append(description.replace("\"", "\\\"")).append("\"");
            if (assigneeId != null)
                json.append(",\"assigneeId\":").append(assigneeId);
            if (deadline != null)
                json.append(",\"deadline\":\"").append(deadline).append("\"");
            json.append(",\"importance\":").append(importance);
            json.append("}");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/project/" + projectId + "/task"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + Session.getToken())
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200)
                showError("Ошибка создания задачи (" + resp.statusCode() + "): " + resp.body());
        } catch (Exception e) {
            showError("Ошибка создания задачи: " + e.getMessage());
        }
    }

    private void deleteTask(Long taskId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/task/" + taskId))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .DELETE()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 204)
                showError("Ошибка удаления задачи (" + resp.statusCode() + "): " + resp.body());
        } catch (Exception e) {
            showError("Ошибка удаления задачи: " + e.getMessage());
        }
    }

    private final List<Long> taskIds = new ArrayList<>();

    // ─── Управление существующим проектом ────────────────────────────────────

    @FXML
    protected void onManageProjectClick() {
        int idx = projectListView.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            showError("Выберите проект для управления");
            return;
        }
        loadAllUsers();
        showManageDialog(projectIds.get(idx));
    }

    private record ProjectDetails(Long creatorId, Long teamLeadId, List<Long> memberIds, List<String> memberNames) {}

    private ProjectDetails loadProjectDetails(Long projectId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/project/" + projectId))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET()
                    .build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

            Long creatorId = null;
            Matcher crm = Pattern.compile("\"creatorId\":(\\d+)").matcher(body);
            if (crm.find()) creatorId = Long.parseLong(crm.group(1));

            Long teamLeadId = null;
            Matcher tlm = Pattern.compile("\"teamLeadId\":(\\d+)").matcher(body);
            if (tlm.find()) teamLeadId = Long.parseLong(tlm.group(1));

            List<Long> memberIds = new ArrayList<>();
            List<String> memberNames = new ArrayList<>();
            int membersStart = body.indexOf("\"members\":[");
            if (membersStart != -1) {
                Matcher mm = Pattern.compile("\"id\":(\\d+),\"username\":\"([^\"]+)\"")
                        .matcher(body.substring(membersStart));
                while (mm.find()) {
                    memberIds.add(Long.parseLong(mm.group(1)));
                    memberNames.add(mm.group(2));
                }
            }
            return new ProjectDetails(creatorId, teamLeadId, memberIds, memberNames);
        } catch (Exception e) {
            showError("Ошибка загрузки данных проекта: " + e.getMessage());
            return new ProjectDetails(null, null, new ArrayList<>(), new ArrayList<>());
        }
    }

    private void showManageDialog(Long projectId) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Управление проектом");
        dialog.getDialogPane().getButtonTypes().add(
                new ButtonType("Готово", ButtonBar.ButtonData.OK_DONE));

        ProjectDetails[] details = { loadProjectDetails(projectId) };

        ObservableList<String> memberNamesObs = FXCollections.observableArrayList(details[0].memberNames());
        Label teamLeadLabel = new Label();

        Runnable refresh = () -> {
            details[0] = loadProjectDetails(projectId);
            memberNamesObs.setAll(details[0].memberNames());
            if (details[0].teamLeadId() != null) {
                int tlIdx = details[0].memberIds().indexOf(details[0].teamLeadId());
                teamLeadLabel.setText("Текущий тимлид: " + (tlIdx >= 0 ? details[0].memberNames().get(tlIdx) : "—"));
            } else {
                teamLeadLabel.setText("Текущий тимлид: не назначен");
            }
        };
        refresh.run();

        // Добавить участника по поиску
        TextField addSearchField = new TextField();
        addSearchField.setPromptText("Никнейм пользователя");
        Label addStatus = new Label();
        Button addBtn = new Button("Добавить");
        addBtn.setOnAction(e -> {
            String query = addSearchField.getText().trim();
            if (query.isBlank()) return;
            int i = allUsernames.indexOf(query);
            if (i < 0) { addStatus.setText("Не найден"); return; }
            Long uid = allUserIds.get(i);
            if (details[0].memberIds().contains(uid)) { addStatus.setText("Уже участник"); return; }
            addMember(projectId, uid);
            addSearchField.clear();
            addStatus.setText("");
            refresh.run();
        });

        // Удалить участника
        ComboBox<String> removeCombo = new ComboBox<>(memberNamesObs);
        removeCombo.setPromptText("Выберите участника");
        Button removeBtn = new Button("Удалить");
        removeBtn.setOnAction(e -> {
            int i = removeCombo.getSelectionModel().getSelectedIndex();
            if (i < 0) return;
            removeMember(projectId, details[0].memberIds().get(i));
            refresh.run();
        });

        // Назначить тимлида
        ComboBox<String> tlCombo = new ComboBox<>(memberNamesObs);
        tlCombo.setPromptText("Выберите тимлида");
        Button setTlBtn = new Button("Назначить");
        setTlBtn.setOnAction(e -> {
            int i = tlCombo.getSelectionModel().getSelectedIndex();
            if (i < 0) return;
            setTeamLead(projectId, details[0].memberIds().get(i));
            refresh.run();
        });

        // Снять тимлида
        Button removeTlBtn = new Button("Снять тимлида");
        removeTlBtn.setOnAction(e -> {
            removeTeamLead(projectId);
            refresh.run();
        });

        // Список текущих участников (виден сразу)
        ListView<String> membersListView = new ListView<>(memberNamesObs);
        membersListView.setPrefHeight(90);

        VBox content = new VBox(10,
                new Label("Участники проекта:"),
                membersListView,
                teamLeadLabel,
                new Separator(),
                new Label("Добавить участника:"),
                new HBox(5, addSearchField, addBtn),
                addStatus,
                new Separator(),
                new Label("Удалить участника:"),
                new HBox(5, removeCombo, removeBtn),
                new Separator(),
                new Label("Назначить тимлида:"),
                new HBox(5, tlCombo, setTlBtn),
                removeTlBtn
        );
        content.setPrefWidth(350);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void removeMember(Long projectId, Long userId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/project/" + projectId + "/member/" + userId))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .DELETE()
                    .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            showError("Ошибка удаления участника: " + e.getMessage());
        }
    }

    private void setTeamLead(Long projectId, Long userId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/project/" + projectId + "/teamlead/" + userId))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            showError("Ошибка назначения тимлида: " + e.getMessage());
        }
    }

    private void removeTeamLead(Long projectId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/project/" + projectId + "/teamlead"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .DELETE()
                    .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            showError("Ошибка снятия тимлида: " + e.getMessage());
        }
    }

    // ─── Удаление проекта ─────────────────────────────────────────────────────

    @FXML
    protected void onDeleteProjectClick() {
        int idx = projectListView.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            showError("Выберите проект для удаления");
            return;
        }

        String name = projectListView.getItems().get(idx);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить проект «" + name + "»?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                deleteProject(projectIds.get(idx));
                Thread.ofVirtual().start(this::loadProjects);
            }
        });
    }

    private void deleteProject(Long projectId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(Session.API_BASE + "/project/" + projectId))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .DELETE()
                    .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            showError("Ошибка удаления проекта: " + e.getMessage());
        }
    }

    // ─── Навигация ────────────────────────────────────────────────────────────

    @FXML protected void onNavHome() { /* уже здесь */ }

    @FXML
    protected void onNavCalendar() { navigate("calendar-view.fxml"); }

    @FXML protected void onNavChats() { navigate("chats-view.fxml"); }
    @FXML protected void onNavSettings() { navigate("settings-view.fxml"); }

    @FXML
    protected void onTasksSectionClick() {
        navigate("tasks-view.fxml");
    }

    private void navigate(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) projectListView.getScene().getWindow();
            stage.setScene(new Scene(loader.load(),
                    stage.getWidth(), stage.getHeight()));
        } catch (Exception e) {
            showError("Ошибка навигации: " + e.getMessage());
        }
    }

    // ─── Утилиты ──────────────────────────────────────────────────────────────

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void setIcon(ImageView iv, String filename) {
        if (iv == null) return;
        Image img = loadImage(filename);
        if (img != null) iv.setImage(img);
    }

    private Image loadImage(String filename) {
        try {
            var stream = getClass().getResourceAsStream(filename);
            return stream != null ? new Image(stream) : null;
        } catch (Exception e) { return null; }
    }
}
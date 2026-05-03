package com.example.desktopwindows;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainController {

    @FXML private ListView<String> projectListView;

    // Индексы совпадают с projectListView: projectIds.get(i) — id проекта в строке i
    private final List<Long> projectIds = new ArrayList<>();

    // Все пользователи, загруженные из GET /user
    private final List<Long> allUserIds = new ArrayList<>();
    private final List<String> allUsernames = new ArrayList<>();

    private final HttpClient client = HttpClient.newHttpClient();

    @FXML
    public void initialize() {
        loadProjects();
        projectListView.setOnMouseClicked(event -> {
            int idx = projectListView.getSelectionModel().getSelectedIndex();
            if (idx < 0) return;
            if (event.getClickCount() == 2) {
                showProjectDialog(projectIds.get(idx), projectListView.getItems().get(idx));
            }
        });

        // Контекстное меню по правому клику
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
                    .uri(URI.create("http://localhost:8080/project"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                showError("Ошибка загрузки проектов (" + resp.statusCode() + "): " + resp.body());
                return;
            }

            projectIds.clear();
            projectListView.getItems().clear();

            // Ищем id и name по отдельности внутри каждого блока проекта.
            // Якорь "creatorId" гарантирует что это объект Project, а не User внутри members.
            // Порядок полей в JSON не фиксирован, поэтому ищем каждое поле отдельно в блоке до "members".
            Pattern blockPat = Pattern.compile("\\{(.*?)\"members\"", Pattern.DOTALL);
            Matcher blockMatcher = blockPat.matcher(resp.body());
            while (blockMatcher.find()) {
                String block = blockMatcher.group(1);
                if (!block.contains("\"creatorId\"")) continue;
                Matcher idM = Pattern.compile("\"id\":(\\d+)").matcher(block);
                Matcher nameM = Pattern.compile("\"name\":\"([^\"]+)\"").matcher(block);
                if (idM.find() && nameM.find()) {
                    projectIds.add(Long.parseLong(idM.group(1)));
                    projectListView.getItems().add(nameM.group(1));
                }
            }
        } catch (Exception e) {
            showError("Ошибка загрузки проектов: " + e.getMessage());
        }
    }

    // ─── Создание проекта с добавлением участников ───────────────────────────

    @FXML
    protected void onAddProjectClick() {
        loadAllUsers();

        Dialog<Map.Entry<String, List<Long>>> dialog = buildCreateDialog();
        dialog.showAndWait().ifPresent(entry -> {
            String name = entry.getKey();
            if (name.isBlank()) return;

            Long projectId = createProject(name);
            if (projectId == null) return;

            for (Long userId : entry.getValue()) {
                addMember(projectId, userId);
            }

            loadProjects();
        });
    }

    private void loadAllUsers() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/user"))
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

    private Dialog<Map.Entry<String, List<Long>>> buildCreateDialog() {
        Dialog<Map.Entry<String, List<Long>>> dialog = new Dialog<>();
        dialog.setTitle("Новый проект");

        ButtonType createBtn = new ButtonType("Создать", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("Название проекта");

        TextField searchField = new TextField();
        searchField.setPromptText("Никнейм участника");
        Button addBtn = new Button("Добавить");
        Label searchStatus = new Label();

        List<Long> selectedIds = new ArrayList<>();
        ObservableList<String> selectedNames = FXCollections.observableArrayList();
        ListView<String> selectedList = new ListView<>(selectedNames);
        selectedList.setPrefHeight(100);

        addBtn.setOnAction(e -> {
            String query = searchField.getText().trim();
            if (query.isBlank()) return;
            int i = allUsernames.indexOf(query);
            if (i < 0) {
                searchStatus.setText("Пользователь «" + query + "» не найден");
                return;
            }
            Long uid = allUserIds.get(i);
            if (selectedIds.contains(uid)) {
                searchStatus.setText("Уже добавлен");
                return;
            }
            selectedIds.add(uid);
            selectedNames.add(query);
            searchField.clear();
            searchStatus.setText("");
        });

        VBox content = new VBox(8,
                new Label("Название:"), nameField,
                new Label("Добавить участников:"),
                new HBox(5, searchField, addBtn),
                searchStatus,
                selectedList
        );
        content.setPrefWidth(300);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn != createBtn) return null;
            return Map.entry(nameField.getText().trim(), new ArrayList<>(selectedIds));
        });

        return dialog;
    }

    private Long createProject(String name) {
        try {
            String json = "{\"name\": \"" + name + "\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/project"))
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
                    .uri(URI.create("http://localhost:8080/project/" + projectId + "/member/" + userId))
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

        // Тимлид
        String teamLeadName = "не назначен";
        if (details.teamLeadId() != null) {
            int tlIdx = details.memberIds().indexOf(details.teamLeadId());
            if (tlIdx >= 0) teamLeadName = details.memberNames().get(tlIdx);
        }

        // ─── Вкладка Информация ───────────────────────────────────────────────
        VBox infoTab = new VBox(0);
        infoTab.setStyle("-fx-background-color: #F5F0EB;");

        // Тимлид
        infoTab.getChildren().add(makeInfoRow("Лидер", teamLeadName));
        infoTab.getChildren().add(new Separator());

        // Участники
        for (String name : details.memberNames()) {
            infoTab.getChildren().add(makeInfoRow("", name));
        }
        infoTab.getChildren().add(new Separator());

        // Описание (пока статична)
        Label descLabel = new Label("Описание");
        descLabel.setStyle("-fx-font-size: 14; -fx-padding: 8 12 4 12;");
        TextArea descArea = new TextArea();
        descArea.setPromptText("Описание проекта...");
        descArea.setPrefRowCount(3);
        descArea.setEditable(false);
        descArea.setStyle("-fx-background-color: #E0E0E0; -fx-background-radius: 8; -fx-padding: 6;");
        VBox.setMargin(descArea, new javafx.geometry.Insets(0, 12, 12, 12));
        infoTab.getChildren().addAll(descLabel, descArea);

        // ─── Вкладка Доска ────────────────────────────────────────────────────
        VBox boardTab = buildBoardTab(projectId, details);

        // ─── Вкладка Чат (заглушка) ───────────────────────────────────────────
        VBox chatTab = new VBox();
        chatTab.setStyle("-fx-background-color: #F5F0EB; -fx-alignment: CENTER;");
        chatTab.getChildren().add(new Label("Чат будет реализован позже"));

        // ─── TabPane ──────────────────────────────────────────────────────────
        Tab tabInfo = new Tab("Информация", new ScrollPane(infoTab));
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

    private HBox makeInfoRow(String role, String name) {
        HBox row = new HBox(12);
        row.setStyle("-fx-padding: 10 16;");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Иконка-аватар
        Label avatar = new Label("👤");
        avatar.setStyle("-fx-background-color: #FAA757; -fx-background-radius: 50;"
                + " -fx-padding: 6 8; -fx-font-size: 14;");

        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-size: 14;");

        row.getChildren().addAll(avatar, nameLbl);

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
        addTaskBtn.setStyle("-fx-background-color: #FAA757; -fx-background-radius: 20;"
                + " -fx-font-size: 13; -fx-padding: 6 16;");

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
                        .uri(URI.create("http://localhost:8080/project/" + projectId + "/task"))
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
        String bg = switch (status) {
            case "READY" -> "#90EE90";
            case "DEVELOPMENT" -> "#FFD580";
            default -> "#E0E0E0";
        };
        String icon = switch (status) {
            case "READY" -> "✅";
            case "DEVELOPMENT" -> "🔄";
            default -> "⬜";
        };

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 16;");

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 14;");
        titleLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLbl, javafx.scene.layout.Priority.ALWAYS);

        Button delBtn = new Button("✕");
        delBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888; -fx-font-size: 12;");
        delBtn.setOnAction(e -> {
            deleteTask(taskId);
            taskIds.remove(taskId);
            // Найти и удалить карточку
            taskList.getChildren().removeIf(node ->
                node instanceof HBox hb && hb.getUserData() != null && hb.getUserData().equals(taskId));
        });

        HBox card = new HBox(10, iconLbl, titleLbl, delBtn);
        card.setUserData(taskId);
        card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 10;"
                + " -fx-padding: 10 14; -fx-cursor: hand;");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void showAddTaskDialog(Long projectId,
                                   List<Long> memberIds, List<String> memberNames,
                                   ListView<String> tasksView) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Новая задача");
        ButtonType addBtn = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        TextField titleField = new TextField();
        titleField.setPromptText("Название задачи");

        TextArea descField = new TextArea();
        descField.setPromptText("Описание (необязательно)");
        descField.setPrefRowCount(3);

        // Исполнитель: первый пункт = никто
        ObservableList<String> assigneeOptions = FXCollections.observableArrayList();
        assigneeOptions.add("— не назначен —");
        assigneeOptions.addAll(memberNames);
        ComboBox<String> assigneeCombo = new ComboBox<>(assigneeOptions);
        assigneeCombo.getSelectionModel().selectFirst();

        TextField deadlineField = new TextField();
        deadlineField.setPromptText("2026-04-15T12:00:00  (необязательно)");

        Spinner<Integer> importanceSpinner = new Spinner<>(1, 5, 3);
        importanceSpinner.setEditable(true);
        importanceSpinner.setPrefWidth(70);

        VBox content = new VBox(8,
                new Label("Название:"), titleField,
                new Label("Описание:"), descField,
                new Label("Исполнитель:"), assigneeCombo,
                new Label("Дедлайн:"), deadlineField,
                new HBox(8, new Label("Важность (1–5):"), importanceSpinner)
        );
        content.setPrefWidth(350);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(btn -> btn);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != addBtn) return;
            String title = titleField.getText().trim();
            if (title.isBlank()) { showError("Название не может быть пустым"); return; }

            int aIdx = assigneeCombo.getSelectionModel().getSelectedIndex();
            Long assigneeId = aIdx > 0 ? memberIds.get(aIdx - 1) : null;

            String deadline = deadlineField.getText().trim().isEmpty() ? null : deadlineField.getText().trim();

            createProjectTask(projectId, title,
                    descField.getText().trim(),
                    assigneeId, deadline,
                    importanceSpinner.getValue());
        });
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
                    .uri(URI.create("http://localhost:8080/project/" + projectId + "/task"))
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
                    .uri(URI.create("http://localhost:8080/task/" + taskId))
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

    private record ProjectDetails(Long teamLeadId, List<Long> memberIds, List<String> memberNames) {}

    private ProjectDetails loadProjectDetails(Long projectId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/project/" + projectId))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET()
                    .build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

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
            return new ProjectDetails(teamLeadId, memberIds, memberNames);
        } catch (Exception e) {
            showError("Ошибка загрузки данных проекта: " + e.getMessage());
            return new ProjectDetails(null, new ArrayList<>(), new ArrayList<>());
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
                    .uri(URI.create("http://localhost:8080/project/" + projectId + "/member/" + userId))
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
                    .uri(URI.create("http://localhost:8080/project/" + projectId + "/teamlead/" + userId))
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
                    .uri(URI.create("http://localhost:8080/project/" + projectId + "/teamlead"))
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
                loadProjects();
            }
        });
    }

    private void deleteProject(Long projectId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/project/" + projectId))
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
    @FXML protected void onNavSettings() { /* TODO */ }

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
}
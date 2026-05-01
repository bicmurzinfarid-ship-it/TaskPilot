package com.example.desktopwindows;

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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CalendarController {

    @FXML private Label monthLabel;
    @FXML private GridPane calendarGrid;

    private YearMonth currentMonth = YearMonth.now();
    private final HttpClient client = HttpClient.newHttpClient();

    // deadline -> список названий задач
    private final Map<LocalDate, List<String[]>> tasksByDate = new HashMap<>();
    // String[0]=title, String[1]=status

    private static final String[] DAY_NAMES = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    @FXML
    public void initialize() {
        loadAllTasks();
        buildCalendar();
    }

    // ─── Загрузка задач ───────────────────────────────────────────────────────

    private void loadAllTasks() {
        tasksByDate.clear();
        try {
            // Получаем все проекты пользователя
            HttpRequest projReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/project"))
                    .header("Authorization", "Bearer " + Session.getToken())
                    .GET().build();
            String projBody = client.send(projReq, HttpResponse.BodyHandlers.ofString()).body();

            List<Long> projectIds = new ArrayList<>();
            Pattern blockPat = Pattern.compile("\\{(.*?)\"members\"", Pattern.DOTALL);
            Matcher blockM = blockPat.matcher(projBody);
            while (blockM.find()) {
                String block = blockM.group(1);
                if (!block.contains("\"creatorId\"")) continue;
                Matcher idM = Pattern.compile("\"id\":(\\d+)").matcher(block);
                if (idM.find()) projectIds.add(Long.parseLong(idM.group(1)));
            }

            // Загружаем задачи из каждого проекта
            for (Long pid : projectIds) {
                HttpRequest taskReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/project/" + pid + "/task"))
                        .header("Authorization", "Bearer " + Session.getToken())
                        .GET().build();
                String taskBody = client.send(taskReq, HttpResponse.BodyHandlers.ofString()).body();
                parseTasks(taskBody);
            }
        } catch (Exception ignored) {}
    }

    private void parseTasks(String body) {
        Pattern blockPat = Pattern.compile("\\{[^{}]*\"title\"[^{}]*\\}", Pattern.DOTALL);
        Matcher blockM = blockPat.matcher(body);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        while (blockM.find()) {
            String block = blockM.group();
            Matcher titleM = Pattern.compile("\"title\":\"([^\"]+)\"").matcher(block);
            Matcher statusM = Pattern.compile("\"status\":\"([^\"]+)\"").matcher(block);
            Matcher deadlineM = Pattern.compile("\"deadline\":\"([^\"]+)\"").matcher(block);

            if (titleM.find() && deadlineM.find()) {
                String title = titleM.group(1);
                String status = statusM.find() ? statusM.group(1) : "WAITING";
                try {
                    LocalDate date = LocalDateTime.parse(deadlineM.group(1), fmt).toLocalDate();
                    tasksByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(new String[]{title, status});
                } catch (Exception ignored) {}
            }
        }
    }

    // ─── Построение календаря ─────────────────────────────────────────────────

    private void buildCalendar() {
        calendarGrid.getChildren().clear();
        calendarGrid.getColumnConstraints().clear();
        calendarGrid.getRowConstraints().clear();

        // 7 равных колонок
        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS);
            cc.setMinWidth(80);
            calendarGrid.getColumnConstraints().add(cc);
        }

        String monthName = currentMonth.getMonth()
                .getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru"));
        monthLabel.setText(monthName.substring(0, 1).toUpperCase() + monthName.substring(1)
                + " " + currentMonth.getYear());

        // Заголовки дней
        for (int i = 0; i < 7; i++) {
            Label lbl = new Label(DAY_NAMES[i]);
            lbl.setMaxWidth(Double.MAX_VALUE);
            lbl.setAlignment(Pos.CENTER);
            lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13;"
                    + (i >= 5 ? " -fx-text-fill: #CC0000;" : ""));
            calendarGrid.add(lbl, i, 0);
        }

        LocalDate first = currentMonth.atDay(1);
        // getdayOfWeek().getValue(): 1=пн, 7=вс
        int startCol = first.getDayOfWeek().getValue() - 1;
        int daysInMonth = currentMonth.lengthOfMonth();
        LocalDate today = LocalDate.now();

        int col = startCol;
        int row = 1;
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = currentMonth.atDay(day);
            List<String[]> tasks = tasksByDate.getOrDefault(date, Collections.emptyList());

            VBox cell = buildDayCell(day, date, tasks, today, col);
            calendarGrid.add(cell, col, row);

            col++;
            if (col == 7) {
                col = 0;
                row++;
            }
        }

        // Высота строк
        for (int r = 0; r <= row; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setMinHeight(r == 0 ? 30 : 80);
            rc.setVgrow(r == 0 ? Priority.NEVER : Priority.ALWAYS);
            calendarGrid.getRowConstraints().add(rc);
        }
    }

    private VBox buildDayCell(int day, LocalDate date, List<String[]> tasks,
                               LocalDate today, int col) {
        boolean isToday = date.equals(today);
        boolean isWeekend = col >= 5;
        boolean hasTasks = !tasks.isEmpty();

        VBox cell = new VBox(2);
        cell.setPadding(new Insets(4));
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setStyle("-fx-border-color: #CCCCCC; -fx-border-width: 0.5;"
                + "-fx-background-color: " + (isToday ? "#E8F0FF" : "white") + ";");

        // Номер дня
        Label dayLbl = new Label(String.valueOf(day));
        dayLbl.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + (isToday ? " -fx-text-fill: #2255CC;" : "")
                + (isWeekend && !isToday ? " -fx-text-fill: #CC0000;" : ""));
        if (isToday) {
            dayLbl.setText("Сегодня");
            dayLbl.setStyle(dayLbl.getStyle() + " -fx-font-size: 11;");
        }
        cell.getChildren().add(dayLbl);

        // Задачи (максимум 2 штуки видно)
        int shown = 0;
        for (String[] task : tasks) {
            if (shown >= 2) {
                Label more = new Label("+" + (tasks.size() - 2) + " ещё");
                more.setStyle("-fx-font-size: 9; -fx-text-fill: #888;");
                cell.getChildren().add(more);
                break;
            }
            String status = task[1];
            String bg = switch (status) {
                case "READY" -> "#90EE90";
                case "DEVELOPMENT" -> "#FFD580";
                default -> {
                    // Просроченная?
                    yield date.isBefore(today) ? "#FFB3B3" : "#E0E0E0";
                }
            };
            Label taskLbl = new Label(task[0]);
            taskLbl.setMaxWidth(Double.MAX_VALUE);
            taskLbl.setWrapText(false);
            taskLbl.setEllipsisString("…");
            taskLbl.setStyle("-fx-font-size: 9; -fx-background-color: " + bg
                    + "; -fx-background-radius: 3; -fx-padding: 1 3;");
            cell.getChildren().add(taskLbl);
            shown++;
        }

        // Клик — показать все задачи дня
        if (hasTasks) {
            cell.setOnMouseClicked(e -> showDayDialog(date, tasks));
            cell.setStyle(cell.getStyle() + " -fx-cursor: hand;");
        }

        return cell;
    }

    private void showDayDialog(LocalDate date, List<String[]> tasks) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setPrefWidth(320);
        box.getChildren().add(new Label("Задачи:"));

        for (String[] task : tasks) {
            String status = task[1];
            String color = switch (status) {
                case "READY" -> "#90EE90";
                case "DEVELOPMENT" -> "#FFD580";
                default -> date.isBefore(LocalDate.now()) ? "#FFB3B3" : "#E0E0E0";
            };
            Label lbl = new Label(task[0]);
            lbl.setMaxWidth(Double.MAX_VALUE);
            lbl.setWrapText(true);
            lbl.setStyle("-fx-background-color: " + color
                    + "; -fx-background-radius: 5; -fx-padding: 6 10; -fx-font-size: 13;");
            box.getChildren().add(lbl);
        }

        dialog.getDialogPane().setContent(new ScrollPane(box));
        dialog.showAndWait();
    }

    // ─── Навигация ────────────────────────────────────────────────────────────

    @FXML
    protected void onPrevMonth() {
        currentMonth = currentMonth.minusMonths(1);
        buildCalendar();
    }

    @FXML
    protected void onNextMonth() {
        currentMonth = currentMonth.plusMonths(1);
        buildCalendar();
    }

    @FXML
    protected void onNavHome() { navigate("main-view.fxml"); }

    @FXML
    protected void onNavCalendar() { /* уже здесь */ }

    private void navigate(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) monthLabel.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), stage.getWidth(), stage.getHeight()));
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }
}

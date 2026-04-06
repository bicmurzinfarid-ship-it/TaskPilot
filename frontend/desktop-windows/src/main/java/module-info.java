module com.example.desktopwindows {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires java.net.http;
    requires javafx.graphics;

    opens com.example.desktopwindows to javafx.fxml;
    exports com.example.desktopwindows;
}
module gitnarwhal.main {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign;

    requires kotlin.stdlib;

    exports com.gitnarwhal;
    exports com.gitnarwhal.components;
    exports com.gitnarwhal.views;
}
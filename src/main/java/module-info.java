module HashsumBuilder {
    requires java.logging;

    requires javafx.controls;
    requires javafx.graphics;

    opens bayern.steinbrecher.hashsumbuilder to javafx.graphics;
}

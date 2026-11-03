
package game;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class Main extends Application {

    @Override
    public void start(Stage stage) {

        // ชื่อเกม
        Label title = new Label("THE DEBT HOUSE");
        title.setFont(Font.font("Serif", 42));
        title.setTextFill(Color.web("#e8e3df"));

  
        // ข้อความบรรยากาศ
        Label tagline = new Label(

        );
        tagline.setFont(Font.font("Serif", 14));
        tagline.setTextFill(Color.web("#aaa29d"));

        // ปุ่มเมนู
        Button startButton = createButton("Start Game");
        Button settingsButton = createButton("Settings");
        Button creditsButton = createButton("Credits");
        Button exitButton = createButton("Exit");

        // คลิก Start Game
      startButton.setOnAction(e -> {
    GameScene gameScene = new GameScene(stage);
    gameScene.show();
});

        // คลิก Settings
        settingsButton.setOnAction(e -> {
            showMessage(stage, "Settings",
                "ระบบตั้งค่าจะเพิ่มในขั้นตอนถัดไป");
        });

        // คลิก Credits
        creditsButton.setOnAction(e -> {
            showMessage(stage, "Credits",
                "THE Debt House \nA Java Horror Game");
        });

        // คลิก Exit
        exitButton.setOnAction(e -> stage.close());

        VBox menu = new VBox(
            12,
            title,
            startButton,
            settingsButton,
            creditsButton,
            exitButton
        );

        menu.setAlignment(Pos.CENTER_LEFT);
        menu.setPadding(new Insets(35, 40, 35, 65));
        menu.setMaxWidth(570);

        VBox.setMargin(startButton,
            new Insets(38, 0, 0, 0));

        VBox.setMargin(tagline,
            new Insets(40, 0, 0, 0));

        menu.getChildren().add(tagline);

        // พื้นหลังหลัก
        
        // โหลดภาพพื้นหลังจาก resources
        var imageStream = getClass().getResourceAsStream(
            "/assets/images/lobby.png"
        );

        if (imageStream == null) {
            throw new IllegalStateException(
                "ไม่พบภาพ lobby.png กรุณาตรวจสอบตำแหน่งไฟล์"
            );
        }

        Image backgroundImage = new Image(imageStream);

        ImageView background = new ImageView(backgroundImage);
        background.setFitWidth(1100);
        background.setFitHeight(650);
        background.setPreserveRatio(false);
        background.setSmooth(true);

        // แผ่นเงามืดช่วยให้อ่านข้อความได้ชัด
        Region darkOverlay = new Region();
        darkOverlay.setStyle(
            "-fx-background-color: " +
            "linear-gradient(to right, " +
            "rgba(0,0,0,0.94) 0%, " +
            "rgba(0,0,0,0.72) 38%, " +
            "rgba(0,0,0,0.12) 75%, " +
            "rgba(0,0,0,0.20) 100%);"
        );

        // วางภาพ เงา และเมนูซ้อนกัน
        StackPane root = new StackPane(
            background,
            darkOverlay,
            menu
        );

        root.setAlignment(Pos.CENTER_LEFT);

        Scene scene = new Scene(root, 1100, 650);

        
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

    }

    private Button createButton(String text) {
        Button button = new Button(text);

        button.setFont(Font.font("Serif", 20));
        button.setTextFill(Color.web("#e8e3df"));
        button.setAlignment(Pos.CENTER_LEFT);
        button.setPrefWidth(300);
        button.setPrefHeight(48);

        button.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-background-radius: 0;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 8 18 8 18;"
        );

        button.setOnMouseEntered(e ->
            button.setStyle(
                "-fx-background-color: #651919;" +
                "-fx-background-radius: 0;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 8 18 8 18;"
            )
        );

        button.setOnMouseExited(e ->
            button.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-background-radius: 0;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 8 18 8 18;"
            )
        );

        return button;
    }

    private void showMessage(
        Stage stage, String title, String message
    ) {
        javafx.scene.control.Alert alert =
            new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION
            );

        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

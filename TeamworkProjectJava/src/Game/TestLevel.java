package Game;

import World.Dungeon;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import World.Generator;
import Renderer.QuickView;
import World.GenerateDungeon;

import java.util.ArrayList;

public class TestLevel  extends Application {
    // TODO: place level gen and display code here; display is sensitive to grid size (recommended 4-8)
    static ArrayList<Dungeon> dungeonList;

    public static void main(String[] args) {
        // Add functionality to display generated map, like a foreach. Use the output of the following method:
        Generator.Generate();
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Setup scene and nodes
        Group root = new Group();
        Scene scene = new Scene(root, 800, 600, Color.BLACK);
        Canvas canvas = new Canvas(800, 600);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.BLACK);
        root.getChildren().add(canvas);

        // Draw grid
        QuickView.drawGrid(gc);

        // First pass
        dungeonList = GenerateDungeon.sampleMake();
        // Register event handler for key presses
        scene.setOnKeyPressed(new EventHandler<KeyEvent>() {
            public void handle(KeyEvent ke) {
                // Advance generation
                gc.setFill(Color.BLACK);
                gc.fillRect(0, 0, 800, 600);
                QuickView.drawGrid(gc);
                if (ke.getCode() == KeyCode.ENTER) {
                    // Finalize generation, make rooms within boundaries
                    dungeonList.get(0).generateDungeon();
                    QuickView.renderDungeon(gc, GenerateDungeon.filterTree(dungeonList));
                } else {
                    // Iterate dungeon
                    GenerateDungeon.sampleStep(dungeonList);
                    QuickView.renderDungeon(gc, dungeonList);
                }
            }
        });

        // Render stage
        primaryStage.setResizable(false);
        primaryStage.setTitle("Test Window");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
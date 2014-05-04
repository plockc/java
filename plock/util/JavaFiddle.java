package plock.util;

import java.io.*;
import java.util.*;
import java.util.stream.*;
import java.util.function.Supplier;
import javafx.application.Application;
import javafx.event.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.*;
import javafx.stage.*;
import javafx.geometry.*;

import plock.math.Finance;

// TODO: cache the last text so we don't recompile on key up for example
public class JavaFiddle extends Application {
    private String lastSource;
	@Override
	public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        SplitPane topAndBottom = new SplitPane();
        SplitPane sourceAndOutput = new SplitPane();
        final TextArea imports = new TextArea();
        final TextArea sourceCode = new TextArea();
        final TextArea console = new TextArea();
        
        EventHandler<KeyEvent> handler = new EventHandler<KeyEvent>() {
            public void handle(KeyEvent event) {
                if(sourceCode.getText().equals(lastSource)) {return;}
                lastSource = sourceCode.getText();
                ByteArrayOutputStream consoleBytes = new ByteArrayOutputStream();
                PrintStream oldOut = System.out;
                PrintStream consolePrinter = null;
                try {consolePrinter = new PrintStream(consoleBytes, true, "UTF-8");} catch (Exception e) {}
                try {
                    System.setOut(consolePrinter);
                    StringBuilder code = new StringBuilder();
                    Stream.of("application", "event", "scene.control", "scene.input", "scene.layout", "scene",
                            "geometry").forEach(p->code.append("import javafx."+p+".*;\n"));
                    Stream.of("plock.math", "java.io", "java.util", "java.util.stream", "javafx.beans.value",
                            "java.util.concurrent.atomic", "javafx.beans.property", "javafx.util.converter",
                            "java.text", "java.util.function").forEach(p->code.append("import "+p+".*;\n"));
                    code.append("public class DynamicSupplier implements java.util.function.Supplier<Node> {\n");
                    code.append("  public Node get() {\n");
                    code.append(sourceCode.getText());
                    code.append("  }\n}\n");
                    Supplier<Node> nodeCreator;
                    nodeCreator = CompileSourceInMemory.createInstance(Supplier.class, code.toString());
                    // TODO: template needs to set imports before parsing, maybe split out construction and parsing
                    if (nodeCreator != null) {
                        sourceAndOutput.getItems().setAll(nodeCreator.get(), sourceCode);
                    }
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    e.printStackTrace(consolePrinter);
                } finally {
                    System.setOut(oldOut);
                    try {console.setText(consoleBytes.toString("UTF-8"));}
                    catch (Exception e) {console.setText("missing UTF-8");}
                }
            }
        }; 
        sourceCode.setText("Node node = \nreturn node;");
        sourceCode.setOnKeyTyped(handler);
        sourceCode.setOnKeyPressed(handler);
        sourceCode.setOnKeyReleased(handler);
        
        topAndBottom.setOrientation(javafx.geometry.Orientation.VERTICAL);
        sourceAndOutput.getItems().setAll(sourceCode);
        topAndBottom.getItems().addAll(sourceAndOutput, console);
        
        root.setCenter(topAndBottom);

        primaryStage.setTitle("Node Creator");
        primaryStage.setScene(new Scene(root, 1280, 500));
        primaryStage.show();
	}

	public static void main(String[] args) {
		launch(args);
	}

}


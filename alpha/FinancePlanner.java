package alpha;

import java.io.*;
import javafx.application.Application;
import javafx.event.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.*;
import javafx.stage.*;

import plock.util.*;

// TODO: use Template for generating reports
// TODO: pre-process variable assignments like 'foo=<expr>', converting into Java puts into a map, then run and get result
//         which used for bindings for the template
// TODO: require defaults
// TODO: create labels of a set of expressions for a variable, and which is to be computed, an ordered set of these defines a scenario
public class FinancePlanner extends Application {
	@Override
	public void start(Stage primaryStage) {
        final TextArea sourceCode = new TextArea();
        final TextArea output = new TextArea();
        output.setEditable(false);
        final TextArea console = new TextArea();
        output.setEditable(false);
        
        EventHandler<KeyEvent> handler = new EventHandler<KeyEvent> () {
        	public void handle(KeyEvent event) {
        		String text = event.getCharacter();
        		// i wonder if this fails on unix / windows?
        		output.appendText(text.equals("\r") ? "\n" : text);
        	}
        };
        handler = new EventHandler<KeyEvent>() {
            public void handle(KeyEvent event) {
                output.setText(CompileSourceInMemory.createSimpleImplCode(Runnable.class, sourceCode.getText()));
                try (ByteArrayOutputStream consoleBytes = new ByteArrayOutputStream();
                     PrintStream consolePrinter = new PrintStream(consoleBytes, true, "UTF-8")) {
                    System.setOut(consolePrinter);
                    try {CompileSourceInMemory.runJavaFragment(sourceCode.getText());}
                    catch (IllegalArgumentException ignored) {System.out.println("Failed compilation");}
                    console.setText(consoleBytes.toString("UTF-8"));
                } catch (Exception e) {
                    StringWriter trace = new StringWriter();
                    e.printStackTrace(new PrintWriter(trace));
                    console.setText(trace.toString());
                }
            }
        }; 
        sourceCode.setOnKeyTyped(handler);
        sourceCode.setOnKeyPressed(handler);
        sourceCode.setOnKeyReleased(handler);
        
        SplitPane topAndBottom = new SplitPane();
        topAndBottom.setOrientation(javafx.geometry.Orientation.VERTICAL);
        SplitPane sourceAndOutput = new SplitPane();
        sourceAndOutput.getItems().addAll(sourceCode, output);
        topAndBottom.getItems().addAll(sourceAndOutput, console);
        
        BorderPane root = new BorderPane();
        root.setCenter(topAndBottom);

        primaryStage.setTitle("Hello World!");
        primaryStage.setScene(new Scene(root, 1000, 500));
        primaryStage.show();
	}

	public static void main(String[] args) {
		launch(args);
	}

}


package alpha;

import java.io.*;
import java.util.*;
import java.util.stream.*;
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
                ByteArrayOutputStream consoleBytes = new ByteArrayOutputStream();
                PrintStream oldOut = System.out;
                PrintStream consolePrinter = null;
                try {consolePrinter = new PrintStream(consoleBytes, true, "UTF-8");} catch (Exception e) {}
                try {
                    System.setOut(consolePrinter);
                    output.setText("");
                    Template tpl = null;
                    tpl = new Template().addImports(Arrays.asList("plock.math.Finance")).setSource(sourceCode.getText().toCharArray());
                    // TODO: template needs to set imports before parsing, maybe split out construction and parsing
                    output.setText(tpl.getJava());
                    System.out.println(tpl.render(Collections.emptyMap()));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    e.printStackTrace(consolePrinter);
                } finally {
                    System.setOut(oldOut);
                    try {console.setText(consoleBytes.toString("UTF-8"));} catch (Exception e) {console.setText("missing UTF-8");}
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


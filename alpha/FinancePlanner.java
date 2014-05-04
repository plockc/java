package alpha;

import java.io.*;
import java.util.*;
import java.util.stream.*;
import java.text.*;
import javafx.application.Application;
import javafx.event.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.*;
import javafx.stage.*;
import javafx.geometry.*;

import plock.util.*;
import plock.math.Finance;

// TODO: cache the last text so we don't recompile on key up for example
public class FinancePlanner extends Application {
    private HashMap<String,Finance> accounts = new HashMap<String,Finance>();

    private Map<String,TextField> paramToField = new HashMap<String,TextField>();
    private ToggleGroup solveToggleGroup = new ToggleGroup();

    private static NumberFormat rateFormat = NumberFormat.getInstance();  
    private static NumberFormat moneyFormat = NumberFormat.getIntegerInstance();
    static {
        moneyFormat.setGroupingUsed(false);
        rateFormat.setMaximumFractionDigits(4);
    }

    private static class Account {
        Finance finance = new Finance();
        Account pvRef, fvRef;
        Date start, end;
        Account startRef, endRef;
    }

    private Account acct = new Account();
    private Finance finance = acct.finance;

    private Runnable recompute = () -> {
        System.out.println(finance);
        Finance f = finance;
        //System.out.println("fv: "+f.solve("fv")+" new fv: "+f.getDouble("fv"));
        RadioButton selectedButton = (RadioButton)solveToggleGroup.getSelectedToggle();
        String toggle = selectedButton.getText();
        String solveFor = (String)selectedButton.getProperties().get("paramName");
        Double updatedVal = f.solve(solveFor);
        System.out.println("solved: "+updatedVal);
        f.set(solveFor, updatedVal);
        TextField field = paramToField.get(solveFor);
        field.setText(((java.text.NumberFormat)field.getProperties().get("format")).format(updatedVal));
    };
    private EventHandler<ActionEvent> formHandler = e -> recompute.run();

    private TextField createDoubleField(String paramName, boolean disabled, NumberFormat format) {
        String initialValue = format.format(finance.getDouble(paramName));
        TextField f = TextFieldBuilder.create().text(initialValue).disable(disabled)
            .onAction(formHandler).alignment(Pos.CENTER_RIGHT).build();
        f.getProperties().put("format", format);
        f.getProperties().put("paramName", paramName);
        paramToField.put(paramName, f);
        f.addEventFilter(KeyEvent.KEY_TYPED, e-> {
            // tab and shift tab focus into this textfield causes key typed events
            //   but only tab gets tab character, save the selection
            int caret = f.getCaretPosition(); // this is the before the character is added
            int anchor = f.getAnchor();
            // we have to parse the new value with the new key added, the rules for numbers are not too bad
            if (!"01234567890.-".contains(e.getCharacter())
                || e.getCharacter().equals(".") && (f.getText().contains(".")
                || f.getText().contains("-") && caret==0)
                || e.getCharacter().equals("-") && (f.getText().startsWith("-") || caret != 0)
                || caret==0 && !"-.".contains(e.getCharacter()) && f.getText().startsWith("-")) {
                e.consume(); // forget that keypress ever existed
                f.setText(f.getText());
                f.positionCaret(caret); // restore the carent position after the setText
                f.selectRange(anchor, caret); // restore the selection after the setText
            }
        });
        f.focusedProperty().addListener( (obsVal, oldVal, newVal) -> {
            if (!newVal) {
            System.out.println("exited field "+paramName);
            if (f.getText().length()==0 || newVal.equals(".")) return;
            finance.set(paramName, Double.parseDouble(f.getText()));
            }
        });
        f.textProperty().addListener( (obsVal,oldVal,newVal) ->{
            System.out.println("change "+paramName);
            if (newVal.length()==0 || newVal.equals(".")) return;
            if (!format.format(finance.getDouble(paramName)).equals(newVal)) {
            try {
                finance.set(paramName, Double.parseDouble(newVal));
                recompute.run();
            } catch (NumberFormatException e) {f.setText(oldVal);}
            }
        });
        return f;
    }


	@Override
	public void start(Stage primaryStage) {
        final TextArea sourceCode = new TextArea();
        final TextArea output = new TextArea();
        output.setEditable(false);
        final TextArea console = new TextArea();
        output.setEditable(false);
        final GridPane form = new GridPane();
        
        EventHandler<KeyEvent> handler = event -> {
            ByteArrayOutputStream consoleBytes = new ByteArrayOutputStream();
            PrintStream oldOut = System.out;
            PrintStream consolePrinter = null;
            try {consolePrinter = new PrintStream(consoleBytes, true, "UTF-8");} catch (Exception e) {}
            try {
                System.setOut(consolePrinter);
                output.setText("");
                Template tpl = null;
                tpl = new Template().addImports(Arrays.asList("plock.math.Finance"))
                    .setSource(sourceCode.getText().toCharArray());
                // TODO: template needs to set imports before parsing, maybe split out construction and parsing
                output.setText(tpl.getJava());
                System.out.println(tpl.render(finance.getValues()));
            } catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace(consolePrinter);
            } finally {
                System.setOut(oldOut);
                try {console.setText(consoleBytes.toString("UTF-8"));}
                catch (Exception e) {console.setText("missing UTF-8");}
            }
        }; 


        sourceCode.setOnKeyTyped(handler);
        sourceCode.setOnKeyPressed(handler);
        sourceCode.setOnKeyReleased(handler);
        
        form.setAlignment(Pos.CENTER);
        form.setHgap(10); form.setVgap(12);
        form.setPadding(new Insets(10,10,10,10));

        RadioButtonBuilder radioButtonBuilder = RadioButtonBuilder.create().onAction(formHandler)
            .toggleGroup(solveToggleGroup).selected(false);

        java.util.function.BiFunction<String,LabeledBuilder,RadioButton> addParamName = (p,b) -> {
            RadioButton button = ((RadioButtonBuilder)b).build();
            button.getProperties().put("paramName", p); return button;
        };

        int i=0;
        form.add(addParamName.apply("fv", radioButtonBuilder.selected(true).text("Future Value")), 0, i);
        form.add(createDoubleField("fv", true, moneyFormat), 1, i++);
        form.add(addParamName.apply("pv", radioButtonBuilder.selected(false).text("Present Value")), 0, i);
        form.add(createDoubleField("pv", false, moneyFormat), 1, i++);
        form.add(addParamName.apply("r", radioButtonBuilder.selected(false).text("Rate")), 0, i);
        form.add(createDoubleField("r", false, rateFormat), 1, i++);
        form.add(addParamName.apply("n", radioButtonBuilder.selected(false).text("Number of Periods")), 0, i);
        form.add(createDoubleField("n", false, rateFormat), 1, i++);
        form.add(addParamName.apply("pmt", radioButtonBuilder.selected(false).text("Payment")), 0, i);
        form.add(createDoubleField("pmt", false, moneyFormat), 1, i++);
        form.add(addParamName.apply("g", radioButtonBuilder.selected(false).text("Growth")), 0, i);
        form.add(createDoubleField("g", false, rateFormat), 1, i++);

        java.util.concurrent.atomic.AtomicInteger row = new java.util.concurrent.atomic.AtomicInteger(6);

        Stream.of("Rate Defined As", "Payment Applied", "Payment Growth Applied").forEach(label -> {
            ToggleGroup periodToggleGroup = new ToggleGroup();
            form.add(LabelBuilder.create().text(label).build(), 0, row.get());
            RadioButtonBuilder buttonBuilder = RadioButtonBuilder.create().onAction(formHandler).toggleGroup(periodToggleGroup).text("Monthly");
            RadioButton button = buttonBuilder.build();
            form.add(button, 1, row.get());
            button = ((RadioButtonBuilder)buttonBuilder.selected(true).text("Yearly")).build();
            form.add(button, 2, row.get());
            row.incrementAndGet();
        });
        form.add(CheckBoxBuilder.create().onAction(formHandler).text("Apply Rate Continuously").build(),0,8,2,8);
        form.add(CheckBoxBuilder.create().onAction(formHandler).text("First Payment Immediately").build(),0,9,2,9);

        SplitPane topAndBottom = new SplitPane();
        topAndBottom.setOrientation(javafx.geometry.Orientation.VERTICAL);
        SplitPane sourceAndOutput = new SplitPane();
        sourceAndOutput.setDividerPositions(.33,.66);
        sourceAndOutput.getItems().addAll(sourceCode, form, output);
        topAndBottom.getItems().addAll(sourceAndOutput, console);
        
        BorderPane root = new BorderPane();
        root.setCenter(topAndBottom);

        primaryStage.setTitle("Hello World!");
        primaryStage.setScene(new Scene(root, 1280, 500));
        primaryStage.show();
	}

	public static void main(String[] args) {
		launch(args);
	}

}


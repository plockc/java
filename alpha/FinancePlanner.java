package alpha;

import java.io.*;
import java.util.*;
import java.util.stream.*;
import java.util.function.*;
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
import plock.math.Finance.TmvParams;

// TODO: cache the last text so we don't recompile on key up for example
// TODO: on change in the account, reprocess the template
public class FinancePlanner extends Application {
    private HashMap<String,Finance> accounts = new HashMap<String,Finance>();

    private Map<String,TextField> paramToField = new HashMap<String,TextField>();
    private ToggleGroup solveToggleGroup = new ToggleGroup();

    private static NumberFormat format = NumberFormat.getIntegerInstance();
    static {
        format.setGroupingUsed(false);
        format.setMaximumFractionDigits(2);
    }

    private static class Account {
        Finance finance = new Finance().comp(12);
        Account pvRef, fvRef;
        Date start, end;
        Account startRef, endRef;
    }

    private Account acct = new Account();
    private Finance finance = acct.finance;

    private Consumer<String> recompute = param -> {
        System.out.println(finance);
        Finance f = finance.copy();
        RadioButton selectedButton = (RadioButton)solveToggleGroup.getSelectedToggle();
        String toggle = selectedButton.getText();
        String solveFor = (String)selectedButton.getProperties().get("paramName");
        Double updatedVal = f.solve(solveFor);
        f.set(solveFor, updatedVal);
        TextField field = paramToField.get(solveFor);
        if (solveFor.equals("r") || solveFor.equals("g")) {
            field.setText(format.format(updatedVal*100.0));
        } else {
            field.setText(format.format(updatedVal));
        }
        switch(param) {
            case "pmt": Optional.of(paramToField.get("comp_pmt")).ifPresent(comp->
                    comp.setText(format.format(f.getDouble("comp_pmt"))));
                break;
            case "comp_pmt": Optional.of(paramToField.get("pmt")).ifPresent(comp->
                    comp.setText(format.format(f.getDouble("pmt"))));
                break;
        }                        
    };
    private EventHandler<ActionEvent> formHandler = e ->  {
        recompute.accept((String)((Node)e.getSource()).getProperties().get("paramName"));
    };

    private TextField createDoubleField(String paramName, boolean disabled, NumberFormat format) {
        String initialValue = format.format(finance.getDouble(paramName));
        TextField f = plock.fx.Controls.createDoubleField();
        f.setText(initialValue);
        f.setDisable(disabled);
        f.setOnAction(formHandler);
        f.getProperties().put("paramName", paramName);
        paramToField.put(paramName, f);
        f.textProperty().addListener( (obsVal,oldVal,newVal) ->{
            System.out.println("change "+paramName+" from "+oldVal+" to "+newVal);
            if (newVal.length()==0 || newVal.equals(".") || newVal.equals("-")) {
                newVal="0.00";
            }
            try {
                switch (paramName) {
                    case "r": case "g": finance.set(paramName, Double.parseDouble(newVal)/100.0); break;
                    default: finance.set(paramName, Double.parseDouble(newVal)); break;
                }
                recompute.accept(paramName);
            } catch (NumberFormatException e) {System.out.println("doh on "+newVal+": "+e); f.setText(oldVal);}
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

        RadioButtonBuilder radioButtonBuilder = RadioButtonBuilder.create().onAction(button -> {
            String solveFor = (String)((Node)button.getSource()).getProperties().get("paramName");
            paramToField.forEach((String k, TextField v)->{
                if (v.isDisabled() && !solveFor.equals(k)) {v.setDisable(false);}
                if (!v.isDisabled() && solveFor.equals(k)) {;v.setDisable(true);}
                if (k.equals("comp_pmt")) {v.setDisable(solveFor.equals("pmt"));}
            });
        }).toggleGroup(solveToggleGroup).selected(false);

        java.util.function.BiFunction<String,LabeledBuilder,RadioButton> addParamName = (p,b) -> {
            RadioButton button = ((RadioButtonBuilder)b).build();
            button.getProperties().put("paramName", p); return button;
        };

        int i=0;
        form.add(addParamName.apply("fv", radioButtonBuilder.selected(true).text("Future Value")), 0, i);
        form.add(createDoubleField("fv", true, format), 2, i++);
        form.add(addParamName.apply("pv", radioButtonBuilder.selected(false).text("Present Value")), 0, i);
        form.add(createDoubleField("pv", false, format), 2, i++);
        form.add(addParamName.apply("r", radioButtonBuilder.selected(false).text("Annual Effective Rate")), 0, i);
        form.add(createDoubleField("r", false, format), 2, i);
        form.add(new Label("%"), 3, i++);
        form.add(addParamName.apply("n", radioButtonBuilder.selected(false).text("Number of Years")), 0, i);
        form.add(createDoubleField("n", false, format), 2, i++);
        form.add(addParamName.apply("pmt", radioButtonBuilder.selected(false).text("Incoming")), 0, i, 1, 2);
        form.add(LabelBuilder.create().text("Monthly").minWidth(Label.USE_PREF_SIZE).build(), 1, i);
        form.add(createDoubleField("pmt", false, format), 2, i++);
        form.add(LabelBuilder.create().text("Yearly").build(), 1, i);
        form.add(createDoubleField("comp_pmt", false, format), 2, i++);
        form.add(addParamName.apply("g", radioButtonBuilder.selected(false).text("Annual Incoming Growth")), 0, i);
        form.add(createDoubleField("g", false, format), 2, i);
        form.add(new Label("%"), 3, i++);

        SplitPane topAndBottom = new SplitPane();
        topAndBottom.setOrientation(javafx.geometry.Orientation.VERTICAL);
        SplitPane sourceAndOutput = new SplitPane();
        sourceAndOutput.setDividerPositions(.33,.66);
        sourceAndOutput.getItems().addAll(sourceCode, form, output);
        topAndBottom.getItems().addAll(sourceAndOutput, console);
        
        BorderPane root = new BorderPane();
        root.setCenter(topAndBottom);

        primaryStage.setTitle("Hello World!");
        primaryStage.setScene(new Scene(root, 1280, 900));
        primaryStage.show();
	}

	public static void main(String[] args) {
		launch(args);
	}

}


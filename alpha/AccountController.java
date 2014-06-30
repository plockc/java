package alpha;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.*;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.*;
import javafx.fxml.*;
import plock.math.Finance;
import plock.math.Finance.TmvParams;
import plock.fx.Controls.DoubleTextField;

// TODO: bind fv/pv to other accounts
public class AccountController extends GridPane {
    @FXML protected Finance finance;
    @FXML private ChoiceBox<String> solveFor;
    @FXML private Map<String,String> paramToLabel;
    private Map<String,String> labelToParam = new HashMap<String,String>();
    private Set<AccountController> fvSubscribers = new CopyOnWriteArraySet<AccountController>();
    private Set<AccountController> pvSubscribers = new CopyOnWriteArraySet<AccountController>();
    private String name;
    private Map<String,TextField> paramToField = new HashMap<String,TextField>();

    private static NumberFormat format = NumberFormat.getIntegerInstance();
    static {
        format.setGroupingUsed(false);
        format.setMaximumFractionDigits(2);
    }

    public AccountController() {
        FXMLLoader fxmlLoader = new FXMLLoader();
        
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load(Files.newInputStream(Paths.get(getClass().getResource("/account.fxml").getPath())));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        finance.comp(12);
    }
    public String getName() {return name;}
    public AccountController setName(String name) {this.name=name; add(new Label(name), 0, 0); return this;}
    public Finance getFinance() {return finance;}
    public void initialize() {
    	System.out.println("initializing");
        paramToLabel.forEach( (k,v) -> { labelToParam.put(v, k); } );
        solveFor.setConverter(new StringConverter<String>() {
            public String toString(String paramName) { return paramToLabel.get(paramName); }
            public String fromString(String paramName) { return labelToParam.get(paramName); }
        });
        solveFor.getSelectionModel().selectedItemProperty().addListener( (obs, oldVal, newVal) -> {
        	System.out.println("Changing solveFor");
        	DoubleTextField oldField = (DoubleTextField)lookupAll(".text-field").stream()
        			.filter(field->oldVal.equals(field.getProperties().get("paramName"))).findFirst().get();
        	DoubleTextField newField = (DoubleTextField)lookupAll(".text-field").stream()
        			.filter(field->newVal.equals(field.getProperties().get("paramName"))).findFirst().get();
        	// new field is un-editable, old field becomes editable
        	oldField.setDisable(false);
            oldField.setStyle("");
            newField.setDisable(true);
            newField.setStyle("-fx-background-color: lightgrey; -fx-opacity: 1;");
            if (newVal.equals("comp_pmt")) {
            	TextField tf = ((TextField)lookupAll(".text-field").stream()
            		.filter(field->"pmt".equals(field.getProperties().get("paramName"))).findFirst().get());
                tf.setDisable(true);
                tf.setStyle("-fx-background-color: lightgrey; -fx-opacity: 1;");
            }
            if (newVal.equals("pmt")) {
            	TextField tf = ((TextField)lookupAll(".text-field").stream()
            		.filter(field->"comp_pmt".equals(field.getProperties().get("paramName"))).findFirst().get());
                tf.setDisable(true);
                tf.setStyle("-fx-background-color: lightgrey; -fx-opacity: 1;");
            }
            // unbind all the old stuff before changing solveFor and rebinding the new stuff
            oldField.valProperty().unbind();
            newField.valProperty().unbindBidirectional(finance.getProperty(TmvParams.valueOf(newVal)));
        	System.out.println("Should be unbound, new field val property is bound: "+newField.valProperty().isBound());
            finance.solveFor(newVal);
        	System.out.println("finance changed solveFor");
        	oldField.valProperty().bindBidirectional(finance.getProperty(TmvParams.valueOf(oldVal)));
        	System.out.println("bound old");
            newField.valProperty().bind(finance.getProperty(finance.getSolveFor()));
        	System.out.println("bound new");
        });
        lookupAll(".text-field").forEach( node -> {
            DoubleTextField tf = (DoubleTextField)node;
            String paramName = (String)tf.getProperties().get("paramName");
            TmvParams fieldParam = TmvParams.valueOf(paramName);
        	finance.getProperty(fieldParam).addListener((obj,oldVal,newVal) -> {System.out.println("finance "+paramName+" updated to "+newVal);});
            if (!fieldParam.equals(finance.getSolveFor())) {
            	System.out.println("binding finance:"+paramName);
            	tf.valProperty().addListener((obj,oldVal,newVal) -> {System.out.println("text double updated to "+newVal);});
            	tf.valProperty().bindBidirectional(finance.getProperty(fieldParam));
            } else {
            	tf.valProperty().bind(finance.getProperty(finance.getSolveFor()));
            }
        });
    }
}


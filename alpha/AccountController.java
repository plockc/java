package alpha;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.*;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.*;
import javafx.beans.value.*;
import javafx.beans.binding.*;
import javafx.beans.property.*;
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
    @FXML private ChoiceBox<AccountController> whichPvForFv;
    @FXML private ChoiceBox<AccountController> whichFvForPv;
    @FXML private Button cancelPVImport, cancelFVImport;
    private SimpleListProperty<AccountController> finances = new SimpleListProperty<AccountController>();
    
    private Map<String,String> labelToParam = new HashMap<String,String>();
//    private Set<AccountController> fvSubscribers = new CopyOnWriteArraySet<AccountController>();
//    private Set<AccountController> pvSubscribers = new CopyOnWriteArraySet<AccountController>();
    private StringProperty name = new SimpleStringProperty("Account");
    private Map<String,TextField> paramToField = new HashMap<String,TextField>();
    @FXML private TextField fvField, pvField, rField, nField, pmtField, cmpPmtField, gField;

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
    public String getName() {return name.get();}
    public void setName(String name) {this.name.set(name);}
    public StringProperty nameProperty() {return name;}
    public Finance getFinance() {return finance;}
    public void setAccountsList(ObservableList<AccountController> accountNames) {
        whichPvForFv.setItems(accountNames);
        whichFvForPv.setItems(accountNames);
        
        finances.set(accountNames);
    }
    public String toString() {return name.get();}
    public void initialize() {
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
            finance.solveFor(newVal);

            boolean hasOtherAccounts = !whichPvForFv.getItems().isEmpty();

        	oldField.valProperty().bindBidirectional(finance.getProperty(TmvParams.valueOf(oldVal)));
            newField.valProperty().bind(finance.getProperty(finance.getSolveFor()));
        });
        lookupAll(".text-field").forEach( node -> {
            DoubleTextField tf = (DoubleTextField)node;
            String paramName = (String)tf.getProperties().get("paramName");
            TmvParams fieldParam = TmvParams.valueOf(paramName);
        	finance.getProperty(fieldParam).addListener((obj,oldVal,newVal) -> {System.out.println("finance "+paramName+" updated to "+newVal);});
            if (!fieldParam.equals(finance.getSolveFor())) {
            	tf.valProperty().addListener((obj,oldVal,newVal) -> {System.out.println("text double updated to "+newVal);});
            	tf.valProperty().bindBidirectional(finance.getProperty(fieldParam));
            } else {
            	tf.valProperty().bind(finance.getProperty(finance.getSolveFor()));
            }
        });
        whichPvForFv.valueProperty().addListener((obs, oldPv, newPv) -> {
            if (newPv == null) {
                finance.getProperty("fv").unbind();
                fvField.setDisable(false);
                fvField.setStyle("");
            } else {
                finance.getProperty("fv").bind(newPv.finance.getProperty("pv"));
                fvField.setDisable(true);
                fvField.setStyle("-fx-background-color: lightgrey; -fx-opacity: 1;");
            }
        });
        whichFvForPv.valueProperty().addListener((obs, oldFv, newFv) -> {
            if (newFv == null) {
                finance.getProperty("pv").unbind();
                pvField.setDisable(false);
                pvField.setStyle("");
            } else {
                pvField.setDisable(true);
                pvField.setStyle("-fx-background-color: lightgrey; -fx-opacity: 1;");
                finance.getProperty("pv").bind(newFv.finance.getProperty("fv"));
            }
        });
    
        whichPvForFv.visibleProperty().bind(finances.emptyProperty().not().and(finance.solveForProperty().isNotEqualTo(TmvParams.fv)));
        whichFvForPv.visibleProperty().bind(finances.emptyProperty().not().and(finance.solveForProperty().isNotEqualTo(TmvParams.pv)));
        
        cancelPVImport.setOnAction(e -> whichFvForPv.setValue(null));
        cancelFVImport.setOnAction(e -> whichPvForFv.setValue(null));
    }
}


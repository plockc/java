package alpha;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Stream;
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
    @FXML private TextField fvField, pvField, rField, nField, pmtField, compPmtField, gField;

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
        	DoubleTextField oldField = (DoubleTextField)lookupAll(".text-field").stream()
        			.filter(field->oldVal.equals(field.getProperties().get("paramName"))).findFirst().get();
        	DoubleTextField newField = (DoubleTextField)lookupAll(".text-field").stream()
        			.filter(field->newVal.equals(field.getProperties().get("paramName"))).findFirst().get();
            oldField.setStyle("");
            if (oldVal.equals("comp_pmt")) {pmtField.setStyle("");}
            if (oldVal.equals("pmt")) {compPmtField.setStyle("");}
            newField.setStyle("-fx-background-color: lightgrey; -fx-opacity: 1;");
            if (newVal.equals("comp_pmt")) {
            	pmtField.setStyle("-fx-background-color: lightgrey; -fx-opacity: 1;");
            }
            if (newVal.equals("pmt")) {
            	compPmtField.setStyle("-fx-background-color: lightgrey; -fx-opacity: 1;");
            }
            // unbind all the old stuff before changing solveFor and rebinding the new stuff
            oldField.valProperty().unbind();
            newField.valProperty().unbindBidirectional(finance.getProperty(TmvParams.valueOf(newVal)));
            finance.solveFor(newVal);

        	oldField.valProperty().bindBidirectional(finance.getProperty(TmvParams.valueOf(oldVal)));
            newField.valProperty().bind(finance.getProperty(finance.getSolveFor()));
        });
        lookupAll(".text-field").forEach( node -> {
            DoubleTextField tf = (DoubleTextField)node;
            String paramName = (String)tf.getProperties().get("paramName");
            TmvParams fieldParam = TmvParams.valueOf(paramName);
            if (!fieldParam.equals(finance.getSolveFor())) {
            	tf.valProperty().bindBidirectional(finance.getProperty(fieldParam));
            } else {
            	tf.valProperty().bind(finance.getProperty(finance.getSolveFor()));
            }
        });
        whichPvForFv.valueProperty().addListener((obs, oldPv, newPv) -> {
            if (newPv == null) {
                finance.getProperty("fv").unbind();
                fvField.setStyle("");
            } else {
                finance.getProperty("fv").bind(newPv.finance.getProperty("pv"));
                fvField.setStyle("-fx-background-color: lightgrey; -fx-opacity: 1;");
            }
        });
        whichFvForPv.valueProperty().addListener((obs, oldFv, newFv) -> {
            if (newFv == null) {
                finance.getProperty("pv").unbind();
                pvField.setStyle("");
            } else {
                pvField.setStyle("-fx-background-color: lightgrey; -fx-opacity: 1;");
                finance.getProperty("pv").bind(newFv.finance.getProperty("fv"));
            }
        });
    
        // if fxml supported booleans for expression binding, then we would not have to bind here
        // basically visible when there are other finances, and we're not solving for the same value
        BooleanExpression notSolvingFv = finance.solveForProperty().isNotEqualTo(TmvParams.fv);
        BooleanExpression notSolvingPv = finance.solveForProperty().isNotEqualTo(TmvParams.pv);
        whichPvForFv.visibleProperty().bind(finances.emptyProperty().not().and(notSolvingFv));
        whichFvForPv.visibleProperty().bind(finances.emptyProperty().not().and(notSolvingPv));
        // TODO Fix to choice box not null
        cancelPVImport.visibleProperty().bind(whichFvForPv.valueProperty().isNotNull().and(notSolvingPv));
        cancelFVImport.visibleProperty().bind(whichPvForFv.valueProperty().isNotNull().and(notSolvingFv));

        Function<TextField,BooleanBinding> matchingSolveFor = f -> {
            ObjectProperty solveFor = finance.solveForProperty();
        	return solveFor.isEqualTo(TmvParams.valueOf((String) f.getProperties().get("paramName")));
        };
        // block entry to fields when they are being solved for, when they are imported values, and tie comp_pmt and pmt together
        Stream.of(fvField, pvField, rField, nField, pmtField, compPmtField, gField).forEach(f->{
        	BooleanExpression disable = matchingSolveFor.apply(f);
        	if (f == fvField) {disable = disable.or(whichPvForFv.valueProperty().isNotNull());}
        	else if (f == pvField) {disable = disable.or(whichFvForPv.valueProperty().isNotNull());}
        	else if (f == compPmtField) {disable = disable.or(pmtField.disableProperty());}
        	f.disableProperty().bind(disable);
        });

        cancelPVImport.setOnAction(e -> whichFvForPv.setValue(null));
        cancelFVImport.setOnAction(e -> whichPvForFv.setValue(null));
    }
}


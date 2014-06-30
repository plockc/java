package alpha;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.*;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.Property;
import javafx.event.*;
import javafx.util.*;
import javafx.fxml.*;
import plock.math.Finance;
import plock.math.Finance.TmvParams;
import plock.fx.Controls.DoubleTextField;

public class CashFlowsController extends BorderPane {
    @FXML private TextField newAccountNameField;
    @FXML private Accordion accountsAccordion;

    @FXML public void addAccount(ActionEvent e) {
        System.out.println("pressed with name "+newAccountNameField.getText());
        AccountController newAccount = new AccountController();
        List panes = accountsAccordion.getPanes();
        if (panes.size() > 0) {
        	Finance finance = ((AccountController)((TitledPane)panes.get(panes.size()-1)).getContent()).getFinance();
            Property lastFv = finance.getProperty(TmvParams.valueOf("fv"));
            newAccount.getFinance().getProperty(TmvParams.valueOf("pv")).bindBidirectional(lastFv);
        }
        panes.add(new TitledPane(newAccountNameField.getText(), newAccount));
    }
}


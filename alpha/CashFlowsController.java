package alpha;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.text.*;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.*;
import javafx.beans.property.*;
import javafx.event.*;
import javafx.util.*;
import javafx.fxml.*;
import plock.math.Finance;
import plock.math.Finance.TmvParams;
import plock.fx.Controls.DoubleTextField;

public class CashFlowsController {
    @FXML private TextField newAccountNameField;
    @FXML private Accordion accountsAccordion;
    @FXML private ObservableList<AccountController> accountsInCashFlow;

    @FXML public void addAccount(ActionEvent event) {
        AccountController newAccount = new AccountController();
        String newAccountName = newAccountNameField.getText();
        newAccount.setName(newAccountName);
        List panes = accountsAccordion.getPanes();
        accountsInCashFlow.add(newAccount);
        TitledPane newAccountPane = new TitledPane(newAccountName, newAccount);
        panes.add(newAccountPane);
        accountsAccordion.setExpandedPane(newAccountPane);
        // this prevents and account from selecting itself as an import
        newAccount.setAccountsList(accountsInCashFlow.filtered(e->!e.getName().equals(newAccount.getName())));
    }
    public Map<String,Finance> getFinances() {
        return new LinkedList<TitledPane>(accountsAccordion.getPanes()).stream().collect(Collectors.toMap(
        		p->p.getText(), p->((AccountController)((TitledPane)p).getContent()).getFinance()));
    }
}


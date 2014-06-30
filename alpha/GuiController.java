package alpha;

import java.io.*;
import java.util.*;

import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.fxml.*;
import plock.util.*;

//TODO: cache the last text so we don't recompile on key up for example
//TODO: on change in the account, reprocess the template
public class GuiController {
    private static ObservableMap<String,AccountController> accounts = FXCollections.observableHashMap();
    @FXML TextArea sourceCode;
    @FXML TextArea console;
    @FXML AccountController accountOne;
    @FXML AccountController accountTwo;

    @FXML public void sourceChanged(KeyEvent event) {
        ByteArrayOutputStream consoleBytes = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        PrintStream consolePrinter = null;
        try {consolePrinter = new PrintStream(consoleBytes, true, "UTF-8");} catch (Exception e) {}
        try {
            System.setOut(consolePrinter);
            Template tpl = null;
            tpl = new Template().addImports(Arrays.asList("plock.math.Finance"))
                .setSource(sourceCode.getText().toCharArray());
            System.out.println(tpl.render(accountOne.getFinance().getValues()));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace(consolePrinter);
        } finally {
            System.setOut(oldOut);
            try {console.setText(consoleBytes.toString("UTF-8"));}
            catch (Exception e) {console.setText("missing UTF-8");}
        }
    }; 

    public void initialize() {
        //accounts.put("one", accountOne);
        //accounts.put("two", accountTwo);
//        accountTwo.linkPvToFvOf(accountOne);

    }
}


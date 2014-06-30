package alpha;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;
import java.util.function.*;
import java.text.*;
import javafx.application.Application;
import javafx.collections.*;
import javafx.event.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.*;
import javafx.scene.text.*;
import javafx.stage.*;
import javafx.geometry.*;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.fxml.*;

import plock.util.*;
import plock.math.Finance;
import plock.math.Finance.TmvParams;

public class FinancePlanner extends Application {
	@Override
	public void start(Stage primaryStage) {
        try {
            BorderPane root = javafx.fxml.FXMLLoader.load(getClass().getResource("/finance.fxml"));
            primaryStage.setTitle("Hello World!");
            primaryStage.setScene(new Scene(root, 1280, 900));
            primaryStage.show();
        } catch (IOException e) {
              e.printStackTrace();
              System.exit(1);
        }
	}

	public static void main(String[] args) {
		launch(args);
	}
}


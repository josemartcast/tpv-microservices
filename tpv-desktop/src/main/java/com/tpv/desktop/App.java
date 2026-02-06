package com.tpv.desktop;

import com.tpv.desktop.core.Nav;
import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {
  @Override
  public void start(Stage stage) {
    Nav.init(stage);
    Nav.goToLogin();
  }

  public static void main(String[] args) {
    launch(args);
  }
}

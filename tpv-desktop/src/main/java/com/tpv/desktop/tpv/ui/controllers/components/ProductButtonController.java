package com.tpv.desktop.tpv.ui.controllers.components;

import com.tpv.desktop.tpv.domain.model.Product;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

public class ProductButtonController {
    @FXML private Button button;
    private Runnable onClick;

    public void bind(Product product, Runnable onClick) {
        this.onClick = onClick;
        button.setText(product.name());
        if (!button.getStyleClass().contains(product.colorClass())) {
            button.getStyleClass().add(product.colorClass());
        }
    }

    @FXML
    public void onClick() {
        if (onClick != null) onClick.run();
    }
}


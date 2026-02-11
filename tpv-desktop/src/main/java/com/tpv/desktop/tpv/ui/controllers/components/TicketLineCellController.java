package com.tpv.desktop.tpv.ui.controllers.components;

import com.tpv.desktop.tpv.domain.model.OrderLine;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class TicketLineCellController {
    @FXML private HBox root;
    @FXML private Label qtyLabel;
    @FXML private Label nameLabel;
    @FXML private Label priceLabel;
    @FXML private Label noteLabel;
    @FXML private Label destinationLabel;

    public void bind(OrderLine line) {
        qtyLabel.setText(line.getQty() + "x");
        nameLabel.setText(line.getProductName());
        priceLabel.setText(String.format(java.util.Locale.US, "%.2f EUR", line.lineTotalCents() / 100.0));
        noteLabel.setText(line.getNote() == null || line.getNote().isBlank() ? "" : "- " + line.getNote());
        destinationLabel.setText(line.getDestination().name());
        destinationLabel.getStyleClass().removeAll("dest-bar", "dest-cocina", "dest-postres");
        switch (line.getDestination()) {
            case BAR -> destinationLabel.getStyleClass().add("dest-bar");
            case COCINA -> destinationLabel.getStyleClass().add("dest-cocina");
            case POSTRES -> destinationLabel.getStyleClass().add("dest-postres");
        }
        if (line.getPendingQty() == 0) {
            root.getStyleClass().add("ticket-line-sent");
        }
    }
}


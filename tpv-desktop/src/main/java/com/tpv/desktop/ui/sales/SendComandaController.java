package com.tpv.desktop.ui.sales;

import com.tpv.desktop.api.pos.TicketLineResponse;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

public class SendComandaController {

    @FXML
    private Label barCountLabel;
    @FXML
    private Label cocinaCountLabel;
    @FXML
    private ListView<String> barList;
    @FXML
    private ListView<String> cocinaList;
    @FXML
    private Button sendAllBtn;
    @FXML
    private Button sendBarBtn;
    @FXML
    private Button sendCocinaBtn;

    private final List<TicketLineResponse> pendingBar = new ArrayList<>();
    private final List<TicketLineResponse> pendingCocina = new ArrayList<>();
    private SendComandaResult result = SendComandaResult.NONE;

    public void init(List<TicketLineResponse> pendingLines) {
        pendingBar.clear();
        pendingCocina.clear();
        for (TicketLineResponse line : pendingLines) {
            if (destinationFor(line) == SendDestination.BAR) {
                pendingBar.add(line);
            } else {
                pendingCocina.add(line);
            }
        }
        barList.setItems(FXCollections.observableArrayList(
                pendingBar.stream().map(this::lineText).toList()));
        cocinaList.setItems(FXCollections.observableArrayList(
                pendingCocina.stream().map(this::lineText).toList()));

        barCountLabel.setText("BAR " + pendingBar.size() + " lines");
        cocinaCountLabel.setText("COCINA " + pendingCocina.size() + " lines");

        sendBarBtn.setDisable(pendingBar.isEmpty());
        sendCocinaBtn.setDisable(pendingCocina.isEmpty());
        sendAllBtn.setDisable(pendingBar.isEmpty() && pendingCocina.isEmpty());
    }

    public SendComandaResult getResult() {
        return result;
    }

    @FXML
    public void onSendAll() {
        result = SendComandaResult.ALL;
        close();
    }

    @FXML
    public void onSendBar() {
        result = SendComandaResult.BAR_ONLY;
        close();
    }

    @FXML
    public void onSendCocina() {
        result = SendComandaResult.COCINA_ONLY;
        close();
    }

    @FXML
    public void onCancel() {
        result = SendComandaResult.NONE;
        close();
    }

    private void close() {
        Stage s = (Stage) sendAllBtn.getScene().getWindow();
        s.close();
    }

    private String lineText(TicketLineResponse line) {
        return line.qty() + "x " + line.productName();
    }

    private SendDestination destinationFor(TicketLineResponse line) {
        String d = line.destination();
        if (d == null) {
            return SendDestination.COCINA;
        }
        return "BAR".equalsIgnoreCase(d) ? SendDestination.BAR : SendDestination.COCINA;
    }
}

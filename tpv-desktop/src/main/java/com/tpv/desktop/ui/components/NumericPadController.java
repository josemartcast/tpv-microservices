package com.tpv.desktop.ui.components;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;

public class NumericPadController {
    private TextInputControl target;

    public void bindTargets(TextInputControl... fields) {
        if (fields == null || fields.length == 0) {
            return;
        }
        for (TextInputControl field : fields) {
            if (field == null) {
                continue;
            }
            field.focusedProperty().addListener((obs, oldV, focused) -> {
                if (Boolean.TRUE.equals(focused)) {
                    setTarget(field);
                }
            });
        }
        if (target == null) {
            setTarget(fields[0]);
        }
    }

    public void setTarget(TextInputControl field) {
        this.target = field;
    }

    @FXML
    private void onDigit(ActionEvent event) {
        if (target == null) {
            return;
        }
        Object src = event.getSource();
        if (!(src instanceof Button btn)) {
            return;
        }
        String value = btn.getUserData() == null ? btn.getText() : btn.getUserData().toString();
        if (value == null || value.isEmpty()) {
            return;
        }
        if (".".equals(value) && target.getText() != null && target.getText().contains(".")) {
            return;
        }
        int caret = target.getCaretPosition();
        target.insertText(caret, value);
    }

    @FXML
    private void onBackspace() {
        if (target == null) {
            return;
        }
        String text = target.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        int start = target.getSelection().getStart();
        int end = target.getSelection().getEnd();
        if (start != end) {
            target.deleteText(start, end);
            return;
        }
        int caret = target.getCaretPosition();
        if (caret <= 0) {
            return;
        }
        target.deleteText(caret - 1, caret);
    }

    @FXML
    private void onClear() {
        if (target == null) {
            return;
        }
        target.clear();
    }
}

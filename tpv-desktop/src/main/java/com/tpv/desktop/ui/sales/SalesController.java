package com.tpv.desktop.ui.sales;

import com.tpv.desktop.api.ApiClient.ApiException;
import com.tpv.desktop.api.pos.*;
import com.tpv.desktop.core.MoneyUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;

import java.util.Arrays;

public class SalesController {

    private final java.util.concurrent.atomic.AtomicBoolean paying = new java.util.concurrent.atomic.AtomicBoolean(false);
    private boolean cashOpen = false;
    Long resumeId = com.tpv.desktop.core.AppState.getResumeTicketId();

    @FXML
    private ListView<CategoryResponse> categoriesList;
    @FXML
    private FlowPane productsPane;

    @FXML
    private TableView<TicketLineResponse> linesTable;
    @FXML
    private TableColumn<TicketLineResponse, String> colName;
    @FXML
    private TableColumn<TicketLineResponse, Integer> colQty;
    @FXML
    private TableColumn<TicketLineResponse, String> colUnit;
    @FXML
    private TableColumn<TicketLineResponse, String> colTotal;

    @FXML
    private Label ticketInfoLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Label errorLabel;

    @FXML
    private Button payCashBtn;
    @FXML
    private Button payCardBtn;
    @FXML
    private Button payBizumBtn;
    @FXML
    private Button cancelBtn;

    private TicketResponse currentTicket;

    private final ObservableList<TicketLineResponse> lines = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        checkCashSessionOrBlock();
        setupTable();
        linesTable.setItems(lines);

        // Cargar categorías y productos
        loadCategories();

        categoriesList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            Long catId = (newV == null) ? null : newV.id();
            loadProducts(catId);
        });

        // Cargar todos por defecto
        loadProducts(null);

        // Deshabilita acciones si no hay ticket
        renderTicket(null);

        if (resumeId != null) {
            try {
                currentTicket = TicketApi.getById(resumeId);
                renderTicket(currentTicket);
            } catch (Exception e) {
                errorLabel.setText("No se pudo cargar el ticket: " + e.getMessage());
            } finally {
                com.tpv.desktop.core.AppState.clearResumeTicketId();
            }
        }
    }

    private void setupTable() {
        colName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().productName()));
        colQty.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().qty()));
        colUnit.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(MoneyUtil.centsToEuros(c.getValue().unitPriceCents())));
        colTotal.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(MoneyUtil.centsToEuros(c.getValue().lineTotalCents())));

        // Context menu para borrar línea
        linesTable.setRowFactory(tv -> {
            TableRow<TicketLineResponse> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem del = new MenuItem("Eliminar línea");
            del.setOnAction(e -> {
                TicketLineResponse line = row.getItem();
                if (line != null) {
                    deleteLine(line.id());
                }
            });
            menu.getItems().add(del);
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu)
            );
            return row;
        });

        // Doble click para cambiar qty
        linesTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TicketLineResponse sel = linesTable.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    promptQtyAndUpdate(sel);
                }
            }
        });
    }

    // ---------- LOADERS ----------
    private void loadCategories() {
        try {
            errorLabel.setText("");
            CategoryResponse[] arr = CatalogApi.categories();
            ObservableList<CategoryResponse> items = FXCollections.observableArrayList(arr);
            categoriesList.setItems(items);

            // Render bonito
            categoriesList.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(CategoryResponse item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.name());
                }
            });
        } catch (Exception e) {
            errorLabel.setText("No se pudieron cargar categorías: " + e.getMessage());
        }
    }

    private void loadProducts(Long categoryId) {
        try {
            errorLabel.setText("");
            productsPane.getChildren().clear();
            ProductResponse[] arr = CatalogApi.products(categoryId);

            Arrays.stream(arr).forEach(p -> {
                Button b = new Button(p.name() + "\n" + MoneyUtil.centsToEuros(p.priceCents()) + " €");
                b.setPrefWidth(180);
                b.setPrefHeight(70);
                b.setOnAction(ev -> addProductToTicket(p));
                productsPane.getChildren().add(b);
            });
        } catch (Exception e) {
            errorLabel.setText("No se pudieron cargar productos: " + e.getMessage());
        }
    }

    // ---------- TICKET FLOW ----------
    @FXML
    public void onNewTicket() {
        checkCashSessionOrBlock();
        errorLabel.setText("");
        try {
            // (opcional) aquí podrías chequear que hay caja abierta
            // CashApi.current(); // si 404, obligar a abrir caja
            currentTicket = TicketApi.create();
            renderTicket(currentTicket);
        } catch (ApiException e) {
            errorLabel.setText("No se pudo crear ticket: " + e.getMessage());
        } catch (Exception e) {
            errorLabel.setText("Error inesperado: " + e.getMessage());
        }
    }

    private void addProductToTicket(ProductResponse p) {
        checkCashSessionOrBlock();
        errorLabel.setText("");
        try {
            // Si no hay ticket o no está OPEN, crea uno nuevo
            if (currentTicket == null || !"OPEN".equalsIgnoreCase(currentTicket.status())) {
                currentTicket = TicketApi.create();
            }

            // Buscar si ya existe una línea con ese producto
            TicketLineResponse existing = null;
            if (currentTicket.lines() != null) {
                for (TicketLineResponse l : currentTicket.lines()) {
                    if (l.productId() == p.id()) {
                        existing = l;
                        break;
                    }
                }
            }

            // Si existe, incrementa qty; si no, añade línea
            if (existing != null) {
                currentTicket = TicketApi.updateQty(
                        currentTicket.id(),
                        existing.id(),
                        existing.qty() + 1
                );
            } else {
                currentTicket = TicketApi.addLine(currentTicket.id(), p.id(), 1);
            }

            renderTicket(currentTicket);

        } catch (Exception e) {
            errorLabel.setText("No se pudo añadir producto: " + e.getMessage());
        }
    }

    private void promptQtyAndUpdate(TicketLineResponse line) {
        if (currentTicket == null) {
            return;
        }

        TextInputDialog d = new TextInputDialog(String.valueOf(line.qty()));
        d.setTitle("Cambiar cantidad");
        d.setHeaderText(line.productName());
        d.setContentText("Nueva cantidad:");

        d.showAndWait().ifPresent(txt -> {
            try {
                int qty = Integer.parseInt(txt.trim());
                if (qty <= 0) {
                    errorLabel.setText("La cantidad debe ser > 0");
                    return;
                }
                currentTicket = TicketApi.updateQty(currentTicket.id(), line.id(), qty);
                renderTicket(currentTicket);
            } catch (Exception e) {
                errorLabel.setText("No se pudo actualizar qty: " + e.getMessage());
            }
        });
    }

    private void deleteLine(long lineId) {
        if (currentTicket == null) {
            return;
        }
        try {
            currentTicket = TicketApi.deleteLine(currentTicket.id(), lineId);
            renderTicket(currentTicket);
        } catch (Exception e) {
            errorLabel.setText("No se pudo eliminar línea: " + e.getMessage());
        }
    }

    @FXML
    public void onRefresh() {
        renderTicket(currentTicket);
    }

    @FXML
    public void onCancel() {
        if (currentTicket == null) {
            return;
        }
        try {
            currentTicket = TicketApi.cancel(currentTicket.id());
            renderTicket(currentTicket);
            currentTicket = null;
            renderTicket(null);
        } catch (Exception e) {
            errorLabel.setText("No se pudo cancelar: " + e.getMessage());
        }
    }

    // ---------- PAYMENTS ----------
    @FXML
    public void onPayCash() {
        payFull("CASH");
    }

    @FXML
    public void onPayCard() {
        payFull("CARD");
    }

    @FXML
    public void onPayBizum() {
        payFull("BIZUM");
    }

    private void payFull(String method) {
        if (currentTicket == null) {
            errorLabel.setText("No hay ticket.");
            return;
        }

        try {
            int amount = currentTicket.totalCents();
            if (amount <= 0) {
                errorLabel.setText("El ticket está vacío.");
                return;
            }

            // Evita doble click mientras cobra
            setPayButtonsEnabled(false);

            // Esto ya realiza el cobro (y por lo visto deja el ticket NO-OPEN)
            PaymentApi.addPayment(currentTicket.id(), method, amount);

            // Limpia UI y deja listo el siguiente
            currentTicket = null;
            renderTicket(null);

            // auto-ticket nuevo
            currentTicket = TicketApi.create();
            renderTicket(currentTicket);

        } catch (Exception e) {
            errorLabel.setText("No se pudo cobrar: " + e.getMessage());
            setPayButtonsEnabled(true);
        } finally {
            paying.set(false);

        }
    }

    // ---------- RENDER ----------
    private void renderTicket(TicketResponse t) {
        if (t == null) {
            ticketInfoLabel.setText("Sin ticket (crea uno o pulsa un producto)");
            lines.clear();
            linesTable.refresh();
            totalLabel.setText("0.00");

            setPayButtonsEnabled(false);
            cancelBtn.setDisable(true);
            return;
        }

        ticketInfoLabel.setText("Ticket #" + t.id() + " — " + t.status());
        lines.setAll(t.lines() == null ? FXCollections.observableArrayList() : t.lines());

        totalLabel.setText(MoneyUtil.centsToEuros(t.totalCents()));
        boolean canPay = "OPEN".equalsIgnoreCase(t.status()) && t.totalCents() > 0;

        setPayButtonsEnabled(canPay);
        cancelBtn.setDisable(!"OPEN".equalsIgnoreCase(t.status()));
    }

    private void setPayButtonsEnabled(boolean enabled) {
        payCashBtn.setDisable(!enabled);
        payCardBtn.setDisable(!enabled);
        payBizumBtn.setDisable(!enabled);
    }

    private void checkCashSessionOrBlock() {
        try {
            // si no hay open, backend devuelve 404
            com.tpv.desktop.api.pos.CashApi.current();
            cashOpen = true;
            setSalesEnabled(true);
            errorLabel.setText("");
        } catch (Exception e) {
            cashOpen = false;
            setSalesEnabled(false);
            errorLabel.setText("Debes abrir caja antes de vender.");
        }
    }

    private void setSalesEnabled(boolean enabled) {
        // deshabilita productos (los botones del FlowPane)
        productsPane.setDisable(!enabled);
        // deshabilita “Nuevo ticket”
        // deshabilita cobros y cancelar
        setPayButtonsEnabled(enabled);
        cancelBtn.setDisable(!enabled);
    }
}

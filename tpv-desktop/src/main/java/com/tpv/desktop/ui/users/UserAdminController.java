package com.tpv.desktop.ui.users;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpv.desktop.api.ApiClient.ApiException;
import com.tpv.desktop.api.auth.AdminUserResponse;
import com.tpv.desktop.api.auth.AuthApi;
import java.util.Comparator;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public class UserAdminController {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private boolean usernameAutoNormalizedPendingNotice;

    @FXML private TableView<AdminUserResponse> usersTable;
    @FXML private TableColumn<AdminUserResponse, Number> idCol;
    @FXML private TableColumn<AdminUserResponse, String> usernameCol;
    @FXML private TableColumn<AdminUserResponse, String> roleCol;
    @FXML private TableColumn<AdminUserResponse, Boolean> activeCol;

    @FXML private TextField createUsernameField;
    @FXML private PasswordField createPasswordField;
    @FXML private ComboBox<String> createRoleBox;

    @FXML private Label selectedUserLabel;
    @FXML private ComboBox<String> editRoleBox;
    @FXML private PasswordField resetPasswordField;
    @FXML private CheckBox activeCheck;

    @FXML private Label statusLabel;

    private final ObservableList<AdminUserResponse> users = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        idCol.setCellValueFactory(data -> new SimpleLongProperty(data.getValue().id()));
        usernameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().username()));
        roleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().role()));
        activeCol.setCellValueFactory(data -> new SimpleBooleanProperty(data.getValue().active()));

        usersTable.setItems(users);
        createRoleBox.setItems(FXCollections.observableArrayList("ADMIN", "USER"));
        editRoleBox.setItems(FXCollections.observableArrayList("ADMIN", "USER"));
        createRoleBox.setValue("USER");
        editRoleBox.setValue("USER");
        installUsernameAutoNormalization();

        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> bindSelectedUser(selected));
        onRefresh();
    }

    @FXML
    public void onRefresh() {
        statusLabel.setText("");
        try {
            users.setAll(
                    AuthApi.listUsers().stream()
                            .sorted(Comparator.comparing(AdminUserResponse::username, String.CASE_INSENSITIVE_ORDER))
                            .toList()
            );
            if (!users.isEmpty() && usersTable.getSelectionModel().getSelectedItem() == null) {
                usersTable.getSelectionModel().selectFirst();
            }
            statusLabel.setText("Usuarios cargados: " + users.size());
        } catch (Exception e) {
            String msg = "No se pudo cargar usuarios: " + renderError(e);
            statusLabel.setText(msg);
            showError("Usuarios", msg);
        }
    }

    @FXML
    public void onCreateUser() {
        statusLabel.setText("");
        String username = value(createUsernameField);
        String password = value(createPasswordField);
        String role = createRoleBox.getValue();

        if (usernameAutoNormalizedPendingNotice) {
            boolean proceed = confirm(
                    "Crear usuario",
                    "Se detectaron espacios en el usuario y se reemplazaron por '_'.\n"
                            + "Usuario final: " + username + "\n\n"
                            + "Deseas continuar?"
            );
            if (!proceed) {
                return;
            }
            usernameAutoNormalizedPendingNotice = false;
        }

        if (!username.matches("[A-Za-z0-9._@\\-]{3,50}")) {
            showWarn(
                    "Crear usuario",
                    "Usuario invalido. Usa 3-50 caracteres sin espacios (letras, numeros, . _ - @)."
            );
            return;
        }
        if (password.length() < 6) {
            showWarn("Crear usuario", "La contraseña debe tener al menos 6 caracteres.");
            return;
        }
        if (role == null || role.isBlank()) {
            showWarn("Crear usuario", "Selecciona un rol.");
            return;
        }

        try {
            AuthApi.createUser(username, password, role);
            createUsernameField.clear();
            createPasswordField.clear();
            createRoleBox.setValue("USER");
            usernameAutoNormalizedPendingNotice = false;
            onRefresh();
            statusLabel.setText("Usuario creado correctamente.");
        } catch (Exception e) {
            String msg = "No se pudo crear usuario: " + renderError(e);
            statusLabel.setText(msg);
            showError("Crear usuario", msg);
        }
    }

    @FXML
    public void onSaveRole() {
        AdminUserResponse selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarn("Rol", "Selecciona un usuario.");
            return;
        }
        String role = editRoleBox.getValue();
        if (role == null || role.isBlank()) {
            showWarn("Rol", "Selecciona un rol.");
            return;
        }

        try {
            AuthApi.updateRole(selected.id(), role);
            onRefresh();
            statusLabel.setText("Rol actualizado.");
        } catch (Exception e) {
            String msg = "No se pudo actualizar rol: " + renderError(e);
            statusLabel.setText(msg);
            showError("Rol", msg);
        }
    }

    @FXML
    public void onResetPassword() {
        AdminUserResponse selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarn("Contraseña", "Selecciona un usuario.");
            return;
        }
        String password = value(resetPasswordField);
        if (password.length() < 6) {
            showWarn("Contraseña", "La nueva contraseña debe tener al menos 6 caracteres.");
            return;
        }

        try {
            AuthApi.updatePassword(selected.id(), password);
            resetPasswordField.clear();
            statusLabel.setText("Contraseña actualizada.");
            showInfo("Contraseña", "Contraseña actualizada para '" + selected.username() + "'.");
        } catch (Exception e) {
            String msg = "No se pudo actualizar contraseña: " + renderError(e);
            statusLabel.setText(msg);
            showError("Contraseña", msg);
        }
    }

    @FXML
    public void onSaveActive() {
        AdminUserResponse selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarn("Estado", "Selecciona un usuario.");
            return;
        }

        boolean active = activeCheck.isSelected();
        String message = active
                ? "Se activará el usuario '" + selected.username() + "'."
                : "Se desactivará el usuario '" + selected.username() + "'.";
        if (!confirm("Confirmar estado", message)) {
            return;
        }

        try {
            AuthApi.setActive(selected.id(), active);
            onRefresh();
            statusLabel.setText("Estado actualizado.");
        } catch (Exception e) {
            String msg = "No se pudo actualizar estado: " + renderError(e);
            statusLabel.setText(msg);
            showError("Estado", msg);
        }
    }

    @FXML
    public void onDeleteUser() {
        AdminUserResponse selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarn("Eliminar usuario", "Selecciona un usuario.");
            return;
        }

        if (!confirm(
                "Eliminar usuario",
                "Vas a eliminar el usuario '" + selected.username() + "'.\n"
                        + "Esta accion es permanente."
        )) {
            return;
        }
        if (!confirm(
                "Confirmacion final",
                "No se puede deshacer.\nConfirmas eliminar '" + selected.username() + "'?"
        )) {
            return;
        }

        try {
            AuthApi.deleteUser(selected.id());
            onRefresh();
            statusLabel.setText("Usuario eliminado.");
        } catch (Exception e) {
            String msg = "No se pudo eliminar usuario: " + renderError(e);
            statusLabel.setText(msg);
            showError("Eliminar usuario", msg);
        }
    }

    private void bindSelectedUser(AdminUserResponse selected) {
        if (selected == null) {
            selectedUserLabel.setText("Usuario seleccionado: -");
            editRoleBox.setValue("USER");
            activeCheck.setSelected(false);
            return;
        }
        selectedUserLabel.setText("Usuario seleccionado: " + selected.username() + " (#" + selected.id() + ")");
        editRoleBox.setValue(selected.role());
        activeCheck.setSelected(selected.active());
    }

    private static String value(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private void installUsernameAutoNormalization() {
        if (createUsernameField == null) {
            return;
        }
        createUsernameField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.isBlank()) {
                return;
            }
            String normalized = normalizeUsernameTyping(newV);
            if (normalized.equals(newV)) {
                return;
            }
            int caret = createUsernameField.getCaretPosition();
            createUsernameField.setText(normalized);
            createUsernameField.positionCaret(Math.min(caret, normalized.length()));
            usernameAutoNormalizedPendingNotice = true;
        });
    }

    private static String normalizeUsernameTyping(String value) {
        return value.replaceAll("\\s+", "_");
    }

    private static String renderError(Exception e) {
        if (e instanceof ApiException apiException) {
            if (apiException.getStatus() == 403 && (apiException.getBody() == null || apiException.getBody().isBlank())) {
                return "No autorizado (requiere rol ADMIN) o token sin permisos de escritura.";
            }
            String body = apiException.getBody();
            if (body != null && !body.isBlank()) {
                try {
                    JsonNode node = MAPPER.readTree(body);
                    if (node.hasNonNull("message")) {
                        return node.get("message").asText();
                    }
                } catch (Exception ignored) {
                    return body;
                }
                return body;
            }
            return "HTTP " + apiException.getStatus();
        }
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private static void showWarn(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(title);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
}

package com.courttrack.sync;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class SyncStatus {
    public enum State { OFFLINE, SYNCING, SYNCED, ERROR }

    private static final SyncStatus INSTANCE = new SyncStatus();

    private final ObjectProperty<State> state = new SimpleObjectProperty<>(State.OFFLINE);
    private final StringProperty message = new SimpleStringProperty("Offline");

    private SyncStatus() {}

    public static SyncStatus getInstance() { return INSTANCE; }

    public ObjectProperty<State> stateProperty() { return state; }
    public StringProperty messageProperty() { return message; }

    public State getState() { return state.get(); }

    public void set(State s, String msg) {
        Platform.runLater(() -> {
            state.set(s);
            message.set(msg);
        });
    }
}

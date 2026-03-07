package com.courttrack.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;

public class ObservableList<T> {
    private final ListProperty<T> property = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final List<Consumer<List<T>>> listeners = new ArrayList<>();

    public void set(List<T> items){
        property.set(FXCollections.observableArrayList(items));
        notifyListeners();
    }

    public void addListener(Consumer<List<T>> listener){
        listeners.add(listener);
    }

    public void notifyListeners(){
        List<T> snapshot = new ArrayList<>(property);
        for ( Consumer<List<T>> listener : listeners){
            listener.accept(snapshot);
        }
    }

    public ListProperty<T> property() {
        return property;
    }

    public ObservableList<T> withListener(Consumer<List<T>> listener){
        addListener(listener);
        return this;
    }

}

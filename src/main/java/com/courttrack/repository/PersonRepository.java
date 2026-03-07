package com.courttrack.repository;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.courttrack.dao.PersonDao;
import com.courttrack.model.Person;
import com.courttrack.sync.SyncCoordinator;
public class PersonRepository {
    private static volatile PersonRepository instance;
    private final PersonDao dao = new PersonDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private PersonRepository() {}
    public static PersonRepository getInstance() {
        if (instance == null) {
            synchronized (PersonRepository.class) {
                if (instance == null) instance = new PersonRepository();
            }
        }
        return instance;
    }
    public void getAll(Consumer<List<Person>> onResult) {
        executor.submit(() -> {
            List<Person> result = dao.findAll();
            onResult.accept(result);
        });
    }
    public void search(String query, Consumer<List<Person>> onResult) {
        executor.submit(() -> {
            List<Person> result = dao.search(query);
            onResult.accept(result);
        });
    }
    public void getById(String id, Consumer<Person> onResult) {
        executor.submit(() -> {
            Person result = dao.findById(id);
            onResult.accept(result);
        });
    }
    public void save(Person person, Runnable onComplete) {
        executor.submit(() -> {
            if (dao.findById(person.getPersonId()) == null) {
                dao.insert(person);
            } else {
                dao.update(person);
            }
            person.setHasChanges(true);
            SyncCoordinator.getInstance().queuePersonSync(person.getPersonId(), "UPSERT");
            if (onComplete != null) onComplete.run();
        });
    }
    public void delete(String personId, Runnable onComplete) {
        executor.submit(() -> {
            dao.softDelete(personId);
            SyncCoordinator.getInstance().queuePersonSync(personId, "DELETE");
            if (onComplete != null) onComplete.run();
        });
    }
    public void shutdown() {
        executor.shutdown();
    }
}
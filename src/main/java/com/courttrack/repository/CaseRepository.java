package com.courttrack.repository;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.courttrack.dao.CaseDao;
import com.courttrack.model.Charge;
import com.courttrack.model.CourtCase;
import com.courttrack.sync.SyncCoordinator;
public class CaseRepository {
    private static volatile CaseRepository instance;
    private final CaseDao dao = new CaseDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CaseRepository() {}
    public static CaseRepository getInstance() {
        if (instance == null) {
            synchronized (CaseRepository.class) {
                if (instance == null) instance = new CaseRepository();
            }
        }
        return instance;
    }
    public void getAll(Consumer<List<CourtCase>> onResult) {
        executor.submit(() -> onResult.accept(dao.findAll()));
    }

    public void getAllPaginated(String status, String category, int offset, int limit, Consumer<List<CourtCase>> onResult) {
        executor.submit(() -> onResult.accept(dao.findByStatusAndCategoryPaginated(status, category, offset, limit)));
    }

    public void countByStatusAndCategory(String status, String category, Consumer<Integer> onResult) {
        executor.submit(() -> onResult.accept(dao.countByStatusAndCategory(status, category)));
    }
    public void getByStatusAndCategory(String status, String category, Consumer<List<CourtCase>> onResult) {
        executor.submit(() -> onResult.accept(dao.findByStatusAndCategory(status, category)));
    }
    public void search(String query, Consumer<List<CourtCase>> onResult) {
        executor.submit(() -> onResult.accept(dao.search(query)));
    }
    public void getById(String caseId, Consumer<CourtCase> onResult) {
        executor.submit(() -> onResult.accept(dao.findById(caseId)));
    }
    public void getRecent(int limit, Consumer<List<CourtCase>> onResult) {
        executor.submit(() -> onResult.accept(dao.findRecent(limit)));
    }
    public void save(CourtCase courtCase, String offenderPersonId, Runnable onComplete) {
        executor.submit(() -> {
            if (dao.findById(courtCase.getCaseId()) == null) {
                dao.insert(courtCase);
            } else {
                dao.update(courtCase);
            }
            courtCase.setHasChanges(true);
            SyncCoordinator.getInstance().queueCaseSync(courtCase.getCaseId(), "UPSERT", offenderPersonId);
            if (onComplete != null) onComplete.run();
        });
    }
    public void getCharges(String caseId, Consumer<List<Charge>> onResult) {
        executor.submit(() -> onResult.accept(dao.getCharges(caseId)));
    }
    public void saveCharge(Charge charge, Runnable onComplete) {
        executor.submit(() -> {
            dao.upsertFirstCharge(charge.getCaseId(), charge.getParticulars(), charge.getPlea(), charge.getVerdict());
            if (onComplete != null) onComplete.run();
        });
    }

    public void delete(String caseId, Runnable onComplete) {
        executor.submit(() -> {
            dao.softDelete(caseId);
            SyncCoordinator.getInstance().queueCaseSync(caseId, "DELETE", null);
            if (onComplete != null) onComplete.run();
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
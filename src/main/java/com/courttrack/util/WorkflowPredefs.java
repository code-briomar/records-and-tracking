package com.courttrack.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WorkflowPredefs {
    private static final Map<String, List<String>> TRANSITION_NOTES = Map.of(
        "Registered", List.of(
            "Case registered and file opened for administrative actions.",
            "New case filed and assigned case number.",
            "Case details indexed and files prepared for registry sorting."
        ),
        "Mention", List.of(
            "Case scheduled for mention to confirm compliance with directions.",
            "File retrieved and placed before magistrate for mention.",
            "Adjourned for mention to allow parties to file documents.",
            "Mention scheduled to settle interlocutory applications.",
            "Case listed for mention on application for bail/bond."
        ),
        "Hearing", List.of(
            "Case scheduled for hearing of the main application.",
            "Hearing commenced; testimony of witness being recorded.",
            "Hearing adjourned due to absence of prosecution witness.",
            "Defence case opened and hearing of witnesses scheduled.",
            "Submissions filed by parties; hearing adjourned for judgment date."
        ),
        "Ruling", List.of(
            "Case scheduled for delivery of ruling.",
            "Ruling prepared and delivered to parties.",
            "Ruling delivery deferred to a future date.",
            "Interim ruling delivered; directions given on main suit."
        ),
        "Appeal", List.of(
            "Notice of appeal filed against the lower court decision.",
            "Record of appeal prepared and forwarded to High Court.",
            "Directions issued on the filing of appeal submissions."
        ),
        "Closed", List.of(
            "Case closed following delivery of final judgment.",
            "Case dismissed for want of prosecution.",
            "Case settled out of court; consent signed by both parties.",
            "Accused discharged/acquitted of all charges."
        )
    );

    public static List<String> getShuffledNotesFor(String status) {
        List<String> notes = TRANSITION_NOTES.get(status);
        if (notes == null || notes.isEmpty()) {
            return List.of("Stage changed to " + status + ".");
        }
        List<String> shuffled = new ArrayList<>(notes);
        Collections.shuffle(shuffled);
        return shuffled;
    }
}

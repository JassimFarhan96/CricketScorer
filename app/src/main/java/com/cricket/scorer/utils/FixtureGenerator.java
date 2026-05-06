package com.cricket.scorer.utils;

import com.cricket.scorer.models.TournamentMatch;
import com.cricket.scorer.models.TournamentTeam;

import java.util.ArrayList;
import java.util.List;

/**
 * FixtureGenerator.java
 *
 * Generates a default round-robin schedule where every team plays every
 * other team exactly once. The user can reorder fixtures in the
 * TournamentScheduleActivity before confirming.
 *
 * For N teams: produces N*(N-1)/2 fixtures.
 *
 *   buildRoundRobin([A,B,C,D])
 *     → [A vs B, A vs C, A vs D, B vs C, B vs D, C vs D]
 */
public class FixtureGenerator {

    public static List<TournamentMatch> buildRoundRobin(List<TournamentTeam> teams) {
        List<TournamentMatch> out = new ArrayList<>();
        int n = teams.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                out.add(new TournamentMatch(teams.get(i).getName(), teams.get(j).getName()));
            }
        }
        return out;
    }
}

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

    /**
     * For 2-team best-of-N tournaments: generates N copies of A vs B.
     * Tournament logic stops the series early once a team has clinched
     * (N+1)/2 wins, so unplayed matches at the tail are simply ignored.
     */
    public static List<TournamentMatch> buildBestOfSeries(List<TournamentTeam> teams,
                                                            int bestOfN) {
        List<TournamentMatch> out = new ArrayList<>();
        if (teams.size() != 2 || bestOfN <= 0) return out;
        String a = teams.get(0).getName();
        String b = teams.get(1).getName();
        for (int i = 0; i < bestOfN; i++) {
            out.add(new TournamentMatch(a, b));
        }
        return out;
    }
}

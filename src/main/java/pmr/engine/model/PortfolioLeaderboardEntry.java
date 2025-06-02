package pmr.engine.model;

import java.time.LocalDate;

public record PortfolioLeaderboardEntry(
        int id,
        String username,
        String name,
        double sharpe,
        LocalDate startDate,
        LocalDate endDate
) {

}



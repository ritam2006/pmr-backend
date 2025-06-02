package pmr.engine.model;

import java.time.LocalDate;

public record PortfolioSummary(
        String name,
        int id,
        Asset[] assets,
        LocalDate start,
        LocalDate end,
        double sharpe,
        double valueAtRisk
) {

}

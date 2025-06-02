package pmr.engine.model;

import java.time.LocalDate;
import java.util.List;

public record PortfolioAnalysisResult(
    String name,
    long userId,
    String username,
    List<LocalDate> tradingDates,
    double[] dailyValues,
    double[] dailyReturns,
    double cumulativeReturn,
    double meanReturn,
    double volatility,
    double sharpe,
    double valueAtRisk,
    Asset[] assets
) {

}

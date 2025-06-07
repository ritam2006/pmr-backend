package pmr.engine.analysis;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import pmr.engine.service.Repository;
import pmr.engine.model.Asset;
import pmr.engine.model.Portfolio;

import java.time.LocalDate;
import java.util.*;

public class PortfolioAnalyzer {
    private final long userId;
    private final Portfolio portfolio;
    private final Repository repository;

    private final List<LocalDate> tradingDates;
    private final double[] dailyValues;
    private final double[] dailyReturns;

    private double cumulativeReturn;
    private double meanReturn;
    private double volatility;
    private double sharpe;

    private double valueAtRisk;

    private static final int NUM_TRADING_DAYS = 252;
    private static final double ANNUAL_RISK_FREE_RATE = 0.05;

    public PortfolioAnalyzer(long userId, Portfolio portfolio, Repository repository) {
        this.userId = userId;
        this.portfolio = portfolio;
        this.repository = repository;

        tradingDates = repository.fetchTradingDates();
        dailyValues = new double[tradingDates.size()];
        dailyReturns = new double[tradingDates.size() - 1];
    }

    public int analyze() {
        calculateDailyValues();
        calculateReturns();
        calculateMetrics();
        calculateMonteCarloValueAtRisk(0.95, 10000);

        return repository.savePortfolio(
                portfolio.name(),
                userId,
                tradingDates,
                dailyValues,
                dailyReturns,
                cumulativeReturn,
                meanReturn,
                volatility,
                sharpe,
                valueAtRisk,
                portfolio.assets()
        );
    }

    private void calculateDailyValues() {
        List<Asset> assets = portfolio.assets();
        List<String> tickers = assets.stream().map(Asset::ticker).toList();
        Map<LocalDate, Map<String, Double>> priceData = repository.fetchClosingPrices(tickers, tradingDates);

        for (int i = 0; i < tradingDates.size(); i++) {
            LocalDate date = tradingDates.get(i);
            Map<String, Double> pricesOnDate = priceData.getOrDefault(date, Collections.emptyMap());

            double weightedSum = 0.0;
            for (Asset asset : assets) {
                Double price = pricesOnDate.get(asset.ticker());
                if (price != null) {
                    weightedSum += price * asset.weight();
                }
            }

            dailyValues[i] = weightedSum;
        }
    }

    private void calculateReturns() {
        for (int i = 1; i < tradingDates.size(); i++) {
            double previousValue = dailyValues[i - 1];
            double currentValue = dailyValues[i];

            double dailyReturn = (currentValue - previousValue) / previousValue;
            dailyReturns[i - 1] = dailyReturn;
        }
    }

    private void calculateMetrics() {
        double cumulativeProduct = 1.0;

        for (double dailyReturn : dailyReturns) {
            cumulativeProduct *= (1 + dailyReturn);
        }

        cumulativeReturn = cumulativeProduct - 1;

        StandardDeviation stdDev = new StandardDeviation();
        Mean mean = new Mean();

        meanReturn = mean.evaluate(dailyReturns);
        volatility = stdDev.evaluate(dailyReturns);

        double dailyRiskFreeRate = Math.pow(1 + ANNUAL_RISK_FREE_RATE, 1.0 / NUM_TRADING_DAYS) - 1;
        sharpe = (meanReturn - dailyRiskFreeRate) / volatility * Math.pow(NUM_TRADING_DAYS, 0.5);
    }

    private void calculateMonteCarloValueAtRisk(double confidenceLevel, int numSimulations) {
        NormalDistribution distribution = new NormalDistribution(meanReturn, volatility);
        double[] simulatedLosses = new double[numSimulations];

        for (int i = 0; i < numSimulations; i++) {
            double simulatedReturn = distribution.sample();
            double simulatedPortfolioValue = portfolio.currentValue() * (1 + simulatedReturn);
            double loss = portfolio.currentValue() - simulatedPortfolioValue;
            simulatedLosses[i] = loss;
        }

        Arrays.sort(simulatedLosses);
        int varIndex = (int) Math.floor((1 - confidenceLevel) * numSimulations);

        valueAtRisk = simulatedLosses[varIndex];
    }
}

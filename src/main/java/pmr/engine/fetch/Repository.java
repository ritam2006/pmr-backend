package pmr.engine.fetch;

import pmr.engine.model.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

public class Repository {
    private final String dbUrl;
    private final String user;
    private final String pass;

    public Repository(String dbUrl, String user, String pass) {
        this.dbUrl = dbUrl;
        this.user = user;
        this.pass = pass;
    }

    public List<LocalDate> fetchTradingDates() {
        List<LocalDate> tradingDays = new ArrayList<>();
        String query = "SELECT DISTINCT date::date FROM historical_prices ORDER BY date::date ASC";

        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                tradingDays.add(rs.getDate(1).toLocalDate());
            }

        } catch (SQLException e) {
            throw new RuntimeException("Database error in fetchTradingDates", e);
        }

        return tradingDays;
    }

    public Map<LocalDate, Map<String, Double>> fetchClosingPrices(List<String> tickers, List<LocalDate> dates) {
        Map<LocalDate, Map<String, Double>> prices = new HashMap<>();

        String query = """
            SELECT date::date, ticker, close
            FROM historical_prices
            WHERE ticker = ANY (?) AND date::date = ANY (?)
        """;

        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            Array sqlTickers = conn.createArrayOf("VARCHAR", tickers.toArray());
            Array sqlDates = conn.createArrayOf("DATE", dates.toArray());

            stmt.setArray(1, sqlTickers);
            stmt.setArray(2, sqlDates);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = rs.getDate("date").toLocalDate();
                    String ticker = rs.getString("ticker");
                    double close = rs.getDouble("close");

                    prices.computeIfAbsent(date, d -> new HashMap<>()).put(ticker, close);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Database error in fetchClosingPrices", e);
        }

        return prices;
    }

    public String[] fetchTickers() {
        List<String> tickers = new ArrayList<>();

        String query = "SELECT ticker FROM assets LIMIT 50";

        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                tickers.add(rs.getString("ticker"));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Database error in fetchTickers", e);
        }

        return tickers.toArray(new String[0]);
    }

    public int savePortfolio(
            String name,
            long userId,
            List<LocalDate> tradingDates,
            double[] dailyValues,
            double[] dailyReturns,
            double cumulativeReturn,
            double meanReturn,
            double volatility,
            double sharpeRatio,
            double valueAtRisk,
            List<Asset> assets
    ) {
        String query = """
            INSERT INTO portfolios (
                name, user_id, trading_dates, daily_values, daily_returns,
                cumulative_return, mean_return, volatility,
                sharpe_ratio, value_at_risk, assets
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
         PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            Array sqlDates = conn.createArrayOf("DATE",
                    tradingDates.stream().map(Date::valueOf).toArray());

            Array sqlDailyValues = conn.createArrayOf("FLOAT8", toObjectArray(dailyValues));
            Array sqlDailyReturns = conn.createArrayOf("FLOAT8", toObjectArray(dailyReturns));

            ObjectMapper mapper = new ObjectMapper();
            String jsonAssets = mapper.writeValueAsString(assets);

            stmt.setString(1, name);
            stmt.setLong(2, userId);
            stmt.setArray(3, sqlDates);
            stmt.setArray(4, sqlDailyValues);
            stmt.setArray(5, sqlDailyReturns);
            stmt.setDouble(6, cumulativeReturn);
            stmt.setDouble(7, meanReturn);
            stmt.setDouble(8, volatility);
            stmt.setDouble(9, sharpeRatio);
            stmt.setDouble(10, valueAtRisk);
            stmt.setObject(11, jsonAssets, java.sql.Types.OTHER);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating portfolio failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating portfolio failed, no ID obtained.");
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Database error in savePortfolio", e);
        }
    }

    public PortfolioAnalysisResult fetchPortfolio(int id) {
        String query = """
            SELECT p.name, p.user_id, p.trading_dates, p.daily_values, p.daily_returns,
                   p.cumulative_return, p.mean_return, p.volatility,
                   p.sharpe_ratio, p.value_at_risk, p.assets, u.username
            FROM portfolios p
            JOIN users u ON p.user_id = u.id
            WHERE p.id = ?
        """;

        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String name = rs.getString("name");
                long userId = rs.getLong("user_id");
                String username = rs.getString("username");

                Array dateArray = rs.getArray("trading_dates");
                LocalDate[] tradingDates = Arrays.stream((Date[]) dateArray.getArray())
                        .map(Date::toLocalDate)
                        .toArray(LocalDate[]::new);

                Double[] dailyValuesBoxed = (Double[]) rs.getArray("daily_values").getArray();
                double[] dailyValues = Arrays.stream(dailyValuesBoxed).mapToDouble(Double::doubleValue).toArray();

                Double[] dailyReturnsBoxed = (Double[]) rs.getArray("daily_returns").getArray();
                double[] dailyReturns = Arrays.stream(dailyReturnsBoxed).mapToDouble(Double::doubleValue).toArray();

                double cumulativeReturn = rs.getDouble("cumulative_return");
                double meanReturn = rs.getDouble("mean_return");
                double volatility = rs.getDouble("volatility");
                double sharpe = rs.getDouble("sharpe_ratio");
                double var = rs.getDouble("value_at_risk");

                String jsonAssets = rs.getString("assets");
                ObjectMapper mapper = new ObjectMapper();
                Asset[] assets = mapper.readValue(jsonAssets, Asset[].class);

                PortfolioAnalysisResult result = new PortfolioAnalysisResult(
                        name,
                        userId,
                        username,
                        Arrays.asList(tradingDates),
                        dailyValues,
                        dailyReturns,
                        cumulativeReturn,
                        meanReturn,
                        volatility,
                        sharpe,
                        var,
                        assets
                );

                return result;
            } else {
                throw new RuntimeException("Portfolio with ID " + id + " not found");
            }

        } catch (Exception e) {
            throw new RuntimeException("Database error in fetchPortfolio", e);
        }
    }

    public PortfolioLeaderboardEntry[] fetchLeaderboardEntries() {
        String query = """
            SELECT p.id, p.name, p.sharpe_ratio, p.trading_dates, u.username
            FROM portfolios p
            JOIN users u ON p.user_id = u.id
            ORDER BY sharpe_ratio DESC LIMIT 50
        """;

        List<PortfolioLeaderboardEntry> leaderboard = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String portfolioName = rs.getString("name");
                double sharpeRatio = rs.getDouble("sharpe_ratio");
                String username = rs.getString("username");

                Array sqlDates = rs.getArray("trading_dates");
                LocalDate[] tradingDates = Arrays.stream((Object[]) sqlDates.getArray())
                        .map(d -> ((Date) d).toLocalDate())
                        .toArray(LocalDate[]::new);

                if (tradingDates.length == 0) continue;

                LocalDate startDate = tradingDates[0];
                LocalDate endDate = tradingDates[tradingDates.length - 1];

                leaderboard.add(new PortfolioLeaderboardEntry(
                        id,
                        username,
                        portfolioName,
                        sharpeRatio,
                        startDate,
                        endDate
                ));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Database error in fetchLeaderboardEntries", e);
        }

        return leaderboard.toArray(new PortfolioLeaderboardEntry[0]);
    }

    public PortfolioSummary[] fetchUserPortfolios(long userId) {
        String query = """
            SELECT id, name, assets, trading_dates, sharpe_ratio, value_at_risk
            FROM portfolios
            WHERE user_id = ?
            ORDER BY created_at DESC
        """;

        List<PortfolioSummary> results = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");

                    String jsonAssets = rs.getString("assets");
                    ObjectMapper mapper = new ObjectMapper();
                    Asset[] assets = mapper.readValue(jsonAssets, Asset[].class);

                    Array dateArray = rs.getArray("trading_dates");
                    LocalDate[] tradingDates = Arrays.stream((Date[]) dateArray.getArray())
                            .map(Date::toLocalDate)
                            .toArray(LocalDate[]::new);

                    if (tradingDates.length == 0) continue;

                    LocalDate startDate = tradingDates[0];
                    LocalDate endDate = tradingDates[tradingDates.length - 1];

                    double sharpe = rs.getDouble("sharpe_ratio");
                    double valueAtRisk = rs.getDouble("value_at_risk");

                    PortfolioSummary result = new PortfolioSummary(
                            name,
                            id,
                            assets,
                            startDate,
                            endDate,
                            sharpe,
                            valueAtRisk
                    );

                    results.add(result);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Database error in fetchUserPortfolios", e);
        }

        return results.toArray(new PortfolioSummary[0]);
    }

    public AccountData fetchAccountData(long userId) {
        String query = """
            SELECT COUNT(*) AS count, COALESCE(MAX(sharpe_ratio), 0) AS best_sharpe
            FROM portfolios
            WHERE user_id = ?
        """;

        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    double bestSharpe = rs.getDouble("best_sharpe");
                    return new AccountData(count, bestSharpe);
                } else {
                    return new AccountData(0, 0.0);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Database error in fetchUserPortfolioStats", e);
        }
    }

    private static Double[] toObjectArray(double[] input) {
        Double[] output = new Double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
}

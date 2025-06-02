package pmr.engine.service;

import okhttp3.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.*;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class MarketDataFetcher {
    private final OkHttpClient client;
    private final String db_url;
    private final String db_user;
    private final String db_pass;
    private final String api_key;

    public MarketDataFetcher() {
        client = new OkHttpClient();
        db_url = System.getenv("DB_URL");
        db_user = System.getenv("DB_USER");
        db_pass = System.getenv("DB_PASS");
        api_key = System.getenv("POLYGON_API_KEY");
    }

    private List<String> fetchTickers() {
        List<String> tickers = new ArrayList<>();
        String query = "SELECT ticker FROM assets LIMIT 50";
        try (Connection conn = DriverManager.getConnection(db_url, db_user, db_pass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

             while (rs.next()) {
                 tickers.add(rs.getString("ticker"));
             }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return tickers;
    }

    private void fetchAndStoreHistoricalPrices(String ticker) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String url = String.format(
                "https://api.polygon.io/v2/aggs/ticker/%s/range/1/day/%s/%s?adjusted=true&sort=asc&limit=1&apiKey=%s",
                ticker,
                yesterday.format(formatter),
                yesterday.format(formatter),
                api_key
        );

        try {
            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                System.err.println("HTTP error for " + ticker + ": " + response.code() + " - " + response.message());
                return;
            }

            String json = response.body().string();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            if (!root.has("results") || !root.get("results").isArray() || root.get("results").isEmpty()) {
                System.out.println("No trading data for " + ticker + " on " + yesterday + " — skipping.");
                return;
            }

            JsonNode result = root.get("results").get(0);
            long timestampMillis = result.get("t").asLong();
            double close = result.get("c").asDouble();
            LocalDate date = Instant.ofEpochMilli(timestampMillis).atZone(ZoneOffset.UTC).toLocalDate();

            try (Connection conn = DriverManager.getConnection(db_url, db_user, db_pass)) {
                conn.setAutoCommit(false);

                String insertQuery = "INSERT INTO historical_prices (ticker, date, close) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                    insertStmt.setString(1, ticker);
                    insertStmt.setDate(2, java.sql.Date.valueOf(date));
                    insertStmt.setDouble(3, close);
                    insertStmt.executeUpdate();
                }

                String countQuery = "SELECT COUNT(*) FROM historical_prices WHERE ticker = ?";
                try (PreparedStatement countStmt = conn.prepareStatement(countQuery)) {
                    countStmt.setString(1, ticker);
                    ResultSet rs = countStmt.executeQuery();
                    if (rs.next() && rs.getInt(1) > 365) {
                        String deleteQuery = "DELETE FROM historical_prices WHERE (ticker, date) IN (" +
                                "SELECT ticker, date FROM historical_prices WHERE ticker = ? ORDER BY date ASC LIMIT 1)";
                        try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery)) {
                            deleteStmt.setString(1, ticker);
                            deleteStmt.executeUpdate();
                            System.out.println("Deleted oldest record for " + ticker);
                        }
                    }
                }

                conn.commit();
                System.out.println("Saved recent market data for " + ticker + " on " + date);

            } catch (SQLException dbException) {
                System.err.println("Database error for fetching market data for " + ticker + ": " + dbException.getMessage());
            }

        } catch (IOException | NullPointerException e) {
            System.err.println("Database error for fetching or parsing market data for " + ticker + ": " + e.getMessage());
        }
    }

    public void fetchMarketData() {
        List<String> tickers = fetchTickers();
        for (String ticker : tickers) {
            fetchAndStoreHistoricalPrices(ticker);

            try {
                Thread.sleep(12000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
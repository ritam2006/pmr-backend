package pmr.engine.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pmr.engine.service.MarketDataFetcher;

@Component
public class MarketDataScheduler {
    private final MarketDataFetcher marketDataFetcher;

    public MarketDataScheduler(MarketDataFetcher marketDataFetcher) {
        this.marketDataFetcher = marketDataFetcher;
    }

    @Scheduled(cron = "0 5 6 * * *", zone = "UTC")
    public void updateMarketDataDaily() {
        marketDataFetcher.fetchMarketData();
    }
}

package pmr.engine.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pmr.engine.service.MarketDataFetcher;

@RestController
@RequestMapping("/market")
public class MarketDataController {
    private final MarketDataFetcher marketDataFetcher;

    public MarketDataController(MarketDataFetcher marketDataFetcher) {
        this.marketDataFetcher = marketDataFetcher;
    }

    @PostMapping("/update")
    public ResponseEntity<String> updateMarketData(@RequestHeader("Authorization") String authHeader) {
        if (!authHeader.equals("Bearer " + System.getenv("POLYGON_API_KEY"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        marketDataFetcher.fetchMarketData();
        return ResponseEntity.ok("Market data update triggered");
    }
}

package pmr.engine.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import pmr.engine.analysis.PortfolioAnalyzer;
import pmr.engine.service.Repository;
import pmr.engine.model.*;

@RestController
public class MainController {
    private final Repository repository;

    public MainController(Repository repository) {
        this.repository = repository;
    }

    @GetMapping("/health")
    public String healthCheck() {
        return "OK";
    }

    @PostMapping("/analyze")
    public int analyze(@RequestBody Portfolio portfolio) {
        long userId = getAuthenticatedUserId();
        PortfolioAnalyzer analyzer = new PortfolioAnalyzer(userId, portfolio, repository);
        int id = analyzer.analyze();
        return id;
    }

    @PostMapping("/fetchPortfolio")
    public PortfolioAnalysisResult fetchPortfolio(@RequestBody int id) {
        return repository.fetchPortfolio(id);
    }

    @GetMapping("/tickers")
    public String[] fetchTickers() {
        return repository.fetchTickers();
    }

    @GetMapping("/userPortfolios")
    public PortfolioSummary[] fetchUserPortfolios() {
        long userId = getAuthenticatedUserId();
        return repository.fetchUserPortfolios(userId);
    }

    @GetMapping("/accountData")
    public AccountData fetchAccountData() {
        long userId = getAuthenticatedUserId();
        return repository.fetchAccountData(userId);
    }

    @GetMapping("/leaderboard")
    public PortfolioLeaderboardEntry[] fetchLeaderboard() {
        return repository.fetchLeaderboardEntries();
    }

    private long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        return userDetails.getId();
    }
}

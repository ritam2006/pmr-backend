package pmr.engine.model;

public record Asset(String ticker, double weight) {
    public String toString() {
        return ticker + ": " + weight;
    }
}

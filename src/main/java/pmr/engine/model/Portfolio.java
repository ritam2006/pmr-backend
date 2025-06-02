package pmr.engine.model;

import java.util.List;

public record Portfolio(String name, int currentValue, List<Asset> assets) {
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append("\n");

        for (Asset asset : assets) {
            sb.append(asset);
            sb.append("\n");
        }

        return sb.toString();
    }
}

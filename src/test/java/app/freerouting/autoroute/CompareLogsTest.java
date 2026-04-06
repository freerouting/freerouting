package app.freerouting.autoroute;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class CompareLogsTest {
    @Test
    public void compareLogs() throws Exception {
        String curFile = "C:/Work/freerouting/logs/freerouting-current.log";
        String v19File = "C:/Work/freerouting/logs/freerouting-v190.log";

        List<String> curLines = readAndFilter(curFile);
        List<String> v19Lines = readAndFilter(v19File);

        int min = Math.min(curLines.size(), v19Lines.size());
        for (int i = 0; i < min; i++) {
            if (!curLines.get(i).equals(v19Lines.get(i))) {
                String ctx = String.join("\n", curLines.subList(Math.max(0, i-3), i));
                String err = "Mismatch at index " + i + "!\nContext:\n" + ctx + "\n\nv1.9 : " + v19Lines.get(i) + "\nCurr : " + curLines.get(i);
                java.nio.file.Files.writeString(java.nio.file.Paths.get("C:/Work/freerouting/divergence.log"), err);
                throw new AssertionError(err);
            }
        }

        if (curLines.size() != v19Lines.size()) {
            java.nio.file.Files.writeString(java.nio.file.Paths.get("C:/Work/freerouting/divergence.log"), "Sizes differ! Cur: " + curLines.size() + ", V19: " + v19Lines.size());
            throw new AssertionError("Sizes differ! Cur: " + curLines.size() + ", V19: " + v19Lines.size());
        }
        java.nio.file.Files.writeString(java.nio.file.Paths.get("C:/Work/freerouting/divergence.log"), "MATCH!");
    }

    private List<String> readAndFilter(String path) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("compare_trace_")) {
                    String norm = line.substring(line.lastIndexOf(']') + 1).trim();
                    if (!norm.startsWith("compare_trace_")) continue;
                    if (norm.startsWith("compare_trace_split") || norm.startsWith("compare_trace_route_item") || norm.startsWith("compare_trace_ripped_item")) continue;
                    norm = norm.replaceAll("expansion_value=[\\d\\.]+", "ev");
                    norm = norm.replaceAll("sorting_value=[\\d\\.]+", "sv");
                    norm = norm.replaceAll("test_level=[^,:]+", "");
                    norm = norm.replaceAll("trace_enabled=[^,:]+", "");
                    lines.add(norm);
                }
            }
        }
        return lines;
    }
}

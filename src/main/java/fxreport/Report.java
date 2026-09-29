package fxreport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 报表渲染。
 *
 * <p>把一批 {@link Entry} 渲染成一份**逐字节确定**的报表文本。格式与语义见 README
 * 「对外契约」一节。
 */
public final class Report {

    /**
     * 渲染报表。
     *
     * @param entries 流水；顺序任意，允许为空
     * @return 报表文本
     */
    public static String render(List<Entry> entries) {
        Map<LocalDate, Map<String, BigDecimal>> groups = new TreeMap<>();
        for (Entry entry : entries) {
            groups.computeIfAbsent(entry.date(), date -> new TreeMap<String, BigDecimal>())
                    .merge(entry.accountId(), entry.amount(), BigDecimal::add);
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

        StringBuilder out = new StringBuilder();
        out.append("date\taccount\tamount").append('\n');
        out.append("-".repeat(40)).append('\n');

        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<LocalDate, Map<String, BigDecimal>> byDate : groups.entrySet()) {
            String date = dateFormatter.format(byDate.getKey());
            for (Map.Entry<String, BigDecimal> byAccount : byDate.getValue().entrySet()) {
                BigDecimal rounded = byAccount.getValue().setScale(2, RoundingMode.HALF_EVEN);
                out.append(date).append('\t')
                        .append(byAccount.getKey()).append('\t')
                        .append(rounded.toPlainString()).append('\n');
                total = total.add(rounded);
            }
        }

        out.append("-".repeat(40)).append('\n');
        out.append("TOTAL\t").append(total.toPlainString()).append('\n');
        return out.toString();
    }

    private Report() {
    }
}

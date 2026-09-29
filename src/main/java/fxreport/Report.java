package fxreport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 报表渲染。
 *
 * <p>把一批 {@link Entry} 渲染成一份**逐字节确定**的报表文本。格式与语义见 README
 * 「对外契约」一节。
 */
public final class Report {

    private static final String HEADER = "date\taccount\tamount\n";
    private static final String SEPARATOR = "-".repeat(40) + "\n";
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    /**
     * 渲染报表。
     *
     * @param entries 流水；顺序任意，允许为空
     * @return 报表文本
     */
    public static String render(List<Entry> entries) {
        Map<LocalDate, TreeMap<String, BigDecimal>> grouped = new TreeMap<>();
        for (Entry entry : entries) {
            grouped.computeIfAbsent(entry.date(), ignored -> new TreeMap<>())
                    .merge(entry.accountId(), entry.amount(), BigDecimal::add);
        }

        StringBuilder report = new StringBuilder(HEADER);
        report.append(SEPARATOR);

        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<LocalDate, TreeMap<String, BigDecimal>> dateEntry : grouped.entrySet()) {
            String date = dateEntry.getKey().format(DATE_FORMAT);
            for (Map.Entry<String, BigDecimal> accountEntry : dateEntry.getValue().entrySet()) {
                BigDecimal amount = accountEntry.getValue().setScale(2, RoundingMode.HALF_EVEN);
                total = total.add(amount);
                report.append(date)
                        .append('\t')
                        .append(accountEntry.getKey())
                        .append('\t')
                        .append(amount.toPlainString())
                        .append('\n');
            }
        }

        report.append(SEPARATOR);
        report.append("TOTAL\t")
                .append(total.toPlainString())
                .append('\n');
        return report.toString();
    }

    private Report() {
    }
}

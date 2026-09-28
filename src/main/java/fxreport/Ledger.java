package fxreport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 流水读入：把一段文本解析成 {@link Entry} 列表。
 *
 * <p>每行一条流水，字段之间用一个制表符（{@code \t}）分隔：
 * {@code <yyyy-MM-dd>\t<accountId>\t<amount>}。空行忽略；以 {@code #} 开头的行是注释。
 */
public final class Ledger {

    /** 解析流水文本；格式不对的行抛 {@link IllegalArgumentException}。 */
    public static List<Entry> parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text 不能为 null");
        }
        List<Entry> entries = new ArrayList<>();
        int lineNo = 0;
        for (String raw : text.split("\n", -1)) {
            lineNo++;
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("第 " + lineNo + " 行应有 3 个制表符分隔的字段：" + line);
            }
            LocalDate date;
            try {
                date = LocalDate.parse(parts[0]);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("第 " + lineNo + " 行的日期不是 yyyy-MM-dd：" + parts[0]);
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(parts[2]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("第 " + lineNo + " 行的金额不是十进制数：" + parts[2]);
            }
            entries.add(new Entry(date, parts[1], amount));
        }
        return entries;
    }

    private Ledger() {
    }
}

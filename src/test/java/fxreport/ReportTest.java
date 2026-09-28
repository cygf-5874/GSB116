package fxreport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * fxreport 的既有用例（12 个），覆盖 README「对外契约」的格式与语义。
 *
 * <p>不依赖 JUnit：静态方法加 {@code main} 聚合自跑，全部通过时打印 {@code 12/12 passed}
 * 并以退出码 0 结束；有失败时打印失败清单并以退出码 1 结束。
 */
public final class ReportTest {

    private static final String HEADER = "date\taccount\tamount";
    private static final String SEP = "-".repeat(40);

    private interface Body {
        /** 返回 null 表示通过，否则返回失败原因。 */
        String run();
    }

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        run("empty-header-and-total", ReportTest::testEmpty);
        run("single-row", ReportTest::testSingleRow);
        run("sorted-by-account-codepoint", ReportTest::testSortedByAccount);
        run("sorted-by-date", ReportTest::testSortedByDate);
        run("grouped-by-date-and-account", ReportTest::testGrouped);
        run("half-even-on-raw-group-sum", ReportTest::testHalfEven);
        run("total-is-sum-of-rounded-rows", ReportTest::testTotal);
        run("header-line-exact", ReportTest::testHeaderExact);
        run("separator-line-exact", ReportTest::testSeparatorExact);
        run("only-lf-line-endings", ReportTest::testOnlyLf);
        run("repeated-render-identical", ReportTest::testRepeated);
        run("locale-independent-en-vs-de", ReportTest::testLocaleIndependent);

        System.out.println(passed + "/12 passed");
        if (!failures.isEmpty()) {
            for (String f : failures) {
                System.out.println("FAIL " + f);
            }
            System.exit(1);
        }
    }

    private static void run(String name, Body body) {
        try {
            String err = body.run();
            if (err == null) {
                passed++;
            } else {
                failures.add(name + "：" + err);
            }
        } catch (Throwable t) {
            failures.add(name + "：抛出 " + t);
        }
    }

    // ------------------------------------------------------------------ 用例

    private static String testEmpty() {
        List<String> lines = lines(Report.render(List.of()));
        List<String> want = List.of(HEADER, SEP, SEP, "TOTAL\t0.00");
        if (!want.equals(lines)) {
            return "期望=空流水只剩表头、分隔线与合计 0.00 → " + want + " 实际=" + lines;
        }
        return null;
    }

    private static String testSingleRow() {
        List<String> lines = lines(Report.render(List.of(e("2024-01-02", "Alpha", "10.00"))));
        return expectLine("单行报表", lines, 2, "2024-01-02\tAlpha\t10.00");
    }

    private static String testSortedByAccount() {
        List<Entry> entries = List.of(
                e("2024-01-02", "alpha", "3.00"),
                e("2024-01-02", "Beta", "2.00"),
                e("2024-01-02", "Alpha", "1.00"));
        List<String> lines = lines(Report.render(entries));
        List<String> want = List.of(
                HEADER, SEP,
                "2024-01-02\tAlpha\t1.00",
                "2024-01-02\tBeta\t2.00",
                "2024-01-02\talpha\t3.00",
                SEP, "TOTAL\t6.00");
        if (!want.equals(lines)) {
            return "期望=同一天按账户码点序（大写字母在前）→ " + want + " 实际=" + lines;
        }
        return null;
    }

    private static String testSortedByDate() {
        List<Entry> entries = List.of(
                e("2024-03-05", "A", "1.00"),
                e("2024-01-02", "A", "2.00"),
                e("2024-02-01", "A", "3.00"));
        List<String> lines = lines(Report.render(entries));
        List<String> want = List.of(
                HEADER, SEP,
                "2024-01-02\tA\t2.00",
                "2024-02-01\tA\t3.00",
                "2024-03-05\tA\t1.00",
                SEP, "TOTAL\t6.00");
        if (!want.equals(lines)) {
            return "期望=按日期升序 → " + want + " 实际=" + lines;
        }
        return null;
    }

    private static String testGrouped() {
        List<Entry> entries = List.of(
                e("2024-01-02", "Alpha", "1.10"),
                e("2024-01-02", "Alpha", "2.20"),
                e("2024-01-02", "Beta", "0.50"));
        List<String> lines = lines(Report.render(entries));
        List<String> want = List.of(
                HEADER, SEP,
                "2024-01-02\tAlpha\t3.30",
                "2024-01-02\tBeta\t0.50",
                SEP, "TOTAL\t3.80");
        if (!want.equals(lines)) {
            return "期望=(日期, 账户) 相同的流水合并成一行 → " + want + " 实际=" + lines;
        }
        return null;
    }

    private static String testHalfEven() {
        // 1.005 + 2.005 = 3.010 → 对原始和做一次 setScale(2, HALF_EVEN) = 3.01
        List<Entry> entries = List.of(
                e("2024-01-05", "Even", "1.005"),
                e("2024-01-05", "Even", "2.005"),
                e("2024-01-05", "Half", "0.125"));
        List<String> lines = lines(Report.render(entries));
        List<String> want = List.of(
                HEADER, SEP,
                "2024-01-05\tEven\t3.01",
                "2024-01-05\tHalf\t0.12",
                SEP, "TOTAL\t3.13");
        if (!want.equals(lines)) {
            return "期望=组内原始和一次 HALF_EVEN → " + want + " 实际=" + lines;
        }
        return null;
    }

    private static String testTotal() {
        // 两行的原始和分别是 1.004 与 2.004 → 各自舍入成 1.00 / 2.00 → 合计 3.00
        List<Entry> entries = List.of(
                e("2024-02-01", "A", "0.502"),
                e("2024-02-01", "A", "0.502"),
                e("2024-02-02", "B", "1.002"),
                e("2024-02-02", "B", "1.002"));
        List<String> lines = lines(Report.render(entries));
        List<String> want = List.of(
                HEADER, SEP,
                "2024-02-01\tA\t1.00",
                "2024-02-02\tB\t2.00",
                SEP, "TOTAL\t3.00");
        if (!want.equals(lines)) {
            return "期望=合计等于各行（已舍入）之和 → " + want + " 实际=" + lines;
        }
        return null;
    }

    private static String testHeaderExact() {
        String out = Report.render(List.of(e("2024-01-02", "A", "1.00")));
        if (!out.startsWith(HEADER + "\n")) {
            return "期望=首行是 \"" + HEADER + "\" 实际=首行是 \"" + firstLine(out) + "\"";
        }
        return null;
    }

    private static String testSeparatorExact() {
        String out = Report.render(List.of(e("2024-01-02", "A", "1.00")));
        List<String> lines = lines(out);
        if (lines.size() < 3) {
            return "期望=至少 3 行 实际=" + lines.size() + " 行";
        }
        if (!SEP.equals(lines.get(1))) {
            return "期望=第 2 行是 40 个 '-' 实际=\"" + lines.get(1) + "\"";
        }
        if (!SEP.equals(lines.get(lines.size() - 2))) {
            return "期望=合计行前一行是 40 个 '-' 实际=\"" + lines.get(lines.size() - 2) + "\"";
        }
        return null;
    }

    private static String testOnlyLf() {
        String out = Report.render(List.of(e("2024-01-02", "A", "1.00")));
        if (out.contains("\r")) {
            return "期望=行尾只用 \\n 实际=出现 \\r";
        }
        if (!out.endsWith("\n")) {
            return "期望=输出以 \\n 结尾 实际=结尾是 \"" + out.substring(Math.max(0, out.length() - 3)) + "\"";
        }
        return null;
    }

    private static String testRepeated() {
        List<Entry> entries = List.of(
                e("2024-01-02", "Alpha", "1.10"),
                e("2024-01-03", "Beta", "-2.35"));
        String first = Report.render(entries);
        String second = Report.render(entries);
        if (!first.equals(second)) {
            return "期望=同一输入两次渲染一致 实际=不一致";
        }
        return null;
    }

    private static String testLocaleIndependent() {
        List<Entry> entries = List.of(
                e("2024-01-02", "Istanbul", "1234.56"),
                e("2024-01-02", "istanbul", "2345.67"));
        Locale oldLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("en-US"));
            String en = Report.render(entries);
            Locale.setDefault(Locale.forLanguageTag("de-DE"));
            String de = Report.render(entries);
            if (!en.equals(de)) {
                return "期望=en-US 与 de-DE 渲染逐字节相同 实际=不同";
            }
            return null;
        } finally {
            Locale.setDefault(oldLocale);
        }
    }

    // ------------------------------------------------------------------ 工具

    private static Entry e(String date, String account, String amount) {
        return new Entry(LocalDate.parse(date), account, new BigDecimal(amount));
    }

    private static List<String> lines(String out) {
        if (out.isEmpty()) {
            return List.of();
        }
        String body = out.endsWith("\n") ? out.substring(0, out.length() - 1) : out;
        return List.of(body.split("\n", -1));
    }

    private static String firstLine(String out) {
        int idx = out.indexOf('\n');
        return idx < 0 ? out : out.substring(0, idx);
    }

    private static String expectLine(String what, List<String> lines, int idx, String wanted) {
        if (lines.size() <= idx) {
            return "期望=" + what + " 第 " + (idx + 1) + " 行是 \"" + wanted + "\" 实际=只有 " + lines.size() + " 行";
        }
        if (!wanted.equals(lines.get(idx))) {
            return "期望=" + what + " 第 " + (idx + 1) + " 行是 \"" + wanted + "\" 实际=\"" + lines.get(idx) + "\"";
        }
        return null;
    }

    private ReportTest() {
    }
}

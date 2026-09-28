import fxreport.Entry;
import fxreport.Report;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * fxreport 的固定验收程序（勿改）。
 *
 * <p>9 个场景覆盖 README「对外契约」：locale 3 / round 2 / order 2 / eol 1 / stable 1。
 * 每个场景都在**独立 JVM 子进程**里跑（{@link ProcessBuilder}），带看门狗，超时判不通过；
 * 某个场景失败不会遮蔽其余场景（失败不早退）。
 *
 * <p>判据全部确定性：没有随机源、没有墙钟、没有性能门槛。locale 组会在子进程里把
 * 默认 Locale 设成 en-US / de-DE / tr-TR、默认 TimeZone 设成三个不同的时区，
 * 三次渲染的结果必须逐字节相同 —— 这也顺带覆盖了「进程重启后重新调用结果相同」。
 *
 * <p>用法：java -cp out;out-check Checker [-list] [--only &lt;组名&gt;[,&lt;组名&gt;...]]
 */
public final class Checker {

    /** 单场景看门狗（秒）。 */
    private static final long WATCHDOG_SECONDS = 30L;

    /** 固定表头与分隔线。 */
    private static final String HEADER = "date\taccount\tamount";
    private static final String SEP = "-".repeat(40);

    private interface Body {
        /** 返回 null 表示 PASS，否则返回可判定的失败原因（内含 期望=… 实际=…）。 */
        String run() throws Exception;
    }

    private static final class Scenario {
        final String name;
        final String group;
        final Body body;

        Scenario(String name, Body body) {
            this.name = name;
            this.body = body;
            int slash = name.indexOf('/');
            this.group = slash < 0 ? name : name.substring(0, slash);
        }
    }

    public static void main(String[] args) throws Exception {
        String scenarioOnly = null;
        List<String> onlyGroups = new ArrayList<>();
        boolean list = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("-list".equals(a)) {
                list = true;
            } else if ("--only".equals(a)) {
                if (i + 1 >= args.length) {
                    System.out.println("--only 需要一个组名");
                    System.exit(2);
                }
                for (String g : args[++i].split(",")) {
                    if (!g.isEmpty()) {
                        onlyGroups.add(g);
                    }
                }
            } else if ("--scenario".equals(a)) {
                if (i + 1 >= args.length) {
                    System.out.println("--scenario 需要一个场景名");
                    System.exit(2);
                }
                scenarioOnly = args[++i];
            } else {
                System.out.println("未知参数：" + a);
                System.exit(2);
            }
        }

        List<Scenario> all = scenarios();

        if (list) {
            for (Scenario s : all) {
                System.out.println(s.name);
            }
            return;
        }

        if (scenarioOnly != null) {
            System.exit(runOne(all, scenarioOnly));
        }

        List<Scenario> selected = new ArrayList<>();
        for (Scenario s : all) {
            if (onlyGroups.isEmpty() || onlyGroups.contains(s.group)) {
                selected.add(s);
            }
        }
        if (selected.isEmpty()) {
            System.out.println("没有匹配的场景（--only " + onlyGroups + "）");
            System.exit(2);
        }

        int pass = 0;
        for (Scenario s : selected) {
            String line = runInChildProcess(s);
            System.out.println(line);
            if (line.startsWith("PASS ")) {
                pass++;
            }
        }
        System.out.println("结果：通过 " + pass + "/" + selected.size());
        if (pass != selected.size()) {
            System.exit(1);
        }
    }

    // ------------------------------------------------- 子进程调度（一场景一 JVM）

    private static String runInChildProcess(Scenario s) {
        String name = s.name;
        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        ProcessBuilder pb = new ProcessBuilder(javaBin,
                "-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8",
                "-cp", classpath, "Checker", "--scenario", name);
        pb.redirectErrorStream(true);

        Process proc;
        try {
            proc = pb.start();
        } catch (Exception ex) {
            return "FAIL " + name + "  期望=能在独立 JVM 子进程里跑该场景 实际=子进程启动失败：" + ex;
        }

        StringBuilder sink = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (InputStream in = proc.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                    synchronized (sink) {
                        sink.append(chunk);
                    }
                }
            } catch (Exception ignored) {
                // 子进程被强杀时读流会失败，忽略即可
            }
        }, "checker-child-reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished;
        try {
            finished = proc.waitFor(WATCHDOG_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        if (!finished) {
            proc.destroyForcibly();
            try {
                reader.join(2000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return "FAIL " + name + "  期望=场景在 " + WATCHDOG_SECONDS + " 秒内给出 PASS/FAIL 实际="
                    + WATCHDOG_SECONDS + " 秒看门狗超时（子进程已强杀）";
        }
        try {
            reader.join(5000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        String out;
        synchronized (sink) {
            out = sink.toString().trim();
        }
        String line = null;
        for (String l : out.split("\n")) {
            String t = l.trim();
            if (t.startsWith("PASS ") || t.startsWith("FAIL ")) {
                line = t;
            }
        }
        if (line == null) {
            String shown = out.isEmpty() ? "(无输出)" : out.replace('\n', ' ');
            return "FAIL " + name + "  期望=子进程打印 PASS/FAIL 行 实际=退出码 "
                    + (proc.isAlive() ? "未知（仍存活）" : String.valueOf(proc.exitValue())) + "，输出=" + shown;
        }
        return line;
    }

    private static int runOne(List<Scenario> all, String name) {
        for (Scenario s : all) {
            if (!s.name.equals(name)) {
                continue;
            }
            String err;
            try {
                err = s.body.run();
            } catch (Throwable t) {
                err = "检查过程抛异常：" + t;
            }
            if (err == null) {
                System.out.println("PASS " + s.name);
                return 0;
            }
            if (!err.contains("期望=")) {
                err = "期望=场景 " + s.name + " 通过 实际=" + err;
            }
            System.out.println("FAIL " + s.name + "  " + err);
            return 1;
        }
        System.out.println("FAIL " + name + "  期望=场景名存在于固定件 实际=未找到该场景");
        return 1;
    }

    // ------------------------------------------------------------- 场景表

    private static List<Scenario> scenarios() {
        List<Scenario> out = new ArrayList<>();

        // ------------------------------------------------ locale（3）

        out.add(new Scenario("locale/en-us-vs-de-de", () -> {
            String en = renderUnder("en-US", "UTC", localeData());
            String de = renderUnder("de-DE", "Asia/Kolkata", localeData());
            String e = expectRows("en-US 下的行", en, List.of(
                    "2024-03-01\tIstanbul\t1234.56",
                    "2024-03-01\tistanbul\t2345.67",
                    "2024-03-02\tZebra\t-1234.56",
                    "2024-03-02\tzebra\t0.10"));
            if (e != null) {
                return e;
            }
            return expectBytes("en-US 与 de-DE（TimeZone 也不同）的渲染", de, en);
        }));

        out.add(new Scenario("locale/en-us-vs-tr-tr", () -> {
            String en = renderUnder("en-US", "UTC", localeData());
            String tr = renderUnder("tr-TR", "America/New_York", localeData());
            String e = expectBytes("en-US 与 tr-TR（TimeZone 也不同）的渲染", tr, en);
            if (e != null) {
                return e;
            }
            return expectTotal("tr-TR 下的合计", tr, "2345.77");
        }));

        out.add(new Scenario("locale/de-de-vs-tr-tr", () -> {
            String de = renderUnder("de-DE", "Asia/Kolkata", localeData());
            String tr = renderUnder("tr-TR", "America/New_York", localeData());
            return expectBytes("de-DE 与 tr-TR 的渲染", tr, de);
        }));

        // ------------------------------------------------ round（2）

        out.add(new Scenario("round/half-even-on-raw-group-sum", () -> {
            List<Entry> entries = List.of(
                    entry("2024-01-05", "Even", "1.005"),
                    entry("2024-01-05", "Even", "2.005"),
                    entry("2024-01-05", "Half", "0.125"));
            String text = Report.render(entries);
            String e = expectRows("组内原始和一次 HALF_EVEN", text, List.of(
                    "2024-01-05\tEven\t3.01",
                    "2024-01-05\tHalf\t0.12"));
            if (e != null) {
                return e;
            }
            e = expectTotal("合计", text, "3.13");
            if (e != null) {
                return e;
            }
            // 「逐项先舍入再相加」会给出 1.00 + 2.00 + 0.13 = 3.13（Even 组会变成 3.00）
            for (String bad : new String[]{"3.00", "0.13"}) {
                if (amountFields(text).contains(bad)) {
                    return "期望=行金额不出现「逐项先舍入」的结果 实际=出现 " + bad + "，行=" + amountFields(text);
                }
            }
            return null;
        }));

        out.add(new Scenario("round/total-from-rounded-rows", () -> {
            List<Entry> entries = List.of(
                    entry("2024-02-01", "A", "0.502"),
                    entry("2024-02-01", "A", "0.502"),
                    entry("2024-02-02", "B", "1.002"),
                    entry("2024-02-02", "B", "1.002"));
            String text = Report.render(entries);
            String e = expectRows("各行先各自舍入", text, List.of(
                    "2024-02-01\tA\t1.00",
                    "2024-02-02\tB\t2.00"));
            if (e != null) {
                return e;
            }
            e = expectTotal("合计 = 已舍入各行之和（先舍入再加）", text, "3.00");
            if (e != null) {
                return e;
            }
            if ("5.01".equals(total(text))) {
                return "期望=合计对已舍入各行求和（3.00） 实际=对原始总和一次性舍入得到 5.01";
            }
            return null;
        }));

        // ------------------------------------------------ order（2）

        out.add(new Scenario("order/rows-sorted-by-date-then-codepoint", () -> {
            List<Entry> entries = List.of(
                    entry("2024-01-02", "Alpha", "1.00"),
                    entry("2024-01-02", "Beta", "2.00"),
                    entry("2024-01-02", "alpha", "3.00"),
                    entry("2024-01-01", "Zulu", "4.00"),
                    entry("2024-01-01", "alpha", "5.00"));
            String text = Report.render(entries);
            return expectRows("按 (日期 升, 账户码点 升)，'Z'(0x5A) 在 'a'(0x61) 之前", text, List.of(
                    "2024-01-01\tZulu\t4.00",
                    "2024-01-01\talpha\t5.00",
                    "2024-01-02\tAlpha\t1.00",
                    "2024-01-02\tBeta\t2.00",
                    "2024-01-02\talpha\t3.00"));
        }));

        out.add(new Scenario("order/header-and-separator-fixed", () -> {
            String text = Report.render(List.of(entry("2024-01-02", "A", "1.00")));
            List<String> all = lines(text);
            if (all.size() != 5) {
                return "期望=1 行数据时共 5 行（表头/分隔线/数据/分隔线/合计） 实际=" + all.size() + " 行：" + show(text);
            }
            if (!HEADER.equals(all.get(0))) {
                return "期望=第 1 行是 \"" + HEADER + "\" 实际=\"" + visible(all.get(0)) + "\"";
            }
            if (!SEP.equals(all.get(1))) {
                return "期望=第 2 行是 40 个 '-' 实际=\"" + visible(all.get(1)) + "\"";
            }
            if (!SEP.equals(all.get(3))) {
                return "期望=第 4 行是 40 个 '-' 实际=\"" + visible(all.get(3)) + "\"";
            }
            if (!all.get(4).startsWith("TOTAL\t")) {
                return "期望=末行以 \"TOTAL\\t\" 开头 实际=\"" + visible(all.get(4)) + "\"";
            }
            return null;
        }));

        // ------------------------------------------------ eol（1）

        out.add(new Scenario("eol/only-lf", () -> {
            String text = Report.render(localeData());
            if (text.contains("\r")) {
                return "期望=行尾一律 \\n（不随平台变 \\r\\n） 实际=出现 \\r";
            }
            if (!text.endsWith("\n")) {
                return "期望=输出以单个 \\n 结尾 实际=结尾=" + visible(tail(text));
            }
            if (text.indexOf('\n', text.length() - 1) != text.length() - 1) {
                return "期望=末尾只有一个换行 实际=末尾有多个换行";
            }
            return null;
        }));

        // ------------------------------------------------ stable（1）

        out.add(new Scenario("stable/repeated-render-identical", () -> {
            List<Entry> entries = localeData();
            String first = Report.render(entries);
            String second = Report.render(entries);
            String e = expectBytes("同一输入连续两次调用", second, first);
            if (e != null) {
                return e;
            }
            // 只动默认 TimeZone（区域无关性由 locale 组负责），再渲染两次，结果必须不变。
            TimeZone oldZone = TimeZone.getDefault();
            try {
                TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
                String third = Report.render(entries);
                TimeZone.setDefault(TimeZone.getTimeZone("America/Anchorage"));
                String fourth = Report.render(entries);
                e = expectBytes("换了默认 TimeZone 之后再渲染", third, first);
                if (e != null) {
                    return e;
                }
                e = expectBytes("再换一次默认 TimeZone 之后渲染", fourth, first);
                if (e != null) {
                    return e;
                }
            } finally {
                TimeZone.setDefault(oldZone);
            }
            return null;
        }));

        return out;
    }

    // ------------------------------------------------------------- 数据

    private static List<Entry> localeData() {
        return List.of(
                entry("2024-03-01", "Istanbul", "1234.56"),
                entry("2024-03-01", "istanbul", "2345.67"),
                entry("2024-03-02", "Zebra", "-1234.56"),
                entry("2024-03-02", "zebra", "0.10"));
    }

    private static Entry entry(String date, String account, String amount) {
        return new Entry(LocalDate.parse(date), account, new BigDecimal(amount));
    }

    // ------------------------------------------------------------- 工具

    /** 在指定默认 Locale / TimeZone 下渲染。 */
    private static String renderUnder(String langTag, String zone, List<Entry> entries) {
        Locale.setDefault(Locale.forLanguageTag(langTag));
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        return Report.render(entries);
    }

    private static List<String> lines(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        String body = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        return new ArrayList<>(List.of(body.split("\n", -1)));
    }

    /** 数据行（两条分隔线之间的行）。 */
    private static List<String> rowLines(String text) {
        List<String> all = lines(text);
        List<String> rows = new ArrayList<>();
        for (int i = 2; i < all.size() - 2; i++) {
            rows.add(all.get(i));
        }
        return rows;
    }

    /** 所有数据行的金额列。 */
    private static List<String> amountFields(String text) {
        List<String> out = new ArrayList<>();
        for (String row : rowLines(text)) {
            String[] parts = row.split("\t", -1);
            out.add(parts.length >= 3 ? parts[2] : "<缺列>");
        }
        return out;
    }

    /** 合计行的金额列。 */
    private static String total(String text) {
        List<String> all = lines(text);
        if (all.isEmpty()) {
            return "<空输出>";
        }
        String last = all.get(all.size() - 1);
        String[] parts = last.split("\t", -1);
        return parts.length >= 2 ? parts[1] : "<缺列：" + visible(last) + ">";
    }

    private static String expectRows(String what, String text, List<String> wanted) {
        List<String> got = rowLines(text);
        if (!wanted.equals(got)) {
            return "期望=" + what + " → " + visibleList(wanted) + " 实际=" + visibleList(got);
        }
        return null;
    }

    private static String expectTotal(String what, String text, String wanted) {
        String got = total(text);
        if (!wanted.equals(got)) {
            return "期望=" + what + "=" + wanted + " 实际=" + got;
        }
        return null;
    }

    private static String expectBytes(String what, String actual, String expected) {
        if (expected.equals(actual)) {
            return null;
        }
        return "期望=" + what + " 逐字节相同 实际=不同：期望=" + show(expected) + " 实际=" + show(actual);
    }

    private static List<String> visibleList(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            out.add(visible(l));
        }
        return out;
    }

    private static String show(String text) {
        return "[" + visible(text) + "]";
    }

    private static String tail(String text) {
        return text.length() <= 3 ? text : text.substring(text.length() - 3);
    }

    private static String visible(String s) {
        String t = s.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
        return t.length() > 300 ? t.substring(0, 300) + "…" : t;
    }

    private Checker() {
    }
}

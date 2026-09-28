# fxreport

Java 17 的**确定性财务报表**渲染库。只用 JDK 标准库（`javac` / `java`），
不用 Maven / Gradle，不引入任何第三方依赖。

```java
List<Entry> entries = Ledger.parse(text);
String report = Report.render(entries);
```

同样的流水，**两台机器（不同默认 Locale、不同默认 TimeZone）生成的报表必须逐字节一致**。

## 目录

```
src/main/java/fxreport/Report.java    报表渲染（本次要交付的，当前是空实现）
src/main/java/fxreport/Ledger.java    流水读入（已给）
src/main/java/fxreport/Entry.java     一条流水：日期 + 账户 + 金额
src/test/java/fxreport/ReportTest.java   既有用例（12 个，手写 runner，无第三方框架）
scripts/build.sh                      编译 main / test
scripts/test.sh                       跑既有用例
scripts/check.sh                      固定验收入口（勿改）
check/Checker.java                    固定验收程序（勿改）
```

## 语言版本前提

- Java 17（`javac` / `java`）。
- 只用 JDK 标准库；构建产物在 `out/`、`out-test/`、`out-check/`，已在 `.gitignore` 中忽略。

## 怎么跑

```bash
bash scripts/build.sh      # 编译
bash scripts/test.sh       # 既有用例（12 个）
bash scripts/check.sh      # 固定验收；支持 -list 与 --only <组名>
bash scripts/check.sh -list
bash scripts/check.sh --only locale
```

## 报表格式（固定）

字段之间用一个**制表符**（`\t`，U+0009）分隔；行与行之间、以及整个输出的结尾都用
**单个 `\n`**（LF）。`TAB` 在下面表示一个制表符。

```
第 1 行（表头，固定）   dateTABaccountTABamount
第 2 行（分隔线，固定）  40 个 ASCII 连字符 '-'
每个报表行              <yyyy-MM-dd>TAB<accountId>TAB<金额>
倒数第 2 行（分隔线）    同第 2 行
末行（合计）            TOTALTAB<金额>
```

`<金额>` 一律是 `BigDecimal` 的 `scale() == 2` 的十进制字面量（`toPlainString()`，
不带千分位、不带正号、负数用 ASCII `-`），例如 `0.00`、`3.01`、`-1234.56`。

## 对外契约

下面 9 条是 `fxreport` 的**对外契约**，它们是本题验收点的唯一出处。
**它们是契约，不是「当前行为」的转述**；实现方式不限，但必须让这 9 条同时成立。

1. **入口**：`Report.render(List<Entry> entries) -> String`，输出上面那份**逐字节确定**的报表文本。
2. **区域无关**：默认 `Locale` 为 `en-US`、`de-DE`、`tr-TR` 时，同一份流水渲染出的文本
   **完全相同**；默认 `TimeZone` 取任意值也不影响结果。
3. **金额**：一律用 `BigDecimal`，舍入模式固定 `HALF_EVEN`，用 `setScale(2, HALF_EVEN)` 之后输出；
   **禁止** `double`，**禁止** `String.format` 的 `%,.2f`（会跟着默认 Locale 走）。
4. **日期**：一律按 `yyyy-MM-dd` 输出（固定格式器，不随默认区域变化）。
5. **行序**：报表行按 `(date, accountId)` **升序**；`accountId` 用**码点序**比较
   （`String.compareTo`，**不是** `Collator`，也不做大小写折叠）。
6. **分组小计**：同一 `(date, accountId)` 的流水合并成一行；该行金额必须对
   **组内原始和的**（十进制精确）**做一次** `setScale(2, HALF_EVEN)`，
   不得对逐项分别舍入后再相加。
7. **合计**：末行金额 = **各报表行（已舍入的）金额之和**，即**先舍入再加**，
   不得对原始总和一次性舍入。
8. **表头与分隔线固定**：见上面「报表格式」一节；行尾一律 `\n`，**不随平台**变成 `\r\n`。
9. **确定性**：同一输入任意多次调用、以及进程重启后重新调用，输出逐字节相同。

## 本次交付物

把 `Report.render` 实现出来，让 12 个既有用例转绿、固定件全过。
`Ledger` 已经能读入流水；`Entry` 的字段与类型已经定死。

> `check/` 与 `src/test/java/fxreport/ReportTest.java` 里的既有用例都是契约的一部分，勿改；
> 公开的方法名与签名已经定死，不要改（可以新增类与内部结构）。

## 验收

`check/Checker.java` 是固定验收程序，**不要修改**。它按 5 组共 9 个场景检查上面的契约。
**每个场景都在独立 JVM 子进程里跑，带看门狗**（30 秒），超时判不通过；
一个场景失败不会遮蔽其余场景（失败不早退）。

| 组 | 场景 | 对应契约 |
| --- | --- | --- |
| `locale` | `en-us-vs-de-de` | 2、3、4 |
| `locale` | `en-us-vs-tr-tr` | 2、3 |
| `locale` | `de-de-vs-tr-tr` | 2、3 |
| `round` | `half-even-on-raw-group-sum` | 3、6 |
| `round` | `total-from-rounded-rows` | 7 |
| `order` | `rows-sorted-by-date-then-codepoint` | 5 |
| `order` | `header-and-separator-fixed` | 8 |
| `eol` | `only-lf` | 8 |
| `stable` | `repeated-render-identical` | 1、9 |

`locale` 三个场景分别在**三个不同的子 JVM** 里把默认 Locale 设成 `en-US` / `de-DE` / `tr-TR`、
默认 TimeZone 设成三个不同时区，三次渲染必须逐字节相同 —— 这也顺带覆盖了契约 9 的
「进程重启后重新调用结果相同」。判据全部确定性：没有随机源、没有墙钟、没有性能门槛。

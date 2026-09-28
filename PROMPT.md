同一天的账，两台机器生成的报表必须逐字节一致。fxreport 是 Java 17 的
财务报表渲染库，只用 JDK 标准库（javac/java），构建走 `scripts/build.sh`，
自检走 `scripts/check.sh`（`check/` 是固定验收程序，别改），既有用例走 `bash scripts/test.sh`。

`Ledger` 已经能读入流水；`Report.render` 是这次要交付的，现在方法体是空实现，
12 个既有用例全是红的。

任务：按 README「对外契约」的 9 条把 `render` 做出来，让既有用例转绿、固定件全过。

验收：
- bash scripts/build.sh 退出码 0；
- bash scripts/test.sh 12/12 全绿；
- bash scripts/check.sh 退出码 0，9 个场景全过（locale 3 + round 2 + order 2 + eol 1 + stable 1）。

约束：
1. 不改 `check/`、不改 `Ledger`；可以新增类。
2. 对外方法名与签名已定死，不要改；`ReportTest.java` 里的用例一条都不许删或改。
3. 不许引入任何第三方依赖，不许加 `pom.xml` / `build.gradle`。
4. 输出不能随默认区域或时区变化。

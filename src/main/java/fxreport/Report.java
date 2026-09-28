package fxreport;

import java.util.List;

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
        throw new UnsupportedOperationException("Report.render 尚未实现");
    }

    private Report() {
    }
}

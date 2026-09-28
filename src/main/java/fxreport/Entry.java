package fxreport;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一条流水：日期 + 账户 + 金额。
 *
 * <p>金额一律用 {@link BigDecimal}（十进制精确值），不得经过 {@code double}。
 */
public record Entry(LocalDate date, String accountId, BigDecimal amount) {

    public Entry {
        if (date == null) {
            throw new IllegalArgumentException("date 不能为 null");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("accountId 不能为 null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount 不能为 null");
        }
    }
}

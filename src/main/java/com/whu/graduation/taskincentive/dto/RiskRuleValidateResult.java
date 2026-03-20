package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 规则表达式校验结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskRuleValidateResult {
    private boolean valid;
    private String message;
    private List<String> missingSharpVariables;

    public static RiskRuleValidateResult valid() {
        return new RiskRuleValidateResult(true, "ok", Collections.emptyList());
    }

    public static RiskRuleValidateResult invalid(String message) {
        return new RiskRuleValidateResult(false, message, Collections.emptyList());
    }

    public static RiskRuleValidateResult invalid(String message, List<String> missing) {
        return new RiskRuleValidateResult(false, message, missing == null ? Collections.emptyList() : missing);
    }
}

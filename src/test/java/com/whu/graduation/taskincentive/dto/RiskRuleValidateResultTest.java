package com.whu.graduation.taskincentive.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RiskRuleValidateResultTest {

    @Test
    void invalid_shouldFallbackToEmptyList_whenMissingIsNull() {
        RiskRuleValidateResult out = RiskRuleValidateResult.invalid("bad", null);
        assertFalse(out.isValid());
        assertEquals("bad", out.getMessage());
        assertNotNull(out.getMissingSharpVariables());
        assertEquals(0, out.getMissingSharpVariables().size());
    }

    @Test
    void invalid_shouldKeepMissingList_whenProvided() {
        RiskRuleValidateResult out = RiskRuleValidateResult.invalid("bad", List.of("#x"));
        assertEquals(1, out.getMissingSharpVariables().size());
        assertEquals("#x", out.getMissingSharpVariables().get(0));
    }
}


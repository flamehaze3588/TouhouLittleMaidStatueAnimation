package com.tlmstatueanimation.client.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RoamingAssignments 漫游变量赋值串解析测试（§8.22）。
 */
class RoamingAssignmentsTest {

    @Test
    void checkboxVarNameStripsPrefix() {
        assertEquals("B", RoamingAssignments.checkboxVarName("v.roaming.B"));
        assertEquals("red_bow_headdress", RoamingAssignments.checkboxVarName("v.roaming.red_bow_headdress"));
    }

    @Test
    void checkboxVarNameRejectsBadInput() {
        assertNull(RoamingAssignments.checkboxVarName("v.other.B"));
        assertNull(RoamingAssignments.checkboxVarName("B"));
        assertNull(RoamingAssignments.checkboxVarName(null));
        assertNull(RoamingAssignments.checkboxVarName("v.roaming."));
        assertNull(RoamingAssignments.checkboxVarName("v.roaming.9abc"));
        // 超长（>32）拒绝
        assertNull(RoamingAssignments.checkboxVarName("v.roaming." + "a".repeat(33)));
    }

    @Test
    void parseAssignmentWithSemicolon() {
        RoamingAssignments.Assignment a = RoamingAssignments.parse("v.roaming.bagemotion=2;");
        assertEquals("bagemotion", a.varName());
        assertEquals(2.0f, a.value());
    }

    @Test
    void parseAssignmentWithoutSemicolonAndNegative() {
        RoamingAssignments.Assignment a = RoamingAssignments.parse("v.roaming.x=-1.5");
        assertEquals("x", a.varName());
        assertEquals(-1.5f, a.value());
    }

    @Test
    void parseRejectsMalformed() {
        assertNull(RoamingAssignments.parse("v.roaming.x"));
        assertNull(RoamingAssignments.parse("v.roaming.x=abc"));
        assertNull(RoamingAssignments.parse("x=2"));
        assertNull(RoamingAssignments.parse(null));
        assertNull(RoamingAssignments.parse("v.roaming.x=NaN.."));
    }

    @Test
    void varNameValidation() {
        assertTrue(RoamingAssignments.isValidVarName("B"));
        assertTrue(RoamingAssignments.isValidVarName("_under_score_9"));
        assertFalse(RoamingAssignments.isValidVarName("9startsDigit"));
        assertFalse(RoamingAssignments.isValidVarName("has space"));
        assertFalse(RoamingAssignments.isValidVarName(""));
    }
}

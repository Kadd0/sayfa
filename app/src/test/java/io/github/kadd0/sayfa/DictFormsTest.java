// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** The candidate dictionary forms tried for a tapped word, in order. */
public class DictFormsTest {
    private static void hasForm(String word, String expected) {
        List<String> forms = Dict.forms(word);
        assertTrue(word + " → " + forms + " should include " + expected, forms.contains(expected));
    }

    @Test
    public void wordItselfComesFirst() {
        assertEquals("running", Dict.forms("running").get(0));
    }

    @Test
    public void irregularFormsMapToTheirBase() {
        hasForm("thought", "think");
        hasForm("were", "be");
        hasForm("understood", "understand");
        hasForm("went", "go");
        hasForm("children", "child");
        hasForm("let's", "let");
    }

    @Test
    public void regularEndingsAreStripped() {
        hasForm("running", "run");
        hasForm("smoked", "smoke");
        hasForm("studies", "study");
        hasForm("quickly", "quick");
        hasForm("happiness", "happy");
        hasForm("bigger", "big");
    }

    @Test
    public void britishAndAmericanSpellingsAreBothTried() {
        hasForm("realise", "realize");
        hasForm("organised", "organize");
        hasForm("analysed", "analyze");
        hasForm("colour", "color");
        hasForm("favourite", "favorite");
        hasForm("centre", "center");
        hasForm("defence", "defense");
        hasForm("traveled", "travelled");
        hasForm("travelled", "traveled");
    }

    @Test
    public void izeVerbsFallBackToTheirRoot() {
        hasForm("stylized", "style");
        hasForm("apologise", "apology");
    }

    @Test
    public void spellingVariantsComeAfterTheWordItself() {
        List<String> forms = Dict.forms("realize");
        assertEquals("realize", forms.get(0));
        assertTrue(forms.indexOf("realise") > 0);
    }

    @Test
    public void wordsSplitAcrossLinesAreRejoined() {
        hasForm("influ-enced", "influenced");
        hasForm("influ-enced", "influence");
    }
}

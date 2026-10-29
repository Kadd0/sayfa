// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Matching a contents entry to the heading a chapter file starts with. */
public class EpubHeadingTest {
    private static boolean same(String heading, String label) {
        return Epub.matches(Epub.simplify(heading), label);
    }

    @Test
    public void numberedEntriesMatchTheirHeading() {
        assertTrue(same("The Law of Small Numbers", "10. The Law of Small Numbers"));
        assertTrue(same("Tom W’s Specialty", "14. Tom W's Specialty"));
        assertTrue(same("Introduction", "Introduction"));
        assertTrue(same("Chapter 3: The Lazy Controller", "3. The Lazy Controller"));
    }

    @Test
    public void differentChaptersDoNotMatch() {
        assertFalse(same("Answering an Easier Question", "10. The Law of Small Numbers"));
        assertFalse(same("", "Introduction"));
        assertFalse(same("I", "Introduction"));
    }

    @Test
    public void numberingIsRemovedButWordsAreKept() {
        assertEquals("anchors", Epub.core("11. Anchors"));
        assertEquals("anchors", Epub.core("Chapter IV Anchors"));
        assertEquals("civil war", Epub.core("Civil War"));
        assertEquals("mix of ideas", Epub.core("Mix of Ideas"));
    }
}

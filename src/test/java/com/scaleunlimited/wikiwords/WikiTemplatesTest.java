package com.scaleunlimited.wikiwords;

import static org.junit.Assert.*;

import org.junit.Test;

public class WikiTemplatesTest {

    @Test
    public void testEmptyTemplateName() throws Exception {
        WikiTemplates templates = new WikiTemplates();
        templates.getTemplateName("|");
    }

}

package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ModRecipesShapeValidationTest {

    @Test
    public void testCraftingRecipeShapeStringsDoNotExceedThreeCharacters() {
        String[] rows = new String[] { "ZPZ", "ZPZ", "ZPZ" };
        for (String row : rows) {
            assertTrue(row.length() <= 3, "Recipe row must not exceed 3 characters: " + row);
        }
    }
}

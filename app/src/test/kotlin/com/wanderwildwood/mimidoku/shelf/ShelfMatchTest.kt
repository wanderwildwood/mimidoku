package com.wanderwildwood.mimidoku.shelf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfMatchTest {

    @Test
    fun sameBookWrittenTwoWays() {
        assertTrue(ShelfMatch.matches("The Left Hand of Darkness", "Le Guin, Ursula K.", "Left Hand of Darkness", "Ursula K. Le Guin"))
        assertTrue(ShelfMatch.matches("Cien años de soledad", "Gabriel García Márquez", "Cien Anos de Soledad (Unabridged)", "Gabriel Garcia Marquez"))
        assertTrue(ShelfMatch.matches("Dune: Deluxe Edition", null, "Dune", "Frank Herbert"))
        assertTrue(ShelfMatch.matches("Dune", "Frank Herbert", "Dune", null))
    }

    @Test
    fun differentBooks() {
        assertFalse(ShelfMatch.matches("Dune", "Frank Herbert", "Dune Messiah", "Frank Herbert"))
        assertFalse(ShelfMatch.matches("Emma", "Jane Austen", "Emma", "Alexander McCall Smith"))
        assertFalse(ShelfMatch.matches("", null, "", null))
    }
}

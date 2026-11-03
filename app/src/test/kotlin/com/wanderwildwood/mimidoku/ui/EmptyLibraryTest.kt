package com.wanderwildwood.mimidoku.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EmptyLibraryTest {
    @Test fun `an empty library offers a folder, a server and a Nextcloud folder`() {
        assertEquals(
            listOf(EmptyAction.FOLDER, EmptyAction.SERVER, EmptyAction.NEXTCLOUD),
            emptyLibraryActions(canChooseFolder = true, canConnectServer = true, canConnectNextcloud = true),
        )
    }

    @Test fun `what is already set up is not offered again`() {
        assertEquals(
            listOf(EmptyAction.FOLDER, EmptyAction.SERVER),
            emptyLibraryActions(canChooseFolder = true, canConnectServer = true, canConnectNextcloud = false),
        )
        assertEquals(
            listOf(EmptyAction.FOLDER, EmptyAction.NEXTCLOUD),
            emptyLibraryActions(canChooseFolder = true, canConnectServer = false, canConnectNextcloud = true),
        )
    }
}

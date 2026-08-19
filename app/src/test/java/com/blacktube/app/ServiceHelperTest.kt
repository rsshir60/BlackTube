package com.blacktube.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.util.ServiceHelper

class ServiceHelperTest {

    @Test
    fun serviceHelper_youTubeServiceIdIsZero() {
        // BlackTube Rule #1: YouTube service ID is strictly 0
        assertEquals(0, ServiceHelper.getSelectedServiceId(null))
    }
}

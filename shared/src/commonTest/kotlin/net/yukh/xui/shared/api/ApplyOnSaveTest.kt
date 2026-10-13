package net.yukh.xui.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals

class ApplyOnSaveTest {

    @Test
    fun panelsThatApplyOnSaveSayAppliedNotRestart() {
        assertEquals(NOTICE_SAVED_APPLIED, xraySaveNotice("3.3.1", xrayRunning = true))
        assertEquals(NOTICE_SAVED_APPLIED, xraySaveNotice("v3.10.0", xrayRunning = true))
    }

    @Test
    fun panelsThatOnlyStoreTheTemplateStillAskForARestart() {
        assertEquals(NOTICE_SAVED_RESTART_XRAY, xraySaveNotice("3.3.0", xrayRunning = true))
        assertEquals(NOTICE_SAVED_RESTART_XRAY, xraySaveNotice("", xrayRunning = true))
    }

    @Test
    fun aStoppedXrayIsNotReportedAsApplied() {
        assertEquals(NOTICE_SAVED_XRAY_STOPPED, xraySaveNotice("3.10.0", xrayRunning = false))
        assertEquals(NOTICE_SAVED_RESTART_XRAY, xraySaveNotice("3.3.0", xrayRunning = false))
    }

    @Test
    fun onlyAnEditedSubscriptionNeedsARestart() {
        assertEquals(NOTICE_SAVED_RESTART_XRAY, outboundSubNotice(OutboundSubChange.Update))
        listOf(
            OutboundSubChange.Create,
            OutboundSubChange.Delete,
            OutboundSubChange.Refresh,
            OutboundSubChange.Move,
        ).forEach { assertEquals(NOTICE_APPLIES_WITHIN_30S, outboundSubNotice(it), it.name) }
    }
}

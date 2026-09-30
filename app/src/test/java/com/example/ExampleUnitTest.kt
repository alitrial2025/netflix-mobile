package com.example

import com.example.data.FirebaseSyncManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testExtractPairingCode_fromUserReportedUrl() {
    val input = "https://NeTFLIXPRO.APP/PAIR?CODE=NF-5RDS5Z"
    val result = FirebaseSyncManager.extractPairingCode(input)
    assertEquals("NF-5RDS5Z", result)
  }

  @Test
  fun testExtractPairingCode_fromStandardUrls() {
    assertEquals("NF-5RDS5Z", FirebaseSyncManager.extractPairingCode("https://netflixpro.app/pair?code=NF-5RDS5Z"))
    assertEquals("NF-5RDS5Z", FirebaseSyncManager.extractPairingCode("http://netflixpro.app/pair?code=nf-5rds5z"))
    assertEquals("NF-5RDS5Z", FirebaseSyncManager.extractPairingCode("https://netflixpro.app/pair/NF-5RDS5Z"))
    assertEquals("NF-5RDS5Z", FirebaseSyncManager.extractPairingCode("netflix://pair?code=NF-5RDS5Z"))
    assertEquals("NF-5RDS5Z", FirebaseSyncManager.extractPairingCode("netflix-tv-auth:NF-5RDS5Z"))
    assertEquals("NF-5RDS5Z", FirebaseSyncManager.extractPairingCode("NF-5RDS5Z"))
    assertEquals("NET-8824", FirebaseSyncManager.extractPairingCode("NET-8824"))
    assertEquals("NF-5RDS5Z", FirebaseSyncManager.extractPairingCode("CODE=5RDS5Z"))
    assertEquals("NF-5RDS5Z", FirebaseSyncManager.extractPairingCode("https://ais-dev-app.run.app/pair?source=tv&code=NF-5RDS5Z&extra=1"))
  }

  @Test
  fun testExtractPairingCode_neverContainsSlashes() {
    val inputs = listOf(
      "https://NeTFLIXPRO.APP/PAIR?CODE=NF-5RDS5Z",
      "https://netflixpro.app/pair/NF-5RDS5Z/",
      "//NF-5RDS5Z//",
      "netflix-tv-auth://NF-5RDS5Z"
    )
    for (input in inputs) {
      val code = FirebaseSyncManager.extractPairingCode(input)
      org.junit.Assert.assertFalse("Code should not contain slashes: $code", code.contains("/"))
      org.junit.Assert.assertFalse("Code should not contain backslashes: $code", code.contains("\\"))
      org.junit.Assert.assertTrue("Code length should be >= 4", code.length >= 4)
    }
  }

  @Test
  fun testSubscriptionPlan_profileLimits() {
    val mobile = com.example.data.model.SubscriptionPlans.getById("plan_mobile")
    val basic = com.example.data.model.SubscriptionPlans.getById("plan_basic")
    val standard = com.example.data.model.SubscriptionPlans.getById("plan_standard")
    val premium = com.example.data.model.SubscriptionPlans.getById("plan_premium")

    assertEquals(1, mobile.maxProfiles)
    assertEquals(2, basic.maxProfiles)
    assertEquals(4, standard.maxProfiles)
    assertEquals(5, premium.maxProfiles)

    val mobileSub = com.example.data.model.UserSubscription(planId = "plan_mobile")
    val basicSub = com.example.data.model.UserSubscription(planId = "plan_basic")
    val standardSub = com.example.data.model.UserSubscription(planId = "plan_standard")
    val premiumSub = com.example.data.model.UserSubscription(planId = "plan_premium")

    assertEquals(1, mobileSub.maxProfiles)
    assertEquals(2, basicSub.maxProfiles)
    assertEquals(4, standardSub.maxProfiles)
    assertEquals(5, premiumSub.maxProfiles)
  }
}

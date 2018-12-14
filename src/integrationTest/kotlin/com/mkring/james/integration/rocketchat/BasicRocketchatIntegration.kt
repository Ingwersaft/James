package com.mkring.james.integration.rocketchat

import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.net.URL

class BasicRocketchatIntegration {

    @Test
    fun testConnectivity() {
        println("### testConnectivity ###")
        try {
            val readText = URL("http://localhost:3000").readText()
            assertTrue(readText.isNotEmpty())
        } catch (e: Exception) {
            println("e: ${e::class.simpleName}# ${e.message}")
            e.printStackTrace()
            throw e;
        }
    }
}
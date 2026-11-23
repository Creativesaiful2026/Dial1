package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.BlockRuleEntity
import com.example.core.database.DialMeDatabase
import com.example.core.database.SpeedDialEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExampleRobolectricTest {

    private lateinit var db: DialMeDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DialMeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app_name matches DialMe`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DialMe", appName)
    }

    @Test
    fun `speed dial and block rule persist in Room`() = runTest {
        db.speedDialDao().upsert(
            SpeedDialEntity(
                slot = 2,
                contactLookupKey = null,
                displayName = "Mom",
                phoneNumber = "+8801711000000",
                normalizedNumber = "+8801711000000",
                simAccountId = null,
                updatedAt = 100L
            )
        )
        val speeds = db.speedDialDao().observeAll().first()
        assertEquals(1, speeds.size)
        assertEquals("Mom", speeds.first().displayName)

        db.blockRuleDao().upsert(
            BlockRuleEntity(
                ruleType = "PREFIX",
                pattern = "+1800",
                normalizedPattern = "1800",
                label = "Toll Free Spam",
                action = "REJECT",
                skipCallLog = false,
                skipNotification = true,
                isEnabled = true,
                syncedToSystemBlocked = false,
                createdAt = 100L
            )
        )
        val rules = db.blockRuleDao().getEnabledRulesOnce()
        assertTrue(rules.any { it.pattern == "+1800" })
    }
}

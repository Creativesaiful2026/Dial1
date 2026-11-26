package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        T9ContactCacheEntity::class,
        SpeedDialEntity::class,
        BlockRuleEntity::class,
        BlockedCallEventEntity::class,
        CallNoteEntity::class,
        CallReminderEntity::class,
        QuickResponseEntity::class,
        ContactExtensionEntity::class,
        PrivateContactEntity::class,
        AutomationRuleEntity::class,
        CustomThemeEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class DialMeDatabase : RoomDatabase() {
    abstract fun t9ContactCacheDao(): T9ContactCacheDao
    abstract fun speedDialDao(): SpeedDialDao
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun blockedCallEventDao(): BlockedCallEventDao
    abstract fun callNoteDao(): CallNoteDao
    abstract fun callReminderDao(): CallReminderDao
    abstract fun quickResponseDao(): QuickResponseDao
    abstract fun contactExtensionDao(): ContactExtensionDao
    abstract fun privateContactDao(): PrivateContactDao
    abstract fun automationRuleDao(): AutomationRuleDao
    abstract fun customThemeDao(): CustomThemeDao

    companion object {
        @Volatile
        private var instance: DialMeDatabase? = null

        /**
         * Returns the single shared database instance. Previously every caller
         * (app container, call screening service, reminder worker) created its own
         * Room instance for the same file and never closed it.
         */
        fun build(context: Context): DialMeDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DialMeDatabase::class.java,
                    "dialme.db"
                ).build().also { instance = it }
            }
        }
    }
}

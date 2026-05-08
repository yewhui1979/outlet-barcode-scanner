package com.outletscanner.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.outletscanner.app.data.model.BarcodeMapping
import com.outletscanner.app.data.model.MinMaxChange
import com.outletscanner.app.data.model.PoRecord
import com.outletscanner.app.data.model.Product

@Database(entities = [Product::class, BarcodeMapping::class, PoRecord::class, MinMaxChange::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun barcodeMappingDao(): BarcodeMappingDao
    abstract fun poRecordDao(): PoRecordDao
    abstract fun minMaxChangeDao(): MinMaxChangeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "outlet_scanner.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

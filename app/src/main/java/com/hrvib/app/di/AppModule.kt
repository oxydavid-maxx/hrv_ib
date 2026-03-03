package com.hrvib.app.di

import android.content.Context
import androidx.room.Room
import com.hrvib.app.BuildConfig
import com.hrvib.app.data.BleGateway
import com.hrvib.app.data.SessionRepository
import com.hrvib.app.data.SettingsStore
import com.hrvib.app.data.ble.BleClient
import com.hrvib.app.data.ble.FakeBleClient
import com.hrvib.app.data.ble.RealBleClient
import com.hrvib.app.data.db.AppDatabase
import com.hrvib.app.domain.MetronomeEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "hrv_ib.db").build()

    @Provides
    @Singleton
    fun provideRealBleClient(@ApplicationContext context: Context): RealBleClient = RealBleClient(context)

    @Provides
    @Singleton
    fun provideFakeBleClient(@ApplicationContext context: Context): FakeBleClient = FakeBleClient(context)

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore = SettingsStore(context)

    @Provides
    @Singleton
    fun provideBleGateway(
        realClient: RealBleClient,
        fakeClient: FakeBleClient,
        settingsStore: SettingsStore
    ): BleGateway = BleGateway(realClient, fakeClient, settingsStore, BuildConfig.USE_FAKE_BLE)

    @Provides
    @Singleton
    fun provideSessionRepository(db: AppDatabase): SessionRepository =
        SessionRepository(db.sessionDao(), db.rrDao(), db.epochDao())

    @Provides
    fun provideMetronomeEngine(): MetronomeEngine = MetronomeEngine()
}

/*
 * Copyright (C) 2023 DerpFest
 * Copyright (C) 2023-2024 The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.custom

import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.CellularTile
import com.android.systemui.qs.tiles.WifiTile
import com.android.systemui.qs.tiles.PowerShareTile
import com.android.systemui.qs.tiles.BatteryChargeLimitTile

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
interface CustomModule {
    /** Inject CellularTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(CellularTile.TILE_SPEC)
    fun bindCellularTile(cellularTile: CellularTile): QSTileImpl<*>

    /** Inject WifiTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(WifiTile.TILE_SPEC)
    fun bindWifiTile(wifiTile: WifiTile): QSTileImpl<*>

    /** Inject PowerShareTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(PowerShareTile.TILE_SPEC)
    fun bindPowerShareTile(powerShareTile: PowerShareTile): QSTileImpl<*>

    @Binds
    @IntoMap
    @StringKey(BatteryChargeLimitTile.TILE_SPEC)
    fun bindBatteryChargeLimitTile(batteryChargeLimitTile: BatteryChargeLimitTile): QSTileImpl<*>
}

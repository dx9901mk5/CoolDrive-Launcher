package com.dxcar.launcher.comms

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.dxcar.launcher.DxCarApp
import com.dxcar.launcher.i18n.DxCarI18n

/**
 * Android Quick Settings Tile allowing one-tap Bluetooth reconnection
 * from the smartphone notification/quick-settings shade.
 */
@RequiresApi(Build.VERSION_CODES.N)
class DxCarBluetoothTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        BluetoothCompanionService.forceReconnect(applicationContext)
        qsTile?.let { tile ->
            tile.state = Tile.STATE_ACTIVE
            tile.label = DxCarI18n.tr("DX-Car 藍牙連線", "DX-Car 蓝牙连接", "DX-Car BT Connect", "DX-Car BT接続")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val targetName = DxCarApp.instance?.preferences?.config?.value?.companionTargetDeviceName.orEmpty()
                tile.subtitle = if (targetName.isNotBlank()) {
                    DxCarI18n.tr("正在重連 $targetName…", "正在重连 $targetName…", "Connecting $targetName…", "$targetName に再接続中…")
                } else {
                    DxCarI18n.tr("正在搜尋車機藍牙…", "正在搜索车机蓝牙…", "Searching Car BT…", "車載 BT を検索中…")
                }
            }
            tile.updateTile()
        }
    }

    private fun refreshTileState() {
        val tile = qsTile ?: return
        val state = BluetoothCompanionService.bridgeState.value
        val targetName = DxCarApp.instance?.preferences?.config?.value?.companionTargetDeviceName.orEmpty()
        tile.label = DxCarI18n.tr("DX-Car 藍牙連線", "DX-Car 蓝牙连接", "DX-Car BT Connect", "DX-Car BT接続")
        tile.state = when (state.status) {
            BridgeConnectionStatus.CONNECTED -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                state.status == BridgeConnectionStatus.CONNECTED && state.peerName.isNotBlank() -> state.peerName
                targetName.isNotBlank() -> targetName
                else -> DxCarI18n.tr("點擊重連車機", "点击重连车机", "Tap to reconnect", "タップして再接続")
            }
        }
        tile.updateTile()
    }
}

package com.pharmabill.app.ui.nav

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pharmabill.app.ui.components.BrandTopBar
import com.pharmabill.app.ui.components.RoundIconButton
import com.pharmabill.app.ui.theme.Pb
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pharmabill.app.R
import com.pharmabill.app.ui.backup.BackupScreen
import com.pharmabill.app.ui.bill.BillScreen
import com.pharmabill.app.ui.camera.CameraScreen
import com.pharmabill.app.ui.dashboard.DashboardScreen
import com.pharmabill.app.ui.history.BillsScreen
import com.pharmabill.app.ui.home.HomeScreen
import com.pharmabill.app.ui.lowstock.LowStockScreen
import com.pharmabill.app.ui.medicines.MedicineEditScreen
import com.pharmabill.app.ui.medicines.MedicinesScreen
import com.pharmabill.app.ui.more.MoreScreen
import com.pharmabill.app.ui.receipt.ReceiptScreen
import com.pharmabill.app.ui.scan.ScanScreen
import com.pharmabill.app.ui.settings.SettingsScreen
import com.pharmabill.app.ui.setup.LanguageScreen
import com.pharmabill.app.ui.setup.ShopSetupScreen
import com.pharmabill.app.ui.stock.StockEntryScreen

object Routes {
    const val LANGUAGE = "language"
    const val SHOP = "shop_setup"
    const val HOME = "home"
    const val BILLS = "bills"
    const val MEDICINES = "medicines"
    const val MORE = "more"
    const val LOW_STOCK = "low_stock"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"

    fun camera(billId: Long) = "camera/$billId"
    fun scan(billId: Long, path: String) = "scan/$billId?path=${Uri.encode(path)}"
    fun bill(billId: Long, confirm: Boolean = false) = "bill/$billId?confirm=$confirm"
    fun receipt(billId: Long) = "receipt/$billId"
    fun medicineEdit(id: Long?) = "medicine_edit?id=${id ?: -1}"
    fun stock(id: Long?) = "stock?medicineId=${id ?: -1}"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Outlined.Home),
    Tab(Routes.BILLS, R.string.tab_bills, Icons.Outlined.Receipt),
    Tab(Routes.MEDICINES, R.string.tab_medicines, Icons.Outlined.MedicalServices),
    Tab(Routes.MORE, R.string.tab_more, Icons.Outlined.MoreHoriz),
)

@Composable
fun AppNav(setupDone: Boolean, shopName: String, openOnStart: String?, onLanguageChanged: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    LaunchedEffect(openOnStart) { if (setupDone && openOnStart == Routes.LOW_STOCK) nav.navigate(Routes.LOW_STOCK) }

    val onTab = tabs.any { it.route == route }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (onTab) {
                BrandTopBar(subtitle = shopName.ifBlank { stringResource(R.string.brand_tagline) }) {
                    RoundIconButton(Icons.Outlined.NotificationsNone, stringResource(R.string.low_stock)) { nav.navigate(Routes.LOW_STOCK) }
                    RoundIconButton(Icons.Outlined.AccountCircle, stringResource(R.string.settings)) { nav.navigate(Routes.SETTINGS) }
                }
            }
        },
        bottomBar = {
            if (onTab) {
                Column {
                    HorizontalDivider(color = Pb.line)
                    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = route == t.route,
                                onClick = { nav.switchTab(t.route) },
                                icon = { Icon(t.icon, null) },
                                label = { Text(stringResource(t.label), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Pb.primary,
                                    selectedTextColor = Pb.primary,
                                    indicatorColor = Pb.primarySoft,
                                    unselectedIconColor = Pb.navInactive,
                                    unselectedTextColor = Pb.navInactive,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = if (setupDone) Routes.HOME else Routes.LANGUAGE, modifier = Modifier.padding(pad)) {
            composable(Routes.LANGUAGE) { Box(Modifier.safeDrawingPadding()) { LanguageScreen(onChosen = { onLanguageChanged(); nav.navigate(Routes.SHOP) }) } }
            composable(Routes.SHOP) {
                ShopSetupScreen(onDone = { nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onTakePhoto = { nav.navigate(Routes.camera(it)) },
                    onOpenBill = { id, confirm -> nav.navigate(Routes.bill(id, confirm)) },
                    onLowStock = { nav.navigate(Routes.LOW_STOCK) },
                    onNeedsStock = { nav.navigate(Routes.stock(null)) },
                    onAllBills = { nav.switchTab(Routes.BILLS) },
                    onOpenReceipt = { nav.navigate(Routes.receipt(it)) },
                )
            }
            composable(Routes.BILLS) {
                BillsScreen(onOpenDraft = { nav.navigate(Routes.bill(it)) }, onOpenReceipt = { nav.navigate(Routes.receipt(it)) })
            }
            composable(Routes.MEDICINES) {
                MedicinesScreen(onEdit = { nav.navigate(Routes.medicineEdit(it)) }, onStock = { nav.navigate(Routes.stock(it)) })
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onDashboard = { nav.navigate(Routes.DASHBOARD) }, onLowStock = { nav.navigate(Routes.LOW_STOCK) },
                    onStock = { nav.navigate(Routes.stock(null)) }, onHistory = { nav.switchTab(Routes.BILLS) },
                    onSettings = { nav.navigate(Routes.SETTINGS) }, onBackup = { nav.navigate(Routes.BACKUP) },
                )
            }
            composable("camera/{billId}", arguments = listOf(navArgument("billId") { type = NavType.LongType })) { e ->
                val billId = e.arguments?.getLong("billId") ?: 0L
                Box(Modifier.safeDrawingPadding()) {
                    CameraScreen(
                        onCaptured = { path -> nav.navigate(Routes.scan(billId, path)) { popUpTo("camera/{billId}") { inclusive = true } } },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            composable(
                "scan/{billId}?path={path}",
                arguments = listOf(navArgument("billId") { type = NavType.LongType }, navArgument("path") { type = NavType.StringType; defaultValue = "" }),
            ) { e ->
                val billId = e.arguments?.getLong("billId") ?: 0L
                ScanScreen(
                    onBack = { nav.popBackStack() },
                    onRetake = { nav.navigate(Routes.camera(billId)) { popUpTo("scan/{billId}?path={path}") { inclusive = true } } },
                    onAdded = { nav.navigate(Routes.bill(billId)) { popUpTo("scan/{billId}?path={path}") { inclusive = true } } },
                )
            }
            composable(
                "bill/{billId}?confirm={confirm}",
                arguments = listOf(navArgument("billId") { type = NavType.LongType }, navArgument("confirm") { type = NavType.BoolType; defaultValue = false }),
            ) { e ->
                val confirm = e.arguments?.getBoolean("confirm") ?: false
                BillScreen(
                    openConfirm = confirm,
                    onBack = { nav.popBackStack() },
                    onPhoto = { nav.navigate(Routes.camera(it)) },
                    onConfirmed = { id -> nav.navigate(Routes.receipt(id)) { popUpTo("bill/{billId}?confirm={confirm}") { inclusive = true } } },
                    onSwitch = { id -> nav.navigate(Routes.bill(id)) { popUpTo("bill/{billId}?confirm={confirm}") { inclusive = true } } },
                )
            }
            composable("receipt/{billId}", arguments = listOf(navArgument("billId") { type = NavType.LongType })) {
                ReceiptScreen(
                    onBack = { nav.popBackStack() },
                    onNewBill = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
                    onStock = { nav.navigate(Routes.stock(it)) },
                )
            }
            composable("medicine_edit?id={id}", arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { e ->
                val id = e.arguments?.getLong("id")?.takeIf { it > 0 }
                MedicineEditScreen(id, onBack = { nav.popBackStack() })
            }
            composable("stock?medicineId={medicineId}", arguments = listOf(navArgument("medicineId") { type = NavType.LongType; defaultValue = -1L })) { e ->
                val id = e.arguments?.getLong("medicineId")?.takeIf { it > 0 }
                StockEntryScreen(id, onBack = { nav.popBackStack() })
            }
            composable(Routes.LOW_STOCK) { LowStockScreen(onBack = { nav.popBackStack() }, onStock = { nav.navigate(Routes.stock(it)) }) }
            composable(Routes.DASHBOARD) { DashboardScreen(onBack = { nav.popBackStack() }, onStock = { nav.navigate(Routes.stock(it)) }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() }, onBackup = { nav.navigate(Routes.BACKUP) }, onLanguageChanged = onLanguageChanged)
            }
            composable(Routes.BACKUP) { BackupScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

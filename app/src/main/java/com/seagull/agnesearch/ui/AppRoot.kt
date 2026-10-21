package com.seagull.agnesearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.seagull.agnesearch.core.Store
import com.seagull.agnesearch.ui.components.BrandBackground
import com.seagull.agnesearch.ui.chat.ChatScreen
import com.seagull.agnesearch.ui.chat.ChatViewModel
import com.seagull.agnesearch.ui.create.CreateScreen
import com.seagull.agnesearch.ui.create.CreateViewModel
import com.seagull.agnesearch.ui.history.HistoryScreen
import com.seagull.agnesearch.ui.history.ProfileScreen
import com.seagull.agnesearch.ui.history.SettingsScreen
import com.seagull.agnesearch.ui.theme.AgnesTheme
import com.seagull.agnesearch.ui.theme.brand

private val TABS = listOf("chat", "create", "history", "profile")

@Composable
fun AppRoot() {
    val settings by Store.settings.collectAsState()
    AgnesTheme(mode = settings.themeMode) {
        val nav = rememberNavController()
        val chatVm: ChatViewModel = viewModel()
        val cvm: CreateViewModel = viewModel()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "chat"
        val showBar = TABS.contains(route)

        BrandBackground {
            NavHost(
                navController = nav,
                startDestination = "chat",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (showBar) 84.dp else 0.dp),
            ) {
                composable("chat") {
                    ChatScreen(vm = chatVm, onOpenSettings = { nav.navigate("settings") })
                }
                composable("create") {
                    CreateScreen(vm = cvm, onOpenSettings = { nav.navigate("settings") })
                }
                composable("history") {
                    HistoryScreen(onOpenChat = { id ->
                        chatVm.open(id)
                        nav.navigate("chat") {
                            popUpTo("chat") { inclusive = true }
                            launchSingleTop = true
                        }
                    })
                }
                composable("profile") {
                    ProfileScreen(onOpenSettings = { nav.navigate("settings") })
                }
                composable("settings") {
                    SettingsScreen(onBack = { nav.popBackStack() })
                }
            }

            if (showBar) {
                BottomBar(
                    route = route,
                    onSelect = { r ->
                        if (r != route) {
                            nav.navigate(r) {
                                popUpTo("chat") { inclusive = r == "chat" }
                                launchSingleTop = true
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    route: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val br = brand
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .background(br.card, shape)
            .border(1.dp, br.cardBorder, shape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem("对话", Icons.Filled.MailOutline, route == "chat") { onSelect("chat") }
        TabItem("创作", Icons.Filled.Create, route == "create") { onSelect("create") }
        TabItem("历史", Icons.AutoMirrored.Filled.List, route == "history") { onSelect("history") }
        TabItem("我的", Icons.Filled.Person, route == "profile") { onSelect("profile") }
    }
}

@Composable
private fun RowScope.TabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val br = brand
    Row(
        modifier = Modifier
            .weight(1f)
            .background(
                brush = if (selected) {
                    Brush.linearGradient(br.gradient)
                } else {
                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                },
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Color.White else br.textSecondary,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = if (selected) Color.White else br.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

package com.altf4.hush

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.altf4.hush.data.Prefs
import com.altf4.hush.ui.AppsScreen
import com.altf4.hush.ui.HushTheme
import com.altf4.hush.ui.NightScreen
import com.altf4.hush.ui.SitesScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(applicationContext)
        setContent { HushApp(prefs) }
    }
}

private enum class HomeTab(val label: String, val icon: ImageVector) {
    Night("Night lock", Icons.Filled.Lock),
    Apps("Apps", Icons.Filled.Menu),
    Sites("Sites", Icons.Filled.Search),
}

@Composable
private fun HushApp(prefs: Prefs) {
    var themeMode by remember { mutableStateOf(prefs.themeMode) }

    HushTheme(themeMode) {
        var selected by rememberSaveable { mutableIntStateOf(0) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    HomeTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selected == tab.ordinal,
                            onClick = { selected = tab.ordinal },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner)) {
                when (HomeTab.entries[selected]) {
                    HomeTab.Night -> NightScreen(
                        startMinutes = prefs.nightStartMinutes,
                        endMinutes = prefs.nightEndMinutes,
                        themeMode = themeMode,
                        onThemeChange = {
                            themeMode = it
                            prefs.themeMode = it
                        },
                    )
                    HomeTab.Apps -> AppsScreen()
                    HomeTab.Sites -> SitesScreen(prefs)
                }
            }
        }
    }
}

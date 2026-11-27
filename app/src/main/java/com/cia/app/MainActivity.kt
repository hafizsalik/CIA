package com.cia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        CiaApp()
                    }
                }
            }
        }
    }
}

private val destinations = listOf("خانه", "تراکنش‌ها", "پیامک‌ها", "حساب‌ها", "بیشتر")

@Composable
private fun CiaApp() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(listOf("⌂", "⇄", "✉", "▣", "•••")[index]) },
                        label = { Text(title, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.Start
        ) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> PlaceholderScreen("تراکنش‌ها", "فهرست و جست‌وجوی تراکنش‌ها در این بخش قرار می‌گیرد.")
                2 -> PlaceholderScreen("پیامک‌های بانکی", "پیامک‌ها پس از دریافت و شناسایی در این بخش نمایش داده می‌شوند.")
                3 -> PlaceholderScreen("حساب‌های من", "حساب‌های نقدی و بانکی خود را اینجا مدیریت می‌کنید.")
                else -> PlaceholderScreen("بخش‌های بیشتر", "دسته‌بندی‌ها، طرف‌حساب‌ها، گزارش‌ها و تنظیمات.")
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    Text("CIA", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(6.dp))
    Text("مدیریت مالی شما", fontSize = 25.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("موجودی کل", color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Text("۰", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("پس از تعریف حساب‌ها و ثبت تراکنش‌ها محاسبه می‌شود.", fontSize = 12.sp)
        }
    }

    Spacer(Modifier.height(14.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard("دریافتی", "۰", Modifier.weight(1f))
        SummaryCard("پرداختی", "۰", Modifier.weight(1f))
    }

    Spacer(Modifier.height(22.dp))
    Text("دسترسی سریع", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Text("ثبت تراکنش، پیامک بانکی و گزارش‌ها در گام‌های بعدی به این صفحه متصل می‌شوند.")
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String) {
    Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text(description, fontSize = 14.sp, lineHeight = 23.sp)
}

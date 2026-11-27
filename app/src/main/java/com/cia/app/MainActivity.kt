package com.cia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.ContactsContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cia.app.data.*
import com.cia.app.ui.dateTime
import com.cia.app.ui.money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF152F8C), secondary = Color(0xFFB58A45), background = Color(0xFFF5F5F5), surface = Color.White, surfaceVariant = Color(0xFFEFF2FA))) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) { CiaApp() }
                }
            }
        }
    }
}

private val tabs = listOf("خانه", "اشخاص", "حساب‌ها", "گزارش‌ها")
private val types = listOf("پول گرفتم", "پول دادم", "درآمد", "هزینه", "هدیه گرفتم", "هدیه دادم", "انتقال بین حساب‌ها", "قرض دادم", "قرض گرفتم", "تسویه بدهی")
private val partyTypes = setOf("پول گرفتم", "پول دادم", "قرض دادم", "قرض گرفتم", "تسویه بدهی")

@Composable
private fun CiaApp() {
    val context = LocalContext.current
    val dao = remember { CiaDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val accounts by dao.observeAccounts().collectAsState(initial = emptyList())
    val parties by dao.observeParties().collectAsState(initial = emptyList())
    val txs by dao.observeTransactions().collectAsState(initial = emptyList())
    val trash by dao.observeTrash().collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addTx by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("پول گرفتم") }
    var addAccount by remember { mutableStateOf(false) }
    var showNewParty by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }

    LaunchedEffect(accounts.isEmpty()) {
        if (accounts.isEmpty()) dao.insertAccount(AccountEntity(UUID.randomUUID().toString(), "کیف پول نقدی"))
    }

    LaunchedEffect(Unit) {
        val legacy = context.getSharedPreferences("cia_parties", android.content.Context.MODE_PRIVATE)
        val raw = legacy.getString("records", "[]") ?: "[]"
        val records = runCatching { org.json.JSONArray(raw) }.getOrElse { org.json.JSONArray() }
        for (index in 0 until records.length()) {
            val item = records.optJSONObject(index) ?: continue
            val phone = item.optString("phone").trim()
            val name = item.optString("name").trim()
            if (name.isNotBlank() && phone.isNotBlank() && dao.findPartyByPhone(phone) == null) {
                dao.insertParty(PartyEntity(UUID.randomUUID().toString(), name, phone, item.optString("nationalCode"), item.optString("postalCode"), item.optString("description")))
            }
        }
        if (records.length() > 0) legacy.edit().remove("records").apply()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(imageVector = listOf(Icons.Filled.Home, Icons.Filled.People, Icons.Filled.AccountBalanceWallet, Icons.Filled.BarChart)[i], contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0 || tab == 4) FloatingActionButton(onClick = { showTypePicker = true }, containerColor = Color(0xFFB58A45), contentColor = Color.White) { Icon(Icons.Filled.Add, contentDescription = "ثبت تراکنش") }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (tab == 0) {
                HomeScreen(accounts, txs, { showTypePicker = true }, { tab = 4 }, { tab = 1 }, { tab = 2 }, { tab = 3 })
            } else {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    when (tab) {
                        1 -> if (showNewParty) NewPartyScreen(parties, onBack = { showNewParty = false }, onSaved = { party -> scope.launch { dao.insertParty(party); showNewParty = false } }) else PartiesScreen(parties, txs) { showNewParty = true }
                        2 -> AccountList(accounts, txs) { addAccount = true }
                        3 -> ReportsScreen(txs)
                        4 -> TransactionList(txs, { addTx = true }) { id -> scope.launch { dao.moveToTrash(id, System.currentTimeMillis()) } }
                        5 -> SmsScreen()
                        else -> MoreScreen(trash.size) { showTrash = true }
                    }
                }
            }
        }
    }

    if (showTypePicker) TransactionTypeSheet(
        onDismiss = { showTypePicker = false },
        onSelect = { kind -> selectedType = kind; showTypePicker = false; addTx = true }
    )
    if (addTx) TransactionDialog(accounts, initialType = selectedType, onClose = { addTx = false }) { item ->
        scope.launch {
            val number = (txs.maxOfOrNull { it.number } ?: 1000L) + 1L
            dao.insertTransaction(item.copy(number = number))
            addTx = false
        }
    }
    if (addAccount) AccountDialog(onClose = { addAccount = false }) { name, opening ->
        scope.launch {
            dao.insertAccount(AccountEntity(UUID.randomUUID().toString(), name, opening))
            addAccount = false
        }
    }
    if (showTrash) TrashDialog(trash, { showTrash = false },
        { id -> scope.launch { dao.restore(id) } },
        { id -> scope.launch { dao.permanentlyDelete(id) } })
}

@Composable
private fun Heading(title: String, subtitle: String? = null) {
    Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
    if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun HomeScreen(
    accounts: List<AccountEntity>, txs: List<TransactionEntity>, add: () -> Unit,
    all: () -> Unit, people: () -> Unit, openAccounts: () -> Unit, reports: () -> Unit
) {
    var innerTab by rememberSaveable { mutableIntStateOf(0) }
    var period by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    val now = System.currentTimeMillis()
    val day = 24L * 60L * 60L * 1000L
    val monthStart = runCatching {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1); cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }.getOrDefault(now - 30 * day)
    val filtered = txs.filter { item ->
        when (period) { 1 -> item.occurredAt >= monthStart; 2 -> item.occurredAt >= (now / day) * day; else -> true }
    }
    val parties = filtered.filter { !it.party.isNullOrBlank() }.groupBy { it.party!!.trim() }
        .map { (name, rows) ->
            val receivable = rows.filter { it.type in setOf("پول دادم", "قرض دادم") }.sumOf { it.amount }
            val payable = rows.filter { it.type in setOf("پول گرفتم", "قرض گرفتم") }.sumOf { it.amount }
            PartyBalance(name, receivable - payable, rows.size)
        }.filter { it.name.contains(search.trim(), ignoreCase = true) }
        .sortedByDescending { kotlin.math.abs(it.balance) }
    val receivableTotal = parties.sumOf { it.balance.coerceAtLeast(0L) }
    val payableTotal = parties.sumOf { (-it.balance).coerceAtLeast(0L) }
    val balance = accounts.sumOf { a -> a.openingBalance + txs.filter { it.accountId == a.id }.sumOf { signed(it) } }
    val navy = Color(0xFF152F8C)
    val pale = Color(0xFFF5F5F5)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(navy).padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("CIA", color = navy, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("دفتر حساب و کتاب", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("برای تغییر دفتر حساب کلیک کنید", color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
                }
                IconButton(onClick = reports) { Icon(Icons.Filled.BarChart, contentDescription = "گزارش‌ها", tint = Color.White) }
                IconButton(onClick = all) { Icon(Icons.Filled.ReceiptLong, contentDescription = "تراکنش‌ها", tint = Color.White) }
            }
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("بازه:", color = navy, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                listOf("مانده", "ماه جاری", "امروز").forEachIndexed { i, label ->
                    FilterChip(selected = period == i, onClick = { period = i }, label = { Text(label, fontSize = 10.sp) }, modifier = Modifier.padding(horizontal = 2.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = navy, selectedLabelColor = Color.White))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("طرف‌حساب‌ها", "تراکنش‌ها").forEachIndexed { i, label ->
                    val selected = innerTab == i
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp))
                        .background(if (selected) Color.White else Color(0xFF2542AA))
                        .clickable { innerTab = i }.padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, color = if (selected) navy else Color.White.copy(alpha = .8f), fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        if (selected) Spacer(Modifier.padding(top = 6.dp).height(2.dp).fillMaxWidth(.48f).background(navy, CircleShape))
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("وضعیت مانده طرف‌حساب‌ها", color = Color(0xFF707070), fontSize = 11.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("بدهکارم", color = Color(0xFF00A105), fontSize = 11.sp)
                            Text(money(payableTotal), color = navy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("طلبکارم", color = Color(0xFFCF0202), fontSize = 11.sp)
                            Text(money(receivableTotal), color = navy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(pale).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = openAccounts, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = navy)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("مشاهده صورت‌حساب‌های زنده", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (accounts.isEmpty()) "در حال حاضر حسابی در دفتر ندارید" else "${accounts.size} حساب ثبت‌شده · موجودی ${money(balance)} تومان", fontSize = 10.sp, color = Color(0xFF707070))
                }
            }
            if (innerTab == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${parties.size} طرف‌حساب", color = Color(0xFF707070), fontSize = 11.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = people) { Text("فهرست اشخاص") }
                }
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("جستجوی نام و شماره", fontSize = 12.sp) }, singleLine = true,
                    shape = RoundedCornerShape(30.dp), trailingIcon = { Icon(Icons.Filled.Search, contentDescription = "جست‌وجو") })
                if (parties.isEmpty()) Empty("طرف‌حسابی برای نمایش وجود ندارد", "در ثبت تراکنش، نام طرف‌حساب را وارد کنید.")
                else parties.forEach { p ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(48.dp), shape = CircleShape, color = Color(0xFFE8EDF9)) {
                                Box(contentAlignment = Alignment.Center) { Text(p.name.take(1), color = navy, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${p.count} تراکنش", color = Color(0xFF888888), fontSize = 10.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(kotlin.math.abs(p.balance)), color = if (p.balance >= 0) Color(0xFF1B7F00) else Color(0xFFCF0202), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(if (p.balance >= 0) "طلبکارم" else "بدهکارم", color = Color(0xFF707070), fontSize = 10.sp)
                            }
                        }
                    }
                }
            } else {
                Text("تراکنش‌های این بازه", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(filtered.size.toString() + " مورد", color = Color(0xFF707070), fontSize = 10.sp)
                OutlinedTextField(
                    value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("جستجوی نام، توضیحات و طرف‌حساب", fontSize = 11.sp) },
                    singleLine = true, shape = RoundedCornerShape(30.dp),
                    trailingIcon = { Text("⌕", fontSize = 20.sp) }
                )
                val visibleTransactions = filtered.filter { item ->
                    listOfNotNull(item.party, item.description, item.type, item.category)
                        .any { it.contains(search.trim(), ignoreCase = true) }
                }
                if (visibleTransactions.isEmpty()) Empty("تراکنشی پیدا نشد", "عبارت جست‌وجو را تغییر دهید یا بازه زمانی دیگری انتخاب کنید.")
                else visibleTransactions.forEach { item ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(88.dp)) {
                                Text(dateTime(item.occurredAt).substringAfter(" "), fontSize = 9.sp, color = Color(0xFF707070))
                                Text(dateTime(item.occurredAt).substringBefore(" "), fontSize = 9.sp, color = Color(0xFF707070))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.party ?: item.type, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(item.description ?: item.category ?: item.type, fontSize = 9.sp, color = Color(0xFF707070), maxLines = 2)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val positive = item.type in setOf("درآمد", "پول گرفتم", "هدیه گرفتم", "قرض گرفتم")
                                Text(money(item.amount), color = if (positive) Color(0xFF1B7F00) else Color(0xFFCF0202), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(if (positive) "دریافت کردم" else "پرداخت کردم", fontSize = 9.sp, color = Color(0xFF707070))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = people, modifier = Modifier.weight(1f), shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.buttonColors(containerColor = navy)) { Text("طرف‌حساب‌ها", fontSize = 11.sp) }
                OutlinedButton(onClick = add, modifier = Modifier.weight(1f), shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = navy)) { Text("＋ ثبت تراکنش", fontSize = 11.sp) }
            }
        }
    }
}

private data class PartyBalance(val name: String, val balance: Long, val count: Int)

@Composable
private fun SummaryTile(title: String, amount: Long, color: Color, symbol: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .09f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(symbol, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp)); Text(title, color = color, fontSize = 12.sp)
            }
            Text(money(amount), color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("تومان", color = color.copy(alpha = .8f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun QuickLink(title: String, detail: String, symbol: String, onClick: () -> Unit, modifier: Modifier) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(symbol, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CashFlowBars(values: List<Pair<Long, Long>>) {
    val maxValue = (values.maxOfOrNull { maxOf(it.first, it.second) } ?: 0L).coerceAtLeast(1L)
    val dayMillis = 24L * 60L * 60L * 1000L
    val today = System.currentTimeMillis() / dayMillis * dayMillis
    val labels = (6 downTo 0).map { offset ->
        SimpleDateFormat("EE", Locale("fa", "IR")).format(Date(today - offset * dayMillis))
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Canvas(Modifier.fillMaxWidth().height(112.dp)) {
            val groupWidth = size.width / 7f
            val barWidth = groupWidth * .24f
            values.forEachIndexed { index, pair ->
                val center = groupWidth * (index + .5f)
                val incomeHeight = (pair.first.toFloat() / maxValue) * size.height * .88f
                val expenseHeight = (pair.second.toFloat() / maxValue) * size.height * .88f
                drawRoundRect(Color(0xFF287B55), Offset(center - barWidth - 2.dp.toPx(), size.height - incomeHeight), Size(barWidth, incomeHeight.coerceAtLeast(3.dp.toPx())), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                drawRoundRect(Color(0xFFD77A70), Offset(center + 2.dp.toPx(), size.height - expenseHeight), Size(barWidth, expenseHeight.coerceAtLeast(3.dp.toPx())), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.asReversed().forEach { Text(it, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Metric(title: String, amount: Long, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .10f))) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = color, fontSize = 12.sp)
            Text(money(amount), color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("تومان", color = color, fontSize = 10.sp)
        }
    }
}

@Composable
private fun TransactionList(txs: List<TransactionEntity>, add: () -> Unit, delete: (String) -> Unit) {
    Heading("تراکنش‌ها", "فهرست دریافت‌ها، پرداخت‌ها و سایر عملیات مالی")
    Button(onClick = add) { Text("＋ ثبت تراکنش") }
    if (txs.isEmpty()) Empty("فهرست خالی است", "تراکنش‌های ثبت‌شده اینجا نمایش داده می‌شوند.")
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(txs, key = { it.id }) { item -> TxRow(item) { delete(item.id) } }
    }
}

@Composable
private fun TxRow(item: TransactionEntity, delete: () -> Unit) {
    val positive = item.type in setOf("درآمد", "پول گرفتم", "هدیه گرفتم", "قرض گرفتم")
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.type + (item.party?.let { " • " + it } ?: ""), fontWeight = FontWeight.SemiBold)
                Text(item.description ?: "بدون توضیحات", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dateTime(item.occurredAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text((if (positive) "+" else "−") + money(item.amount), color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Text("شماره " + item.number, fontSize = 9.sp)
                TextButton(onClick = delete) { Text("حذف", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun AccountList(accounts: List<AccountEntity>, txs: List<TransactionEntity>, add: () -> Unit) {
    Heading("حساب‌های من", "حساب‌های بانکی و نقدی")
    Button(onClick = add) { Text("＋ افزودن حساب") }
    accounts.forEach { a ->
        val balance = a.openingBalance + txs.filter { it.accountId == a.id }.sumOf { signed(it) }
        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "حساب", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, fontWeight = FontWeight.SemiBold)
                    Text("موجودی فعلی", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(balance) + " تومان", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PartiesScreen(parties: List<PartyEntity>, txs: List<TransactionEntity>, add: () -> Unit) {
    val transactionNames = txs.mapNotNull { it.party?.takeIf(String::isNotBlank) }
    val names = (parties.map { it.name } + transactionNames).distinct().sorted()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Heading("طرف حساب‌ها", "اطلاعات اشخاص و طرف‌حساب‌های شما")
        Button(onClick = add, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF152F8C))) { Text("＋ طرف حساب جدید") }
        if (names.isEmpty()) Empty("هنوز شخصی ثبت نشده است", "برای افزودن مشخصات، دکمه طرف حساب جدید را بزنید.")
        else names.forEach { name ->
            val count = txs.count { it.party == name }
            val balance = txs.filter { it.party == name }.sumOf { item ->
                when (item.type) {
                    "پول دادم", "قرض دادم" -> item.amount
                    "پول گرفتم", "قرض گرفتم" -> -item.amount
                    else -> 0L
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(44.dp), shape = CircleShape, color = Color(0xFFE8EDF9)) {
                        Box(contentAlignment = Alignment.Center) { Text(name.take(1), color = Color(0xFF152F8C), fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        Text("$count تراکنش", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(kotlin.math.abs(balance)), fontWeight = FontWeight.Bold, color = if (balance >= 0) Color(0xFF1B7F00) else Color(0xFFCF0202))
                        Text(if (balance >= 0) "طلبکارم" else "بدهکارم", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewPartyScreen(existing: List<PartyEntity>, onBack: () -> Unit, onSaved: (PartyEntity) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var nationalCode by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val navy = Color(0xFF152F8C)
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
                }
                val contactId = uri.lastPathSegment
                if (contactId != null) {
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                        arrayOf(contactId), null
                    )?.use { cursor -> if (cursor.moveToFirst()) phone = cursor.getString(0) ?: phone }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().height(138.dp).background(navy).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "بازگشت", tint = Color.White) }
            Text("طرف حساب جدید", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 67.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(29.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                OutlinedButton(onClick = { contactPicker.launch(null) }, modifier = Modifier.width(92.dp).height(94.dp), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(4.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF001870))) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Filled.Contacts, contentDescription = null, modifier = Modifier.size(28.dp))
                        Text("مخاطبان", fontSize = 12.sp)
                    }
                }
                OutlinedTextField(name, { name = it; error = "" }, placeholder = { Text("نام و نام خانوادگی", color = Color.Gray) }, modifier = Modifier.weight(1f).height(94.dp), singleLine = true, shape = RoundedCornerShape(6.dp))
            }
            OutlinedTextField(phone, { phone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' } }, placeholder = { Text("شماره تماس", color = Color.Gray) }, modifier = Modifier.fillMaxWidth().height(94.dp), singleLine = true, shape = RoundedCornerShape(6.dp))
            OutlinedTextField(nationalCode, { nationalCode = it.filter(Char::isDigit) }, placeholder = { Text("کد ملی (اختیاری)", color = Color.Gray) }, modifier = Modifier.fillMaxWidth().height(94.dp), singleLine = true, shape = RoundedCornerShape(6.dp))
            OutlinedTextField(postalCode, { postalCode = it.filter(Char::isDigit) }, placeholder = { Text("کد پستی (اختیاری)", color = Color.Gray) }, modifier = Modifier.fillMaxWidth().height(94.dp), singleLine = true, shape = RoundedCornerShape(6.dp))
            OutlinedTextField(description, { description = it }, placeholder = { Text("توضیحات", color = Color.Gray) }, modifier = Modifier.fillMaxWidth().height(158.dp), minLines = 4, shape = RoundedCornerShape(6.dp))
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
        Button(onClick = {
            if (name.isBlank() || phone.isBlank()) error = "نام و شماره تماس را وارد کنید."
            else if (existing.any { it.phone == phone.trim() }) error = "این شماره تماس قبلاً ثبت شده است."
            else onSaved(PartyEntity(UUID.randomUUID().toString(), name.trim(), phone.trim(), nationalCode.trim(), postalCode.trim(), description.trim()))
        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 59.dp).height(94.dp).padding(bottom = 0.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = navy)) {
            Text("ثبت طرف حساب", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(95.dp))
    }
}

@Composable
private fun ReportsScreen(txs: List<TransactionEntity>) {
    val income = txs.filter { it.type in setOf("درآمد", "پول گرفتم", "هدیه گرفتم", "قرض گرفتم") }.sumOf { it.amount }
    val expense = txs.filter { it.type in setOf("هزینه", "هزینه کردم", "خرج کردم", "پول دادم", "هدیه دادم", "قرض دادم") }.sumOf { it.amount }
    Heading("گزارش‌ها", "خلاصه دریافت‌ها و پرداخت‌ها")
    Metric("مجموع دریافتی", income, Color(0xFF287B55), Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    Metric("مجموع پرداختی", expense, Color(0xFFB64C4C), Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    Text("تعداد تراکنش‌ها: " + txs.size.toString(), fontWeight = FontWeight.SemiBold)
    Text("گزارش‌های تفصیلی و خروجی PDF و Excel در مرحله بعد تکمیل می‌شوند.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SmsScreen() {
    Heading("پیامک‌های بانکی", "شناسایی خودکار؛ ثبت فقط با تأیید شما")
    Empty("بخش پیامک بانکی در حال تکمیل است", "پس از فعال‌سازی دریافت پیامک، پیام‌های بانکی برای بررسی در این صفحه نمایش داده می‌شوند. هیچ تراکنشی بدون تأیید شما ثبت نخواهد شد.")
}

@Composable
private fun MoreScreen(count: Int, openTrash: () -> Unit) {
    Heading("بیشتر", "ابزارها و تنظیمات")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("سطل زباله", fontWeight = FontWeight.Bold)
            Text(count.toString() + " تراکنش حذف‌شده", fontSize = 12.sp)
            TextButton(onClick = openTrash) { Text("مشاهده و بازیابی") }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text("گزارش PDF، Excel، دسته‌بندی‌ها و پشتیبان‌گیری در مراحل بعدی اضافه می‌شوند.", fontSize = 12.sp)
}

@Composable
private fun Empty(title: String, detail: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionTypeSheet(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val choices = listOf(
        "پول دادم" to Color(0xFFFC3030), "پول گرفتم" to Color(0xFF145C01),
        "خرج کردم" to Color(0xFFFC3030), "هزینه کردم" to Color(0xFFFC3030),
        "هدیه دادم" to Color(0xFFFC3030), "هدیه گرفتم" to Color(0xFF145C01)
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(topStart = 42.dp, topEnd = 42.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 34.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            choices.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    pair.forEach { (label, color) ->
                        Button(onClick = { onSelect(label) }, modifier = Modifier.weight(1f).height(76.dp), shape = RoundedCornerShape(44.dp), colors = ButtonDefaults.buttonColors(containerColor = color)) {
                            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionDialog(accounts: List<AccountEntity>, initialType: String, onClose: () -> Unit, save: (TransactionEntity) -> Unit) {
    var type by remember(initialType) { mutableStateOf(initialType) }
    var amount by remember { mutableStateOf("") }
    var account by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var party by remember { mutableStateOf("") }
    var noParty by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())) }
    var typeOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("ثبت تراکنش جدید") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                MenuField(type, typeOpen, { typeOpen = it }, types) { type = it; error = "" }
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("مبلغ به تومان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                MenuField(accounts.firstOrNull { it.id == account }?.name ?: "انتخاب حساب", accountOpen, { accountOpen = it }, accounts.map { it.name }) { selected ->
                    account = accounts.firstOrNull { it.name == selected }?.id ?: ""
                }
                if (type in partyTypes) {
                    OutlinedTextField(party, { party = it; noParty = false }, label = { Text("طرف‌حساب") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(noParty, { noParty = it; if (it) party = "" })
                        Text("بدون طرف‌حساب", fontSize = 12.sp)
                    }
                }
                OutlinedTextField(category, { category = it }, label = { Text("دسته‌بندی (اختیاری)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("تاریخ و ساعت: yyyy-MM-dd HH:mm") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                val value = amount.toLongOrNull()
                val time = runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }.parse(date)?.time }.getOrNull()
                error = when {
                    value == null || value <= 0 -> "مبلغ معتبر وارد کنید."
                    account.isBlank() -> "حساب را انتخاب کنید."
                    type in partyTypes && party.isBlank() && !noParty -> "طرف‌حساب را وارد کنید یا بدون طرف‌حساب را انتخاب کنید."
                    time == null -> "تاریخ و ساعت معتبر نیست."
                    type == "انتقال بین حساب‌ها" -> "انتقال داخلی در مرحله بعد فعال می‌شود."
                    else -> ""
                }
                if (error.isEmpty() && value != null && time != null) {
                    save(TransactionEntity(UUID.randomUUID().toString(), 0L, type, value, account, party.takeIf { it.isNotBlank() }, category.takeIf { it.isNotBlank() }, note.takeIf { it.isNotBlank() }, time))
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("انصراف") } }
    )
}

@Composable
private fun MenuField(value: String, expanded: Boolean, setExpanded: (Boolean) -> Unit, options: List<String>, selected: (String) -> Unit) {
    Column {
        OutlinedButton(onClick = { setExpanded(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(value, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { selected(option); setExpanded(false) }) }
        }
    }
}

@Composable
private fun AccountDialog(onClose: () -> Unit, save: (String, Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("0") }
    AlertDialog(onDismissRequest = onClose, title = { Text("افزودن حساب") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام حساب") }, singleLine = true)
                OutlinedTextField(opening, { opening = it.filter(Char::isDigit) }, label = { Text("موجودی اولیه (تومان)") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) save(name.trim(), opening.toLongOrNull() ?: 0L) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onClose) { Text("انصراف") } })
}

@Composable
private fun TrashDialog(items: List<TransactionEntity>, close: () -> Unit, restore: (String) -> Unit, delete: (String) -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("سطل زباله") },
        text = {
            if (items.isEmpty()) Text("سطل زباله خالی است.")
            else Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                items.forEach { item ->
                    Text(item.type + " • " + money(item.amount) + " تومان", fontWeight = FontWeight.SemiBold)
                    Row {
                        TextButton(onClick = { restore(item.id) }) { Text("بازیابی") }
                        TextButton(onClick = { delete(item.id) }) { Text("حذف دائمی") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("بستن") } })
}

private fun signed(item: TransactionEntity): Long = when (item.type) {
    "درآمد", "پول گرفتم", "هدیه گرفتم", "قرض گرفتم" -> item.amount
    "هزینه", "هزینه کردم", "خرج کردم", "پول دادم", "هدیه دادم", "قرض دادم" -> -item.amount
    else -> 0L
}

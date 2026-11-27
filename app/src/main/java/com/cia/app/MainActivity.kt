package com.cia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF246B57), secondary = Color(0xFFB58A45))) {
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
    val txs by dao.observeTransactions().collectAsState(initial = emptyList())
    val trash by dao.observeTrash().collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addTx by remember { mutableStateOf(false) }
    var addAccount by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }

    LaunchedEffect(accounts.isEmpty()) {
        if (accounts.isEmpty()) dao.insertAccount(AccountEntity(UUID.randomUUID().toString(), "کیف پول نقدی"))
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Text(listOf("⌂", "♙", "▣", "▤")[i]) },
                        label = { Text(label, fontSize = 10.sp) })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0 || tab == 4) FloatingActionButton(onClick = { addTx = true }) { Text("+", fontSize = 24.sp) }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            when (tab) {
                0 -> HomeScreen(accounts, txs, { addTx = true }, { tab = 4 }, { tab = 1 }, { tab = 2 }, { tab = 3 })
                1 -> PartiesScreen(txs)
                2 -> AccountList(accounts, txs) { addAccount = true }
                3 -> ReportsScreen(txs)
                4 -> TransactionList(txs, { addTx = true }) { id -> scope.launch { dao.moveToTrash(id, System.currentTimeMillis()) } }
                5 -> SmsScreen()
                else -> MoreScreen(trash.size) { showTrash = true }
            }
        }
    }

    if (addTx) TransactionDialog(accounts, onClose = { addTx = false }) { item ->
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
    val balance = accounts.sumOf { a -> a.openingBalance + txs.filter { it.accountId == a.id }.sumOf { signed(it) } }
    val incomeTypes = setOf("درآمد", "پول گرفتم", "هدیه گرفتم", "قرض گرفتم")
    val expenseTypes = setOf("هزینه", "پول دادم", "هدیه دادم", "قرض دادم")
    val income = txs.filter { it.type in incomeTypes }.sumOf { it.amount }
    val expense = txs.filter { it.type in expenseTypes }.sumOf { it.amount }
    val parties = txs.mapNotNull { it.party?.takeIf(String::isNotBlank) }.distinct()
    val recent = txs.take(5)
    val dayMillis = 24L * 60L * 60L * 1000L
    val today = System.currentTimeMillis() / dayMillis * dayMillis
    val week = (6 downTo 0).map { offset ->
        val start = today - offset * dayMillis
        val dayTxs = txs.filter { it.occurredAt >= start && it.occurredAt < start + dayMillis }
        dayTxs.filter { it.type in incomeTypes }.sumOf { it.amount } to
            dayTxs.filter { it.type in expenseTypes }.sumOf { it.amount }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("دفتر حساب و کتاب", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("همه حساب‌ها، یک نگاه", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = CircleShape, color = Color(0xFFE7F1EC), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("CIA", color = Color(0xFF246B57), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF174F43))) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("موجودی کل حساب‌ها", color = Color.White.copy(alpha = .82f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("● فعال", color = Color(0xFFBCE6D0), fontSize = 10.sp)
                }
                Text(money(balance), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("تومان", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                HorizontalDivider(color = Color.White.copy(alpha = .18f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(accounts.size.toString() + " حساب فعال", color = Color.White.copy(alpha = .85f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = openAccounts) { Text("مدیریت حساب‌ها", color = Color.White, fontSize = 11.sp) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryTile("دریافتی", income, Color(0xFF247B58), "↑", Modifier.weight(1f))
            SummaryTile("پرداختی", expense, Color(0xFFB64C4C), "↓", Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("روند دخل‌وخرج", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("مقایسه دریافت و پرداخت در ۷ روز اخیر", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("۷ روز", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                }
                CashFlowBars(week)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(8.dp), shape = CircleShape, color = Color(0xFF287B55)) {}
                        Spacer(Modifier.width(5.dp)); Text("دریافت", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(8.dp), shape = CircleShape, color = Color(0xFFD77A70)) {}
                        Spacer(Modifier.width(5.dp)); Text("پرداخت", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("دسترسی سریع", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = add, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(vertical = 13.dp)) { Text("＋ ثبت تراکنش", fontSize = 12.sp) }
                OutlinedButton(onClick = all, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(vertical = 13.dp)) { Text("فهرست تراکنش‌ها", fontSize = 12.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickLink("اشخاص", parties.size.toString() + " طرف‌حساب", "♙", people, Modifier.weight(1f))
                QuickLink("حساب‌ها", accounts.size.toString() + " حساب", "▣", openAccounts, Modifier.weight(1f))
                QuickLink("گزارش‌ها", "خلاصه مالی", "▤", reports, Modifier.weight(1f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("آخرین تراکنش‌ها", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = all) { Text("مشاهده همه", fontSize = 11.sp) }
            }
            if (recent.isEmpty()) Empty("هنوز تراکنشی ثبت نشده است", "با ثبت اولین دریافت یا پرداخت، گردش مالی شما اینجا نمایش داده می‌شود.")
            else recent.forEach { item -> TxRow(item) {} }
        }
        Spacer(Modifier.height(4.dp))
    }
}

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
                Text("▣", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
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
private fun PartiesScreen(txs: List<TransactionEntity>) {
    val parties = txs.mapNotNull { it.party?.takeIf { name -> name.isNotBlank() } }.distinct().sorted()
    Heading("اشخاص", "طرف‌حساب‌های ثبت‌شده در تراکنش‌ها")
    if (parties.isEmpty()) Empty("هنوز شخصی ثبت نشده است", "هنگام ثبت تراکنش، نام طرف‌حساب را وارد کنید تا اینجا نمایش داده شود.")
    else parties.forEach { name ->
        val count = txs.count { it.party == name }
        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(Modifier.padding(15.dp)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(count.toString() + " تراکنش", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReportsScreen(txs: List<TransactionEntity>) {
    val income = txs.filter { it.type in setOf("درآمد", "پول گرفتم", "هدیه گرفتم", "قرض گرفتم") }.sumOf { it.amount }
    val expense = txs.filter { it.type in setOf("هزینه", "پول دادم", "هدیه دادم", "قرض دادم") }.sumOf { it.amount }
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
            Text("○", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransactionDialog(accounts: List<AccountEntity>, onClose: () -> Unit, save: (TransactionEntity) -> Unit) {
    var type by remember { mutableStateOf("هزینه") }
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
            Text("⌄")
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
    "هزینه", "پول دادم", "هدیه دادم", "قرض دادم" -> -item.amount
    else -> 0L
}

package com.example.habitly

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// ---------- Theme ----------
val Ink = Color(0xFF3B3552)
val Primary = Color(0xFF9B87D6)
val Bg = Color(0xFFFDF8FF)
val PASTELS = listOf(0xFFCDB4F6, 0xFFFFD6A5, 0xFFA7D8F0, 0xFFB5EAD7, 0xFFF8C8DC, 0xFFFFF1A8, 0xFFFFC9C9, 0xFFC7E9B0)

// ---------- Model ----------
data class Cat(
    val id: String,
    val name: String,
    val color: Long,
    val unit: String = "",
    val target: Double = 0.0,
    val items: List<String> = emptyList(),
    val anyCounts: Boolean = false,
    val custom: Boolean = false
)

val DEFAULTS = listOf(
    Cat("sleep", "Sleep", PASTELS[0], "hrs", 8.0),
    Cat("food", "Food", PASTELS[1],
        items = listOf("Healthy Carbs", "Fibers", "Protein", "Micronutrients", "No Sugar", "No Junk Food")),
    Cat("water", "Water", PASTELS[2], "L", 3.0),
    Cat("workout", "Workout", PASTELS[3],
        items = listOf("Rest Day", "Arms", "Biceps", "Triceps", "Shoulder", "Chest", "Abs", "Back", "Legs", "Glutes"),
        anyCounts = true),
    Cat("meditation", "Meditation", PASTELS[4], "min", 15.0),
    Cat("reading", "Reading", PASTELS[5], "hrs", 1.0)
)

fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(d)

// ---------- Storage ----------
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("habits", Context.MODE_PRIVATE)
    val values = mutableStateMapOf<String, Double>()
    val targets = mutableStateMapOf<String, Double>()
    val customs = mutableStateListOf<Cat>()

    init {
        sp.getString("values", null)?.let { s ->
            val o = JSONObject(s); o.keys().forEach { values[it] = o.getDouble(it) }
        }
        sp.getString("targets", null)?.let { s ->
            val o = JSONObject(s); o.keys().forEach { targets[it] = o.getDouble(it) }
        }
        sp.getString("customs", null)?.let { s ->
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val its = o.getJSONArray("items")
                customs.add(Cat(o.getString("id"), o.getString("name"), o.getLong("color"),
                    o.getString("unit"), o.getDouble("target"),
                    (0 until its.length()).map { its.getString(it) }, false, true))
            }
        }
    }

    private fun save() {
        val arr = JSONArray()
        customs.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)
                .put("unit", it.unit).put("target", it.target).put("items", JSONArray(it.items)))
        }
        sp.edit()
            .putString("values", JSONObject(values.toMap()).toString())
            .putString("targets", JSONObject(targets.toMap()).toString())
            .putString("customs", arr.toString())
            .apply()
    }

    fun cats(): List<Cat> = (DEFAULTS + customs).map { it.copy(target = targets[it.id] ?: it.target) }
    fun key(d: LocalDate, id: String, item: String = "") = "$d|$id|$item"
    fun set(k: String, v: Double) { if (v == 0.0) values.remove(k) else values[k] = v; save() }
    fun setTarget(id: String, v: Double) { targets[id] = v; save() }
    fun addCustom(name: String, unit: String, target: Double, items: List<String>) {
        customs.add(Cat("c" + System.currentTimeMillis(), name, PASTELS[(6 + customs.size) % PASTELS.size],
            unit, target, items, false, true)); save()
    }
    fun removeCustom(id: String) { customs.removeAll { it.id == id }; save() }
    fun hasData(d: LocalDate): Boolean { val p = "$d|"; return values.keys.any { it.startsWith(p) } }

    fun score(c: Cat, d: LocalDate): Double {
        if (c.items.isEmpty()) {
            val v = values[key(d, c.id)] ?: 0.0
            return if (c.target > 0) (v / c.target).coerceIn(0.0, 1.0) else if (v > 0) 1.0 else 0.0
        }
        val n = c.items.count { values[key(d, c.id, it)] == 1.0 }
        return if (c.anyCounts) (if (n > 0) 1.0 else 0.0) else n.toDouble() / c.items.size
    }
}

// ---------- Activity ----------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Primary, background = Bg, surface = Bg, onSurface = Ink)) {
                App(store)
            }
        }
    }
}

@Composable
fun App(st: Store) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("📅") }, label = { Text("Today") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("📊") }, label = { Text("Progress") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) TodayScreen(st) else ProgressScreen(st)
        }
    }
}

// ---------- Today ----------
@Composable
fun TodayScreen(st: Store) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var sel by remember { mutableStateOf(LocalDate.now()) }
    var showAdd by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)); Calendar(st, month, sel, { month = it }, { sel = it }) }
        item { Text(sel.toString(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
        items(st.cats(), key = { it.id }) { CatCard(st, it, sel) }
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add category") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showAdd) AddDialog({ showAdd = false }) { n, u, t, i -> st.addCustom(n, u, t, i); showAdd = false }
}

@Composable
fun Calendar(st: Store, month: YearMonth, sel: LocalDate, onMonth: (YearMonth) -> Unit, onSel: (LocalDate) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ onMonth(month.minusMonths(1)) }) { Text("‹", fontSize = 22.sp) }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Ink
                )
                TextButton({ onMonth(month.plusMonths(1)) }) { Text("›", fontSize = 22.sp) }
            }
            Row {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = Ink)
                }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val len = month.lengthOfMonth()
            val rows = (offset + len + 6) / 7
            val today = LocalDate.now()
            for (r in 0 until rows) {
                Row {
                    for (col in 0 until 7) {
                        val n = r * 7 + col - offset + 1
                        if (n < 1 || n > len) {
                            Spacer(Modifier.weight(1f).height(44.dp))
                        } else {
                            val d = month.atDay(n)
                            Box(
                                Modifier.weight(1f).height(44.dp).padding(2.dp).clip(CircleShape)
                                    .background(if (d == sel) Primary else if (d == today) Color(0xFFE9DDFB) else Color.Transparent)
                                    .clickable { onSel(d) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$n", fontSize = 14.sp, color = if (d == sel) Color.White else Ink)
                                    if (st.hasData(d)) Box(
                                        Modifier.size(5.dp).clip(CircleShape)
                                            .background(if (d == sel) Color.White else Primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatCard(st: Store, c: Cat, d: LocalDate) {
    var editVal by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf(false) }
    val s = st.score(c, d)
    Card(colors = CardDefaults.cardColors(containerColor = Color(c.color)), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("${(s * 100).toInt()}%", color = Ink)
                if (c.custom) TextButton({ st.removeCustom(c.id) }) { Text("Delete", color = Ink, fontSize = 12.sp) }
            }
            LinearProgressIndicator(
                progress = { s.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Ink, trackColor = Color.White.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            if (c.items.isEmpty()) {
                val v = st.values[st.key(d, c.id)] ?: 0.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${fmt(v)} / ${fmt(c.target)} ${c.unit}", Modifier.weight(1f), color = Ink, fontSize = 16.sp)
                    TextButton({ editVal = true }) { Text("Log", color = Ink) }
                    TextButton({ editTarget = true }) { Text("Target", color = Ink) }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    c.items.forEach { item ->
                        val k = st.key(d, c.id, item)
                        val on = st.values[k] == 1.0
                        FilterChip(
                            selected = on,
                            onClick = { st.set(k, if (on) 0.0 else 1.0) },
                            label = { Text(item) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = 0.6f),
                                selectedContainerColor = Color.White,
                                labelColor = Ink, selectedLabelColor = Ink
                            )
                        )
                    }
                }
                if (c.id == "food") Text("Tap to mark Yes. Unmarked = No.", fontSize = 11.sp, color = Ink)
            }
        }
    }
    if (editVal) NumDialog("${c.name} today (${c.unit})", st.values[st.key(d, c.id)] ?: 0.0,
        { st.set(st.key(d, c.id), it); editVal = false }, { editVal = false })
    if (editTarget) NumDialog("${c.name} target (${c.unit})", c.target,
        { st.setTarget(c.id, it); editTarget = false }, { editTarget = false })
}

@Composable
fun NumDialog(title: String, initial: Double, onDone: (Double) -> Unit, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(if (initial == 0.0) "" else fmt(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(t, { t = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        },
        confirmButton = { TextButton({ onDone(t.toDoubleOrNull() ?: 0.0) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddDialog(onDismiss: () -> Unit, onAdd: (String, String, Double, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var items by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(items, { items = it }, label = { Text("Checklist items, comma separated (optional)") })
                Text("Or leave empty for a numeric goal:", fontSize = 12.sp)
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit (e.g. pages)") }, singleLine = true)
                OutlinedTextField(target, { target = it }, label = { Text("Daily target") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton({
                if (name.isNotBlank()) onAdd(name.trim(), unit.trim(), target.toDoubleOrNull() ?: 1.0,
                    items.split(",").map { it.trim() }.filter { it.isNotEmpty() })
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

// ---------- Progress ----------
@Composable
fun ProgressScreen(st: Store) {
    var weekly by remember { mutableStateOf(true) }
    val today = LocalDate.now()
    val days = if (weekly) (6 downTo 0).map { today.minusDays(it.toLong()) }
    else YearMonth.now().let { ym -> (1..ym.lengthOfMonth()).map { ym.atDay(it) } }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(weekly, { weekly = true }, { Text("Last 7 days") })
                FilterChip(!weekly, { weekly = false }, { Text("This month") })
            }
        }
        items(st.cats(), key = { it.id }) { c ->
            val scores = days.map { st.score(c, it) }
            val done = scores.count { it >= 1.0 }
            Card(colors = CardDefaults.cardColors(containerColor = Color(c.color)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Text(c.name, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("avg ${(scores.average() * 100).toInt()}%", color = Ink)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().height(60.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        scores.forEach { s ->
                            Box(
                                Modifier.weight(1f).fillMaxHeight(maxOf(s.toFloat(), 0.05f))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Ink.copy(alpha = if (s >= 1.0) 0.7f else 0.3f))
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Goal reached: $done / ${days.size} days", fontSize = 13.sp, color = Ink)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

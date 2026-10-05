package com.kanan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

private val Brown = Color(0xFF1A0F0A)
private val CardBrown = Color(0xFF2A1A12)
private val Gold = Color(0xFFC9A24B)
private val Cream = Color(0xFFF5ECD9)

private val auth get() = FirebaseAuth.getInstance()
private val db get() = FirebaseFirestore.getInstance()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scheme = darkColorScheme(
                primary = Gold, onPrimary = Brown,
                background = Brown, onBackground = Cream,
                surface = CardBrown, onSurface = Cream,
                surfaceVariant = CardBrown, onSurfaceVariant = Cream,
                secondaryContainer = Gold, onSecondaryContainer = Brown
            )
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = Brown) { App() }
                }
            }
        }
    }
}

@Composable
fun App() {
    var loggedIn by remember { mutableStateOf(auth.currentUser != null) }
    if (!loggedIn) AuthScreen { loggedIn = true }
    else HomeScreen { auth.signOut(); loggedIn = false }
}

@Composable
fun Logo(size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Gold),
        contentAlignment = Alignment.Center
    ) {
        Text("ق", color = Brown, fontSize = (size / 2).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AuthScreen(onDone: () -> Unit) {
    var signup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Logo(110)
        Text("قهوة الكنان", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Gold)
        if (signup) {
            OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("رقم الهاتف") }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(email, { email = it }, label = { Text("البريد الإلكتروني") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            pass, { pass = it }, label = { Text("كلمة السر (6 أحرف على الأقل)") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            error = ""
            if (email.isBlank() || pass.length < 6 || (signup && (name.isBlank() || phone.isBlank()))) {
                error = "تأكد من تعبئة كل الحقول، وكلمة السر 6 أحرف على الأقل"
                return@Button
            }
            busy = true
            val fail = { _: Exception -> error = "تعذر إتمام العملية، تحقق من البيانات ومن الإنترنت وحاول مرة أخرى"; busy = false }
            if (signup) {
                auth.createUserWithEmailAndPassword(email.trim(), pass).addOnSuccessListener { r ->
                    db.collection("users").document(r.user!!.uid).set(
                        mapOf(
                            "name" to name.trim(), "phone" to phone.trim(), "email" to email.trim(),
                            "role" to "customer", "shopId" to "kanan",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).addOnSuccessListener { onDone() }.addOnFailureListener(fail)
                }.addOnFailureListener(fail)
            } else {
                auth.signInWithEmailAndPassword(email.trim(), pass)
                    .addOnSuccessListener { onDone() }.addOnFailureListener(fail)
            }
        }) { Text(if (signup) "إنشاء حساب" else "تسجيل الدخول") }
        TextButton(onClick = { signup = !signup; error = "" }) {
            Text(if (signup) "عندي حساب" else "ما عندي حساب؟ أنشئ حساب", color = Gold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onLogout: () -> Unit) {
    var cats by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var products by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val r1 = db.collection("categories").limit(30).addSnapshotListener { s, e ->
            if (e == null) cats = s?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty()
        }
        val r2 = db.collection("products").limit(50).addSnapshotListener { s, e ->
            if (e != null) error = "تعذر تحميل المنتجات، تحقق من الإنترنت"
            else { error = ""; products = s?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty() }
        }
        onDispose { r1.remove(); r2.remove() }
    }

    val shown = products.filter {
        (selected == null || it["categoryId"] == selected) &&
            (query.isBlank() || "${it["name"]} ${it["description"]}".contains(query, true))
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Logo(40)
                Text("قهوة الكنان", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Gold)
            }
            TextButton(onClick = onLogout) { Text("خروج", color = Gold) }
        }
        OutlinedTextField(query, { query = it }, label = { Text("ابحث عن منتج") }, modifier = Modifier.fillMaxWidth())
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            item { FilterChip(selected = selected == null, onClick = { selected = null }, label = { Text("الكل") }) }
            items(cats, key = { it["id"] as String }) { c ->
                FilterChip(
                    selected = selected == c["id"],
                    onClick = { selected = c["id"] as String },
                    label = { Text("${c["name"]}") }
                )
            }
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        if (shown.isEmpty() && error.isEmpty()) Text("لا توجد منتجات بعد", Modifier.padding(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it["id"] as String }) { p ->
                val available = p["available"] != false
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBrown)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${p["name"]}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        val d = "${p["description"] ?: ""}"
                        if (d.isNotBlank()) Text(d)
                        Text("${p["price"]} ل.س", color = Gold, fontWeight = FontWeight.Bold)
                        if (!available) Text("غير متوفر حاليًا", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

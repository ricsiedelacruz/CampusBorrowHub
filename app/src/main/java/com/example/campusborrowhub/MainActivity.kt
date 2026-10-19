@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.campusborrowhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.clickable
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.example.campusborrowhub.ui.theme.CampusBorrowHubTheme
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =====================================================================
//  Campus BorrowHub  -  real version (Jetpack Compose + Firebase)
//  Firebase Authentication  : sign-up / sign-in / school-email verification
//  Cloud Firestore          : listings, requests, chats, ratings, notifications
// =====================================================================

/** Only emails ending in this domain can use the app. */
const val SCHOOL_DOMAIN = "plmun.edu.ph"

const val DAY = 86_400_000L
val CATEGORIES = listOf("Textbooks", "Calculators", "Chargers & Cables", "Lab Equipment", "Other")
val CONDITIONS = listOf("New", "Like new", "Good", "Fair")
val RETURN_CONDITIONS = listOf("Same as before", "Slightly worn", "Damaged")

fun catEmoji(c: String): String = when (c) {
    "Textbooks" -> "📚"
    "Calculators" -> "🧮"
    "Chargers & Cables" -> "🔌"
    "Lab Equipment" -> "🔬"
    else -> "🎒"
}

fun fmtDate(ms: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms))
fun fmtTime(ms: Long): String = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ms))

fun daysLeft(due: Long, now: Long): Int = Math.ceil((due - now).toDouble() / DAY).toInt()

fun dueText(left: Int): String = when {
    left < 0 -> "Overdue by ${-left} day(s)"
    left == 0 -> "Due today"
    left == 1 -> "Due tomorrow"
    else -> "Due in $left days"
}

fun plural(n: Int, word: String): String = if (n == 1) "$n $word" else "$n ${word}s"

fun friendly(e: Exception): String {
    if (e is FirebaseNetworkException) return "No internet connection. Please check your network and try again."
    if (e is FirebaseTooManyRequestsException) return "Too many attempts. Please wait a few minutes and try again."
    if (e is FirebaseFirestoreException) {
        return when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Permission denied. Make sure your email is verified and the Firestore rules are published."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Can't reach the server right now. Showing saved data."
            else -> e.message ?: "Something went wrong."
        }
    }
    if (e is FirebaseAuthException) {
        return when (e.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "That email is already registered. Try signing in instead."
            "ERROR_WEAK_PASSWORD" -> "Password is too weak. Use at least 8 characters."
            "ERROR_INVALID_EMAIL" -> "That email address isn't valid."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" ->
                "Incorrect email or password."
            "ERROR_USER_DISABLED" -> "This account has been disabled."
            else -> e.message ?: "Authentication failed."
        }
    }
    return e.message ?: "Something went wrong."
}

// ------------------------------- Models -------------------------------

enum class ReqStatus(val label: String, val color: Color) {
    PENDING("Pending", Color(0xFFF59E0B)),
    APPROVED("Approved", Color(0xFF3B82F6)),
    BORROWED("Borrowed", Color(0xFF8B5CF6)),
    RETURNED("Returned", Color(0xFF10B981)),
    DECLINED("Declined", Color(0xFFEF4444)),
    CANCELLED("Cancelled", Color(0xFF6B7280))
}

enum class Stage { LOADING, SIGNED_OUT, VERIFY, ERROR, READY }

class User(val id: String, val name: String, val email: String, val studentId: String)

class HistoryEntry(val at: Long, val borrowerName: String, val condition: String, val note: String)

class Item(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val title: String,
    val category: String,
    val description: String,
    val condition: String,
    val availableUntil: Long,
    val maxDays: Int,
    val status: String,
    val createdAt: Long,
    val history: List<HistoryEntry>
)

class BorrowRequest(
    val id: String,
    val itemId: String,
    val itemTitle: String,
    val borrowerId: String,
    val borrowerName: String,
    val lenderId: String,
    val lenderName: String,
    val days: Int,
    val note: String,
    val createdAt: Long,
    val status: ReqStatus,
    val dueAt: Long,
    val returnCondition: String,
    val returnNote: String,
    val returnedAt: Long
)

class Message(
    val id: String,
    val itemId: String,
    val itemTitle: String,
    val fromId: String,
    val fromName: String,
    val toId: String,
    val toName: String,
    val text: String,
    val at: Long
)

class Review(
    val fromId: String,
    val fromName: String,
    val toId: String,
    val requestId: String,
    val stars: Int,
    val comment: String
)

class Notice(val id: String, val userId: String, val text: String, val at: Long, val read: Boolean)

sealed class Screen {
    object Tabs : Screen()
    object AddItem : Screen()
    object Notices : Screen()
    data class Detail(val itemId: String) : Screen()
    data class Chat(val itemId: String, val itemTitle: String, val otherId: String, val otherName: String) : Screen()
}

// ------------------------------- Firestore -> models -------------------------------

fun DocumentSnapshot.toItem(): Item {
    val hist = (get("history") as? List<*>)?.mapNotNull { h ->
        val m = h as? Map<*, *>
        if (m == null) null else HistoryEntry(
            (m["at"] as? Number)?.toLong() ?: 0L,
            m["borrowerName"] as? String ?: "",
            m["condition"] as? String ?: "",
            m["note"] as? String ?: ""
        )
    } ?: emptyList()
    return Item(
        id = id,
        ownerId = getString("ownerId") ?: "",
        ownerName = getString("ownerName") ?: "Student",
        title = getString("title") ?: "",
        category = getString("category") ?: "Other",
        description = getString("description") ?: "",
        condition = getString("condition") ?: "Good",
        availableUntil = getLong("availableUntil") ?: 0L,
        maxDays = (getLong("maxDays") ?: 1L).toInt(),
        status = getString("status") ?: "AVAILABLE",
        createdAt = getLong("createdAt") ?: 0L,
        history = hist
    )
}

fun DocumentSnapshot.toRequest(): BorrowRequest {
    val st = try {
        ReqStatus.valueOf(getString("status") ?: "PENDING")
    } catch (e: Exception) {
        ReqStatus.PENDING
    }
    return BorrowRequest(
        id = id,
        itemId = getString("itemId") ?: "",
        itemTitle = getString("itemTitle") ?: "Item",
        borrowerId = getString("borrowerId") ?: "",
        borrowerName = getString("borrowerName") ?: "Student",
        lenderId = getString("lenderId") ?: "",
        lenderName = getString("lenderName") ?: "Student",
        days = (getLong("days") ?: 1L).toInt(),
        note = getString("note") ?: "",
        createdAt = getLong("createdAt") ?: 0L,
        status = st,
        dueAt = getLong("dueAt") ?: 0L,
        returnCondition = getString("returnCondition") ?: "",
        returnNote = getString("returnNote") ?: "",
        returnedAt = getLong("returnedAt") ?: 0L
    )
}

fun DocumentSnapshot.toMessage(): Message = Message(
    id = id,
    itemId = getString("itemId") ?: "",
    itemTitle = getString("itemTitle") ?: "Item",
    fromId = getString("fromId") ?: "",
    fromName = getString("fromName") ?: "Student",
    toId = getString("toId") ?: "",
    toName = getString("toName") ?: "Student",
    text = getString("text") ?: "",
    at = getLong("at") ?: 0L
)

fun DocumentSnapshot.toReview(): Review = Review(
    fromId = getString("fromId") ?: "",
    fromName = getString("fromName") ?: "Student",
    toId = getString("toId") ?: "",
    requestId = getString("requestId") ?: "",
    stars = (getLong("stars") ?: 5L).toInt(),
    comment = getString("comment") ?: ""
)

fun DocumentSnapshot.toNotice(): Notice = Notice(
    id = id,
    userId = getString("userId") ?: "",
    text = getString("text") ?: "",
    at = getLong("at") ?: 0L,
    read = getBoolean("read") ?: false
)

// ------------------------------- Store (ViewModel) -------------------------------

class Store : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val regs = mutableListOf<ListenerRegistration>()
    private val reminded = mutableSetOf<String>()
    private var sessionUid: String? = null

    var stage by mutableStateOf(Stage.LOADING)
    var loadError by mutableStateOf("")
    var syncError by mutableStateOf("")
    var me by mutableStateOf<User?>(null)

    var items by mutableStateOf<List<Item>>(emptyList())
    private var asBorrower by mutableStateOf<List<BorrowRequest>>(emptyList())
    private var asLender by mutableStateOf<List<BorrowRequest>>(emptyList())
    var reviews by mutableStateOf<List<Review>>(emptyList())
    var messages by mutableStateOf<List<Message>>(emptyList())
    var notices by mutableStateOf<List<Notice>>(emptyList())
    var chatArchived by mutableStateOf<Map<String, Long>>(emptyMap())
    var chatDeleted by mutableStateOf<Map<String, Long>>(emptyMap())

    val requests: List<BorrowRequest>
        get() = (asBorrower + asLender).distinctBy { it.id }
    val myId: String
        get() = me?.id ?: ""

    private val authListener = FirebaseAuth.AuthStateListener { fa -> onAuth(fa.currentUser) }

    init {
        auth.addAuthStateListener(authListener)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        stopSession()
        super.onCleared()
    }

    fun currentEmail(): String = auth.currentUser?.email ?: ""

    // ----- session handling -----

    private fun onAuth(user: FirebaseUser?) {
        if (user == null) {
            stopSession()
            stage = Stage.SIGNED_OUT
            return
        }
        if (!user.isEmailVerified) {
            stopSession()
            stage = Stage.VERIFY
            return
        }
        if (sessionUid == user.uid && stage == Stage.READY) return
        startSession(user)
    }

    private fun startSession(user: FirebaseUser) {
        stopSession()
        sessionUid = user.uid
        stage = Stage.LOADING
        val ref = db.collection("users").document(user.uid)
        ref.get()
            .addOnSuccessListener { snap ->
                if (sessionUid == user.uid) {
                    val u: User
                    if (snap.exists()) {
                        u = User(
                            user.uid,
                            snap.getString("name") ?: (user.displayName ?: "Student"),
                            snap.getString("email") ?: (user.email ?: ""),
                            snap.getString("studentId") ?: "-"
                        )
                    } else {
                        u = User(user.uid, user.displayName ?: (user.email ?: "Student"), user.email ?: "", "-")
                        ref.set(
                            mapOf(
                                "name" to u.name, "email" to u.email, "studentId" to u.studentId,
                                "createdAt" to System.currentTimeMillis()
                            )
                        )
                    }
                    me = u
                    chatArchived = if (snap.exists()) snap.longMap("chatArchived") else emptyMap()
                    chatDeleted = if (snap.exists()) snap.longMap("chatDeleted") else emptyMap()
                    attachListeners(u.id)
                    stage = Stage.READY
                }
            }
            .addOnFailureListener { e ->
                loadError = friendly(e)
                stage = Stage.ERROR
            }
    }

    private fun stopSession() {
        regs.forEach { it.remove() }
        regs.clear()
        sessionUid = null
        me = null
        items = emptyList()
        asBorrower = emptyList()
        asLender = emptyList()
        reviews = emptyList()
        messages = emptyList()
        notices = emptyList()
        chatArchived = emptyMap()
        chatDeleted = emptyMap()
        syncError = ""
    }

    private fun attachListeners(uid: String) {
        regs += db.collection("items").addSnapshotListener { s, e ->
            if (e != null) {
                syncError = friendly(e)
                return@addSnapshotListener
            }
            if (s != null) {
                syncError = ""
                items = s.documents.map { it.toItem() }.sortedByDescending { it.createdAt }
            }
        }
        regs += db.collection("requests").whereEqualTo("borrowerId", uid).addSnapshotListener { s, e ->
            if (e != null) {
                syncError = friendly(e)
                return@addSnapshotListener
            }
            if (s != null) {
                asBorrower = s.documents.map { it.toRequest() }
                checkReminders()
            }
        }
        regs += db.collection("requests").whereEqualTo("lenderId", uid).addSnapshotListener { s, e ->
            if (e != null) {
                syncError = friendly(e)
                return@addSnapshotListener
            }
            if (s != null) {
                asLender = s.documents.map { it.toRequest() }
                checkReminders()
            }
        }
        regs += db.collection("reviews").addSnapshotListener { s, e ->
            if (e != null) {
                syncError = friendly(e)
                return@addSnapshotListener
            }
            if (s != null) reviews = s.documents.map { it.toReview() }
        }
        regs += db.collection("messages").whereArrayContains("participants", uid).addSnapshotListener { s, e ->
            if (e != null) {
                syncError = friendly(e)
                return@addSnapshotListener
            }
            if (s != null) messages = s.documents.map { it.toMessage() }
        }
        regs += db.collection("notices").whereEqualTo("userId", uid).addSnapshotListener { s, e ->
            if (e != null) {
                syncError = friendly(e)
                return@addSnapshotListener
            }
            if (s != null) notices = s.documents.map { it.toNotice() }
        }
    }

    fun retry() {
        val u = auth.currentUser
        if (u == null) {
            stage = Stage.SIGNED_OUT
        } else {
            sessionUid = null
            onAuth(u)
        }
    }

    // ----- authentication -----

    fun signUp(name: String, email: String, studentId: String, password: String, done: (String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { res ->
                val u = res.user
                if (u != null) {
                    u.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())
                    db.collection("users").document(u.uid).set(
                        mapOf(
                            "name" to name, "email" to email, "studentId" to studentId,
                            "createdAt" to System.currentTimeMillis()
                        )
                    )
                    u.sendEmailVerification()
                }
                done(null)
            }
            .addOnFailureListener { e -> done(friendly(e)) }
    }

    fun signIn(email: String, password: String, done: (String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { done(null) }
            .addOnFailureListener { e -> done(friendly(e)) }
    }

    fun resetPassword(email: String, done: (String?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { done(null) }
            .addOnFailureListener { e -> done(friendly(e)) }
    }

    fun resendVerification(done: (String?) -> Unit) {
        val u = auth.currentUser
        if (u == null) {
            done("You are signed out.")
            return
        }
        u.sendEmailVerification()
            .addOnSuccessListener { done(null) }
            .addOnFailureListener { e -> done(friendly(e)) }
    }

    fun refreshVerification(done: (String?) -> Unit) {
        val u = auth.currentUser
        if (u == null) {
            done("You are signed out.")
            return
        }
        u.reload()
            .addOnSuccessListener {
                val cu = auth.currentUser
                if (cu != null && cu.isEmailVerified) {
                    // refresh the sign-in token so Firestore rules see email_verified = true
                    cu.getIdToken(true).addOnCompleteListener {
                        onAuth(cu)
                        done(null)
                    }
                } else {
                    done("Not verified yet. Open the link in the email first (check your spam folder), then try again.")
                }
            }
            .addOnFailureListener { e -> done(friendly(e)) }
    }

    fun signOut() {
        auth.signOut()
    }

    // ----- helpers -----

    fun ratingOf(uid: String): Pair<Double, Int> {
        val l = reviews.filter { it.toId == uid }
        return if (l.isEmpty()) Pair(0.0, 0) else Pair(l.map { it.stars }.average(), l.size)
    }

    fun ratingText(uid: String): String {
        val (avg, n) = ratingOf(uid)
        return if (n == 0) "New user" else String.format(Locale.US, "★ %.1f (%d)", avg, n)
    }

    fun itemStatus(item: Item): String = when {
        item.status != "AVAILABLE" -> "Unavailable"
        item.availableUntil < System.currentTimeMillis() -> "Expired"
        else -> "Available"
    }

    private fun noticeData(userId: String, text: String): Map<String, Any> =
        mapOf("userId" to userId, "text" to text, "at" to System.currentTimeMillis(), "read" to false)

    private fun fail(e: Exception) {
        syncError = friendly(e)
    }

    private fun checkReminders() {
        val uid = myId
        if (uid.isEmpty()) return
        val now = System.currentTimeMillis()
        val dayKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
        for (r in requests) {
            if (r.status != ReqStatus.BORROWED) continue
            val left = daysLeft(r.dueAt, now)
            if (left > 1) continue
            if (r.borrowerId == uid) {
                pushReminder("due_${r.id}_${uid}_$dayKey", uid, "Reminder: \"${r.itemTitle}\" - ${dueText(left)}.")
            } else if (r.lenderId == uid && left < 0) {
                pushReminder(
                    "late_${r.id}_${uid}_$dayKey", uid,
                    "\"${r.itemTitle}\" borrowed by ${r.borrowerName} is overdue by ${-left} day(s)."
                )
            }
        }
    }

    private fun pushReminder(id: String, userId: String, text: String) {
        if (!reminded.add(id)) return
        // If this reminder already exists (same day, after an app restart) the rules refuse the
        // overwrite on purpose, so a failure here is expected and ignored.
        db.collection("notices").document(id).set(noticeData(userId, text)).addOnFailureListener { }
    }

    // ----- listings -----

    fun addItem(title: String, category: String, description: String, condition: String, availDays: Int, maxDays: Int) {
        val u = me ?: return
        val now = System.currentTimeMillis()
        db.collection("items").add(
            mapOf(
                "ownerId" to u.id, "ownerName" to u.name, "title" to title, "category" to category,
                "description" to description, "condition" to condition,
                "availableUntil" to now + availDays * DAY, "maxDays" to maxDays,
                "status" to "AVAILABLE", "createdAt" to now, "history" to emptyList<Any>()
            )
        ).addOnFailureListener { fail(it) }
    }

    fun deleteItem(item: Item) {
        db.collection("items").document(item.id).delete().addOnFailureListener { fail(it) }
    }

    fun report(item: Item, reason: String) {
        val u = me ?: return
        db.collection("reports").add(
            mapOf(
                "itemId" to item.id, "itemTitle" to item.title, "ownerId" to item.ownerId,
                "reporterId" to u.id, "reason" to reason, "at" to System.currentTimeMillis()
            )
        ).addOnFailureListener { fail(it) }
    }

    // ----- borrowing flow -----

    fun createRequest(item: Item, days: Int, note: String) {
        val u = me ?: return
        val batch = db.batch()
        batch.set(
            db.collection("requests").document(),
            mapOf(
                "itemId" to item.id, "itemTitle" to item.title,
                "borrowerId" to u.id, "borrowerName" to u.name,
                "lenderId" to item.ownerId, "lenderName" to item.ownerName,
                "days" to days, "note" to note, "createdAt" to System.currentTimeMillis(),
                "status" to ReqStatus.PENDING.name, "dueAt" to 0L,
                "returnCondition" to "", "returnNote" to "", "returnedAt" to 0L
            )
        )
        batch.set(
            db.collection("notices").document(),
            noticeData(item.ownerId, "${u.name} wants to borrow \"${item.title}\" for ${plural(days, "day")}.")
        )
        batch.commit().addOnFailureListener { fail(it) }
    }

    fun approve(r: BorrowRequest) {
        val batch = db.batch()
        batch.update(db.collection("requests").document(r.id), "status", ReqStatus.APPROVED.name)
        batch.update(db.collection("items").document(r.itemId), "status", "RESERVED")
        batch.set(
            db.collection("notices").document(),
            noticeData(r.borrowerId, "${r.lenderName} approved your request for \"${r.itemTitle}\". Message them to arrange pickup.")
        )
        requests.filter { it.itemId == r.itemId && it.id != r.id && it.status == ReqStatus.PENDING }.forEach { o ->
            batch.update(db.collection("requests").document(o.id), "status", ReqStatus.DECLINED.name)
            batch.set(
                db.collection("notices").document(),
                noticeData(o.borrowerId, "Your request for \"${o.itemTitle}\" was declined (it was given to someone else).")
            )
        }
        batch.commit().addOnFailureListener { fail(it) }
    }

    fun decline(r: BorrowRequest) {
        val batch = db.batch()
        batch.update(db.collection("requests").document(r.id), "status", ReqStatus.DECLINED.name)
        batch.set(db.collection("notices").document(), noticeData(r.borrowerId, "Your request for \"${r.itemTitle}\" was declined."))
        batch.commit().addOnFailureListener { fail(it) }
    }

    /** Lender cancels an approved (not yet handed over) reservation. */
    fun lenderCancel(r: BorrowRequest) {
        val batch = db.batch()
        batch.update(db.collection("requests").document(r.id), "status", ReqStatus.CANCELLED.name)
        batch.update(db.collection("items").document(r.itemId), "status", "AVAILABLE")
        batch.set(
            db.collection("notices").document(),
            noticeData(r.borrowerId, "${r.lenderName} cancelled the reservation for \"${r.itemTitle}\".")
        )
        batch.commit().addOnFailureListener { fail(it) }
    }

    /** Borrower cancels their own pending request. */
    fun cancel(r: BorrowRequest) {
        val batch = db.batch()
        batch.update(db.collection("requests").document(r.id), "status", ReqStatus.CANCELLED.name)
        batch.set(
            db.collection("notices").document(),
            noticeData(r.lenderId, "${r.borrowerName} cancelled the request for \"${r.itemTitle}\".")
        )
        batch.commit().addOnFailureListener { fail(it) }
    }

    fun handOver(r: BorrowRequest) {
        val due = System.currentTimeMillis() + r.days * DAY
        val batch = db.batch()
        batch.update(
            db.collection("requests").document(r.id),
            mapOf("status" to ReqStatus.BORROWED.name, "dueAt" to due)
        )
        batch.update(db.collection("items").document(r.itemId), "status", "BORROWED")
        batch.set(
            db.collection("notices").document(),
            noticeData(r.borrowerId, "You picked up \"${r.itemTitle}\". Return it by ${fmtDate(due)}.")
        )
        batch.commit().addOnFailureListener { fail(it) }
    }

    fun markReturned(r: BorrowRequest, condition: String, note: String) {
        val now = System.currentTimeMillis()
        val batch = db.batch()
        batch.update(
            db.collection("requests").document(r.id),
            mapOf(
                "status" to ReqStatus.RETURNED.name, "returnCondition" to condition,
                "returnNote" to note, "returnedAt" to now
            )
        )
        batch.update(
            db.collection("items").document(r.itemId),
            mapOf(
                "status" to "AVAILABLE",
                "history" to FieldValue.arrayUnion(
                    mapOf("at" to now, "borrowerName" to r.borrowerName, "condition" to condition, "note" to note)
                )
            )
        )
        batch.set(
            db.collection("notices").document(),
            noticeData(r.borrowerId, "\"${r.itemTitle}\" was marked as returned (${condition.lowercase()}). Thanks!")
        )
        batch.commit().addOnFailureListener { fail(it) }
    }

    fun addReview(r: BorrowRequest, stars: Int, comment: String) {
        val u = me ?: return
        val to = if (u.id == r.borrowerId) r.lenderId else r.borrowerId
        val batch = db.batch()
        batch.set(
            db.collection("reviews").document("${r.id}_${u.id}"),
            mapOf(
                "fromId" to u.id, "fromName" to u.name, "toId" to to, "requestId" to r.id,
                "stars" to stars, "comment" to comment, "at" to System.currentTimeMillis()
            )
        )
        batch.set(db.collection("notices").document(), noticeData(to, "${u.name} left you a $stars-star rating."))
        batch.commit().addOnFailureListener { fail(it) }
    }

    // ----- messaging & notifications -----

    fun sendMessage(itemId: String, itemTitle: String, otherId: String, otherName: String, text: String) {
        val u = me ?: return
        val last = messages
            .filter { it.itemId == itemId && ((it.fromId == u.id && it.toId == otherId) || (it.fromId == otherId && it.toId == u.id)) }
            .maxByOrNull { it.at }
        val batch = db.batch()
        batch.set(
            db.collection("messages").document(),
            mapOf(
                "itemId" to itemId, "itemTitle" to itemTitle,
                "fromId" to u.id, "fromName" to u.name, "toId" to otherId, "toName" to otherName,
                "text" to text, "at" to System.currentTimeMillis(),
                "participants" to listOf(u.id, otherId)
            )
        )
        if (last == null || last.fromId != u.id) {
            batch.set(
                db.collection("notices").document(),
                noticeData(otherId, "New message from ${u.name} about \"$itemTitle\".")
            )
        }
        batch.commit().addOnFailureListener { fail(it) }
    }

    fun markNoticeRead(id: String) {
        db.collection("notices").document(id).update("read", true).addOnFailureListener { fail(it) }
    }

    private fun DocumentSnapshot.longMap(field: String): Map<String, Long> {
        val raw = get(field) as? Map<*, *> ?: return emptyMap()
        val out = HashMap<String, Long>()
        for ((k, v) in raw) {
            val key = k as? String
            val num = v as? Number
            if (key != null && num != null) out[key] = num.toLong()
        }
        return out
    }

    fun chatKey(itemId: String, otherId: String): String = itemId + "_" + otherId

    private fun saveChatPrefs() {
        val uid = me?.id ?: return
        db.collection("users").document(uid)
            .update(mapOf("chatArchived" to chatArchived, "chatDeleted" to chatDeleted))
            .addOnFailureListener { fail(it) }
    }

    fun archiveChat(key: String) {
        chatArchived = chatArchived + (key to System.currentTimeMillis())
        saveChatPrefs()
    }

    fun unarchiveChat(key: String) {
        chatArchived = chatArchived - key
        saveChatPrefs()
    }

    /** Removes the conversation from this account only; the other student keeps their copy. */
    fun deleteChat(key: String) {
        chatDeleted = chatDeleted + (key to System.currentTimeMillis())
        chatArchived = chatArchived - key
        saveChatPrefs()
    }

    fun markNoticesRead() {
        val unread = notices.filter { !it.read }
        if (unread.isEmpty()) return
        val batch = db.batch()
        unread.forEach { batch.update(db.collection("notices").document(it.id), "read", true) }
        batch.commit().addOnFailureListener { fail(it) }
    }
}

// ------------------------------- Activity -------------------------------

class MainActivity : ComponentActivity() {
    private val store: Store by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampusBorrowHubTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CampusApp(store)
                }
            }
        }
    }
}

@Composable
fun CampusApp(store: Store) {
    when (store.stage) {
        Stage.LOADING -> LoadingScreen()
        Stage.SIGNED_OUT -> AuthScreen(store)
        Stage.VERIFY -> VerifyScreen(store)
        Stage.ERROR -> ErrorScreen(store)
        Stage.READY -> key(store.myId) { MainFlow(store) }
    }
}

@Composable
fun MainFlow(store: Store) {
    var screen by remember { mutableStateOf<Screen>(Screen.Tabs) }
    var tab by remember { mutableStateOf(0) }

    BackHandler(enabled = screen !is Screen.Tabs) { screen = Screen.Tabs }

    when (val s = screen) {
        is Screen.Tabs -> TabsShell(store, tab, { tab = it }, { screen = it })
        is Screen.AddItem -> AddItemScreen(store, onBack = { screen = Screen.Tabs }, onDone = { tab = 1; screen = Screen.Tabs })
        is Screen.Notices -> NoticesScreen(store, onBack = { screen = Screen.Tabs })
        is Screen.Detail -> DetailScreen(store, s.itemId, onBack = { screen = Screen.Tabs }, onOpen = { screen = it })
        is Screen.Chat -> ChatScreen(store, s, onBack = { screen = Screen.Tabs })
    }
}

// ------------------------------- Shared UI bits -------------------------------

@Composable
fun StatusChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Text(
            text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o) })
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun EmojiBox(emoji: String, size: Int = 56) {
    Box(
        Modifier.size(size.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) { Text(emoji, fontSize = (size / 2).sp) }
}

@Composable
fun StarPicker(value: Int, onChange: (Int) -> Unit) {
    Row {
        for (i in 1..5) {
            IconButton(onClick = { onChange(i) }) {
                Text("★", fontSize = 30.sp, color = if (i <= value) Color(0xFFFFB300) else Color.LightGray)
            }
        }
    }
}

@Composable
fun BackTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 22.sp) } }
    )
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Loading Campus Borrow Hub...")
        }
    }
}

// ------------------------------- Auth screens -------------------------------

@Composable
fun AuthScreen(store: Store) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var sid by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }

    Scaffold { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF4338CA), Color(0xFF6D5BD0)))),
                contentAlignment = Alignment.Center
            ) {
                Column(Modifier.padding(vertical = 24.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.logo), contentDescription = "Campus Borrow Hub logo", modifier = Modifier.size(96.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Campus Borrow Hub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Borrow. Lend. Share.", color = Color(0xFFE0E7FF), textAlign = TextAlign.Center)
                }
            }
            Text(
                "Borrow and lend school items with fellow students. Only @$SCHOOL_DOMAIN emails can join.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !creating, onClick = { creating = false; error = ""; info = "" }, label = { Text("Sign in") })
                FilterChip(selected = creating, onClick = { creating = true; error = ""; info = "" }, label = { Text("Create account") })
            }

            if (creating) {
                OutlinedTextField(
                    name, { name = it }, label = { Text("Full name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    sid, { sid = it }, label = { Text("Student ID (e.g. 2023-01234)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                email, { email = it }, label = { Text("School email (name@$SCHOOL_DOMAIN)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showPw = !showPw }) { Text(if (showPw) "Hide" else "Show") } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            if (info.isNotEmpty()) Text(info, color = Color(0xFF10B981))

            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    error = ""
                    info = ""
                    val e = email.trim().lowercase()
                    val problem = when {
                        creating && name.isBlank() -> "Please enter your full name."
                        creating && !Regex("^[0-9A-Za-z-]{6,15}$").matches(sid.trim()) ->
                            "Enter a valid student ID (6-15 characters)."
                        !e.endsWith("@$SCHOOL_DOMAIN") -> "Please use your school email (name@$SCHOOL_DOMAIN)."
                        creating && password.length < 8 -> "Password must be at least 8 characters."
                        password.isEmpty() -> "Please enter your password."
                        else -> ""
                    }
                    if (problem.isNotEmpty()) {
                        error = problem
                    } else {
                        busy = true
                        val cb: (String?) -> Unit = { err ->
                            busy = false
                            if (err != null) error = err
                        }
                        if (creating) store.signUp(name.trim(), e, sid.trim(), password, cb)
                        else store.signIn(e, password, cb)
                    }
                }
            ) { Text(if (creating) "Create account" else "Sign in") }

            if (!creating) {
                TextButton(onClick = {
                    val e = email.trim().lowercase()
                    if (!e.endsWith("@$SCHOOL_DOMAIN")) {
                        error = "Type your school email above first."
                    } else {
                        store.resetPassword(e) { err ->
                            if (err == null) {
                                error = ""
                                info = "Password reset email sent. Check your inbox (and spam)."
                            } else {
                                error = err
                            }
                        }
                    }
                }) { Text("Forgot password?") }
            }
        }
    }
}

@Composable
fun VerifyScreen(store: Store) {
    var msg by remember { mutableStateOf("") }
    var good by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Scaffold { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text("📧", fontSize = 56.sp)
            Text("Verify your school email", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("We sent a verification link to:")
            Text(store.currentEmail(), fontWeight = FontWeight.Bold)
            Text(
                "Open that email, tap the link, then come back here and press the button below. " +
                        "If you can't find it, check your spam / junk folder.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (msg.isNotEmpty()) Text(msg, color = if (good) Color(0xFF10B981) else MaterialTheme.colorScheme.error)

            Button(
                enabled = !busy, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    msg = ""
                    store.refreshVerification { err ->
                        busy = false
                        good = false
                        if (err != null) msg = err
                    }
                }
            ) { Text("I've verified my email") }

            OutlinedButton(
                enabled = !busy, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    store.resendVerification { err ->
                        good = err == null
                        msg = err ?: "Verification email sent again."
                    }
                }
            ) { Text("Resend email") }

            TextButton(onClick = { store.signOut() }) { Text("Use a different account") }
        }
    }
}

@Composable
fun ErrorScreen(store: Store) {
    Scaffold { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Text("⚠️", fontSize = 48.sp)
            Text("Couldn't load your account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(store.loadError, color = MaterialTheme.colorScheme.error)
            Button(onClick = { store.retry() }, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
            OutlinedButton(onClick = { store.signOut() }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        }
    }
}

// ------------------------------- Tabs shell -------------------------------

@Composable
fun TabsShell(store: Store, tab: Int, onTab: (Int) -> Unit, onOpen: (Screen) -> Unit) {
    val me = store.myId
    val titles = listOf("Browse", "My Items", "Requests", "Messages", "Profile")
    val tabs = listOf(
        "Browse" to "🔎",
        "My Items" to "📦",
        "Requests" to "🔁",
        "Chats" to "💬",
        "Profile" to "👤"
    )
    val pending = store.requests.count { it.lenderId == me && it.status == ReqStatus.PENDING }
    val unread = store.notices.count { !it.read }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titles[tab], fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { onOpen(Screen.Notices) }) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                            Text("🔔", fontSize = 22.sp)
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { onTab(i) },
                        icon = {
                            if (i == 2 && pending > 0) {
                                BadgedBox(badge = { Badge { Text("$pending") } }) { Text(icon, fontSize = 20.sp) }
                            } else Text(icon, fontSize = 20.sp)
                        },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (store.syncError.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        store.syncError, color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp, modifier = Modifier.padding(10.dp)
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> BrowseTab(store, onOpen)
                    1 -> MyItemsTab(store, onOpen)
                    2 -> RequestsTab(store, onOpen)
                    3 -> MessagesTab(store, onOpen)
                    else -> ProfileTab(store)
                }
            }
        }
    }
}

// ------------------------------- Browse -------------------------------

@Composable
fun BrowseTab(store: Store, onOpen: (Screen) -> Unit) {
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("All") }
    val me = store.myId
    val now = System.currentTimeMillis()
    val list = store.items.filter {
        (cat == "All" || it.category == cat) &&
                (query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true))
    }
    val due = store.requests.filter {
        it.borrowerId == me && it.status == ReqStatus.BORROWED && daysLeft(it.dueAt, now) <= 1
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        due.forEach { r ->
            item(key = "due${r.id}") {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("⏰ Return reminder", fontWeight = FontWeight.Bold, color = Color(0xFF5D3A00))
                        Text("\"${r.itemTitle}\" - ${dueText(daysLeft(r.dueAt, now))}", color = Color(0xFF5D3A00))
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search items (calculator, charger...)") },
                leadingIcon = { Text("🔎") }
            )
        }
        item { ChipRow(listOf("All") + CATEGORIES, cat) { cat = it } }
        if (list.isEmpty()) {
            item {
                EmptyState(
                    if (store.items.isEmpty()) "No items listed yet.\nBe the first - go to My Items and list one!"
                    else "No items match your search."
                )
            }
        }
        items(list, key = { it.id }) { item -> ItemCard(store, item) { onOpen(Screen.Detail(item.id)) } }
    }
}

@Composable
fun ItemCard(store: Store, item: Item, onClick: () -> Unit) {
    val status = store.itemStatus(item)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBox(catEmoji(item.category))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${item.ownerName} • ${store.ratingText(item.ownerId)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(status, if (status == "Available") Color(0xFF10B981) else Color(0xFF6B7280))
                    StatusChip(item.condition, Color(0xFF3B82F6))
                    if (item.ownerId == store.myId) StatusChip("Your listing", Color(0xFF8B5CF6))
                }
            }
        }
    }
}

// ------------------------------- Item detail -------------------------------

@Composable
fun DetailScreen(store: Store, itemId: String, onBack: () -> Unit, onOpen: (Screen) -> Unit) {
    val item = store.items.firstOrNull { it.id == itemId }
    Scaffold(topBar = { BackTopBar("Item details", onBack) }) { pad ->
        if (item == null) {
            Box(Modifier.padding(pad)) { EmptyState("This item no longer exists.") }
        } else {
            DetailBody(store, item, Modifier.padding(pad), onOpen)
        }
    }
}

@Composable
fun DetailBody(store: Store, item: Item, modifier: Modifier, onOpen: (Screen) -> Unit) {
    val me = store.myId
    val isMine = item.ownerId == me
    val busy = item.status != "AVAILABLE"
    val expired = item.availableUntil < System.currentTimeMillis()
    val active = store.requests.firstOrNull {
        it.itemId == item.id && it.borrowerId == me &&
                (it.status == ReqStatus.PENDING || it.status == ReqStatus.APPROVED || it.status == ReqStatus.BORROWED)
    }
    val dayOptions = listOf(1, 2, 3, 5, 7, 14).filter { it <= item.maxDays }.ifEmpty { listOf(1) }
    var days by remember { mutableStateOf(dayOptions.first()) }
    var note by remember { mutableStateOf("") }
    var showReport by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    val history = item.history.sortedByDescending { it.at }
    val ownerReviews = store.reviews.filter { it.toId == item.ownerId }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBox(catEmoji(item.category), 72)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(item.category, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val st = store.itemStatus(item)
            StatusChip(st, if (st == "Available") Color(0xFF10B981) else Color(0xFF6B7280))
            StatusChip("Condition: ${item.condition}", Color(0xFF3B82F6))
        }
        Text(item.description)
        Text("📅 Available until ${fmtDate(item.availableUntil)}  •  Max ${plural(item.maxDays, "day")}")
        Text("👤 Lender: ${item.ownerName}  ${store.ratingText(item.ownerId)}", fontWeight = FontWeight.SemiBold)

        HorizontalDivider()
        if (isMine) {
            Text("This is your listing.", fontStyle = FontStyle.Italic)
        } else {
            if (active != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text("Your request is ${active.status.label.lowercase()}. Check the Requests tab.", Modifier.padding(14.dp))
                }
            } else if (busy) {
                Text("This item is currently reserved or borrowed by someone else.", color = MaterialTheme.colorScheme.error)
            } else if (expired) {
                Text("This listing has expired.", color = MaterialTheme.colorScheme.error)
            } else {
                SectionTitle("Request to borrow")
                Text("How long do you need it?")
                ChipRow(dayOptions.map { plural(it, "day") }, plural(days, "day")) { sel ->
                    days = dayOptions.first { plural(it, "day") == sel }
                }
                OutlinedTextField(
                    note, { note = it }, label = { Text("Note to lender (purpose, pickup spot)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { store.createRequest(item, days, note.trim()) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Send borrow request")
                }
            }
            OutlinedButton(
                onClick = { onOpen(Screen.Chat(item.id, item.title, item.ownerId, item.ownerName)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("💬  Message lender") }
        }

        SectionTitle("Condition history")
        if (history.isEmpty()) Text("No borrows recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        history.forEach { h ->
            Text(
                "• ${fmtDate(h.at)} - returned by ${h.borrowerName}: ${h.condition}" +
                        if (h.note.isNotBlank()) " (\"${h.note}\")" else "",
                fontSize = 13.sp
            )
        }

        SectionTitle("Reviews of ${item.ownerName}")
        if (ownerReviews.isEmpty()) Text("No reviews yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ownerReviews.forEach { ReviewRow(it) }

        if (!isMine) {
            TextButton(onClick = { showReport = true }, enabled = !reported) {
                Text(if (reported) "Reported - thank you" else "Report this listing", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showReport) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text("Report listing") },
            text = {
                OutlinedTextField(reason, { reason = it }, label = { Text("What's wrong? (scam, wrong info, abuse...)") })
            },
            confirmButton = {
                Button(onClick = {
                    if (reason.isNotBlank()) {
                        store.report(item, reason.trim())
                        reported = true
                        showReport = false
                    }
                }) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { showReport = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ReviewRow(r: Review) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(
            "${"★".repeat(r.stars)}${"☆".repeat(5 - r.stars)}  ${r.fromName}",
            color = Color(0xFFFFB300), fontSize = 13.sp
        )
        if (r.comment.isNotBlank()) Text(r.comment, fontSize = 13.sp)
    }
}

// ------------------------------- My items / add -------------------------------

@Composable
fun MyItemsTab(store: Store, onOpen: (Screen) -> Unit) {
    val me = store.myId
    val mine = store.items.filter { it.ownerId == me }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Button(onClick = { onOpen(Screen.AddItem) }, modifier = Modifier.fillMaxWidth()) {
                Text("＋  List a new item to lend")
            }
        }
        if (mine.isEmpty()) item { EmptyState("You haven't listed anything yet.\nLend a spare calculator or textbook!") }
        items(mine, key = { it.id }) { item ->
            val inUse = item.status != "AVAILABLE" || store.requests.any {
                it.itemId == item.id && it.status == ReqStatus.PENDING
            }
            Column {
                ItemCard(store, item) { onOpen(Screen.Detail(item.id)) }
                TextButton(
                    onClick = { store.deleteItem(item) }, enabled = !inUse,
                    modifier = Modifier.align(Alignment.End)
                ) { Text(if (inUse) "Has active requests" else "Remove listing") }
            }
        }
    }
}

@Composable
fun AddItemScreen(store: Store, onBack: () -> Unit, onDone: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(CATEGORIES[0]) }
    var desc by remember { mutableStateOf("") }
    var cond by remember { mutableStateOf("Good") }
    var avail by remember { mutableStateOf("14 days") }
    var maxDays by remember { mutableStateOf("3 days") }
    var error by remember { mutableStateOf("") }

    Scaffold(topBar = { BackTopBar("List an item", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                title, { title = it.take(100) }, label = { Text("Item name") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Text("Category"); ChipRow(CATEGORIES, cat) { cat = it }
            OutlinedTextField(desc, { desc = it.take(500) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Text("Current condition"); ChipRow(CONDITIONS, cond) { cond = it }
            Text("Available for the next"); ChipRow(listOf("7 days", "14 days", "30 days", "90 days"), avail) { avail = it }
            Text("Maximum borrowing period"); ChipRow(listOf("1 day", "3 days", "7 days", "14 days"), maxDays) { maxDays = it }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    if (title.isBlank() || desc.isBlank()) {
                        error = "Please add a name and a description."
                    } else {
                        store.addItem(
                            title.trim(), cat, desc.trim(), cond,
                            avail.substringBefore(" ").toInt(), maxDays.substringBefore(" ").toInt()
                        )
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Publish listing") }
        }
    }
}

// ------------------------------- Requests -------------------------------

@Composable
fun RequestsTab(store: Store, onOpen: (Screen) -> Unit) {
    val me = store.myId
    var seg by remember { mutableStateOf(0) }
    val list = store.requests.filter { if (seg == 0) it.borrowerId == me else it.lenderId == me }
        .sortedByDescending { it.createdAt }

    Column {
        TabRow(selectedTabIndex = seg) {
            Tab(selected = seg == 0, onClick = { seg = 0 }, text = { Text("I'm borrowing") })
            Tab(selected = seg == 1, onClick = { seg = 1 }, text = { Text("I'm lending") })
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isEmpty()) {
                item { EmptyState(if (seg == 0) "You haven't requested anything yet." else "No one has requested your items yet.") }
            }
            items(list, key = { it.id }) { r -> RequestCard(store, r, asLender = seg == 1, onOpen = onOpen) }
        }
    }
}

@Composable
fun RequestCard(store: Store, r: BorrowRequest, asLender: Boolean, onOpen: (Screen) -> Unit) {
    val me = store.myId
    val otherId = if (asLender) r.borrowerId else r.lenderId
    val otherName = if (asLender) r.borrowerName else r.lenderName
    var showReturn by remember { mutableStateOf(false) }
    var showRate by remember { mutableStateOf(false) }
    val reviewed = store.reviews.any { it.requestId == r.id && it.fromId == me }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.itemTitle, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                StatusChip(r.status.label, r.status.color)
            }
            Text("${if (asLender) "Borrower" else "Lender"}: $otherName • ${plural(r.days, "day")}", fontSize = 13.sp)
            if (r.note.isNotBlank()) {
                Text("\"${r.note}\"", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (r.status == ReqStatus.BORROWED) {
                val left = daysLeft(r.dueAt, System.currentTimeMillis())
                Text(
                    "⏰ ${dueText(left)} (${fmtDate(r.dueAt)})", fontWeight = FontWeight.SemiBold,
                    color = if (left <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            if (r.status == ReqStatus.RETURNED) {
                Text("Returned ${fmtDate(r.returnedAt)} • Condition: ${r.returnCondition}", fontSize = 13.sp)
                if (r.returnNote.isNotBlank()) Text("Note: ${r.returnNote}", fontSize = 13.sp)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (asLender) {
                    when (r.status) {
                        ReqStatus.PENDING -> {
                            Button(onClick = { store.approve(r) }) { Text("Approve") }
                            OutlinedButton(onClick = { store.decline(r) }) { Text("Decline") }
                        }
                        ReqStatus.APPROVED -> {
                            Button(onClick = { store.handOver(r) }) { Text("Mark handed over") }
                            OutlinedButton(onClick = { store.lenderCancel(r) }) { Text("Cancel") }
                        }
                        ReqStatus.BORROWED -> Button(onClick = { showReturn = true }) { Text("Mark returned") }
                        ReqStatus.RETURNED -> {
                            if (!reviewed) Button(onClick = { showRate = true }) { Text("Rate borrower") }
                        }
                        else -> {}
                    }
                } else {
                    when (r.status) {
                        ReqStatus.PENDING -> OutlinedButton(onClick = { store.cancel(r) }) { Text("Cancel") }
                        ReqStatus.RETURNED -> {
                            if (!reviewed) Button(onClick = { showRate = true }) { Text("Rate lender") }
                        }
                        else -> {}
                    }
                }
                if (r.status != ReqStatus.CANCELLED) {
                    TextButton(onClick = { onOpen(Screen.Chat(r.itemId, r.itemTitle, otherId, otherName)) }) { Text("Message") }
                }
            }
        }
    }

    if (showReturn) {
        ReturnDialog(onDismiss = { showReturn = false }) { cond, note ->
            store.markReturned(r, cond, note)
            showReturn = false
        }
    }
    if (showRate) {
        RatingDialog(title = "Rate $otherName", onDismiss = { showRate = false }) { stars, comment ->
            store.addReview(r, stars, comment)
            showRate = false
        }
    }
}

@Composable
fun ReturnDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var cond by remember { mutableStateOf(RETURN_CONDITIONS[0]) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Item returned") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What condition is the item in?")
                ChipRow(RETURN_CONDITIONS, cond) { cond = it }
                OutlinedTextField(note, { note = it.take(200) }, label = { Text("Condition notes (optional)") })
            }
        },
        confirmButton = { Button(onClick = { onSubmit(cond, note.trim()) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun RatingDialog(title: String, onDismiss: () -> Unit, onSubmit: (Int, String) -> Unit) {
    var stars by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                StarPicker(stars) { stars = it }
                OutlinedTextField(comment, { comment = it.take(300) }, label = { Text("Feedback (optional)") })
            }
        },
        confirmButton = { Button(onClick = { onSubmit(stars, comment.trim()) }) { Text("Submit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------- Messages -------------------------------

@Composable
fun MessagesTab(store: Store, onOpen: (Screen) -> Unit) {
    val me = store.myId
    var showArchived by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Message?>(null) }

    fun keyOf(m: Message): String = store.chatKey(m.itemId, if (m.fromId == me) m.toId else m.fromId)

    val allThreads = store.messages
        .filter { it.fromId == me || it.toId == me }
        .filter { it.at > (store.chatDeleted[keyOf(it)] ?: 0L) }
        .groupBy { keyOf(it) }
        .map { entry -> entry.value.maxByOrNull { it.at }!! }
        .sortedByDescending { it.at }

    // a chat stays archived until a newer message arrives
    fun isArchived(m: Message): Boolean {
        val at = store.chatArchived[keyOf(m)] ?: return false
        return m.at <= at
    }

    val active = allThreads.filter { !isArchived(it) }
    val archived = allThreads.filter { isArchived(it) }
    val threads = if (showArchived) archived else active

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("Chats (${active.size})") })
                FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("Archived (${archived.size})") })
            }
        }
        if (threads.isEmpty()) {
            item {
                EmptyState(
                    if (showArchived) "No archived conversations."
                    else "No conversations yet.\nOpen an item and tap \"Message lender\"."
                )
            }
        }
        items(threads, key = { keyOf(it) }) { last ->
            val otherId = if (last.fromId == me) last.toId else last.fromId
            val otherName = if (last.fromId == me) last.toName else last.fromName
            Card(
                onClick = { onOpen(Screen.Chat(last.itemId, last.itemTitle, otherId, otherName)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp)) {
                    Text(otherName, fontWeight = FontWeight.SemiBold)
                    Text("Re: ${last.itemTitle}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text((if (last.fromId == me) "You: " else "") + last.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (showArchived) {
                            TextButton(onClick = { store.unarchiveChat(keyOf(last)) }) { Text("Unarchive") }
                        } else {
                            TextButton(onClick = { store.archiveChat(keyOf(last)) }) { Text("Archive") }
                        }
                        TextButton(onClick = { confirmDelete = last }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    val target = confirmDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete conversation?") },
            text = { Text("This removes the chat from your account only. The other student can still see their copy. If they message you again, a new conversation will start.") },
            confirmButton = {
                TextButton(onClick = { store.deleteChat(keyOf(target)); confirmDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ChatScreen(store: Store, chat: Screen.Chat, onBack: () -> Unit) {
    val me = store.myId
    var text by remember { mutableStateOf("") }
    val msgs = store.messages
        .filter {
            it.itemId == chat.itemId &&
                    ((it.fromId == me && it.toId == chat.otherId) || (it.fromId == chat.otherId && it.toId == me)) &&
                    it.at > (store.chatDeleted[store.chatKey(chat.itemId, chat.otherId)] ?: 0L)
        }
        .sortedBy { it.at }
    val listState = rememberLazyListState()
    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size)
    }

    Scaffold(
        topBar = { BackTopBar("${chat.otherName} • ${chat.itemTitle}", onBack) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(8.dp).imePadding(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it.take(1000) }, placeholder = { Text("Message...") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    if (text.isNotBlank()) {
                        store.sendMessage(chat.itemId, chat.itemTitle, chat.otherId, chat.otherName, text.trim())
                        text = ""
                    }
                }) { Text("➤", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary) }
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(), state = listState,
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    "Coordinate pickup and return here. Tip: meet in public campus areas like the library or canteen.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(msgs, key = { it.id }) { m ->
                val mine = m.fromId == me
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            val fg = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                            Text(m.text, color = fg)
                            Text(fmtTime(m.at), fontSize = 10.sp, color = fg.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------- Notifications -------------------------------

fun noticeIcon(text: String): String {
    val t = text.lowercase()
    return when {
        "message" in t -> "💬"
        "rating" in t || "star" in t -> "⭐"
        "due" in t || "return" in t -> "⏰"
        "request" in t || "approved" in t || "declined" in t || "handed" in t -> "🔁"
        else -> "🔔"
    }
}

@Composable
fun NoticesScreen(store: Store, onBack: () -> Unit) {
    var filter by remember { mutableStateOf("All") }
    var showSheet by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Notice?>(null) }

    val all = store.notices.sortedByDescending { it.at }
    val unread = all.count { !it.read }
    val list = when (filter) {
        "Unread" -> all.filter { !it.read }
        "Read" -> all.filter { it.read }
        else -> all
    }

    Scaffold(topBar = { BackTopBar("Notifications", onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilterChip(
                    selected = true,
                    onClick = { showSheet = true },
                    label = { Text(if (filter == "Unread") "Unread ($unread)  ▾" else "$filter  ▾") }
                )
                OutlinedButton(onClick = { store.markNoticesRead() }, enabled = unread > 0) {
                    Text("Mark all as read")
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                if (list.isEmpty()) {
                    item {
                        EmptyState(
                            when (filter) {
                                "Unread" -> "No unread notifications."
                                "Read" -> "No read notifications yet."
                                else -> "You're all caught up."
                            }
                        )
                    }
                }
                items(list, key = { it.id }) { n ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                opened = n
                                if (!n.read) store.markNoticeRead(n.id)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(46.dp).background(
                                if (n.read) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                            contentAlignment = Alignment.Center
                        ) { Text(noticeIcon(n.text), fontSize = 20.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                n.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold
                            )
                            Text(fmtTime(n.at), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!n.read) {
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                listOf("All", "Unread", "Read").forEach { option ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (filter == option) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { filter = option; showSheet = false }
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Text(option, fontWeight = if (filter == option) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }

    val n = opened
    if (n != null) {
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(noticeIcon(n.text) + "  Notification") },
            text = {
                Column {
                    Text(n.text)
                    Spacer(Modifier.height(8.dp))
                    Text(fmtTime(n.at), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { opened = null }) { Text("Close") } }
        )
    }
}

// ------------------------------- Profile -------------------------------

@Composable
fun ProfileTab(store: Store) {
    val user = store.me ?: return
    val me = user.id
    val history = store.requests
        .filter { it.status == ReqStatus.RETURNED }
        .sortedByDescending { it.returnedAt }
    val myReviews = store.reviews.filter { it.toId == me }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        user.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 28.sp, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(user.email, fontSize = 13.sp)
                    Text("ID: ${user.studentId}", fontSize = 13.sp)
                    Text("✔ Verified student  •  ${store.ratingText(me)}", fontSize = 13.sp, color = Color(0xFF10B981))
                }
            }
        }
        item { SectionTitle("Borrowing history") }
        if (history.isEmpty()) item { Text("Nothing returned yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(history, key = { it.id }) { r ->
            val borrowed = r.borrowerId == me
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(r.itemTitle, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${if (borrowed) "Borrowed from ${r.lenderName}" else "Lent to ${r.borrowerName}"} • returned ${fmtDate(r.returnedAt)}",
                        fontSize = 12.sp
                    )
                    Text("Condition on return: ${r.returnCondition}", fontSize = 12.sp)
                }
            }
        }
        item { SectionTitle("Ratings & feedback received") }
        if (myReviews.isEmpty()) item { Text("No ratings yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(myReviews) { ReviewRow(it) }

        item { Spacer(Modifier.height(8.dp)) }
        item {
            OutlinedButton(onClick = { store.signOut() }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        }
    }
}
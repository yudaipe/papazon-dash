package com.smartse.papazon_dash.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.smartse.papazon_dash.data.model.Item
import com.smartse.papazon_dash.data.model.PairInfo
import com.smartse.papazon_dash.data.model.User
import com.smartse.papazon_dash.data.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PapazonAuth"

@Singleton
class FirebaseRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
) {
    private var activePairId: String? = null
    private var openItemsRegistration: ListenerRegistration? = null
    private var historyRegistration: ListenerRegistration? = null
    private var pairJoinListenerRegistration: ListenerRegistration? = null

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _pairInfo = MutableStateFlow<PairInfo?>(null)
    val pairInfo: StateFlow<PairInfo?> = _pairInfo.asStateFlow()

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private val _historyItems = MutableStateFlow<List<Item>>(emptyList())
    val historyItems: StateFlow<List<Item>> = _historyItems.asStateFlow()

    // ── Email/Password Auth (v1.0.2) ──────────────────────────────────────────

    fun createAccount(
        email: String,
        password: String,
        role: UserRole,
        name: String,
        onResult: (Boolean, String?) -> Unit,
    ) {
        val resolvedName = name.ifBlank { role.displayName }
        val existing = auth.currentUser

        if (existing != null && existing.isAnonymous) {
            // Link anonymous account to email/password (preserves UID + all data)
            val credential = EmailAuthProvider.getCredential(email, password)
            Log.d(TAG, "createAccount: linking anonymous uid=${existing.uid} to email=$email")
            existing.linkWithCredential(credential)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid.orEmpty()
                    Log.d(TAG, "linkWithCredential SUCCESS: uid=$uid")
                    bindUser(uid, role, resolvedName)
                    onResult(true, null)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "linkWithCredential FAILED: ${e.message}, falling back to createFreshAccount")
                    createFreshAccount(email, password, role, resolvedName, onResult)
                }
        } else {
            createFreshAccount(email, password, role, resolvedName, onResult)
        }
    }

    private fun createFreshAccount(
        email: String,
        password: String,
        role: UserRole,
        name: String,
        onResult: (Boolean, String?) -> Unit,
    ) {
        Log.d(TAG, "createFreshAccount: email=$email role=$role")
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid.orEmpty()
                Log.d(TAG, "createUserWithEmailAndPassword SUCCESS: uid=$uid")
                bindUser(uid, role, name)
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "createUserWithEmailAndPassword FAILED: ${e.message}")
                onResult(false, mapFirebaseError(e))
            }
    }

    fun signInWithEmail(
        email: String,
        password: String,
        onResult: (Boolean, String?) -> Unit,
    ) {
        Log.d(TAG, "signInWithEmail: email=$email")
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid.orEmpty()
                Log.d(TAG, "signInWithEmailAndPassword SUCCESS: uid=$uid")
                loadUserProfile(uid) { profile ->
                    if (profile != null) {
                        _currentUser.value = profile
                        if (profile.pairId != null) {
                            activePairId = profile.pairId
                            setPaired(profile.pairId)
                        }
                        refreshFcmToken(uid)
                        onResult(true, null)
                    } else {
                        // Profile not found — user needs to re-register
                        Log.w(TAG, "signInWithEmail: profile not found for uid=$uid")
                        onResult(false, "アカウント情報が見つかりません。再度登録してください")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "signInWithEmailAndPassword FAILED: ${e.message}")
                onResult(false, mapFirebaseError(e))
            }
    }

    fun tryAutoSignIn(onComplete: () -> Unit = {}) {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null || firebaseUser.isAnonymous) {
            Log.d(TAG, "tryAutoSignIn: no email/password user found")
            onComplete()
            return
        }
        Log.d(TAG, "tryAutoSignIn: email/password user uid=${firebaseUser.uid}")
        loadUserProfile(firebaseUser.uid) { profile ->
            if (profile != null) {
                _currentUser.value = profile
                if (profile.pairId != null) {
                    activePairId = profile.pairId
                    setPaired(profile.pairId)
                }
                refreshFcmToken(firebaseUser.uid)
            }
            onComplete()
        }
    }

    private fun loadUserProfile(uid: String, onResult: (User?) -> Unit) {
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val roleStr = doc.getString("role") ?: UserRole.MASTER.firestoreValue
                    val role = UserRole.values().firstOrNull { it.firestoreValue == roleStr }
                        ?: UserRole.MASTER
                    val user = User(
                        uid = uid,
                        displayName = doc.getString("displayName") ?: role.displayName,
                        role = role,
                        pairId = doc.getString("pairId"),
                    )
                    Log.d(TAG, "loadUserProfile: loaded uid=$uid role=$role pairId=${user.pairId}")
                    onResult(user)
                } else {
                    Log.w(TAG, "loadUserProfile: no doc for uid=$uid")
                    onResult(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "loadUserProfile FAILED: uid=$uid ${e.message}")
                onResult(null)
            }
    }

    private fun mapFirebaseError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            e is FirebaseAuthUserCollisionException ||
                msg.contains("email address is already in use", ignoreCase = true) ->
                "このメールアドレスは既に登録されています"
            msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                msg.contains("password is invalid", ignoreCase = true) ||
                msg.contains("no user record", ignoreCase = true) ->
                "メールアドレスまたはパスワードが間違っています"
            msg.contains("badly formatted", ignoreCase = true) ||
                msg.contains("INVALID_EMAIL", ignoreCase = true) ->
                "メールアドレスの形式が正しくありません"
            msg.contains("WEAK_PASSWORD", ignoreCase = true) ||
                msg.contains("weak password", ignoreCase = true) ->
                "パスワードは6文字以上で設定してください"
            msg.contains("network", ignoreCase = true) ->
                "ネットワーク接続を確認してください"
            else -> "エラーが発生しました。しばらくしてから再試行してください"
        }
    }

    // ── Anonymous Sign-In (legacy / fallback) ────────────────────────────────

    fun signIn(role: UserRole = UserRole.MASTER, name: String = "") {
        Log.d(TAG, "signIn() called: role=$role name=${name.ifBlank { "(default)" }}")
        val existing = auth.currentUser
        if (existing != null) {
            Log.d(TAG, "signIn: existing Firebase user found uid=${existing.uid}")
            bindUser(existing.uid, role, name)
            return
        }
        Log.d(TAG, "signIn: no existing user, calling signInAnonymously()")
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid.orEmpty()
                Log.d(TAG, "signInAnonymously SUCCESS: uid=$uid")
                bindUser(uid, role, name)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "signInAnonymously FAILED: ${e.message}", e)
                _currentUser.value = null
                _isPaired.value = false
                _pairInfo.value = null
                _items.value = emptyList()
                _historyItems.value = emptyList()
            }
    }

    // ── Sign-Out ─────────────────────────────────────────────────────────────

    fun signOut() {
        Log.d(TAG, "signOut: clearing all state")
        openItemsRegistration?.remove()
        historyRegistration?.remove()
        pairJoinListenerRegistration?.remove()
        pairJoinListenerRegistration = null
        openItemsRegistration = null
        historyRegistration = null
        activePairId = null
        auth.signOut()
        _currentUser.value = null
        _isPaired.value = false
        _pairInfo.value = null
        _items.value = emptyList()
        _historyItems.value = emptyList()
    }

    // ── Pairing ───────────────────────────────────────────────────────────────

    fun generateInviteCode(): String {
        val user = _currentUser.value ?: return ""
        val code = (100000..999999).random().toString()
        val pairRef = firestore.collection("pairs").document()
        activePairId = pairRef.id
        Log.d(TAG, "generateInviteCode: creating pairId=${pairRef.id} master_uid=${user.uid}")
        pairRef
            .set(
                mapOf(
                    "pairId" to pairRef.id,
                    "inviteCode" to code,
                    "master_uid" to user.uid,
                    "master_role" to UserRole.MASTER.firestoreValue,
                    "member_role" to UserRole.MEMBER.firestoreValue,
                    "created_at" to FieldValue.serverTimestamp(),
                    "active" to true,
                    "partners" to listOf(user.uid),
                ),
            )
            .addOnSuccessListener {
                Log.d(TAG, "generateInviteCode SUCCESS: pairId=${pairRef.id} inviteCode=$code")
                startListeningForMemberJoin(pairRef.id)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "generateInviteCode FAILED: pairId=${pairRef.id} message=${e.message}", e)
                if (activePairId == pairRef.id) {
                    activePairId = null
                }
            }
        return code
    }

    private fun startListeningForMemberJoin(pairId: String) {
        pairJoinListenerRegistration?.remove()
        Log.d(TAG, "startListeningForMemberJoin: pairId=$pairId")
        pairJoinListenerRegistration = firestore.collection("pairs").document(pairId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "listenForMemberJoin FAILED: pairId=$pairId message=${e.message}", e)
                    return@addSnapshotListener
                }
                val memberUid = snapshot?.getString("member_uid")
                Log.d(TAG, "listenForMemberJoin SNAPSHOT: pairId=$pairId member_uid=$memberUid isPaired=${_isPaired.value}")
                if (memberUid != null && !_isPaired.value) {
                    Log.d(TAG, "listenForMemberJoin: member joined! auto-calling setPaired pairId=$pairId")
                    pairJoinListenerRegistration?.remove()
                    pairJoinListenerRegistration = null
                    setPaired(pairId)
                }
            }
    }

    fun completeInvite() {
        val pairId = activePairId ?: return
        Log.d(TAG, "completeInvite: pairId=$pairId")
        setPaired(pairId)
    }

    fun joinByCode(code: String, onResult: (Boolean) -> Unit) {
        val user = _currentUser.value
        if (code.length != 6 || user == null) {
            onResult(false)
            return
        }
        firestore.collection("pairs")
            .whereEqualTo("inviteCode", code)
            .whereEqualTo("active", true)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val pairDoc = snapshot.documents.firstOrNull()
                if (pairDoc == null) {
                    onResult(false)
                    return@addOnSuccessListener
                }
                activePairId = pairDoc.id
                Log.d(TAG, "joinByCode: matched pairId=${pairDoc.id} uid=${user.uid}")
                pairDoc.reference
                    .update(
                        mapOf(
                            "member_uid" to user.uid,
                            "joined_at" to FieldValue.serverTimestamp(),
                            "partners" to FieldValue.arrayUnion(user.uid),
                        ),
                    )
                    .addOnSuccessListener {
                        Log.d(TAG, "joinByCode SUCCESS: pairId=${pairDoc.id} member_uid=${user.uid}")
                        setPaired(pairDoc.id)
                        onResult(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "joinByCode UPDATE FAILED: pairId=${pairDoc.id} message=${e.message}", e)
                        onResult(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "joinByCode QUERY FAILED: code=$code message=${e.message}", e)
                onResult(false)
            }
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    fun addItem(name: String) {
        val user = _currentUser.value ?: return
        val pairId = activePairId ?: user.pairId ?: return
        Log.d(TAG, "addItem: pairId=$pairId createdBy=${user.uid} name=$name")
        firestore.collection("pairs").document(pairId).collection("items")
            .add(
                mapOf(
                    "name" to name,
                    "status" to "open",
                    "createdBy" to user.uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "reminderAt" to null,
                ),
            )
            .addOnSuccessListener { doc ->
                Log.d(TAG, "addItem SUCCESS: pairId=$pairId itemId=${doc.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "addItem FAILED: pairId=$pairId message=${e.message}", e)
            }
    }

    fun toggleItem(itemId: String) {
        val item = _items.value.firstOrNull { it.id == itemId }
            ?: _historyItems.value.firstOrNull { it.id == itemId }
            ?: return
        val nextStatus = if (item.status == "open") "done" else "open"
        val pairId = activePairId ?: _currentUser.value?.pairId ?: return
        Log.d(TAG, "toggleItem: pairId=$pairId itemId=$itemId nextStatus=$nextStatus")
        firestore.collection("pairs").document(pairId).collection("items").document(itemId)
            .update(
                mapOf(
                    "status" to nextStatus,
                    "completedAt" to if (nextStatus == "done") FieldValue.serverTimestamp() else null,
                ),
            )
            .addOnSuccessListener {
                Log.d(TAG, "toggleItem SUCCESS: pairId=$pairId itemId=$itemId status=$nextStatus")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "toggleItem FAILED: pairId=$pairId itemId=$itemId message=${e.message}", e)
            }
    }

    fun unpair() {
        val pairId = activePairId ?: _currentUser.value?.pairId
        openItemsRegistration?.remove()
        historyRegistration?.remove()
        openItemsRegistration = null
        historyRegistration = null
        activePairId = null
        _isPaired.value = false
        _pairInfo.value = null
        _items.value = emptyList()
        _historyItems.value = emptyList()
        _currentUser.value = _currentUser.value?.copy(pairId = null)
        // Clear pairId from Firestore (v1.0.2: enables restore on re-login)
        auth.currentUser?.uid?.let { uid ->
            firestore.collection("users").document(uid)
                .update(mapOf("pairId" to null))
                .addOnSuccessListener { Log.d(TAG, "unpair: pairId cleared in Firestore uid=$uid") }
                .addOnFailureListener { e -> Log.w(TAG, "unpair: pairId clear failed", e) }
        }
        if (pairId != null) {
            Log.d(TAG, "unpair: deactivating pairId=$pairId")
            firestore.collection("pairs").document(pairId)
                .update(mapOf("active" to false))
                .addOnSuccessListener {
                    Log.d(TAG, "unpair SUCCESS: pairId=$pairId deactivated")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "unpair FAILED deactivate: pairId=$pairId message=${e.message}", e)
                }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun bindUser(uid: String, role: UserRole, name: String = "") {
        val resolvedName = name.ifBlank { role.displayName }
        Log.d(TAG, "bindUser: uid=$uid role=$role displayName=$resolvedName")
        val user = User(uid = uid, displayName = resolvedName, role = role, pairId = null)
        _currentUser.value = user
        saveUserProfile(user)
        refreshFcmToken(uid)
    }

    private fun setPaired(pairId: String) {
        activePairId = pairId
        val currentRole = _currentUser.value?.role
        val currentName = _currentUser.value?.displayName ?: ""
        Log.d(TAG, "setPaired: pairId=$pairId currentRole=$currentRole currentName=$currentName")
        _pairInfo.value = PairInfo(
            pairId = pairId,
            masterName = if (currentRole == UserRole.MASTER) currentName else UserRole.MASTER.displayName,
            memberName = if (currentRole == UserRole.MEMBER) currentName else UserRole.MEMBER.displayName,
        )
        _isPaired.value = true
        _currentUser.value = _currentUser.value?.copy(pairId = pairId)
        listenToItems()
        // Persist pairId to Firestore (v1.0.2: enables restore on re-login)
        _currentUser.value?.uid?.let { uid ->
            firestore.collection("users").document(uid)
                .update(mapOf("pairId" to pairId))
                .addOnSuccessListener { Log.d(TAG, "setPaired: pairId persisted uid=$uid pairId=$pairId") }
                .addOnFailureListener { e -> Log.w(TAG, "setPaired: pairId persist failed", e) }
        }
    }

    private fun saveUserProfile(user: User) {
        firestore.collection("users").document(user.uid)
            .set(
                mapOf(
                    "uid" to user.uid,
                    "displayName" to user.displayName,
                    "role" to user.role.firestoreValue,
                    "pairId" to user.pairId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
    }

    private fun refreshFcmToken(uid: String) {
        messaging.token.addOnSuccessListener { token ->
            firestore.collection("users").document(uid)
                .update(
                    mapOf(
                        "fcmToken" to token,
                        "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                .addOnFailureListener {
                    firestore.collection("users").document(uid)
                        .set(
                            mapOf(
                                "uid" to uid,
                                "fcmToken" to token,
                                "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
                            ),
                            com.google.firebase.firestore.SetOptions.merge(),
                        )
                }
        }
    }

    private fun listenToItems() {
        openItemsRegistration?.remove()
        historyRegistration?.remove()
        val pairId = activePairId ?: return
        val itemsRef = firestore.collection("pairs").document(pairId).collection("items")
        openItemsRegistration = itemsRef
            .whereEqualTo("status", "open")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "listenToItems OPEN FAILED: pairId=$pairId message=${e.message}", e)
                    return@addSnapshotListener
                }
                Log.d(TAG, "listenToItems OPEN SNAPSHOT: pairId=$pairId count=${snapshot?.size() ?: 0}")
                _items.value = snapshot?.documents.orEmpty().map { doc -> doc.toItem() }
            }
        historyRegistration = itemsRef
            .whereEqualTo("status", "done")
            .orderBy("completedAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "listenToItems HISTORY FAILED: pairId=$pairId message=${e.message}", e)
                    return@addSnapshotListener
                }
                Log.d(TAG, "listenToItems HISTORY SNAPSHOT: pairId=$pairId count=${snapshot?.size() ?: 0}")
                _historyItems.value = snapshot?.documents.orEmpty().map { doc -> doc.toItem() }
            }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toItem(): Item {
        return Item(
            id = id,
            name = getString("name").orEmpty(),
            status = getString("status") ?: "open",
            createdBy = getString("createdBy").orEmpty(),
            createdAt = getTimestamp("createdAt").toLocalDateTime(),
            completedAt = getTimestamp("completedAt")?.toLocalDateTime(),
            reminderAt = getTimestamp("reminderAt")?.toLocalDateTime(),
        )
    }

    private fun Timestamp?.toLocalDateTime(): LocalDateTime {
        return this?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
            ?: LocalDateTime.now()
    }
}

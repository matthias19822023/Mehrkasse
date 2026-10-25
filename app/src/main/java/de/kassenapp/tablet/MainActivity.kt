package de.kassenapp.tablet

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument

import android.content.Intent


import android.os.Bundle

import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.shape.RoundedCornerShape



import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.ArrowBack

import androidx.compose.material.icons.filled.Delete

import androidx.compose.material.icons.filled.Settings

import androidx.compose.material3.AlertDialog

import androidx.compose.material3.Button

import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme

import androidx.compose.material3.OutlinedButton

import androidx.compose.material3.OutlinedTextField

import androidx.compose.material3.Scaffold

import androidx.compose.material3.Text

import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateListOf

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.core.content.FileProvider

import androidx.room.Dao

import androidx.room.Database

import androidx.room.Entity

import androidx.room.Insert
import androidx.room.Index

import androidx.room.OnConflictStrategy

import androidx.room.PrimaryKey

import androidx.room.Query

import androidx.room.Room

import androidx.room.RoomDatabase

import androidx.room.Transaction

import androidx.room.migration.Migration

import androidx.sqlite.db.SupportSQLiteDatabase

import java.io.File
import java.io.FileOutputStream

import java.text.SimpleDateFormat

import java.util.Date

import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

 

@Entity(tableName = "articles")

data class ArticleEntity(

    @PrimaryKey val id: Int,

    val name: String,

    val price: Double,

    val enabled: Boolean

)

 

@Entity(
    tableName = "handover",
    indices = [Index(value = ["syncId"], unique = true), Index(value = ["registerRole", "timestamp"])]
)
data class HandoverEntity(

    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val timestamp: Long,

    val articleId: Int,

    val articleName: String,

    val unitPrice: Double,

    val quantity: Int,

    val totalPrice: Double,

    val registerRole: String = "Masterkasse",

    val syncId: String = UUID.randomUUID().toString()

)

 

@Entity(tableName = "total_evaluation")

data class TotalEvaluationEntity(

    @PrimaryKey val articleId: Int,

    val articleName: String,

    val unitPrice: Double,

    val quantity: Int,

    val totalPrice: Double

)

 

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val name: String,
    val savedAt: Long,
    val settingsJson: String,
    val articlesJson: String,
    val handoversJson: String,
    val evaluationJson: String
)

@Entity(tableName = "pending_sync")
data class PendingSyncEntity(
    @PrimaryKey val syncId: String,
    val timestamp: Long,
    val articleId: Int,
    val articleName: String,
    val unitPrice: Double,
    val quantity: Int,
    val totalPrice: Double,
    val registerRole: String,
    val attempts: Int = 0,
    val lastError: String = ""
)

@Dao
interface KassenDao {

    @Query("SELECT * FROM profiles ORDER BY savedAt DESC")
    suspend fun getProfiles(): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE name = :name")
    suspend fun deleteProfile(name: String)

    @Query("DELETE FROM articles")
    suspend fun clearArticles()

    @Query("DELETE FROM total_evaluation")
    suspend fun clearTotalEvaluation()


    @Query("SELECT * FROM articles ORDER BY id")

    suspend fun getArticles(): List<ArticleEntity>

 

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun saveArticles(articles: List<ArticleEntity>)

 

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueuePending(item: PendingSyncEntity): Long

    @Query("SELECT * FROM pending_sync ORDER BY timestamp ASC")
    suspend fun getPendingSync(): List<PendingSyncEntity>

    @Query("UPDATE pending_sync SET attempts = attempts + 1, lastError = :error WHERE syncId = :syncId")
    suspend fun recordSyncFailure(syncId: String, error: String)

    @Query("DELETE FROM pending_sync WHERE syncId = :syncId")
    suspend fun removePendingSync(syncId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRemoteHandover(item: HandoverEntity): Long

    @Query("SELECT * FROM handover WHERE syncId = :syncId LIMIT 1")
    suspend fun findHandoverBySyncId(syncId: String): HandoverEntity?

    @Query("SELECT * FROM handover WHERE registerRole = :role ORDER BY timestamp DESC")
    suspend fun getHandoversForRole(role: String): List<HandoverEntity>

    @Insert

    suspend fun insertHandovers(items: List<HandoverEntity>)

 

    @Query("SELECT * FROM handover ORDER BY timestamp DESC")

    suspend fun getHandovers(): List<HandoverEntity>

 

    @Query("DELETE FROM handover")

    suspend fun deleteHandovers()

 

    @Query("SELECT * FROM total_evaluation ORDER BY articleId")

    suspend fun getTotalEvaluation(): List<TotalEvaluationEntity>

 

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun saveTotalEvaluation(item: TotalEvaluationEntity)

 

    @Query("DELETE FROM total_evaluation")

    suspend fun deleteTotalEvaluation()

 

    @Transaction
    suspend fun receiveRemoteHandover(item: HandoverEntity): Boolean {
        val insertedId = insertRemoteHandover(item)
        if (insertedId == -1L) return false
        val existing = getTotalEvaluation().firstOrNull { it.articleId == item.articleId }
        if (existing == null) {
            saveTotalEvaluation(TotalEvaluationEntity(item.articleId, item.articleName, item.unitPrice, item.quantity, item.totalPrice))
        } else {
            saveTotalEvaluation(existing.copy(
                articleName = item.articleName,
                unitPrice = item.unitPrice,
                quantity = existing.quantity + item.quantity,
                totalPrice = existing.totalPrice + item.totalPrice
            ))
        }
        return true
    }

    @Transaction

    suspend fun saveHandoverAndTotal(items: List<HandoverEntity>) {

        insertHandovers(items)

 

        for (item in items) {

            if (item.registerRole != "Masterkasse") {
                enqueuePending(
                    PendingSyncEntity(
                        syncId = item.syncId,
                        timestamp = item.timestamp,
                        articleId = item.articleId,
                        articleName = item.articleName,
                        unitPrice = item.unitPrice,
                        quantity = item.quantity,
                        totalPrice = item.totalPrice,
                        registerRole = item.registerRole
                    )
                )
            }

            val existing = getTotalEvaluation()

                .firstOrNull { it.articleId == item.articleId }

 

            if (existing == null) {

                saveTotalEvaluation(

                    TotalEvaluationEntity(

                        articleId = item.articleId,

                        articleName = item.articleName,

                        unitPrice = item.unitPrice,

                        quantity = item.quantity,

                        totalPrice = item.totalPrice

                    )

                )

            } else {

                saveTotalEvaluation(

                    existing.copy(

                        articleName = item.articleName,

                        unitPrice = item.unitPrice,

                        quantity = existing.quantity + item.quantity,

                        totalPrice = existing.totalPrice + item.totalPrice

                    )

                )

            }

        }

    }

}

 

@Database(

    entities = [

        ArticleEntity::class,

        HandoverEntity::class,

        TotalEvaluationEntity::class,
        ProfileEntity::class, PendingSyncEntity::class],

    version = 6,

    exportSchema = false

)

abstract class KassenDb : RoomDatabase() {

    abstract fun dao(): KassenDao

 

    companion object {

        @Volatile

        private var INSTANCE: KassenDb? = null

 

        private val MIGRATION_1_2 = object : Migration(1, 2) {

            override fun migrate(database: SupportSQLiteDatabase) {

                database.execSQL(

                    """

                    CREATE TABLE IF NOT EXISTS total_evaluation (

                        articleId INTEGER NOT NULL,

                        articleName TEXT NOT NULL,

                        unitPrice REAL NOT NULL,

                        quantity INTEGER NOT NULL,

                        totalPrice REAL NOT NULL,

                        PRIMARY KEY(articleId)

                    )

                    """.trimIndent()

                )

 

                database.execSQL(

                    """

                    INSERT OR REPLACE INTO total_evaluation

                    (articleId, articleName, unitPrice, quantity, totalPrice)

                    SELECT articleId, articleName, unitPrice,

                           SUM(quantity), SUM(totalPrice)

                    FROM handover

                    GROUP BY articleId, articleName, unitPrice

                    """.trimIndent()

                )

            }

        }

 

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS profiles (name TEXT NOT NULL, savedAt INTEGER NOT NULL, settingsJson TEXT NOT NULL, articlesJson TEXT NOT NULL, handoversJson TEXT NOT NULL, evaluationJson TEXT NOT NULL, PRIMARY KEY(name))")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE handover ADD COLUMN registerRole TEXT NOT NULL DEFAULT 'Masterkasse'"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE handover ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE handover SET syncId = 'legacy-' || id WHERE syncId = ''")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_handover_syncId ON handover(syncId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_handover_registerRole_timestamp ON handover(registerRole, timestamp)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_sync (syncId TEXT NOT NULL PRIMARY KEY, timestamp INTEGER NOT NULL, articleId INTEGER NOT NULL, articleName TEXT NOT NULL, unitPrice REAL NOT NULL, quantity INTEGER NOT NULL, totalPrice REAL NOT NULL, registerRole TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, lastError TEXT NOT NULL DEFAULT '')"
                )
            }
        }

        fun get(context: Context): KassenDb {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(

                    context.applicationContext,

                    KassenDb::class.java,

                    "kasse.db"

                )

                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

                    .build()

                INSTANCE = instance

                instance

            }

        }

    }

}

 

data class OrganizationInfo(
    val name: String = "",
    val address: String = "",
    val contact: String = "",
    val extra: String = "",
    val event: String = ""
)

data class Article(

    val id: Int,

    val name: String,

    val price: Double,

    val enabled: Boolean,

    val color: Color,

    val symbol: String = "🛒"

)

 

private val articleSymbolGroups = listOf(
    "Getränke" to listOf("🍻", "🥛", "🥤", "🧋", "🍹", "🍸", "🍷", "🥂", "🍾", "🧃", "🫗", "☕", "🍵", "🫖", "🥃", "🧊", "🚰"),
    "Speisen und Imbiss" to listOf("🍟", "🍔", "🌭", "🍕", "🥨", "🥪", "🥩", "🍗", "🍖", "🧀", "🥗", "🍲", "🥣", "🌮"),
    "Kuchen und Süßes" to listOf("🧇", "🍰", "🎂", "🧁", "🍩", "🍪", "🥞", "🍦", "🍫", "🍬", "🍿", "🥐"),
    "Obst" to listOf("🍎", "🍌", "🍇", "🍊", "🍓"),
    "Sonstiges" to listOf(
        "🛒", "🎄", "🎟️", "🥜", "🥚", "🍞", "🥖"
    )
)

private val articleSymbols = articleSymbolGroups.flatMap { it.second }.distinct()

private val articleColors = listOf(

    Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784),

    Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DD0E1),

    Color(0xFFFF8A65), Color(0xFFA1887F), Color(0xFF7986CB),

    Color(0xFFAED581), Color(0xFFFFD54F), Color(0xFF90A4AE)

)

 

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0878E8),
                    onPrimary = Color.White,
                    secondary = Color(0xFFFF9800),
                    onSecondary = Color.White,
                    tertiary = Color(0xFF7C3AED),
                    background = Color(0xFFF1F8FF),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFE3F2FD),
                    error = Color(0xFFE53945)
                )
            ) { KassenApp() }
        }

    }

}

 

@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun KassenApp() {

    val context = androidx.compose.ui.platform.LocalContext.current

    val db = remember { KassenDb.get(context) }
    val scope = rememberCoroutineScope()

 

    val prefs = remember { context.getSharedPreferences("kassen_app_settings", Context.MODE_PRIVATE) }
    var activeRole by remember {
        mutableStateOf(prefs.getString("register_role", "Masterkasse") ?: "Masterkasse")
    }
    DisposableEffect(db, activeRole) {
        val coordinator = LanSyncCoordinator(context, db.dao(), activeRole) { status ->
            android.util.Log.i("KassenappLAN", status)
        }
        coordinator.start()
        onDispose { coordinator.stop() }
    }
    val enabledArticleSymbols = remember {
        mutableStateListOf<String>().apply {
            val saved = prefs.getStringSet("enabled_article_symbols", null)
            addAll(saved ?: articleSymbols.toSet())
        }
    }
    var currentPage by remember { mutableStateOf(prefs.getString("last_page", "kasse") ?: "kasse") }
    val pageHistory = remember { mutableStateListOf<String>() }
    fun navigateTo(page: String) { pageHistory.add(currentPage); currentPage = page }
    fun goBack() { if (pageHistory.isNotEmpty()) currentPage = pageHistory.removeAt(pageHistory.lastIndex) else currentPage = "kasse" }
    var pin by remember { mutableStateOf(prefs.getString("pin", "1234") ?: "1234") }
    var organization by remember {
        mutableStateOf(OrganizationInfo(
            prefs.getString("org_name", "") ?: "",
            prefs.getString("org_address", "") ?: "",
            prefs.getString("org_contact", "") ?: "",
            prefs.getString("org_extra", "") ?: "",
            prefs.getString("org_event", "") ?: ""
        ))
    }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinAction by remember { mutableStateOf("") }

    val articles = remember {

        mutableStateListOf(

            Article(1, "Artikel 1", 1.00, true, articleColors[0], prefs.getString("article_symbol_1", "🛒") ?: "🛒"),

            Article(2, "Artikel 2", 2.00, true, articleColors[1], prefs.getString("article_symbol_2", "🛒") ?: "🛒"),

            Article(3, "Artikel 3", 3.00, true, articleColors[2], prefs.getString("article_symbol_3", "🛒") ?: "🛒"),

            Article(4, "Artikel 4", 4.00, true, articleColors[3], prefs.getString("article_symbol_4", "🛒") ?: "🛒"),

            Article(5, "Artikel 5", 5.00, true, articleColors[4], prefs.getString("article_symbol_5", "🛒") ?: "🛒"),

            Article(6, "Artikel 6", 6.00, true, articleColors[5], prefs.getString("article_symbol_6", "🛒") ?: "🛒"),

            Article(7, "Artikel 7", 7.00, true, articleColors[6], prefs.getString("article_symbol_7", "🛒") ?: "🛒"),

            Article(8, "Artikel 8", 8.00, true, articleColors[7], prefs.getString("article_symbol_8", "🛒") ?: "🛒"),

            Article(9, "Artikel 9", 9.00, false, articleColors[8], prefs.getString("article_symbol_9", "🛒") ?: "🛒"),

            Article(10, "Artikel 10", 10.00, false, articleColors[9], prefs.getString("article_symbol_10", "🛒") ?: "🛒"),

            Article(11, "Artikel 11", 11.00, false, articleColors[10], prefs.getString("article_symbol_11", "🛒") ?: "🛒"),

            Article(12, "Artikel 12", 12.00, false, articleColors[11], prefs.getString("article_symbol_12", "🛒") ?: "🛒")

        )

    }

 

    val quantities = remember {
        mutableStateListOf<Int>().apply {
            val saved = prefs.getString("quantities", null)
            if (saved != null) addAll(saved.split(",").mapNotNull { it.toIntOrNull() }.take(12))
            while (size < 12) add(0)
        }
    }

    LaunchedEffect(Unit) {
        val savedArticles = db.dao().getArticles()
        if (savedArticles.isNotEmpty()) {
            savedArticles.forEach { saved ->
                val index = articles.indexOfFirst { it.id == saved.id }
                if (index >= 0) articles[index] = articles[index].copy(name = saved.name, price = saved.price, enabled = saved.enabled)
            }
        } else {
            db.dao().saveArticles(articles.map { ArticleEntity(it.id, it.name, it.price, it.enabled) })
        }
    }
    LaunchedEffect(currentPage) { prefs.edit().putString("last_page", currentPage).apply() }
    LaunchedEffect(pin) { prefs.edit().putString("pin", pin).apply() }
    LaunchedEffect(quantities.toList()) { prefs.edit().putString("quantities", quantities.joinToString(",")).apply() }
    LaunchedEffect(organization) {
        prefs.edit().putString("org_name", organization.name).putString("org_address", organization.address)
            .putString("org_contact", organization.contact).putString("org_extra", organization.extra)
            .putString("org_event", organization.event).apply()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                androidx.compose.material3.Surface(
                    color = Color(0xFF0865D9), contentColor = Color.White, shadowElevation = 5.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            organization.name.ifBlank { "Organisation" } + if (organization.event.isNotBlank()) "  ·  ${organization.event}" else "",
                            modifier = Modifier.align(Alignment.Center),
                            maxLines = 1,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.align(Alignment.CenterStart),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🪙", fontSize = 30.sp)
                            Spacer(Modifier.size(8.dp))
                            Text("Kassenapp", fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFEAF5FF)).padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("🧾", "Kasse", "kasse"),
                        Triple("👥", "Übergaben", "history"),
                        Triple("📊", "Auswertung", "evaluation"),
                        Triple("⚙️", "Einstellungen", "settings")
                    ).forEach { item ->
                        Column(
                            modifier = Modifier.weight(1f).heightIn(min = 62.dp)
                                .background(
                                    when (item.third) {
                                        "kasse" -> Color(0xFF1594F6)
                                        "history" -> Color(0xFFFF8A00)
                                        "evaluation" -> Color(0xFF7928D8)
                                        else -> Color(0xFF385A7A)
                                    }, RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (item.third == "kasse") {
                                        currentPage = "kasse"
                                        pageHistory.clear()
                                    } else if (item.third != currentPage) {
                                        navigateTo(item.third)
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(item.first, fontSize = 22.sp)
                            Text(item.second, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    ) { padding ->

        Box(Modifier.fillMaxSize()) {
        when (currentPage) {

            "kasse" -> KassePage(

                Modifier.padding(padding),

                articles,

                quantities,

                { index -> quantities[index]++ },

                {

                    val items = articles.mapIndexedNotNull { index, article ->

                        if (quantities[index] > 0) {

                            HandoverEntity(

                                timestamp = System.currentTimeMillis(),

                                articleId = article.id,

                                articleName = article.name,

                                unitPrice = article.price,

                                quantity = quantities[index],

                                totalPrice = article.price * quantities[index],

                                registerRole = prefs.getString("register_role", "Masterkasse") ?: "Masterkasse"

                            )

                        } else null

                    }

 

                    if (items.isNotEmpty()) {

                        scope.launch(Dispatchers.IO) {
                            db.dao().saveHandoverAndTotal(items)
                        }

                    }

 

                    for (i in quantities.indices) quantities[i] = 0

                },

                { for (i in quantities.indices) quantities[i] = 0 }

            )

 

            "settings" -> SettingsPage(
                modifier = Modifier.padding(padding),
                onArticles = { navigateTo("articles") },
                onEvaluation = { navigateTo("evaluation") },
                onPinChange = { navigateTo("pin") },
                onOrganization = { navigateTo("organization") },
                onCountCash = { navigateTo("countcash") },
                onSymbolManagement = { navigateTo("symbols") },
                onProfiles = { navigateTo("profiles") },
                onRole = { pinAction = "open_role"; showPinDialog = true }
            )

            "role" -> RegisterRolePage(
                modifier = Modifier.padding(padding),
                currentRole = prefs.getString("register_role", "Masterkasse") ?: "Masterkasse",
                prefs = prefs,
                context = context,
                onSave = { role ->
                    prefs.edit().putString("register_role", role).apply()
                    activeRole = role
                    goBack()
                }
            )

            "profiles" -> ProfilesPage(
                modifier = Modifier.padding(padding),
                prefs = prefs,
                organizationEvent = organization.event,
                db = db,
                pin = pin
            )

            "symbols" -> SymbolManagementPage(
                modifier = Modifier.padding(padding),
                enabledSymbols = enabledArticleSymbols,
                onSymbolToggled = { symbol, enabled ->
                    if (enabled && symbol !in enabledArticleSymbols) enabledArticleSymbols.add(symbol)
                    if (!enabled) enabledArticleSymbols.remove(symbol)
                    prefs.edit().putStringSet("enabled_article_symbols", enabledArticleSymbols.toSet()).apply()
                }
            )

            "articles" -> ArticleManagementPage(
                modifier = Modifier.padding(padding),
                articles = articles,
                enabledSymbols = enabledArticleSymbols,
                onArticleChanged = { index, article ->
                    articles[index] = article
                    prefs.edit().putString("article_symbol_${article.id}", article.symbol).apply()
                    scope.launch(Dispatchers.IO) {
                        db.dao().saveArticles(articles.map { ArticleEntity(it.id, it.name, it.price, it.enabled) })
                    }
                }
            )

            "evaluation" -> EvaluationMenuPage(
                modifier = Modifier.padding(padding),
                onHistory = { navigateTo("history") },
                onTotal = { navigateTo("total") },
                onResetHistory = { pinAction = "reset_history"; showPinDialog = true },
                onResetTotal = { pinAction = "reset_total"; showPinDialog = true },
                onResetCashCount = { pinAction = "reset_cash_count"; showPinDialog = true },
                onExportHistoryPdf = { exportHistoryPdf(context, db, organization) },
                onExportTotalPdf = { exportTotalPdf(context, db, organization, prefs) }
            )

            "history" -> HistoryPage(modifier = Modifier.padding(padding), db = db)

            "total" -> TotalEvaluationPage(
                modifier = Modifier.padding(padding),
                db = db,
                prefs = prefs
            )

            "countcash" -> CountCashPage(
                modifier = Modifier.padding(padding),
                prefs = prefs
            )

            "organization" -> OrganizationPage(
                modifier = Modifier.padding(padding),
                initial = organization,
                onSave = { saved -> organization = saved; goBack() }
            )

            "pin" -> PinChangePage(
                modifier = Modifier.padding(padding),
                onSave = { newPin ->
                    if (newPin.length >= 4) {
                        pin = newPin
                        goBack()
                    }
                }
            )

        }

    }

 

    if (showPinDialog) {

        PinDialog(

            onDismiss = { showPinDialog = false },

            onConfirmed = { enteredPin ->

                if (enteredPin == pin) {

                    when (pinAction) {

                        "open_role" -> navigateTo("role")

                        "reset_history" ->

                            scope.launch(Dispatchers.IO) { db.dao().deleteHandovers() }

                        "reset_total" ->

                            scope.launch(Dispatchers.IO) { db.dao().deleteTotalEvaluation() }

                        "reset_cash_count" ->
                            prefs.edit()
                                .putString("cash_count_columns", "0,0,0,0,0,0,0")
                                .remove("cash_count_change")
                                .remove("cash_count_change_timestamp")
                                .remove("cash_count_settlement")
                                .remove("cash_count_settlement_timestamp")
                                .apply()

                    }

                    showPinDialog = false

                }

            }

        )

    }

}

 

@Composable

fun KassePage(

    modifier: Modifier = Modifier,

    articles: List<Article>,

    quantities: List<Int>,

    onQuantityChanged: (Int) -> Unit,

    onHandover: () -> Unit,

    onUndo: () -> Unit

) {

    val total = articles.indices.sumOf {

        articles[it].price * quantities[it]

    }

 

    Column(modifier.fillMaxSize().padding(12.dp)) {

        Text(

            "Artikel",

            style = MaterialTheme.typography.headlineSmall,

            fontWeight = FontWeight.Bold

        )

 

        Spacer(Modifier.height(8.dp))

 

        val visibleArticles = articles.withIndex().filter { it.value.enabled }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(visibleArticles.size) { visibleIndex ->
                val (itemIndex, article) = visibleArticles[visibleIndex]
                ArticleButton(
                    article = article,
                    quantity = quantities[itemIndex],
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onQuantityChanged(itemIndex) }
                )
            }
        }

 

        Spacer(Modifier.height(8.dp))

 

        Card(Modifier.fillMaxWidth()) {

            Row(

                Modifier.fillMaxWidth().padding(16.dp),

                verticalAlignment = Alignment.CenterVertically

            ) {

                Text(text = "Summe:", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))

                Text(

                    String.format(Locale.GERMANY, "%.2f €", total),

                    style = MaterialTheme.typography.headlineMedium,

                    fontWeight = FontWeight.Bold

                )

            }

        }

 

        Spacer(Modifier.height(8.dp))

 

        Row(

            Modifier.fillMaxWidth(),

            horizontalArrangement = Arrangement.spacedBy(8.dp)

        ) {

            Button(onClick = onUndo, modifier = Modifier.weight(1f)) {

                Icon(Icons.Default.Delete, null)

                Spacer(Modifier.size(6.dp))

                Text("Ups, vertippt")

            }

            Button(onClick = onHandover, modifier = Modifier.weight(1f)) {

                Text("Übergabe")

            }

        }

    }

}

 

@Composable

fun ArticleButton(

    article: Article,

    quantity: Int,

    modifier: Modifier = Modifier,

    onClick: () -> Unit

) {

    Button(

        onClick = onClick,

        enabled = article.enabled,

        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 1.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF4FAFF),
            contentColor = Color(0xFF102B55),
            disabledContainerColor = Color(0xFFE6EDF4),
            disabledContentColor = Color.Gray
        )

    ) {

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = article.symbol, fontSize = 30.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = article.name, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (quantity > 0) {
                    Text(
                        text = "× $quantity",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFB45309),
                        maxLines = 1
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                Text(
                    String.format(Locale.GERMANY, "%.2f €", article.price),
                    color = Color(0xFF174A8B),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

    }

}

 

@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    onArticles: () -> Unit,
    onEvaluation: () -> Unit,
    onPinChange: () -> Unit,
    onOrganization: () -> Unit,
    onCountCash: () -> Unit,
    onSymbolManagement: () -> Unit,
    onProfiles: () -> Unit,
    onRole: () -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Button(onClick = onArticles, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("Artikelverwaltung") }
        Button(onClick = onSymbolManagement, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("Symbolverwaltung") }
        Button(onClick = onProfiles, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("Profile") }
        OutlinedButton(onClick = onRole, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Rolle (PIN geschützt)") }
        Button(onClick = onOrganization, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("Organisation") }
        Button(onClick = onCountCash, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("Kasse zählen") }
        OutlinedButton(onClick = onPinChange, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("PIN ändern") }
    }
}

@Composable
fun RegisterRolePage(
    modifier: Modifier = Modifier,
    currentRole: String,
    prefs: android.content.SharedPreferences,
    context: android.content.Context,
    onSave: (String) -> Unit
) {
    var selected by remember(currentRole) { mutableStateOf(currentRole) }
    val roles = listOf("Masterkasse", "Zusatzkasse 1", "Zusatzkasse 2")
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Kassenrolle", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Wähle aus, welche Rolle dieses Tablet im Kassenverbund hat.")
        roles.forEach { role ->
            OutlinedButton(
                onClick = { selected = role },
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Text(if (selected == role) "●  $role" else "○  $role")
            }
        }
        var pairingKey by remember { mutableStateOf(prefs.getString("lan_pairing_key", "") ?: "") }
        var masterHost by remember { mutableStateOf(prefs.getString("lan_master_host", "") ?: "") }
        var masterPortText by remember { mutableStateOf(prefs.getInt("lan_master_port", LanDiscovery.DEFAULT_PORT).toString()) }
        if (selected == "Masterkasse") {
            val masterKey = remember { LanAuth.getOrCreateMasterKey(context) }
            Text("Kopplungscode für Zusatzkassen", fontWeight = FontWeight.Bold)
            Text(masterKey, style = MaterialTheme.typography.bodyMedium)
            Text("Diesen Code auf Zusatzkassen eintragen. Er authentifiziert Geräte im lokalen WLAN; die Verbindung ist weiterhin nicht verschlüsselt.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            OutlinedTextField(
                value = pairingKey, onValueChange = { pairingKey = it },
                label = { Text("Kopplungscode der Masterkasse") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Text("Falls die automatische Suche die Masterkasse nicht findet, kannst du ihre WLAN-IP hier manuell eintragen.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            OutlinedTextField(
                value = masterHost, onValueChange = { masterHost = it.trim() },
                label = { Text("Master-IP-Adresse (optional)") },
                placeholder = { Text("z. B. 192.168.1.25") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = masterPortText, onValueChange = { masterPortText = it.filter(Char::isDigit).take(5) },
                label = { Text("Port") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }
        Button(onClick = {
            if (selected != "Masterkasse") {
                LanAuth.setKey(context, pairingKey)
                val parsedPort = masterPortText.toIntOrNull()
                val validPort = parsedPort != null && parsedPort in 1..65535
                val validHost = masterHost.isBlank() || masterHost.matches(Regex("^[A-Za-z0-9.-]+$"))
                if (validPort && validHost) {
                    prefs.edit().putString("lan_master_host", masterHost.ifBlank { null })
                        .putInt("lan_master_port", parsedPort!!).apply()
                }
            }
            onSave(selected)
        }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Text("Rolle speichern")
        }
        Text(
            "Die Masterkasse zeigt einen Kopplungscode. Zusatzkassen müssen denselben Code eintragen. Die Master-IP kann optional manuell eingetragen werden; eine später erkannte Masterkasse aktualisiert die gespeicherte Adresse.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
fun SymbolManagementPage(
    modifier: Modifier = Modifier,
    enabledSymbols: List<String>,
    onSymbolToggled: (String, Boolean) -> Unit
) {
    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Text("Symbolverwaltung", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Wähle aus, welche Symbole in der Artikelverwaltung erscheinen sollen.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(articleSymbolGroups) { group ->
                Column {
                    Text(group.first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                    val rows = group.second.distinct().chunked(4)
                    rows.forEach { rowSymbols ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowSymbols.forEach { symbol ->
                                Row(
                                    modifier = Modifier.weight(1f).clickable { onSymbolToggled(symbol, symbol !in enabledSymbols) }.padding(vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Checkbox(checked = symbol in enabledSymbols, onCheckedChange = { onSymbolToggled(symbol, it) })
                                    Text(symbol, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                            repeat(4 - rowSymbols.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfilesPage(
    modifier: Modifier = Modifier,
    prefs: android.content.SharedPreferences,
    organizationEvent: String,
    db: KassenDb,
    pin: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf("") }
    var profileNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var deleteTarget by remember { mutableStateOf("") }
    var enteredPin by remember { mutableStateOf("") }
    LaunchedEffect(refresh) { profileNames = db.dao().getProfiles().map { it.name } }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Profile sichern Einstellungen, Artikel, Übergaben und Auswertungsstand in der Datenbank.")
        if (profileNames.isNotEmpty()) {
            Text("Gespeicherte Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            profileNames.forEach { name ->
                Row(Modifier.fillMaxWidth().background(if (selected == name) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButtonCompat(selected == name) { selected = name }
                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val profile = db.dao().getProfiles().firstOrNull { it.name == name } ?: return@launch
                            try {
                                val obj = org.json.JSONObject(profile.settingsJson)
                                val editor = prefs.edit()
                                obj.keys().forEach { key ->
                                    val value = obj.get(key)
                                    when (value) {
                                        is org.json.JSONArray -> if (key == "enabled_article_symbols") { val set = mutableSetOf<String>(); for (i in 0 until value.length()) set.add(value.optString(i)); editor.putStringSet(key, set) } else editor.putString(key, value.toString())
                                        is Boolean -> editor.putBoolean(key, value)
                                        is Int -> editor.putInt(key, value)
                                        is Long -> editor.putLong(key, value)
                                        is Number -> editor.putFloat(key, value.toFloat())
                                        else -> if (value != org.json.JSONObject.NULL) editor.putString(key, value.toString())
                                    }
                                }
                                // Einstellungen synchron schreiben, damit sie beim Neuaufbau sofort verfügbar sind.
                                if (!editor.commit()) throw IllegalStateException("Einstellungen konnten nicht gespeichert werden")
                                val articles = mutableListOf<ArticleEntity>(); val aj = org.json.JSONArray(profile.articlesJson)
                                // Artikelsymbole werden explizit mit dem Artikelprofil wiederhergestellt.
                                // Sie liegen zusätzlich in SharedPreferences, nicht in ArticleEntity.
                                for (i in 0 until aj.length()) {
                                    val a = aj.getJSONObject(i)
                                    val articleId = a.getInt("id")
                                    articles.add(ArticleEntity(articleId, a.getString("name"), a.getDouble("price"), a.getBoolean("enabled")))
                                    if (a.has("symbol")) editor.putString("article_symbol_$articleId", a.getString("symbol"))
                                }
                                val handovers = mutableListOf<HandoverEntity>(); val hj=org.json.JSONArray(profile.handoversJson)
                                for(i in 0 until hj.length()){val h=hj.getJSONObject(i); handovers.add(HandoverEntity(
                                        id = h.getLong("id"),
                                        timestamp = h.getLong("timestamp"),
                                        articleId = h.getInt("articleId"),
                                        articleName = h.getString("articleName"),
                                        unitPrice = h.getDouble("unitPrice"),
                                        quantity = h.getInt("quantity"),
                                        totalPrice = h.getDouble("totalPrice"),
                                        registerRole = h.optString("registerRole", "Masterkasse"),
                                        syncId = h.optString("syncId", UUID.randomUUID().toString())
                                    ))}
                                val eval = mutableListOf<TotalEvaluationEntity>(); val ej=org.json.JSONArray(profile.evaluationJson)
                                for(i in 0 until ej.length()){val e=ej.getJSONObject(i); eval.add(TotalEvaluationEntity(e.getInt("articleId"),e.getString("articleName"),e.getDouble("unitPrice"),e.getInt("quantity"),e.getDouble("totalPrice")))}
                                db.dao().clearArticles(); db.dao().saveArticles(articles); db.dao().deleteHandovers(); if(handovers.isNotEmpty()) db.dao().insertHandovers(handovers); db.dao().clearTotalEvaluation(); eval.forEach { db.dao().saveTotalEvaluation(it) }
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context,"Profil geladen.",android.widget.Toast.LENGTH_LONG).show()
                                    // Compose hält Artikel, Organisation, Symbole und Mengen im Arbeitsspeicher.
                                    // Durch einen Neustart werden diese Zustände aus den geladenen Profilwerten neu aufgebaut.
                                    (context as? android.app.Activity)?.recreate()
                                }
                            } catch (_: Exception) { kotlinx.coroutines.withContext(Dispatchers.Main) { android.widget.Toast.makeText(context,"Profil konnte nicht geladen werden.",android.widget.Toast.LENGTH_LONG).show() } }
                        }
                    }) { Text("Laden") }
                    OutlinedButton(onClick = { deleteTarget = name; enteredPin = "" }) { Text("Löschen") }
                }
            }
        } else Text("Noch keine Profile gespeichert.")
        Button(onClick = {
            scope.launch(Dispatchers.IO) {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY).format(Date())
                val base = organizationEvent.trim().ifBlank { "Veranstaltung" }
                val name = "$base – $stamp"
                val settings = org.json.JSONObject()
                prefs.all.forEach { (key,value) -> if(!key.startsWith("profile_") && key != "saved_profile_names") when(value){ is Set<*> -> settings.put(key,org.json.JSONArray(value.toList())); null -> Unit; else -> settings.put(key,value) } }
                val articles = org.json.JSONArray()
                db.dao().getArticles().forEach { article ->
                    articles.put(
                        org.json.JSONObject()
                            .put("id", article.id)
                            .put("name", article.name)
                            .put("price", article.price)
                            .put("enabled", article.enabled)
                            .put("symbol", prefs.getString("article_symbol_${article.id}", "🛒") ?: "🛒")
                    )
                }
                val handovers = org.json.JSONArray(); db.dao().getHandovers().forEach { handovers.put(org.json.JSONObject().put("id",it.id).put("timestamp",it.timestamp).put("articleId",it.articleId).put("articleName",it.articleName).put("unitPrice",it.unitPrice).put("quantity",it.quantity).put("totalPrice",it.totalPrice).put("registerRole",it.registerRole).put("syncId",it.syncId)) }
                val eval = org.json.JSONArray(); db.dao().getTotalEvaluation().forEach { eval.put(org.json.JSONObject().put("articleId",it.articleId).put("articleName",it.articleName).put("unitPrice",it.unitPrice).put("quantity",it.quantity).put("totalPrice",it.totalPrice)) }
                db.dao().saveProfile(ProfileEntity(name,System.currentTimeMillis(),settings.toString(),articles.toString(),handovers.toString(),eval.toString()))
                kotlinx.coroutines.withContext(Dispatchers.Main) { selected=name; refresh++; android.widget.Toast.makeText(context,"Profil gespeichert: $name",android.widget.Toast.LENGTH_LONG).show() }
            }
        }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Aktuellen Stand als Profil speichern") }
    }
    if (deleteTarget.isNotBlank()) AlertDialog(
        onDismissRequest = { deleteTarget = "" },
        title = { Text("Profil löschen") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("PIN eingeben, um '$deleteTarget' endgültig zu löschen."); OutlinedTextField(enteredPin, { enteredPin = it }, label = { Text("PIN") }, singleLine = true) } },
        confirmButton = { Button(onClick = { if (enteredPin == pin) { scope.launch(Dispatchers.IO) { db.dao().deleteProfile(deleteTarget); kotlinx.coroutines.withContext(Dispatchers.Main) { refresh++; deleteTarget = ""; android.widget.Toast.makeText(context,"Profil gelöscht.",android.widget.Toast.LENGTH_SHORT).show() } } } else { android.widget.Toast.makeText(context,"Falsche PIN.",android.widget.Toast.LENGTH_SHORT).show() } }) { Text("Löschen") } },
        dismissButton = { OutlinedButton(onClick = { deleteTarget = "" }) { Text("Abbrechen") } }
    )
}

@Composable
private fun RadioButtonCompat(selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
}

@Composable
fun CountCashPage(
    modifier: Modifier = Modifier,
    prefs: android.content.SharedPreferences
) {
    val denominations = listOf(50, 100, 200, 500, 1000, 2000, 5000)
    val labels = listOf("50 Cent", "1 €", "2 €", "5 €", "10 €", "20 €", "50 €")
    val currentColumns = remember {
        mutableStateListOf<Int>().apply {
            val saved = prefs.getString("cash_count_columns", null)
            if (saved != null) addAll(saved.split(",").mapNotNull { it.toIntOrNull() }.take(7))
            while (size < 7) add(0)
        }
    }
    var savedChange by remember { mutableStateOf(prefs.getInt("cash_count_change", -1)) }
    var savedSettlement by remember { mutableStateOf(prefs.getInt("cash_count_settlement", -1)) }
    var savedChangeTimestamp by remember { mutableStateOf(prefs.getLong("cash_count_change_timestamp", -1L)) }
    var savedSettlementTimestamp by remember { mutableStateOf(prefs.getLong("cash_count_settlement_timestamp", -1L)) }
    val total = currentColumns.sum()

    LaunchedEffect(currentColumns.toList()) {
        prefs.edit().putString("cash_count_columns", currentColumns.joinToString(",")).apply()
    }
    LaunchedEffect(savedChange) {
        if (savedChange >= 0) prefs.edit().putInt("cash_count_change", savedChange).apply()
    }
    LaunchedEffect(savedSettlement) {
        if (savedSettlement >= 0) prefs.edit().putInt("cash_count_settlement", savedSettlement).apply()
    }

    fun money(cents: Int): String = String.format(java.util.Locale.GERMANY, "%.2f €", cents / 100.0)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Kasse zählen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Tippe die passende Münze oder den Geldschein an. Die zweite Zeile zählt jeweils den fünffachen Wert.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            labels.forEachIndexed { index, label ->
                Button(
                    onClick = { currentColumns[index] = currentColumns[index] + denominations[index] },
                    modifier = Modifier.weight(1f).height(58.dp),
                    contentPadding = PaddingValues(horizontal = 1.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(label, textAlign = TextAlign.Center, fontSize = 10.sp, lineHeight = 12.sp) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            labels.forEachIndexed { index, _ ->
                val fivefold = denominations[index] * 5
                Button(
                    onClick = { currentColumns[index] = currentColumns[index] + fivefold },
                    modifier = Modifier.weight(1f).height(58.dp),
                    contentPadding = PaddingValues(horizontal = 1.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("5×\n${money(fivefold)}", textAlign = TextAlign.Center, fontSize = 9.sp, lineHeight = 11.sp) }
            }
        }
        Text("Wert je Spalte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            labels.forEachIndexed { index, _ ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(money(currentColumns[index]), fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color.Red, RoundedCornerShape(50))
                            .clickable { currentColumns[index] = 0 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("X", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gesamtwert der Kasse", style = MaterialTheme.typography.titleMedium)
                Text(money(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    savedChange = total
                    savedChangeTimestamp = System.currentTimeMillis()
                    prefs.edit()
                        .putInt("cash_count_change", total)
                        .putLong("cash_count_change_timestamp", savedChangeTimestamp)
                        .apply()
                    for (i in currentColumns.indices) currentColumns[i] = 0
                },
                modifier = Modifier.weight(1f).height(60.dp)
            ) { Text("Wechselgeld") }
            Button(
                onClick = {
                    savedSettlement = total
                    savedSettlementTimestamp = System.currentTimeMillis()
                    prefs.edit()
                        .putInt("cash_count_settlement", total)
                        .putLong("cash_count_settlement_timestamp", savedSettlementTimestamp)
                        .apply()
                    for (i in currentColumns.indices) currentColumns[i] = 0
                },
                modifier = Modifier.weight(1f).height(60.dp)
            ) { Text("Abrechnung") }
        }
        Text("Gespeicherte Werte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.Card(Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Wechselgeld")
                    Text(if (savedChange >= 0) money(savedChange) else "Noch nicht gespeichert", fontWeight = FontWeight.Bold)
                    if (savedChangeTimestamp > 0L) Text(SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date(savedChangeTimestamp)), style = MaterialTheme.typography.bodySmall)
                }
            }
            androidx.compose.material3.Card(Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Abrechnung")
                    Text(if (savedSettlement >= 0) money(savedSettlement) else "Noch nicht gespeichert", fontWeight = FontWeight.Bold)
                    if (savedSettlementTimestamp > 0L) Text(SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date(savedSettlementTimestamp)), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun OrganizationPage(
    modifier: Modifier = Modifier,
    initial: OrganizationInfo,
    onSave: (OrganizationInfo) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var address by remember { mutableStateOf(initial.address) }
    var contact by remember { mutableStateOf(initial.contact) }
    var extra by remember { mutableStateOf(initial.extra) }
    var event by remember { mutableStateOf(initial.event) }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Organisation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, label = { Text("Organisationsname") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(address, { address = it }, label = { Text("Anschrift") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedTextField(contact, { contact = it }, label = { Text("Kontakt (Telefon / E-Mail)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(extra, { extra = it }, label = { Text("Zusatzangaben (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedTextField(event, { event = it }, label = { Text("Veranstaltung") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { onSave(OrganizationInfo(name.trim(), address.trim(), contact.trim(), extra.trim(), event.trim())) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Einstellungen speichern") }
    }
}

@Composable
fun PinChangePage(
    modifier: Modifier = Modifier,
    onSave: (String) -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("PIN ändern", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Bitte gib eine neue PIN mit mindestens 4 Zeichen ein.")
        OutlinedTextField(
            value = newPin,
            onValueChange = { newPin = it.filter(Char::isDigit); error = false },
            label = { Text("Neue PIN") },
            singleLine = true,
            isError = error
        )
        if (error) Text("Die PIN muss mindestens 4 Ziffern enthalten.", color = MaterialTheme.colorScheme.error)
        Button(
            onClick = { if (newPin.length >= 4) onSave(newPin) else error = true },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) { Text("PIN speichern") }
    }
}

@Composable
fun ArticleManagementPage(
    modifier: Modifier = Modifier,
    articles: MutableList<Article>,
    enabledSymbols: List<String>,
    onArticleChanged: (Int, Article) -> Unit
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Artikelverwaltung", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(articles) { article ->
                ArticleSettingsCard(article, enabledSymbols) { changed ->
                    val index = articles.indexOfFirst { it.id == changed.id }
                    if (index >= 0) onArticleChanged(index, changed)
                }
            }
        }
    }
}

@Composable
fun EvaluationMenuPage(
    modifier: Modifier = Modifier,
    onHistory: () -> Unit,
    onTotal: () -> Unit,
    onResetHistory: () -> Unit,
    onResetTotal: () -> Unit,
    onResetCashCount: () -> Unit,
    onExportHistoryPdf: () -> Unit,
    onExportTotalPdf: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Auswertung", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Button(onClick = onHistory, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Auswertung Übergaben") }
        Button(onClick = onTotal, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Gesamtauswertung") }
        OutlinedButton(onClick = onExportHistoryPdf, modifier = Modifier.fillMaxWidth()) { Text("Übergaben als PDF exportieren") }
        OutlinedButton(onClick = onResetHistory, modifier = Modifier.fillMaxWidth()) { Text("Auswertung Übergaben zurücksetzen (PIN)") }
        OutlinedButton(onClick = onExportTotalPdf, modifier = Modifier.fillMaxWidth()) { Text("Gesamtauswertung als PDF exportieren") }
        OutlinedButton(onClick = onResetTotal, modifier = Modifier.fillMaxWidth()) { Text("Gesamtauswertung zurücksetzen (PIN)") }
        OutlinedButton(onClick = onResetCashCount, modifier = Modifier.fillMaxWidth()) { Text("Kasse zählen zurücksetzen (PIN)") }
    }
}


@Composable
fun ArticleSettingsCard(
    article: Article,
    enabledSymbols: List<String>,
    onChanged: (Article) -> Unit
) {
    var name by remember(article.id, article.name) { mutableStateOf(article.name) }
    var price by remember(article.id, article.price) {
        mutableStateOf(String.format(Locale.US, "%.2f", article.price))
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onChanged(article.copy(name = it))
                },
                label = { Text("Bezeichnung") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = price,
                onValueChange = { value ->
                    price = value
                    value.replace(',', '.').toDoubleOrNull()?.let { parsed ->
                        onChanged(article.copy(price = parsed))
                    }
                },
                label = { Text("Preis in €") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            var symbolMenuExpanded by remember(article.id) { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { symbolMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Symbol: ${article.symbol}  ▾", fontSize = 18.sp)
                }
                DropdownMenu(
                    expanded = symbolMenuExpanded,
                    onDismissRequest = { symbolMenuExpanded = false }
                ) {
                    enabledSymbols.forEach { symbol ->
                        DropdownMenuItem(
                            text = { Text(symbol, fontSize = 22.sp) },
                            onClick = {
                                onChanged(article.copy(symbol = symbol))
                                symbolMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (article.enabled) "Aktiv" else "Deaktiviert",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { onChanged(article.copy(enabled = !article.enabled)) }
                ) {
                    Text(if (article.enabled) "Deaktivieren" else "Aktivieren")
                }
            }
        }
    }
}

@Composable
fun HistoryPage(modifier: Modifier = Modifier, db: KassenDb) {
    var handovers by remember { mutableStateOf<List<HandoverEntity>>(emptyList()) }
    LaunchedEffect(Unit) { handovers = db.dao().getHandovers() }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Auswertung Übergaben",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        if (handovers.isEmpty()) {
            Text("Noch keine Übergaben vorhanden.")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val grouped = handovers.groupBy { it.timestamp }
                items(grouped.entries.toList()) { entry ->
                    HandoverCard(entry.key, entry.value)
                }
            }
        }
    }
}

@Composable
fun HandoverCard(timestamp: Long, items: List<HandoverEntity>) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY) }
    val total = items.sumOf { it.totalPrice }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(dateFormat.format(Date(timestamp)), fontWeight = FontWeight.Bold)
            items.firstOrNull()?.let { Text("Kasse: ${it.registerRole}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            Spacer(Modifier.height(6.dp))
            items.forEach { item ->
                Text(
                    "${item.articleName}: ${item.quantity} × " +
                        String.format(Locale.GERMANY, "%.2f €", item.unitPrice) +
                        " = " + String.format(Locale.GERMANY, "%.2f €", item.totalPrice)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Gesamt: " + String.format(Locale.GERMANY, "%.2f €", total),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TotalEvaluationPage(
    modifier: Modifier = Modifier,
    db: KassenDb,
    prefs: android.content.SharedPreferences
) {
    var totals by remember { mutableStateOf<List<TotalEvaluationEntity>>(emptyList()) }
    var allHandovers by remember { mutableStateOf<List<HandoverEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        totals = db.dao().getTotalEvaluation()
        allHandovers = db.dao().getHandovers()
    }
    val turnover = allHandovers.sumOf { it.totalPrice }.takeIf { allHandovers.isNotEmpty() } ?: totals.sumOf { it.totalPrice }
    val registerSummaries = allHandovers.groupBy { it.registerRole }.mapValues { (_, rows) ->
        Pair(rows.sumOf { it.quantity }, rows.sumOf { it.totalPrice })
    }.toSortedMap()
    val savedChange = prefs.getInt("cash_count_change", -1)
    val savedChangeTimestamp = prefs.getLong("cash_count_change_timestamp", -1L)
    val savedSettlement = prefs.getInt("cash_count_settlement", -1)
    val savedSettlementTimestamp = prefs.getLong("cash_count_settlement_timestamp", -1L)
    val cashDateFormat = remember { SimpleDateFormat("dd.MM.yyyy 'um' HH:mm:ss", Locale.GERMANY) }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Gesamtauswertung",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text("Umsatz nach Kasse", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (registerSummaries.isEmpty()) {
            Text("Noch keine Übergaben mit Kassenrolle vorhanden.")
        } else {
            registerSummaries.forEach { (role, values) ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(role, fontWeight = FontWeight.Bold)
                            Text("${values.first} Artikel verkauft")
                        }
                        Text(String.format(Locale.GERMANY, "%.2f €", values.second), fontWeight = FontWeight.Bold)
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Gesamt alle Kassen", fontWeight = FontWeight.Bold)
                    Text(String.format(Locale.GERMANY, "%.2f €", turnover), fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val cashTurnoverCents = if (savedChange >= 0 && savedSettlement >= 0) savedSettlement - savedChange else null
        val handoverTurnoverCents = kotlin.math.round(turnover * 100.0).toInt()
        val remainderCents = cashTurnoverCents?.minus(handoverTurnoverCents)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 600.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kasse gezählt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        CashSavedValueCard("Wechselgeld", savedChange, savedChangeTimestamp, cashDateFormat)
                        CashSavedValueCard("Abrechnung", savedSettlement, savedSettlementTimestamp, cashDateFormat)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Berechnung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        CashCalculationCard(
                            "Umsatz gezählt in Kasse",
                            cashTurnoverCents?.let { String.format(Locale.GERMANY, "%.2f €", it / 100.0) }
                                ?: "Nicht berechenbar – Wechselgeld und Abrechnung müssen gespeichert sein"
                        )
                        CashCalculationCard("Umsatz nach Übergaben", String.format(Locale.GERMANY, "%.2f €", turnover))
                        CashCalculationCard(
                            "Differenz in Kasse",
                            remainderCents?.let { (if (it >= 0) "+" else "−") + String.format(Locale.GERMANY, "%.2f €", kotlin.math.abs(it) / 100.0) }
                                ?: "Nicht berechenbar",
                            emphasize = true
                        )
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kasse gezählt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        CashSavedValueCard("Wechselgeld", savedChange, savedChangeTimestamp, cashDateFormat)
                        CashSavedValueCard("Abrechnung", savedSettlement, savedSettlementTimestamp, cashDateFormat)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Berechnung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        CashCalculationCard(
                            "Umsatz gezählt in Kasse",
                            cashTurnoverCents?.let { String.format(Locale.GERMANY, "%.2f €", it / 100.0) }
                                ?: "Nicht berechenbar – Wechselgeld und Abrechnung müssen gespeichert sein"
                        )
                        CashCalculationCard("Umsatz nach Übergaben", String.format(Locale.GERMANY, "%.2f €", turnover))
                        CashCalculationCard(
                            "Differenz in Kasse",
                            remainderCents?.let { (if (it >= 0) "+" else "−") + String.format(Locale.GERMANY, "%.2f €", kotlin.math.abs(it) / 100.0) }
                                ?: "Nicht berechenbar",
                            emphasize = true
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Artikelübersicht", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (totals.isEmpty()) {
            Text("Noch keine Gesamtauswertung vorhanden.")
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(totals) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.articleName, fontWeight = FontWeight.Bold)
                            Text("Einzelpreis: " + String.format(Locale.GERMANY, "%.2f €", item.unitPrice))
                            Text("Gesamtmenge: ${item.quantity}")
                            Text("Gesamtpreis: " + String.format(Locale.GERMANY, "%.2f €", item.totalPrice))
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Umsatz nach Übergaben:", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(
                                String.format(Locale.GERMANY, "%.2f €", turnover),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CashCalculationCard(label: String, value: String, emphasize: Boolean = false) {
    Card(Modifier.fillMaxWidth().heightIn(min = 84.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(
                value,
                style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun CashSavedValueCard(label: String, valueCents: Int, timestamp: Long, dateFormat: SimpleDateFormat) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(if (valueCents >= 0) String.format(Locale.GERMANY, "%.2f €", valueCents / 100.0) else "Noch nicht gespeichert")
            if (valueCents >= 0) {
                Text(
                    if (timestamp > 0L) "Berechnet am: ${dateFormat.format(Date(timestamp))}" else "Berechnungszeitpunkt nicht gespeichert (älterer Eintrag)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun PinDialog(onDismiss: () -> Unit, onConfirmed: (String) -> Unit) {
    var enteredPin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN erforderlich") },
        text = {
            OutlinedTextField(
                value = enteredPin,
                onValueChange = { enteredPin = it },
                label = { Text("PIN") },
                singleLine = true
            )
        },
        confirmButton = { Button(onClick = { onConfirmed(enteredPin) }) { Text("Bestätigen") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
fun PinChangeDialog(onDismiss: () -> Unit, onConfirmed: (String) -> Unit) {
    var newPin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN ändern") },
        text = {
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it },
                label = { Text("Neue PIN") },
                singleLine = true
            )
        },
        confirmButton = { Button(onClick = { onConfirmed(newPin) }) { Text("Speichern") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

private fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "PDF exportieren").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun createReportPdf(context: Context, filename: String, title: String, org: OrganizationInfo, period: String, rows: List<String>) {
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 36f
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = android.graphics.Color.rgb(45, 45, 45) }
    val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD); color = android.graphics.Color.rgb(35, 55, 75) }
    val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD); color = android.graphics.Color.rgb(35, 55, 75) }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = android.graphics.Color.DKGRAY }
    val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(246, 248, 250); style = Paint.Style.FILL }
    val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(220, 225, 230); style = Paint.Style.STROKE; strokeWidth = 1f }
    val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(65, 100, 130); strokeWidth = 2f }
    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas = page.canvas
    var y = 42f
    fun newPage() {
        document.finishPage(page)
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page.canvas
        y = 42f
    }
    fun ensureSpace(height: Float) { if (y + height > pageHeight - 42f) newPage() }
    fun wrapped(text: String, paint: Paint, x: Float, startY: Float, maxWidth: Float): Float {
        val words = text.split(Regex("\\s+"))
        var line = ""
        var lineY = startY
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, x, lineY, paint); lineY += 15f; line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) { canvas.drawText(line, x, lineY, paint); lineY += 15f }
        return lineY
    }
    canvas.drawRect(0f, 0f, pageWidth.toFloat(), 8f, accent)
    y = wrapped(title, heading, margin, y + 4f, pageWidth - margin * 2)
    y += 2f
    y = wrapped(org.name.ifBlank { "Organisation: keine Angaben" }, bold, margin, y, pageWidth - margin * 2)
    if (org.event.isNotBlank()) y = wrapped("Veranstaltung: ${org.event}", bold, margin, y, pageWidth - margin * 2)
    if (org.address.isNotBlank()) y = wrapped(org.address, small, margin, y, pageWidth - margin * 2)
    if (org.contact.isNotBlank()) y = wrapped("Kontakt: ${org.contact}", small, margin, y, pageWidth - margin * 2)
    if (org.extra.isNotBlank()) y = wrapped(org.extra, small, margin, y, pageWidth - margin * 2)
    y += 5f
    val metaHeight = 44f
    ensureSpace(metaHeight)
    canvas.drawRoundRect(margin, y, pageWidth - margin, y + metaHeight, 8f, 8f, cardFill)
    canvas.drawRoundRect(margin, y, pageWidth - margin, y + metaHeight, 8f, 8f, cardStroke)
    canvas.drawText("Berichtszeitraum", margin + 12f, y + 17f, bold)
    canvas.drawText(period.take(90), margin + 132f, y + 17f, body)
    canvas.drawText("Erstellt am", margin + 12f, y + 34f, bold)
    canvas.drawText(SimpleDateFormat("dd.MM.yyyy 'um' HH:mm:ss", Locale.GERMANY).format(Date()), margin + 132f, y + 34f, body)
    y += metaHeight + 14f

    var cardTop = -1f
    var cardLines = mutableListOf<String>()
    fun drawCard() {
        if (cardTop < 0f) return
        val lineCount = cardLines.sumOf { maxOf(1, (body.measureText(it) / (pageWidth - margin * 2 - 28f)).toInt() + 1) }
        val height = 30f + lineCount * 16f
        ensureSpace(height + 8f)
        val top = y
        canvas.drawRoundRect(margin, top, pageWidth - margin, top + height, 8f, 8f, cardFill)
        canvas.drawRoundRect(margin, top, pageWidth - margin, top + height, 8f, 8f, cardStroke)
        var cy = top + 21f
        cardLines.forEachIndexed { index, text ->
            val paint = if (index == 0) bold else body
            cy = wrapped(text, paint, margin + 14f, cy, pageWidth - margin * 2 - 28f) + 1f
        }
        y = top + height + 8f
        cardTop = -1f
        cardLines = mutableListOf()
    }
    rows.forEach { row ->
        if (row.startsWith("CARD:")) {
            drawCard()
            cardTop = y
            cardLines.add(row.removePrefix("CARD:"))
        } else if (row.startsWith("TOTAL:")) {
            drawCard()
            ensureSpace(42f)
            canvas.drawRoundRect(margin, y, pageWidth - margin, y + 38f, 8f, 8f, cardFill)
            canvas.drawRoundRect(margin, y, pageWidth - margin, y + 38f, 8f, 8f, cardStroke)
            canvas.drawText(row.removePrefix("TOTAL:"), margin + 14f, y + 24f, bold)
            y += 48f
        } else if (cardTop >= 0f) {
            cardLines.add(row)
        } else {
            ensureSpace(22f)
            y = wrapped(row, body, margin, y, pageWidth - margin * 2) + 4f
        }
    }
    drawCard()
    document.finishPage(page)
    val file = File(context.cacheDir, filename)
    FileOutputStream(file).use { document.writeTo(it) }
    document.close()
    sharePdf(context, file)
}

fun exportHistoryPdf(context: Context, db: KassenDb, org: OrganizationInfo) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        val items = db.dao().getHandovers().sortedBy { it.timestamp }
        val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        val period = if (items.isEmpty()) "Keine Daten vorhanden" else "${df.format(Date(items.first().timestamp))} bis ${df.format(Date(items.last().timestamp))}"
        val rows = mutableListOf<String>()
        items.groupBy { it.timestamp }.forEach { (timestamp, handoverItems) ->
            rows.add("CARD:${df.format(Date(timestamp))}")
            handoverItems.forEach { item ->
                rows.add("${item.articleName}  ·  ${item.quantity} × ${String.format(Locale.GERMANY, "%.2f €", item.unitPrice)}  =  ${String.format(Locale.GERMANY, "%.2f €", item.totalPrice)}")
            }
            rows.add("TOTAL:Übergabesumme: ${String.format(Locale.GERMANY, "%.2f €", handoverItems.sumOf { it.totalPrice })}")
        }
        if (items.isEmpty()) rows.add("Keine Übergaben im Berichtszeitraum.")
        createReportPdf(context, "Auswertung_Uebergaben.pdf", "Auswertung Übergaben", org, period, rows)
    }
}

fun exportTotalPdf(context: Context, db: KassenDb, org: OrganizationInfo, prefs: android.content.SharedPreferences) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        val items = db.dao().getTotalEvaluation()
        val rows = mutableListOf<String>()
        items.forEach { item ->
            rows.add("CARD:${item.articleName}")
            rows.add("Einzelpreis: ${String.format(Locale.GERMANY, "%.2f €", item.unitPrice)}")
            rows.add("Gesamtmenge: ${item.quantity}")
            rows.add("Gesamtpreis: ${String.format(Locale.GERMANY, "%.2f €", item.totalPrice)}")
        }
        if (items.isEmpty()) rows.add("Noch keine Gesamtauswertung vorhanden.")
        val turnover = items.sumOf { it.totalPrice }
        rows.add("TOTAL:Umsatz nach Übergaben: ${String.format(Locale.GERMANY, "%.2f €", turnover)}")
        val change = prefs.getInt("cash_count_change", -1)
        val changeTime = prefs.getLong("cash_count_change_timestamp", -1L)
        val settlement = prefs.getInt("cash_count_settlement", -1)
        val settlementTime = prefs.getLong("cash_count_settlement_timestamp", -1L)
        val cashDateFormat = SimpleDateFormat("dd.MM.yyyy 'um' HH:mm:ss", Locale.GERMANY)
        rows.add("CARD:Kasse gezählt")
        rows.add("Wechselgeld: ${if (change >= 0) String.format(Locale.GERMANY, "%.2f €", change / 100.0) else "Noch nicht gespeichert"}")
        rows.add("Berechnet am: ${if (changeTime > 0L) cashDateFormat.format(Date(changeTime)) else "Kein Zeitpunkt gespeichert"}")
        rows.add("Abrechnung: ${if (settlement >= 0) String.format(Locale.GERMANY, "%.2f €", settlement / 100.0) else "Noch nicht gespeichert"}")
        rows.add("Berechnet am: ${if (settlementTime > 0L) cashDateFormat.format(Date(settlementTime)) else "Kein Zeitpunkt gespeichert"}")
        rows.add("CARD:Berechnung")
        if (change >= 0 && settlement >= 0) {
            val countedCents = settlement - change
            val turnoverCents = kotlin.math.round(turnover * 100.0).toInt()
            val restCents = countedCents - turnoverCents
            rows.add("Umsatz gezählt in Kasse: ${String.format(Locale.GERMANY, "%.2f €", countedCents / 100.0)}")
            rows.add("Umsatz nach Übergaben: ${String.format(Locale.GERMANY, "%.2f €", turnover)}")
            rows.add("TOTAL:Differenz in Kasse: ${(if (restCents >= 0) "+" else "−") + String.format(Locale.GERMANY, "%.2f €", kotlin.math.abs(restCents) / 100.0)}")
        } else {
            rows.add("Umsatz gezählt in Kasse: Nicht berechenbar – Wechselgeld und Abrechnung müssen gespeichert sein")
            rows.add("Umsatz nach Übergaben: ${String.format(Locale.GERMANY, "%.2f €", turnover)}")
            rows.add("Differenz in Kasse: Nicht berechenbar")
        }
        createReportPdf(context, "Gesamtauswertung.pdf", "Gesamtauswertung", org, "Gesamter gespeicherter Zeitraum", rows)
    }
}
}

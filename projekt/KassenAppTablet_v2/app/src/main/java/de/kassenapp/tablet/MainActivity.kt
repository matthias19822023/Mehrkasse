package de.kassenapp.tablet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Entity(tableName = "articles")
data class Article(
    @PrimaryKey val id: Int,
    val name: String,
    val priceCents: Int,
    val color: Long,
    val enabled: Boolean = true
)

@Entity(tableName = "handovers")
data class Handover(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val totalQuantity: Int,
    val totalCents: Int
)

@Entity(tableName = "handover_lines", primaryKeys = ["handoverId", "articleId"])
data class HandoverLine(
    val handoverId: Long,
    val articleId: Int,
    val articleName: String,
    val quantity: Int,
    val totalCents: Int
)

@Dao
interface KassenDao {
    @Query("SELECT * FROM articles ORDER BY id") fun articles(): Flow<List<Article>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveArticle(a: Article)
    @Insert suspend fun saveHandover(h: Handover): Long
    @Insert suspend fun saveLines(lines: List<HandoverLine>)
    @Query("SELECT * FROM handovers ORDER BY timestamp DESC") fun handovers(): Flow<List<Handover>>
    @Query("SELECT * FROM handover_lines ORDER BY handoverId DESC, articleId") fun lines(): Flow<List<HandoverLine>>
    @Query("DELETE FROM handover_lines") suspend fun clearLines()
    @Query("DELETE FROM handovers") suspend fun clearHandovers()
}

@Database(entities=[Article::class,Handover::class,HandoverLine::class], version=1)
abstract class KassenDb : RoomDatabase() {
    abstract fun dao(): KassenDao
    companion object {
        @Volatile private var INSTANCE: KassenDb? = null
        fun get(c: Context): KassenDb = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(c.applicationContext,KassenDb::class.java,"kasse.db").build().also { INSTANCE=it }
        }
    }
}

class KassenVm(private val dao: KassenDao): ViewModel() {
    val articles = dao.articles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val handovers = dao.handovers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lines = dao.lines().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var counts by mutableStateOf(IntArray(8)); private set

    init { viewModelScope.launch {
        if (dao.articles().first().isEmpty()) {
            val colors=listOf(0xFF1976D2,0xFF388E3C,0xFFF57C00,0xFF7B1FA2,0xFFD32F2F,0xFF00796B,0xFF455A64,0xFFC2185B)
            for(i in 0 until 8) dao.saveArticle(Article(i+1,"Artikel ${i+1}",0,colors[i],true))
        }
    }}
    fun add(id:Int){ counts=counts.copyOf().also{it[id-1]++} }
    fun saveArticle(a:Article){viewModelScope.launch{dao.saveArticle(a)}}
    fun handover(){
        viewModelScope.launch{
            val as_=articles.first(); val ts=System.currentTimeMillis()
            val ls=as_.mapIndexedNotNull{idx,a->val q=counts[idx];if(q>0)HandoverLine(0,a.id,a.name,q,q*a.priceCents)else null}
            if(ls.isEmpty())return@launch
            val hid=dao.saveHandover(Handover(0,ts,ls.sumOf{it.quantity},ls.sumOf{it.totalCents}))
            dao.saveLines(ls.map{it.copy(handoverId=hid)}); counts=IntArray(8)
        }
    }
    fun resetEvaluation(){viewModelScope.launch{dao.clearLines();dao.clearHandovers()}}
}

fun money(c:Int)=NumberFormat.getCurrencyInstance(Locale.GERMANY).format(c/100.0)
fun date(t:Long)=SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.GERMANY).format(Date(t))

@Composable
fun KassenApp(vm:KassenVm){
    var page by remember{mutableStateOf("kasse")}
    var unlocked by remember{mutableStateOf(false)}
    var showPin by remember{mutableStateOf(false)}
    var showReset by remember{mutableStateOf(false)}
    var showHistory by remember{mutableStateOf(false)}
    val articles by vm.articles.collectAsState()
    if(showPin) PinDialog(onOk={unlocked=true;showPin=false;page="settings"},onCancel={showPin=false})
    if(showReset) AlertDialog(onDismissRequest={showReset=false},title={Text("Auswertung zurücksetzen?")},text={Text("Alle gespeicherten Übergaben und deren Positionen werden dauerhaft gelöscht.")},confirmButton={TextButton(onClick={vm.resetEvaluation();showReset=false}){Text("LÖSCHEN")}},dismissButton={TextButton(onClick={showReset=false}){Text("ABBRECHEN")}})
    Scaffold(topBar={TopAppBar(title={Text(if(page=="kasse")"Kasse" else if(page=="history")"Übergaben" else "Einstellungen",fontWeight=FontWeight.Bold)},actions={
        if(page=="kasse"){
            IconButton(onClick={if(unlocked){page="settings"}else{showPin=true}}){Icon(Icons.Default.Settings,"Einstellungen")}
        } else IconButton(onClick={page="kasse"}){Icon(Icons.Default.Home,"Kasse")}
    })}){pad->
        when(page){
            "kasse"->KassePage(vm,articles,Modifier.padding(pad),onHistory={page="history"})
            "history"->HistoryPage(vm,Modifier.padding(pad))
            else->SettingsPage(vm,articles,Modifier.padding(pad),onReset={showReset=true},onHistory={page="history"})
        }
    }
}

@Composable
fun KassePage(vm:KassenVm,articles:List<Article>,m:Modifier,onHistory:()->Unit){
    val total=articles.indices.sumOf{it->vm.counts[it]*articles[it].priceCents}; val qty=vm.counts.sum()
    Column(m.fillMaxSize().padding(12.dp)){
        Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            (0..3).forEach{col->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){(0..1).forEach{row->
                val i=row*4+col; ArticleButton(articles.getOrNull(i),vm.counts.getOrElse(i){0}){vm.add(i+1)}
            }}}
        }
        Spacer(Modifier.height(10.dp))
        Surface(tonalElevation=4.dp,shape=RoundedCornerShape(14.dp)){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("Aktuelle Kasse",fontSize=17.sp);Text("$qty Artikel",fontSize=16.sp)}
            Text(money(total),fontSize=28.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.width(16.dp))
            OutlinedButton(onClick=onHistory,modifier=Modifier.height(54.dp)){Icon(Icons.Default.List,null);Spacer(Modifier.width(5.dp));Text("ÜBERGABEN")}
            Spacer(Modifier.width(8.dp))
            Button(onClick={vm.handover()},enabled=qty>0,modifier=Modifier.height(54.dp)){Icon(Icons.Default.Check,null);Spacer(Modifier.width(6.dp));Text("ÜBERGABE",fontWeight=FontWeight.Bold)}
        }}
    }
}

@Composable
fun ArticleButton(a:Article?,count:Int,onClick:()->Unit){
    if(a==null||!a.enabled){Spacer(Modifier.fillMaxWidth().weight(1f));return}
    Button(onClick=onClick,shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(a.color)),modifier=Modifier.fillMaxWidth().weight(1f)){
        Column(horizontalAlignment=Alignment.CenterHorizontally){Text(a.name,fontSize=21.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center);Text(money(a.priceCents),fontSize=17.sp);if(count>0)Text("✓ $count",fontSize=19.sp,fontWeight=FontWeight.Bold)}
    }
}

@Composable
fun HistoryPage(vm:KassenVm,m:Modifier){
    val hs by vm.handovers.collectAsState(); val ls by vm.lines.collectAsState()
    LazyColumn(m.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        items(hs){h->val rows=ls.filter{it.handoverId==h.id};Surface(tonalElevation=3.dp,shape=RoundedCornerShape(12.dp)){Column(Modifier.fillMaxWidth().padding(14.dp)){
            Row{Text("Übergabe #${h.id}",fontWeight=FontWeight.Bold,Modifier.weight(1f));Text(date(h.timestamp));}
            Spacer(Modifier.height(6.dp));rows.forEach{r->Row(Modifier.fillMaxWidth().padding(vertical=2.dp)){Text(r.articleName,Modifier.weight(1f));Text("${r.quantity} ×");Spacer(Modifier.width(25.dp));Text(money(r.totalCents))}}
            HorizontalDivider(Modifier.padding(vertical=5.dp));Row{Text("${h.totalQuantity} Artikel",Modifier.weight(1f),fontWeight=FontWeight.Bold);Text(money(h.totalCents),fontWeight=FontWeight.Bold)}
        }}}
    }
}

@Composable
fun SettingsPage(vm:KassenVm,articles:List<Article>,m:Modifier,onReset:()->Unit,onHistory:()->Unit){
    val ctx=androidx.compose.ui.platform.LocalContext.current
    Row(m.fillMaxSize().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)){
        Column(Modifier.weight(1f).fillMaxHeight()){
            Text("Artikel einrichten",fontSize=24.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(articles){a->ArticleSetting(a,vm)}}
        }
        Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Verwaltung",fontSize=24.sp,fontWeight=FontWeight.Bold)
            Button(onClick=onHistory,Modifier.fillMaxWidth().height(52.dp)){Icon(Icons.Default.List,null);Spacer(Modifier.width(8.dp));Text("ÜBERGABEN ANZEIGEN")}
            Button(onClick={exportCsv(ctx,vm);Toast.makeText(ctx,"CSV-Datei wurde erstellt.",Toast.LENGTH_LONG).show()},Modifier.fillMaxWidth().height(52.dp)){Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("AUSWERTUNG ALS CSV EXPORTIEREN")}
            Button(onClick=onReset,Modifier.fillMaxWidth().height(52.dp),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Icon(Icons.Default.Delete,null);Spacer(Modifier.width(8.dp));Text("AUSWERTUNG ZURÜCKSETZEN",fontWeight=FontWeight.Bold)}
            PinChange(ctx)
        }
    }
}

@Composable
fun ArticleSetting(a:Article,vm:KassenVm){
    var name by remember(a.id,a.name){mutableStateOf(a.name)}
    var price by remember(a.id,a.priceCents){mutableStateOf("%.2f".format(Locale.US,a.priceCents/100.0))}
    var enabled by remember(a.id,a.enabled){mutableStateOf(a.enabled)}
    Surface(tonalElevation=2.dp,shape=RoundedCornerShape(10.dp)){Row(Modifier.fillMaxWidth().padding(7.dp),verticalAlignment=Alignment.CenterVertically){
        Switch(checked=enabled,onCheckedChange={enabled=it;vm.saveArticle(a.copy(enabled=it))})
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(name,{name=it;vm.saveArticle(a.copy(name=it))},Modifier.weight(1f),singleLine=true,label={Text("Bezeichnung")})
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(price,{price=it;val c=((it.replace(',','.').toDoubleOrNull()?:0.0)*100).toInt();vm.saveArticle(a.copy(name=name,priceCents=c,enabled=enabled))},Modifier.width(120.dp),singleLine=true,label={Text("Preis €")})
    }}
}

@Composable
fun PinDialog(onOk:()->Unit,onCancel:()->Unit){
    val ctx=androidx.compose.ui.platform.LocalContext.current
    var pin by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onCancel,title={Text("Einstellungen geschützt")},text={OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(8)},singleLine=true,label={Text("PIN")})},confirmButton={TextButton(onClick={if(pin==ctx.getSharedPreferences("prefs",0).getString("pin","1234"))onOk()else Toast.makeText(ctx,"Falsche PIN",Toast.LENGTH_SHORT).show()}){Text("ÖFFNEN")}},dismissButton={TextButton(onClick=onCancel){Text("ABBRECHEN")}})
}

@Composable
fun PinChange(ctx:Context){
    var open by remember{mutableStateOf(false)};var p1 by remember{mutableStateOf("")};var p2 by remember{mutableStateOf("")}
    OutlinedButton(onClick={open=true},Modifier.fillMaxWidth().height(50.dp)){Icon(Icons.Default.Lock,null);Spacer(Modifier.width(8.dp));Text("PIN ÄNDERN")}
    if(open)AlertDialog(onDismissRequest={open=false},title={Text("PIN ändern")},text={Column{OutlinedTextField(p1,{p1=it.filter(Char::isDigit).take(8)},label={Text("Neue PIN")});OutlinedTextField(p2,{p2=it.filter(Char::isDigit).take(8)},label={Text("PIN wiederholen")})}},confirmButton={TextButton(onClick={if(p1.length>=4&&p1==p2){ctx.getSharedPreferences("prefs",0).edit().putString("pin",p1).apply();open=false;Toast.makeText(ctx,"PIN geändert",Toast.LENGTH_SHORT).show()}else Toast.makeText(ctx,"PINs stimmen nicht überein",Toast.LENGTH_SHORT).show()}){Text("SPEICHERN")}},dismissButton={TextButton(onClick={open=false}){Text("ABBRECHEN")}})
}

fun exportCsv(ctx:Context,vm:KassenVm){
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch{
        val hs=vm.handovers.first();val ls=vm.lines.first()
        val sb=StringBuilder("\uFEFFÜbergabe;Datum;Artikel;Anzahl;Gesamtpreis\n")
        hs.forEach{h->ls.filter{it.handoverId==h.id}.forEach{r->sb.append("${h.id};${date(h.timestamp)};${r.articleName};${r.quantity};${money(r.totalCents)}\n")}}
        val uri=androidx.core.content.FileProvider.getUriForFile(ctx,"de.kassenapp.tablet.fileprovider",java.io.File(ctx.cacheDir,"kassenauswertung.csv"))
        // FileProvider is intentionally avoided; use ACTION_SEND with content written to cache.
        val f=java.io.File(ctx.cacheDir,"kassenauswertung.csv");f.writeText(sb.toString(),Charsets.UTF_8)
        val intent=Intent(Intent.ACTION_SEND).apply{type="text/csv";putExtra(Intent.EXTRA_STREAM,androidx.core.content.FileProvider.getUriForFile(ctx,"de.kassenapp.tablet.fileprovider",f));addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        ctx.startActivity(Intent.createChooser(intent,"Auswertung exportieren"))
    }
}

class MainActivity:ComponentActivity(){
    override fun onCreate(b:Bundle?){super.onCreate(b);enableEdgeToEdge();val db=KassenDb.get(this)
        setContent{MaterialTheme{val vm=viewModel<KassenVm>(factory=object:androidx.lifecycle.ViewModelProvider.Factory{
            override fun<T:ViewModel>create(c:Class<T>):T=KassenVm(db.dao()) as T
        });KassenApp(vm)}}
    }
}

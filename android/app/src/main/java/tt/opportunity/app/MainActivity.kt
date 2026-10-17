package tt.opportunity.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

private val Navy = Color(0xFF183047)
private val Teal = Color(0xFF087F79)
private val Background = Color(0xFFF5F8FA)
private val Warm = Color(0xFFFBF1DC)
private val Muted = Color(0xFF536579)

data class Candidate(
    val id: String = "self",
    val name: String = "",
    val region: String = "",
    val skills: List<String> = emptyList(),
    val qualifications: List<String> = emptyList(),
    val licences: List<String> = emptyList(),
    val years: Double = 0.0,
    val categories: List<String> = emptyList(),
    val visible: Boolean = false,
    val demo: Boolean = false
)

data class Job(
    val id: String,
    val title: String,
    val employer: String,
    val region: String,
    val type: String,
    val category: String,
    val skills: List<String>,
    val hardSkills: List<String>,
    val qualification: String,
    val licence: String,
    val years: Double,
    val pay: String,
    val description: String,
    val demo: Boolean,
    val contact: String = "",
    val sourcePage: Int = 0,
    val sourceDate: String = "",
    val needsReview: Boolean = false
)

data class AppData(
    val profile: Candidate = Candidate(),
    val localJobs: List<Job> = emptyList(),
    val saved: Set<String> = emptySet(),
    val applications: Map<String, String> = emptyMap()
)

data class Result(val eligible: Boolean, val score: Int, val label: String, val reasons: List<String>, val gaps: List<String>)

private fun normalize(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), " ")
internal fun commaList(s: String): List<String> = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { normalize(it) }
internal fun List<String>.asCsv() = joinToString(", ")

internal fun match(candidate: Candidate, job: Job): Result {
    val skills = candidate.skills.map(::normalize).toSet()
    val qualifications = candidate.qualifications.map(::normalize).toSet()
    val licences = candidate.licences.map(::normalize).toSet()
    val gaps = mutableListOf<String>()
    job.hardSkills.forEach { if (normalize(it) !in skills) gaps += "Required skill missing: $it" }
    if (job.qualification.isNotBlank() && normalize(job.qualification) !in qualifications) gaps += "Required qualification missing: ${job.qualification}"
    if (job.licence.isNotBlank() && normalize(job.licence) !in licences) gaps += "Required licence missing: ${job.licence}"
    if (gaps.isNotEmpty()) return Result(false, 0, "Not eligible", emptyList(), gaps)
    var score = 0
    val reasons = mutableListOf<String>()
    if (candidate.categories.any { normalize(it) == normalize(job.category) }) {
        score += 20
        reasons += "Preferred job category"
    }
    if (job.skills.isNotEmpty()) {
        val matched = job.skills.filter { normalize(it) in skills }
        score += (35.0 * matched.size / job.skills.size).roundToInt()
        if (matched.isNotEmpty()) reasons += "Skills: ${matched.joinToString(", ")}"
        val missing = job.skills.filter { normalize(it) !in skills }
        if (missing.isNotEmpty()) gaps += "Other requested skills: ${missing.joinToString(", ")}"
    }
    if (job.years <= 0) {
        score += 10
    } else if (candidate.years >= job.years) {
        score += 15
        reasons += "Meets preferred experience"
    } else if (candidate.years >= job.years * 0.67) {
        score += 8
        gaps += "Employer prefers ${job.years} years of experience"
    }
    if (candidate.region.isNotBlank() && normalize(candidate.region) == normalize(job.region)) {
        score += 15
        reasons += "Preferred region"
    }
    // Employment-type preferences will be added once included in profile onboarding.
    score = score.coerceIn(0, 100)
    val label = when {
        score >= 75 -> "Strong match"
        score >= 50 -> "Good match"
        else -> "Partial match"
    }
    return Result(true, score, label, reasons, gaps)
}

internal fun loadPublishedJobs(context: Context): List<Job> =
    try {
        context.assets.open("classified_jobs_20261017.json").bufferedReader().use { reader ->
            val arr = JSONObject(reader.readText()).getJSONArray("jobs")
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::parseJob) }
        }
    } catch (_: Exception) { emptyList() }

private fun Candidate.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("region", region)
    put("skills", JSONArray(skills)); put("qualifications", JSONArray(qualifications)); put("licences", JSONArray(licences))
    put("years", years); put("categories", JSONArray(categories)); put("visible", visible)
}
private fun Job.toJson() = JSONObject().apply {
    put("id", id); put("title", title); put("employer", employer); put("region", region)
    put("type", type); put("category", category); put("skills", JSONArray(skills))
    put("hardSkills", JSONArray(hardSkills)); put("qualification", qualification); put("licence", licence)
    put("years", years); put("pay", pay); put("description", description)
    put("contact", contact); put("sourcePage", sourcePage); put("sourceDate", sourceDate)
    put("needsReview", needsReview)
}
private fun strings(o: JSONObject, k: String): List<String> {
    val arr = o.optJSONArray(k) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
}
private fun parseCandidate(o: JSONObject) = Candidate(
    id = o.optString("id", "self"), name = o.optString("name"), region = o.optString("region"),
    skills = strings(o, "skills"), qualifications = strings(o, "qualifications"), licences = strings(o, "licences"),
    years = o.optDouble("years", 0.0), categories = strings(o, "categories"), visible = o.optBoolean("visible")
)
private fun parseJob(o: JSONObject) = Job(
    id = o.optString("id"), title = o.optString("title"), employer = o.optString("employer"),
    region = o.optString("region"), type = o.optString("type", "Full-time"), category = o.optString("category"),
    skills = strings(o, "skills"), hardSkills = strings(o, "hardSkills"),
    qualification = o.optString("qualification"), licence = o.optString("licence"),
    years = o.optDouble("years", 0.0), pay = o.optString("pay"), description = o.optString("description"), demo = false,
    contact = o.optString("contact"), sourcePage = o.optInt("sourcePage"),
    sourceDate = o.optString("sourceDate"), needsReview = o.optBoolean("needsReview")
)

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("opportunitytt_v1", Context.MODE_PRIVATE)
    fun load(): AppData {
        return try {
        val raw = prefs.getString("data", null) ?: return AppData()
        val o = JSONObject(raw)
        val jobs = o.optJSONArray("localJobs") ?: JSONArray()
        val saved = o.optJSONArray("saved") ?: JSONArray()
        val stages = o.optJSONObject("applications") ?: JSONObject()
        AppData(
            profile = o.optJSONObject("profile")?.let(::parseCandidate) ?: Candidate(),
            localJobs = (0 until jobs.length()).mapNotNull { jobs.optJSONObject(it)?.let(::parseJob) },
            saved = (0 until saved.length()).map { saved.optString(it) }.toSet(),
            applications = stages.keys().asSequence().associateWith { stages.optString(it) }
        )
        } catch (_: Exception) { AppData() }
    }
    fun readBackup(): String = prefs.getString("data", null) ?: JSONObject().toString()
    fun restoreBackup(raw: String): AppData {
        val parsed = JSONObject(raw)
        require(parsed.has("profile") || parsed.has("saved") || parsed.has("localJobs")) { "Not an OpportunityTT backup" }
        val old = prefs.getString("data", null)
        prefs.edit().putString("data", raw).commit()
        val out = load()
        if (old != null && raw.isNotBlank() && out == AppData()) {
            prefs.edit().putString("data", old).commit()
            throw IllegalArgumentException("Invalid saved content")
        }
        return out
    }
    fun save(data: AppData) {
        val o = JSONObject().apply {
            put("profile", data.profile.toJson())
            put("localJobs", JSONArray().apply { data.localJobs.forEach { put(it.toJson()) } })
            put("saved", JSONArray(data.saved.toList()))
            put("applications", JSONObject(data.applications))
        }
        prefs.edit().putString("data", o.toString()).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OpportunityExperience() }
    }
}

@Composable
private fun OpportunityTTApp() {
    val context = LocalContext.current
    val store = remember { LocalStore(context.applicationContext) }
    var data by remember { mutableStateOf(store.load()) }
    fun update(value: AppData) { data = value; store.save(value) }
    var hiring by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val publishedJobs = remember { loadPublishedJobs(context.applicationContext) }
    val jobs = remember(data.localJobs) { publishedJobs + data.localJobs }
    val profileReady = data.profile.name.isNotBlank() &&
        (data.profile.skills.isNotEmpty() || data.profile.categories.isNotEmpty() || data.profile.region.isNotBlank())
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal, onPrimary = Color.White,
            primaryContainer = Color(0xFFD8F2EE),
            background = Background, surface = Color.White, onSurface = Navy,
            secondary = Navy
        )
    ) {
        Scaffold(
            containerColor = Background,
            topBar = {
                Column(Modifier.fillMaxWidth().background(Navy).statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("OpportunityTT", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Color.White)
                    Text("Discover talent. Discover opportunity.", fontSize = 12.sp, color = Color(0xFFC9DEE6))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !hiring,
                            onClick = { hiring = false; tab = 0 },
                            label = { Text("Find work") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White, labelColor = Navy,
                                selectedContainerColor = Color(0xFFD4F7EF), selectedLabelColor = Navy
                            )
                        )
                        FilterChip(
                            selected = hiring,
                            onClick = { hiring = true; tab = 0 },
                            label = { Text("Hire staff") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White, labelColor = Navy,
                                selectedContainerColor = Color(0xFFD4F7EF), selectedLabelColor = Navy
                            )
                        )
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    val names = if (hiring) listOf("My drafts", "Preview", "Post job")
                                else listOf("Find jobs", "Saved", "My profile")
                    val icons = listOf(Icons.Default.Search, Icons.Default.Bookmark, if (hiring) Icons.Default.Business else Icons.Default.Person)
                    names.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = tab == index, onClick = { tab = index },
                            icon = { Icon(icons[index], contentDescription = null) },
                            label = { Text(title, maxLines = 1) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner)) {
                if (!hiring) when (tab) {
                    0 -> FindJobs(data, jobs, profileReady, onEditProfile = { tab = 2 }, onToggleSave = { job ->
                        update(data.copy(saved = if (job.id in data.saved) data.saved - job.id else data.saved + job.id))
                    })
                    1 -> SavedJobs(data, jobs, onToggleSave = { job -> update(data.copy(saved = data.saved - job.id)) })
                    else -> ProfileEditor(data.profile, onSave = { update(data.copy(profile = it)) })
                } else when (tab) {
                    0 -> EmployerPosts(data.localJobs, goToPost = { tab = 2 }, delete = { job ->
                        update(data.copy(localJobs = data.localJobs.filterNot { it.id == job.id }, saved = data.saved - job.id))
                    })
                    1 -> CandidateDirectory(jobs, data.profile, profileReady)
                    else -> PostJob(onCreate = { job ->
                        update(data.copy(localJobs = data.localJobs + job))
                        tab = 0
                    })
                }
            }
        }
    }
}

@Composable private fun Scrolling(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content
    )
}

@Composable private fun SectionTitle(heading: String, sub: String) {
    Text(heading, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(sub, style = MaterialTheme.typography.bodyMedium, color = Muted)
}

@Composable private fun Notice(text: String) {
    Surface(color = Warm, shape = RoundedCornerShape(14.dp)) {
        Text(text, Modifier.fillMaxWidth().padding(14.dp), fontSize = 13.sp, color = Navy, lineHeight = 19.sp)
    }
}

@Composable private fun Tag(label: String, important: Boolean = false) {
    Surface(color = if (important) Color(0xFFDDF2ED) else Color(0xFFEEF1F5), shape = RoundedCornerShape(12.dp)) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = if (important) Teal else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun JobCard(job: Job, result: Result?, isSaved: Boolean, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        shape = RoundedCornerShape(17.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(job.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (job.employer != "Employer not named") {
                        Text(job.employer, fontSize = 13.sp, color = Muted)
                    }
                }
                if (isSaved) Icon(Icons.Default.Bookmark, "Saved job", tint = Teal)
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Tag(job.region)
                Tag(job.category)
                if (job.sourcePage > 0) Tag("Express • p.${job.sourcePage}")
                else Tag("LOCAL DRAFT")
            }
            if (job.pay.isNotBlank()) Text(job.pay, color = Navy, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (job.contact.isNotBlank()) Text(
                job.contact, color = Muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (result != null) {
                HorizontalDivider()
                Text(
                    if (!result.eligible) "Stated requirement not met"
                    else "${result.score}% • ${result.label} (estimated)",
                    color = if (result.eligible) Teal else Color(0xFFAC5138),
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                )
            }
            if (job.needsReview) Text("Some printed details need checking", color = Color(0xFF97513A), fontSize = 12.sp)
            Text("View details and contact →", fontSize = 12.sp, color = Teal, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun JobDetail(job: Job, result: Result?, saved: Boolean, onSave: (() -> Unit)?, dismiss: () -> Unit) {
    val context = LocalContext.current
    val firstNumber = remember(job.contact) {
        Regex("(?:\\+?1[ -]?)?(?:868[- ]?)?(\\d{3})[- ]?(\\d{4})")
            .find(job.contact)?.let { it.groupValues[1] + it.groupValues[2] }
    }
    val email = remember(job.contact) {
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
            .find(job.contact)?.value
    }
    val formUrl = remember(job.contact) { Regex("forms\\.gle/[A-Za-z0-9]+").find(job.contact)?.value }
    fun openIntent(action: String, uri: String) {
        try { context.startActivity(Intent(action, Uri.parse(uri))) }
        catch (_: Exception) { android.widget.Toast.makeText(context, "No compatible app found", android.widget.Toast.LENGTH_SHORT).show() }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(job.title, fontSize = 19.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 510.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${job.employer} • ${job.region} • ${job.type}", color = Muted)
                Notice(
                    if (job.sourcePage > 0)
                        "Printed 17 October 2026 • Trinidad Express classifieds, newspaper page ${job.sourcePage}. Real advertisement, but vacancy availability and advertiser identity are NOT independently verified."
                    else "Local draft stored on your phone only. Other users cannot see or apply to this job."
                )
                if (job.needsReview) Notice("CAUTION: Some details in this scanned advertisement are unclear. Check the original newspaper page before relying on the contact or requirements.")
                if (job.pay.isNotBlank()) Text("Advertised pay: ${job.pay}", fontWeight = FontWeight.Bold)
                if (job.description.isNotBlank()) Text(job.description)
                if (job.skills.isNotEmpty()) Text("Mentioned skills: ${job.skills.joinToString(", ")}")
                if (job.hardSkills.isNotEmpty()) Text("Mandatory skills: ${job.hardSkills.joinToString(", ")}")
                if (job.qualification.isNotBlank()) Text("Stated qualification: ${job.qualification}")
                if (job.licence.isNotBlank()) Text("Stated permit / licence: ${job.licence}")
                if (job.years > 0) Text("Experience noted: ${job.years} year(s)")
                if (job.contact.isNotBlank()) {
                    HorizontalDivider()
                    Text("How to enquire or apply", fontWeight = FontWeight.Bold)
                    Text(job.contact, color = Navy)
                    if (firstNumber != null) OutlinedButton(onClick = { openIntent(Intent.ACTION_DIAL, "tel:$firstNumber") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Call advertiser")
                    }
                    if (firstNumber != null && job.contact.contains("WhatsApp", ignoreCase = true) &&
                        !job.contact.contains("no WhatsApp", ignoreCase = true)) {
                        OutlinedButton(onClick = { openIntent(Intent.ACTION_VIEW, "https://wa.me/1868$firstNumber") }, modifier = Modifier.fillMaxWidth()) {
                            Text("Open WhatsApp")
                        }
                    }
                    if (email != null) OutlinedButton(onClick = { openIntent(Intent.ACTION_SENDTO, "mailto:$email") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Email advertiser")
                    }
                    if (formUrl != null) OutlinedButton(onClick = { openIntent(Intent.ACTION_VIEW, "https://$formUrl") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open application form")
                    }
                }
                if (result != null) {
                    HorizontalDivider()
                    Text(if (result.eligible) "${result.score}% — estimated ${result.label}" else "A stated mandatory requirement is missing",
                        fontWeight = FontWeight.Bold, color = if (result.eligible) Teal else Color(0xFFAC5138))
                    result.reasons.forEach { Text("✓ $it", fontSize = 13.sp) }
                    result.gaps.forEach { Text("• $it", color = Color(0xFF97513A), fontSize = 13.sp) }
                    Text("A score measures information in the advert only; it does not guarantee eligibility, hiring, accuracy or an open vacancy.", fontSize = 12.sp, color = Muted)
                }
            }
        },
        confirmButton = { if (onSave != null) TextButton(onClick = onSave) { Text(if (saved) "Unsave" else "Save job") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Close") } }
    )
}

@Composable
private fun FindJobs(data: AppData, jobs: List<Job>, profileReady: Boolean, onEditProfile: () -> Unit, onToggleSave: (Job) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All categories") }
    var region by rememberSaveable { mutableStateOf("All regions") }
    var onlyEligible by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Job?>(null) }
    val categories = remember(jobs) { listOf("All categories") + jobs.map { it.category }.distinct().sorted() }
    val regions = remember(jobs) {
        listOf("All regions") + listOf("North", "Central", "South", "East", "Tobago")
    }
    fun matchesRegion(job: Job): Boolean {
        val r = job.region.lowercase()
        return when (region) {
            "North" -> listOf("port of spain", "st james", "laventille", "san juan", "caroni").any { it in r }
            "Central" -> listOf("chaguanas", "couva", "freeport", "cunupia", "caroni", "curepe").any { it in r }
            "South" -> listOf("san fernando", "south", "palmiste", "marabella", "princes town", "rio claro", "barrackpore", "ste madeleine", "gooding").any { it in r }
            "East" -> listOf("arima", "tunapuna", "piarco", "trincity", "st augustine", "east", "tacarigua").any { it in r }
            "Tobago" -> "tobago" in r || "lowlands" in r
            else -> true
        }
    }
    val results = remember(jobs, data.profile, profileReady, search, category, region, onlyEligible) {
        jobs.map { it to if (profileReady) match(data.profile, it) else null }
            .filter { (job, matchResult) ->
                (search.isBlank() || listOf(job.title, job.employer, job.region, job.category, job.description, job.contact)
                    .any { it.contains(search, ignoreCase = true) }) &&
                (category == "All categories" || job.category == category) &&
                matchesRegion(job) &&
                (!onlyEligible || matchResult?.eligible == true)
            }
            .sortedWith(compareByDescending<Pair<Job, Result?>> { it.second?.score ?: 0 }
                .thenBy { it.first.title })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("Find a real job", "17 October 2026 • Trinidad Express classifieds")
            Spacer(Modifier.height(8.dp))
            Notice("${jobs.count { it.sourcePage > 0 }} positions extracted from this issue. Printed advertisements are not verified active vacancies. Check with the advertiser before applying.")
        }
        if (!profileReady) item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFE0F5F2))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Personalise your results", fontWeight = FontWeight.Bold)
                    Text("Add your region, skills or preferred job categories to compare yourself with the stated requirements.")
                    Button(onClick = onEditProfile) { Text("Set up my profile") }
                }
            }
        }
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Search job title, location or employer") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, singleLine = true
            )
        }
        item {
            Text("Job category", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { name -> FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) }) }
            }
        }
        item {
            Text("Area", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                regions.forEach { name -> FilterChip(selected = region == name, onClick = { region = name }, label = { Text(name) }) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Meets stated mandatory requirements only", Modifier.weight(1f), fontSize = 13.sp)
                Switch(checked = onlyEligible, onCheckedChange = { onlyEligible = it }, enabled = profileReady)
            }
        }
        item { Text("${results.size} result(s)", color = Muted, fontSize = 13.sp) }
        if (results.isEmpty()) item {
            Notice("Nothing matches these filters. Try All categories, All regions, or a shorter search.")
            OutlinedButton(onClick = { search = ""; category = "All categories"; region = "All regions"; onlyEligible = false }) { Text("Clear filters") }
        }
        items(results, key = { it.first.id }) { (job, result) ->
            JobCard(job, result, job.id in data.saved) { selected = job }
        }
        item {
            Text("Source: 17 Oct 2026 Trinidad Express, printed pp. 22–24. No background updates or employer verification. Applicant profiles remain private on this phone.", color = Muted, fontSize = 12.sp)
        }
    }
    selected?.let { job ->
        JobDetail(job, if (profileReady) match(data.profile, job) else null, job.id in data.saved,
            onSave = { onToggleSave(job); selected = null }, dismiss = { selected = null })
    }
}

@Composable
private fun SavedJobs(data: AppData, jobs: List<Job>, onToggleSave: (Job) -> Unit) {
    var selected by remember { mutableStateOf<Job?>(null) }
    val savedJobs = jobs.filter { it.id in data.saved }
    Scrolling {
        SectionTitle("Saved jobs", "Keep promising roles in one place.")
        if (savedJobs.isEmpty()) Notice("No saved jobs yet. Open Find jobs, choose a listing, then tap Save job.")
        savedJobs.forEach { job ->
            JobCard(job, null, true) { selected = job }
        }
    }
    selected?.let { job ->
        JobDetail(job, null, true, onSave = { onToggleSave(job); selected = null }, dismiss = { selected = null })
    }
}

@Composable private fun Field(label: String, value: String, change: (String) -> Unit,
                                single: Boolean = true, numeric: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = change, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = single, minLines = if (single) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text)
    )
}

@Composable
private fun ProfileEditor(profile: Candidate, onSave: (Candidate) -> Unit) {
    var name by remember(profile) { mutableStateOf(profile.name) }
    var region by remember(profile) { mutableStateOf(profile.region) }
    var skills by remember(profile) { mutableStateOf(profile.skills.asCsv()) }
    var quals by remember(profile) { mutableStateOf(profile.qualifications.asCsv()) }
    var licences by remember(profile) { mutableStateOf(profile.licences.asCsv()) }
    var years by remember(profile) { mutableStateOf(profile.years.toString()) }
    var categories by remember(profile) { mutableStateOf(profile.categories.asCsv()) }
    var visible by remember(profile) { mutableStateOf(profile.visible) }
    var feedback by remember { mutableStateOf("") }
    Scrolling {
        SectionTitle("My candidate profile", "Match jobs to your actual skills and qualifications.")
        Notice("Your profile is stored on this device only. Visibility allows local demonstration matching; it does not publish your profile online.")
        Field("Your name", name, { name = it })
        Field("Preferred region (e.g. Chaguanas)", region, { region = it })
        Field("Skills — separate with commas", skills, { skills = it })
        Field("Qualifications — separate with commas", quals, { quals = it })
        Field("Licences or permits — separate with commas", licences, { licences = it })
        Field("Years of experience", years, { years = it }, numeric = true)
        Field("Preferred job categories — separate with commas", categories, { categories = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Show profile in local candidate preview", fontWeight = FontWeight.SemiBold)
                Text("Only on this phone — off by default", color = Muted, fontSize = 12.sp)
            }
            Switch(checked = visible, onCheckedChange = { visible = it })
        }
        if (feedback.isNotBlank()) Notice(feedback)
        Button(
            onClick = {
                val count = years.toDoubleOrNull()
                feedback = when {
                    name.isBlank() -> "Enter your name before saving."
                    count == null || count < 0 || count > 80 -> "Enter a valid number of experience years (0–80)."
                    else -> {
                        onSave(profile.copy(name = name.trim(), region = region.trim(), skills = commaList(skills),
                            qualifications = commaList(quals), licences = commaList(licences),
                            years = count, categories = commaList(categories), visible = visible))
                        "Profile saved on this device."
                    }
                }
            }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)
        ) { Text("Save profile") }
    }
}

@Composable
private fun EmployerPosts(localJobs: List<Job>, goToPost: () -> Unit, delete: (Job) -> Unit) {
    var selected by remember { mutableStateOf<Job?>(null) }
    var pendingDelete by remember { mutableStateOf<Job?>(null) }
    Scrolling {
        SectionTitle("My job posts", "Create and manage vacancies saved on this phone.")
        Notice("Local draft mode: your posts are NOT advertised or visible to anyone on another device.")
        Button(onClick = goToPost, modifier = Modifier.fillMaxWidth()) { Text("+ Create a job") }
        if (localJobs.isEmpty()) Notice("No posts yet. Create a job to preview which sample candidate profiles would match it.")
        localJobs.reversed().forEach { job ->
            JobCard(job, null, false, onClick = { selected = job })
            OutlinedButton(onClick = { pendingDelete = job }, modifier = Modifier.fillMaxWidth()) { Text("Delete this post") }
        }
    }
    selected?.let { JobDetail(it, null, false, onSave = null, dismiss = { selected = null }) }
    pendingDelete?.let { job ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null }, title = { Text("Delete job post?") },
            text = { Text("This local post will be permanently removed from this phone.") },
            confirmButton = { TextButton(onClick = { delete(job); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CandidateDirectory(jobs: List<Job>, own: Candidate, ready: Boolean) {
    var selectedId by rememberSaveable { mutableStateOf("") }
    val chosen = jobs.firstOrNull { it.id == selectedId } ?: jobs.firstOrNull()
    Scrolling {
        SectionTitle("Candidate match preview", "Employers: this is not a public applicant database.")
        Notice("No real applicant accounts are connected. This app will NOT display fictional applicants as if they are looking for work. A shared employer–employee service requires a secure server, accounts and explicit candidate consent.")
        if (!ready || !own.visible) {
            Text("No candidate profiles are shared with this employer preview.", fontWeight = FontWeight.SemiBold)
            Text("A local candidate can optionally appear here by creating a profile and enabling local preview visibility. Nothing is uploaded.", color = Muted)
        } else {
            Text("Choose a listed job to compare with the one locally opted-in profile.", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                jobs.take(25).forEach { job ->
                    FilterChip(selected = chosen?.id == job.id, onClick = { selectedId = job.id }, label = { Text(job.title) })
                }
            }
            chosen?.let { job ->
                val result = match(own, job)
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(own.name, fontWeight = FontWeight.Bold)
                        Text("Opted-in profile on this device only", color = Muted)
                        Text(if (result.eligible) "${result.score}% — ${result.label}" else "Stated mandatory requirements not met", color = Teal)
                        result.reasons.forEach { Text("✓ $it", fontSize = 13.sp) }
                        result.gaps.forEach { Text("• $it", fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostJob(onCreate: (Job) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var employer by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Full-time") }
    var category by rememberSaveable { mutableStateOf("") }
    var skills by rememberSaveable { mutableStateOf("") }
    var hardSkills by rememberSaveable { mutableStateOf("") }
    var qualification by rememberSaveable { mutableStateOf("") }
    var licence by rememberSaveable { mutableStateOf("") }
    var years by rememberSaveable { mutableStateOf("0") }
    var pay by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Scrolling {
        SectionTitle("Create a job", "Add the criteria that matter for matching.")
        Notice("This is a private draft on your device. It will not be published online or sent to applicants.")
        Field("Job title *", title, { title = it })
        Field("Business / employer *", employer, { employer = it })
        Field("Location / region *", region, { region = it })
        Text("Employment type", fontWeight = FontWeight.Medium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Full-time", "Part-time", "Contract", "Temporary").forEach { kind ->
                FilterChip(selected = type == kind, onClick = { type = kind }, label = { Text(kind) })
            }
        }
        Field("Job category (e.g. Retail)", category, { category = it })
        Field("Desired skills — comma separated", skills, { skills = it })
        Field("Mandatory skills — comma separated", hardSkills, { hardSkills = it })
        Field("Mandatory qualification (optional)", qualification, { qualification = it })
        Field("Mandatory licence or permit (optional)", licence, { licence = it })
        Field("Preferred years of experience", years, { years = it }, numeric = true)
        Field("Pay / salary (optional)", pay, { pay = it })
        Field("Job description", description, { description = it }, single = false)
        if (error.isNotBlank()) Notice(error)
        Button(onClick = {
            val num = years.toDoubleOrNull()
            error = when {
                title.isBlank() || employer.isBlank() || region.isBlank() -> "Title, employer and region are required."
                num == null || num < 0 || num > 80 -> "Enter valid preferred experience years (0–80)."
                else -> {
                    onCreate(Job(UUID.randomUUID().toString(), title.trim(), employer.trim(), region.trim(),
                        type, category.trim(), commaList(skills), commaList(hardSkills),
                        qualification.trim(), licence.trim(), num, pay.trim(), description.trim(), demo = false))
                    ""
                }
            }
        }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)) {
            Text("Save local job post")
        }
    }
}

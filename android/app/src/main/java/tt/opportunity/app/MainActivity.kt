package tt.opportunity.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    val demo: Boolean
)

data class AppData(
    val profile: Candidate = Candidate(),
    val localJobs: List<Job> = emptyList(),
    val saved: Set<String> = emptySet()
)

data class Result(val eligible: Boolean, val score: Int, val label: String, val reasons: List<String>, val gaps: List<String>)

private fun normalize(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), " ")
private fun commaList(s: String): List<String> = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { normalize(it) }
private fun List<String>.asCsv() = joinToString(", ")

private fun match(candidate: Candidate, job: Job): Result {
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

private fun sampleJobs(): List<Job> = listOf(
    Job("demo-1", "Retail sales associate", "Sample retail business", "Chaguanas", "Full-time", "Retail",
        listOf("Customer service", "Sales", "Cash handling"), emptyList(), "", "", 1.0,
        "Pay not provided", "Assist shoppers, manage stock, and handle transactions. Demonstration listing; not an actual vacancy.", true),
    Job("demo-2", "Administrative assistant", "Sample office", "Port of Spain", "Full-time", "Administration",
        listOf("Microsoft Excel", "Data entry", "Customer service"), emptyList(), "", "", 1.0,
        "Pay not provided", "Support records, scheduling and front-desk administration. Demonstration listing.", true),
    Job("demo-3", "Delivery driver", "Sample delivery company", "San Fernando", "Full-time", "Logistics",
        listOf("Driving", "Customer service", "Route planning"), emptyList(), "", "Class 3 driving permit", 2.0,
        "Pay not provided", "Handle daily delivery routes. A valid Class 3 driving permit is a sample mandatory condition.", true),
    Job("demo-4", "Customer support representative", "Sample service company", "Arima", "Part-time", "Customer service",
        listOf("Customer service", "Communication", "Computer literacy"), emptyList(), "", "", 0.0,
        "Pay not provided", "Help clients by phone and email. Demonstration listing.", true),
    Job("demo-5", "Pharmacy assistant", "Sample pharmacy", "San Fernando", "Full-time", "Healthcare",
        listOf("Stock management", "Customer service"), emptyList(), "Pharmacy assistant certificate", "", 1.0,
        "Pay not provided", "Support stock and customer operations. Qualification is a sample mandatory condition.", true),
    Job("demo-6", "Junior electrician", "Sample trades business", "Tobago", "Contract", "Skilled trades",
        listOf("Electrical installation", "Safety procedures"), listOf("Electrical installation"), "", "", 1.0,
        "Pay not provided", "Assist a qualified electrician with site tasks. Demonstration listing.", true)
)

private fun sampleCandidates(): List<Candidate> = listOf(
    Candidate("c1", "Demo candidate A", "Chaguanas", listOf("Customer service", "Sales", "Cash handling"), years = 2.0, categories = listOf("Retail"), demo = true),
    Candidate("c2", "Demo candidate B", "Port of Spain", listOf("Microsoft Excel", "Data entry", "Customer service"), years = 4.0, categories = listOf("Administration"), demo = true),
    Candidate("c3", "Demo candidate C", "San Fernando", listOf("Driving", "Route planning", "Customer service"), licences = listOf("Class 3 driving permit"), years = 3.0, categories = listOf("Logistics"), demo = true),
    Candidate("c4", "Demo candidate D", "Arima", listOf("Communication", "Computer literacy", "Customer service"), years = 1.0, categories = listOf("Customer service"), demo = true)
)

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
    years = o.optDouble("years", 0.0), pay = o.optString("pay"), description = o.optString("description"), demo = false
)

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("opportunitytt_v1", Context.MODE_PRIVATE)
    fun load(): AppData {
        return try {
        val raw = prefs.getString("data", null) ?: return AppData()
        val o = JSONObject(raw)
        val jobs = o.optJSONArray("localJobs") ?: JSONArray()
        val saved = o.optJSONArray("saved") ?: JSONArray()
        AppData(
            profile = o.optJSONObject("profile")?.let(::parseCandidate) ?: Candidate(),
            localJobs = (0 until jobs.length()).mapNotNull { jobs.optJSONObject(it)?.let(::parseJob) },
            saved = (0 until saved.length()).map { saved.optString(it) }.toSet()
        )
        } catch (_: Exception) { AppData() }
    }
    fun save(data: AppData) {
        val o = JSONObject().apply {
            put("profile", data.profile.toJson())
            put("localJobs", JSONArray().apply { data.localJobs.forEach { put(it.toJson()) } })
            put("saved", JSONArray(data.saved.toList()))
        }
        prefs.edit().putString("data", o.toString()).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OpportunityTTApp() }
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
    val jobs = remember(data.localJobs) { sampleJobs() + data.localJobs }
    val profileReady = data.profile.name.isNotBlank() && data.profile.skills.isNotEmpty()
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
                    val names = if (hiring) listOf("My posts", "Candidates", "Post job")
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
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        shape = RoundedCornerShape(17.dp), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(job.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(job.employer, fontSize = 13.sp, color = Muted)
                }
                if (isSaved) Icon(Icons.Default.Bookmark, "Saved job", tint = Teal)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Tag(job.region); Tag(job.type); Tag(if (job.demo) "SAMPLE JOB" else "ON THIS DEVICE")
            }
            if (result != null) {
                HorizontalDivider()
                Text(if (!result.eligible) "Not eligible • mandatory requirements missing"
                     else "${result.score}% • ${result.label}", color = if (result.eligible) Teal else Color(0xFFAC5138),
                     fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Text("Tap to view details and requirements", fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun JobDetail(job: Job, result: Result?, saved: Boolean, onSave: (() -> Unit)?, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(job.title, fontSize = 19.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${job.employer} • ${job.region} • ${job.type}", color = Muted)
                Notice(if (job.demo) "Demonstration vacancy only — do not apply. This is not a verified, active job."
                       else "This listing was entered locally on this device; it is not published to other users.")
                if (job.pay.isNotBlank()) Text("Pay: ${job.pay}", fontWeight = FontWeight.SemiBold)
                if (job.description.isNotBlank()) Text(job.description)
                if (job.skills.isNotEmpty()) Text("Desired skills: ${job.skills.joinToString(", ")}")
                if (job.hardSkills.isNotEmpty()) Text("Required skills: ${job.hardSkills.joinToString(", ")}")
                if (job.qualification.isNotBlank()) Text("Required qualification: ${job.qualification}")
                if (job.licence.isNotBlank()) Text("Required licence: ${job.licence}")
                if (job.years > 0) Text("Preferred experience: ${job.years} years")
                if (result != null) {
                    HorizontalDivider()
                    Text(if (result.eligible) "${result.score}% — ${result.label}" else "Not eligible", fontWeight = FontWeight.Bold, color = Teal)
                    result.reasons.forEach { Text("✓ $it", fontSize = 13.sp) }
                    result.gaps.forEach { Text("• $it", color = Color(0xFF97513A), fontSize = 13.sp) }
                    Text("Scores are guidance, not hiring decisions.", fontSize = 12.sp, color = Muted)
                }
            }
        },
        confirmButton = {
            if (onSave != null) TextButton(onClick = onSave) {
                Text(if (saved) "Remove saved" else "Save job")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Close") } }
    )
}

@Composable
private fun FindJobs(data: AppData, jobs: List<Job>, profileReady: Boolean, onEditProfile: () -> Unit, onToggleSave: (Job) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var onlyEligible by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Job?>(null) }
    val results = jobs.map { it to if (profileReady) match(data.profile, it) else null }
        .filter { (job, res) ->
            (search.isBlank() || listOf(job.title, job.region, job.category, job.employer, job.skills.asCsv())
                .any { it.contains(search, ignoreCase = true) }) && (!onlyEligible || res?.eligible == true)
        }.sortedByDescending { (_, res) -> res?.score ?: 0 }
    Scrolling {
        SectionTitle("Jobs for you", "Explore roles around Trinidad & Tobago.")
        Notice("OFFLINE DEMO: All starter jobs are examples, not live vacancies. Jobs added in Hire staff stay on this phone.")
        if (!profileReady) {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFE0F5F2))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Get personalised matches", fontWeight = FontWeight.Bold)
                    Text("Add your name, skills and preferences to see match scores and mandatory requirement checks.")
                    Button(onClick = onEditProfile) { Text("Create my profile") }
                }
            }
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Search jobs, skills or locations") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, singleLine = true
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Eligible only", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Switch(checked = onlyEligible, onCheckedChange = { onlyEligible = it }, enabled = profileReady)
        }
        Text("${results.size} listing${if (results.size == 1) "" else "s"}", color = Muted, fontSize = 13.sp)
        if (results.isEmpty()) Notice("No results. Try a different keyword or turn off the eligibility filter.")
        results.forEach { (job, res) ->
            JobCard(job, res, job.id in data.saved, onClick = { selected = job })
        }
        Text("No job information is sent to a server. Your searches and profile remain on this device.", color = Muted, fontSize = 12.sp)
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
    var selectedId by rememberSaveable { mutableStateOf("demo-1") }
    val selected = jobs.firstOrNull { it.id == selectedId } ?: jobs.first()
    val candidates = sampleCandidates() + listOfNotNull(own.takeIf { ready && it.visible })
    val ranked = candidates.map { it to match(it, selected) }.sortedWith(
        compareByDescending<Pair<Candidate, Result>> { it.second.eligible }.thenByDescending { it.second.score }
    )
    Scrolling {
        SectionTitle("Candidate preview", "Compare skills against a selected job.")
        Notice("DEMONSTRATION ONLY: Sample candidates are fictional. No live applicant directory, messages or employer access exists yet.")
        Text("Select a job to match", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            jobs.forEach { job ->
                FilterChip(selected = selected.id == job.id, onClick = { selectedId = job.id },
                    label = { Text(job.title, maxLines = 1) })
            }
        }
        Text("Matches for ${selected.title}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        ranked.forEach { (candidate, result) ->
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(candidate.name, fontWeight = FontWeight.Bold)
                            Text("${candidate.region} • ${candidate.years} years experience", color = Muted, fontSize = 12.sp)
                        }
                        Tag(if (candidate.demo) "DEMO" else "LOCAL PROFILE")
                    }
                    Text(if (result.eligible) "${result.score}% • ${result.label}" else "Not eligible • hard requirements missing",
                        color = if (result.eligible) Teal else Color(0xFFAC5138), fontWeight = FontWeight.Bold)
                    Text("Skills: ${candidate.skills.asCsv().ifEmpty { "Not entered" }}", fontSize = 13.sp)
                    result.reasons.forEach { Text("✓ $it", fontSize = 12.sp) }
                    result.gaps.forEach { Text("• $it", fontSize = 12.sp, color = Color(0xFF97513A)) }
                }
            }
        }
        Text("Scores support human decisions; they do not establish eligibility to work or suitability for hiring.", color = Muted, fontSize = 12.sp)
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

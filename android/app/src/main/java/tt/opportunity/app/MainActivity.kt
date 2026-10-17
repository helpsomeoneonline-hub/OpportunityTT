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


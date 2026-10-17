package tt.opportunity.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.UUID

private val Blue = Color(0xFF0969AD)
private val Dark = Color(0xFF142B3C)
private val Silver = Color(0xFFF4F6F8)
private val SoftBlue = Color(0xFFE7F2FB)
private val Subtle = Color(0xFF526778)
private val Warn = Color(0xFFFFF0D9)

private fun textToast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
private fun phoneFrom(ad: String): String? {
    val regex = Regex("(?:\\b868[- ]?)?(\\d{3})[- ]?(\\d{4})\\b")
    return regex.find(ad)?.let { it.groupValues[1] + it.groupValues[2] }
}
private fun emailFrom(ad: String): String? =
    Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(ad)?.value
private fun linkFrom(ad: String): String? =
    Regex("(?:https?://)?forms\\.gle/[A-Za-z0-9]+").find(ad)?.value?.let {
        if (it.startsWith("http")) it else "https://" + it
    }
private fun contactIntent(context: Context, action: String, uri: Uri): Boolean {
    return try {
        context.startActivity(Intent(action, uri))
        true
    } catch (_: Exception) {
        textToast(context, "No app is available for this contact method.")
        false
    }
}
private fun contactJob(context: Context, job: Job) {
    val contact = job.contact
    val phone = phoneFrom(contact)
    val email = emailFrom(contact)
    val form = linkFrom(contact)
    when {
        job.needsReview && form != null -> textToast(context, "This link needs checking against the printed newspaper page before use.")
        contact.contains("WhatsApp", true) && !contact.contains("no WhatsApp", true) && phone != null ->
            contactIntent(context, Intent.ACTION_VIEW, Uri.parse("https://wa.me/1868" + phone))
        email != null -> contactIntent(context, Intent.ACTION_SENDTO, Uri.parse("mailto:" + email))
        form != null -> contactIntent(context, Intent.ACTION_VIEW, Uri.parse(form))
        phone != null -> contactIntent(context, Intent.ACTION_DIAL, Uri.parse("tel:" + phone))
        else -> copyText(context, "Advertiser contact", contact)
    }
}
private fun copyText(context: Context, name: String, value: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(name, value))
    textToast(context, "Copied to clipboard")
}
private fun shareJob(context: Context, job: Job) {
    val message = job.title + "\n" + job.region + "\n" + job.contact +
        "\nTrinidad Express classifieds • 17 October 2026 • newspaper page " + job.sourcePage +
        "\nPrinted advertisement; check that the position is still available."
    try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }, "Share job"))
    } catch (_: Exception) { copyText(context, "Job", message) }
}
private fun regionGroup(where: String): String {
    val r = where.lowercase()
    return when {
        "tobago" in r || "lowlands" in r -> "Tobago"
        listOf("san fernando", "palmiste", "marabella", "princes town", "south", "barrackpore", "rio claro", "ste madeleine", "california", "gooding").any { it in r } -> "South"
        listOf("chaguanas", "couva", "cunupia", "freeport", "caroni", "warrenville", "mc bean").any { it in r } -> "Central"
        listOf("arima", "tunapuna", "piarco", "trincity", "curepe", "st augustine", "tacarigua", "east").any { it in r } -> "East"
        listOf("port of spain", "san juan", "laventille", "st james", "north", "barataria").any { it in r } -> "North"
        else -> "Other / unclear"
    }
}

@Composable
internal fun OpportunityExperience() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("opttt_ui_v3", Context.MODE_PRIVATE) }
    val store = remember { LocalStore(context.applicationContext) }
    var data by remember { mutableStateOf(store.load()) }
    val listings = remember { loadPublishedJobs(context.applicationContext) }
    val allJobs = remember(data.localJobs) { listings + data.localJobs }
    var employer by rememberSaveable { mutableStateOf(prefs.getBoolean("employer", false)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("All areas") }
    var category by rememberSaveable { mutableStateOf("All categories") }
    var onlyFitting by rememberSaveable { mutableStateOf(false) }
    var showPostForm by rememberSaveable { mutableStateOf(false) }
    val profileReady = data.profile.name.isNotBlank() &&
        (data.profile.skills.isNotEmpty() || data.profile.categories.isNotEmpty() || data.profile.region.isNotBlank())
    fun persist(value: AppData) {
        data = value
        store.save(value)
    }
    fun toggleSaved(job: Job) {
        persist(data.copy(saved = if (job.id in data.saved) data.saved - job.id else data.saved + job.id))
    }
    fun selectRole(value: Boolean) {
        employer = value
        tab = 0
        selectedId = ""
        showPostForm = false
        prefs.edit().putBoolean("employer", value).apply()
    }
    val selectedJob = allJobs.firstOrNull { it.id == selectedId }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue, onPrimary = Color.White,
            primaryContainer = SoftBlue,
            onPrimaryContainer = Dark,
            secondary = Dark,
            background = Silver,
            surface = Color.White,
            onSurface = Dark,
            onSurfaceVariant = Subtle
        ),
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp)
        )
    ) {
        Scaffold(
            containerColor = Silver,
            topBar = {
                if (selectedJob != null || showPostForm) {
                    Row(
                        Modifier.fillMaxWidth().background(Color.White).statusBarsPadding()
                            .heightIn(min = 58.dp).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            selectedId = ""
                            showPostForm = false
                        }) { Icon(Icons.Default.ArrowBack, "Back") }
                        Text(
                            if (showPostForm) "Create a local job draft" else "Job details",
                            fontWeight = FontWeight.Bold, fontSize = 19.sp
                        )
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().background(Color.White)
                            .statusBarsPadding().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("OpportunityTT", color = Blue, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
                                Text("Real classifieds. Local opportunities.", color = Subtle, fontSize = 12.sp)
                            }
                            Surface(color = SoftBlue, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.Work, contentDescription = null,
                                    tint = Blue, modifier = Modifier.padding(11.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            FilterChip(
                                selected = !employer,
                                onClick = { selectRole(false) },
                                label = { Text("Find work") },
                                leadingIcon = { if (!employer) Icon(Icons.Default.Check, null, Modifier.size(15.dp)) }
                            )
                            FilterChip(
                                selected = employer,
                                onClick = { selectRole(true) },
                                label = { Text("Hire staff") },
                                leadingIcon = { if (employer) Icon(Icons.Default.Check, null, Modifier.size(15.dp)) }
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (selectedJob == null && !showPostForm) {
                    NavigationBar(containerColor = Color.White, tonalElevation = 3.dp) {
                        val labels = if (employer) listOf("Workspace", "New draft", "Profile")
                                     else listOf("Home", "Search", "Saved", "Profile")
                        val icons = if (employer)
                            listOf(Icons.Default.Business, Icons.Default.Add, Icons.Default.Person)
                            else listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Bookmark, Icons.Default.Person)
                        labels.forEachIndexed { i, label ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i; if (employer && i == 1) showPostForm = true },
                                icon = { Icon(icons[i], contentDescription = null, modifier = Modifier.size(23.dp)) },
                                label = { Text(label, maxLines = 1, fontSize = 11.sp) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                } else if (selectedJob != null) {
                    Row(
                        Modifier.fillMaxWidth().background(Color.White)
                            .navigationBarsPadding().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { toggleSaved(selectedJob) },
                            modifier = Modifier.heightIn(min = 51.dp),
                        ) {
                            Icon(
                                if (selectedJob.id in data.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = null, modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(if (selectedJob.id in data.saved) "Saved" else "Save")
                        }
                        Button(
                            onClick = { contactJob(context, selectedJob) },
                            modifier = Modifier.weight(1f).heightIn(min = 51.dp)
                        ) {
                            Text(if (selectedJob.contact.isBlank()) "No contact listed" else "Apply / contact")
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Default.ArrowForward, null, Modifier.size(17.dp))
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    selectedJob != null -> V3Details(
                        job = selectedJob, matched = if (profileReady) match(data.profile, selectedJob) else null,
                        stage = data.applications[selectedJob.id] ?: "",
                        onStage = { stage ->
                            persist(data.copy(applications = if (stage.isBlank()) data.applications - selectedJob.id
                                else data.applications + (selectedJob.id to stage)))
                        }
                    )
                    showPostForm -> V3PostDraft(
                        onCancel = { showPostForm = false },
                        onSave = { job ->
                            persist(data.copy(localJobs = data.localJobs + job))
                            showPostForm = false
                            tab = 0
                        }
                    )
                    employer -> when (tab) {
                        0 -> V3EmployerWorkspace(data.localJobs, onNew = { showPostForm = true },
                            onOpen = { selectedId = it.id },
                            onDelete = { job -> persist(data.copy(localJobs = data.localJobs.filterNot { it.id == job.id })) })
                        else -> V3Profile(data.profile, onSave = { persist(data.copy(profile = it)) }, store = store,
                            onRestore = { data = it }, onHire = { selectRole(false) })
                    }
                    tab == 0 -> V3Home(
                        jobs = listings, profileReady = profileReady,
                        saved = data.saved, applicationStages = data.applications,
                        profile = data.profile,
                        onSearch = { keyword = it; tab = 1 },
                        onViewAll = { tab = 1 },
                        onOpen = { selectedId = it.id },
                        onToggle = { toggleSaved(it) },
                        onProfile = { tab = 3 }
                    )
                    tab == 1 -> V3Search(
                        jobs = listings, profile = data.profile, profileReady = profileReady,
                        saved = data.saved, keyword = keyword, onKeyword = { keyword = it },
                        region = region, onRegion = { region = it },
                        category = category, onCategory = { category = it },
                        fitting = onlyFitting, onFitting = { onlyFitting = it },
                        onOpen = { selectedId = it.id }, onToggle = { toggleSaved(it) }
                    )
                    tab == 2 -> V3Saved(
                        jobs = allJobs, saved = data.saved, statuses = data.applications,
                        onOpen = { selectedId = it.id }, onToggle = { toggleSaved(it) }
                    )
                    else -> V3Profile(data.profile, onSave = { persist(data.copy(profile = it)) },
                        store = store, onRestore = { data = it }, onHire = { selectRole(true) })
                }
            }
        }
    }
}

@Composable
private fun V3Notice(text: String, modifier: Modifier = Modifier) {
    Surface(color = Warn, shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(13.dp), fontSize = 12.sp, color = Dark, lineHeight = 17.sp)
    }
}
@Composable
private fun V3Pill(text: String, blue: Boolean = false) {
    Surface(color = if (blue) SoftBlue else Silver, shape = RoundedCornerShape(9.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (blue) Blue else Subtle
        )
    }
}
@Composable
private fun V3Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 23.sp, color = Dark)
        Text(subtitle, color = Subtle, fontSize = 13.sp)
    }
}
@Composable
private fun V3JobCard(
    job: Job, saved: Boolean, result: Result? = null,
    onOpen: () -> Unit, onToggle: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(46.dp).background(SoftBlue, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Work, null, tint = Blue, modifier = Modifier.size(23.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(job.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                    Text(
                        if (job.employer == "Employer not named") "Employer not named in advert" else job.employer,
                        color = Subtle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        if (saved) "Remove saved job" else "Save job", tint = if (saved) Blue else Subtle
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Place, null, tint = Subtle, modifier = Modifier.size(16.dp))
                Text(job.region, color = Subtle, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                V3Pill(job.category)
                if (job.type != "Not stated" && job.type.isNotBlank()) V3Pill(job.type)
                if (job.pay.isNotBlank()) V3Pill(job.pay, true)
                if (job.sourcePage > 0) V3Pill("Express p." + job.sourcePage)
            }
            if (result != null) {
                Text(
                    if (result.eligible) "Profile fit: " + result.score + "% • estimated"
                    else "Some stated requirements need checking",
                    color = if (result.eligible) Blue else Color(0xFF985319),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(color = Color(0xFFEAF0F4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("17 Oct 2026 • Printed classifieds", color = Subtle, fontSize = 11.sp,
                    modifier = Modifier.weight(1f))
                Text("View job", color = Blue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, null, tint = Blue, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun V3SearchBox(value: String, onChange: (String) -> Unit, hint: String = "Job title, skill, employer or town") {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(hint, maxLines = 1, fontSize = 14.sp) },
        singleLine = true, shape = RoundedCornerShape(16.dp),
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Blue) },
        trailingIcon = { if (value.isNotBlank()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Clear search") } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
        )
    )
}
@Composable
private fun V3Home(
    jobs: List<Job>, profileReady: Boolean, saved: Set<String>,
    applicationStages: Map<String, String>, profile: Candidate,
    onSearch: (String) -> Unit, onViewAll: () -> Unit,
    onOpen: (Job) -> Unit, onToggle: (Job) -> Unit, onProfile: () -> Unit
) {
    var quickSearch by rememberSaveable { mutableStateOf("") }
    val highlights = remember(jobs, profile, profileReady) {
        if (profileReady)
            jobs.sortedByDescending { match(profile, it).score }.take(12)
        else jobs.take(12)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            V3Heading("Find your next job", "Trinidad & Tobago • October 17 classifieds")
            Spacer(Modifier.height(13.dp))
            V3SearchBox(quickSearch, { quickSearch = it })
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSearch(quickSearch) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Search all jobs") }
        }
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = SoftBlue),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(jobs.size.toString() + " positions from the newspaper",
                        fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Dark)
                    Text("Real printed advertisements from the 17 October 2026 Trinidad Express, pp. 22–24.",
                        fontSize = 13.sp, color = Subtle)
                    Text("Vacancies and advertiser details have not been independently verified.",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Dark)
                    TextButton(onClick = onViewAll) { Text("Browse every listing  →") }
                }
            }
        }
        if (!profileReady) {
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = Blue, modifier = Modifier.size(29.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("See jobs matching your skills", fontWeight = FontWeight.Bold)
                            Text("Add a private profile. No account required.", color = Subtle, fontSize = 12.sp)
                        }
                        TextButton(onClick = onProfile) { Text("Set up") }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Featured listings", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onViewAll) { Text("See all") }
            }
        }
        if (jobs.isEmpty()) {
            item { V3Notice("Unable to load newspaper listings. Please reopen the app or reinstall the latest APK.") }
        }
        items(highlights, key = { it.id }) { job ->
            V3JobCard(job, job.id in saved, if (profileReady) match(profile, job) else null,
                onOpen = { onOpen(job) }, onToggle = { onToggle(job) })
        }
        item { Text("Saved: " + saved.size + " • Applications tracked: " + applicationStages.size +
                "\nListings are stored in the app; there is no automatic live update.",
            color = Subtle, fontSize = 12.sp) }
    }
}

@Composable
private fun V3Chips(values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        values.forEach { name ->
            FilterChip(selected = selected == name, onClick = { onSelect(name) }, label = { Text(name) })
        }
    }
}
@Composable
private fun V3Search(
    jobs: List<Job>, profile: Candidate, profileReady: Boolean,
    saved: Set<String>, keyword: String, onKeyword: (String) -> Unit,
    region: String, onRegion: (String) -> Unit,
    category: String, onCategory: (String) -> Unit,
    fitting: Boolean, onFitting: (Boolean) -> Unit,
    onOpen: (Job) -> Unit, onToggle: (Job) -> Unit
) {
    val allCats = remember(jobs) { listOf("All categories") + jobs.map { it.category }.distinct().sorted() }
    val regions = remember { listOf("All areas", "North", "East", "Central", "South", "Tobago", "Other / unclear") }
    val results = remember(jobs, keyword, region, category, fitting, profileReady, profile) {
        jobs.asSequence()
            .filter { j ->
                keyword.isBlank() ||
                    listOf(j.title, j.employer, j.description, j.region, j.category, j.contact, j.skills.joinToString(" "))
                        .any { it.contains(keyword.trim(), ignoreCase = true) }
            }
            .filter { category == "All categories" || it.category == category }
            .filter { region == "All areas" || regionGroup(it.region) == region }
            .filter { !fitting || !profileReady || match(profile, it).eligible }
            .sortedWith(compareByDescending<Job> { if (profileReady) match(profile, it).score else 0 }
                .thenBy { it.title })
            .toList()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { V3Heading("Search jobs", "Find the type of work and location you prefer.") }
        item { V3SearchBox(keyword, onKeyword) }
        item {
            Text("Area", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(5.dp))
            V3Chips(regions, region, onRegion)
        }
        item {
            Text("Industry", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(5.dp))
            V3Chips(allCats, category, onCategory)
        }
        if (profileReady) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hide jobs with unmet stated mandatory requirements",
                    Modifier.weight(1f), fontSize = 12.sp)
                Switch(checked = fitting, onCheckedChange = onFitting)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(results.size.toString() + " jobs found", Modifier.weight(1f),
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
                TextButton(onClick = {
                    onKeyword(""); onRegion("All areas"); onCategory("All categories"); onFitting(false)
                }) { Text("Reset filters") }
            }
        }
        if (results.isEmpty()) item {
            V3Notice("No listings match your search. Clear a filter or use a broader keyword.")
        }
        items(results, key = { it.id }) { job ->
            V3JobCard(job, job.id in saved, if (profileReady) match(profile, job) else null,
                onOpen = { onOpen(job) }, onToggle = { onToggle(job) })
        }
        item { Text("Areas are grouped from wording in the printed adverts. Listings with uncertain locations may appear under Other / unclear.",
            color = Subtle, fontSize = 12.sp) }
    }
}

@Composable
private fun V3Saved(
    jobs: List<Job>, saved: Set<String>, statuses: Map<String, String>,
    onOpen: (Job) -> Unit, onToggle: (Job) -> Unit
) {
    var which by rememberSaveable { mutableStateOf("Saved jobs") }
    val results = remember(jobs, saved, statuses, which) {
        if (which == "Saved jobs") jobs.filter { it.id in saved }
        else jobs.filter { !statuses[it.id].isNullOrBlank() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            V3Heading("My job tracker", "Keep everything you are interested in together.")
            Spacer(Modifier.height(10.dp))
            V3Chips(listOf("Saved jobs", "Applications"), which, { which = it })
        }
        item {
            Text(results.size.toString() + " job(s)", fontWeight = FontWeight.SemiBold)
        }
        if (results.isEmpty()) {
            item { V3Notice(if (which == "Saved jobs")
                "You have not saved any jobs. Tap a bookmark on a job to keep it here."
                else "Open a job and mark its application stage to track it here. This does not send an application.") }
        }
        items(results, key = { it.id }) { j ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                V3JobCard(j, j.id in saved, onOpen = { onOpen(j) }, onToggle = { onToggle(j) })
                if (!statuses[j.id].isNullOrBlank()) {
                    Text("Tracked status: " + statuses.getValue(j.id), color = Blue, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}
@Composable
private fun V3Details(job: Job, matched: Result?, stage: String, onStage: (String) -> Unit) {
    val context = LocalContext.current
    val phone = remember(job.contact) { phoneFrom(job.contact) }
    val email = remember(job.contact) { emailFrom(job.contact) }
    val link = remember(job.contact) { linkFrom(job.contact) }
    var showStages by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
        .padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text(job.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 29.sp)
                Text(if (job.employer == "Employer not named") "Advertiser did not name a business" else job.employer,
                    color = Subtle, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, null, Modifier.size(18.dp), tint = Blue)
                    Spacer(Modifier.width(5.dp))
                    Text(job.region, fontSize = 14.sp)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    V3Pill(job.category)
                    if (job.type != "Not stated") V3Pill(job.type)
                    if (job.pay.isNotBlank()) V3Pill(job.pay, true)
                }
                HorizontalDivider()
                if (job.sourcePage > 0) {
                    Text("SOURCE: Trinidad Express Classifieds", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Blue)
                    Text("Printed Saturday, 17 October 2026 • newspaper page " + job.sourcePage,
                        color = Subtle, fontSize = 12.sp)
                } else {
                    Text("LOCAL DRAFT: Visible on this phone only", color = Subtle, fontSize = 12.sp)
                }
            }
        }
        V3Notice(
            if (job.sourcePage > 0)
                "This is a real printed advertisement, not a verified active vacancy. Confirm details and legitimacy directly with the advertiser before sharing personal information."
            else "This employer draft is private to this phone and has not been advertised online."
        )
        if (job.needsReview) V3Notice(
            "Some transcription details are uncertain. Check this entry against the printed source on page " + job.sourcePage + "."
        )
        V3Section("About the role") {
            Text(if (job.description.isBlank()) "No detailed duties were printed in the advertisement." else job.description,
                fontSize = 14.sp, lineHeight = 22.sp)
        }
        V3Section("Requirements") {
            if (job.skills.isEmpty() && job.hardSkills.isEmpty() && job.qualification.isBlank() &&
                job.licence.isBlank() && job.years == 0.0)
                Text("The advertisement did not specify detailed qualifications. Ask the advertiser.", color = Subtle, fontSize = 14.sp)
            job.hardSkills.forEach { V3Bullet("Required skill: " + it) }
            job.skills.forEach { V3Bullet("Mentioned skill: " + it) }
            if (job.qualification.isNotBlank()) V3Bullet("Qualification stated: " + job.qualification)
            if (job.licence.isNotBlank()) V3Bullet("Permit / licence: " + job.licence)
            if (job.years > 0) V3Bullet("Experience stated: " + job.years.toInt() + " years")
        }
        if (matched != null) V3Section("Your fit to this job") {
            Text(if (matched.eligible)
                matched.score.toString() + "% approximate fit from available details"
                else "Your profile may lack one or more stated mandatory requirements",
                fontWeight = FontWeight.SemiBold, color = if (matched.eligible) Blue else Color(0xFF935124))
            matched.reasons.forEach { V3Bullet(it) }
            matched.gaps.forEach { V3Bullet(it) }
            Text("This is guidance from the limited classified text—not an employer decision or guarantee.",
                color = Subtle, fontSize = 12.sp)
        }
        V3Section("Contact the advertiser") {
            if (job.contact.isBlank()) Text("No contact given.", color = Subtle)
            else Text(job.contact, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (phone != null) OutlinedButton(
                onClick = { contactIntent(context, Intent.ACTION_DIAL, Uri.parse("tel:" + phone)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Icon(Icons.Default.Phone, null); Spacer(Modifier.width(8.dp)); Text("Call " + phone) }
            if (phone != null && job.contact.contains("WhatsApp", true) && !job.contact.contains("no WhatsApp", true)) {
                OutlinedButton(onClick = { contactIntent(context, Intent.ACTION_VIEW, Uri.parse("https://wa.me/1868" + phone)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("Message on WhatsApp")
                }
            }
            if (email != null) OutlinedButton(
                onClick = { contactIntent(context, Intent.ACTION_SENDTO, Uri.parse("mailto:" + email)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Icon(Icons.Default.Email, null); Spacer(Modifier.width(8.dp)); Text("Email advertiser") }
            if (link != null && !job.needsReview) OutlinedButton(
                onClick = { contactIntent(context, Intent.ACTION_VIEW, Uri.parse(link)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Open application link") }
            OutlinedButton(onClick = { copyText(context, "Job contact", job.contact) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copy contact details")
            }
        }
        V3Section("Track your application") {
            Text(if (stage.isBlank()) "Not tracked yet" else "Current status: " + stage, color = Subtle)
            OutlinedButton(onClick = { showStages = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (stage.isBlank()) "Record an application" else "Change tracking status")
            }
            Text("Tracking a status only saves a note on your phone. It does not send or submit anything.",
                color = Subtle, fontSize = 12.sp)
        }
        OutlinedButton(onClick = { shareJob(context, job) }, modifier = Modifier.fillMaxWidth().heightIn(min = 49.dp)) {
            Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share this listing")
        }
        Spacer(Modifier.height(14.dp))
    }
    if (showStages) AlertDialog(
        onDismissRequest = { showStages = false },
        title = { Text("Application tracker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("" to "Clear tracking", "Interested" to "Interested", "Contacted" to "Contacted",
                    "Applied" to "Applied", "Interview" to "Interview scheduled",
                    "Offer" to "Received offer", "Not proceeding" to "Not proceeding").forEach { (value, title) ->
                    TextButton(onClick = { onStage(value); showStages = false },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(title, Modifier.fillMaxWidth(), color = if (stage == value) Blue else Dark)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showStages = false }) { Text("Close") } }
    )
}
@Composable private fun V3Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            content()
        }
    }
}
@Composable private fun V3Bullet(value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = Blue)
        Text(value, color = Dark, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
@Composable
private fun V3Field(
    label: String, text: String, update: (String) -> Unit, numeric: Boolean = false,
    lines: Int = 1
) {
    OutlinedTextField(
        value = text, onValueChange = update, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = lines == 1,
        minLines = lines, keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text
        ), shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
    )
}
@Composable
private fun V3Profile(
    profile: Candidate, onSave: (Candidate) -> Unit, store: LocalStore,
    onRestore: (AppData) -> Unit, onHire: () -> Unit
) {
    val context = LocalContext.current
    var name by remember(profile) { mutableStateOf(profile.name) }
    var region by remember(profile) { mutableStateOf(profile.region) }
    var skills by remember(profile) { mutableStateOf(profile.skills.asCsv()) }
    var quals by remember(profile) { mutableStateOf(profile.qualifications.asCsv()) }
    var licences by remember(profile) { mutableStateOf(profile.licences.asCsv()) }
    var experience by remember(profile) { mutableStateOf(profile.years.toString()) }
    var cats by remember(profile) { mutableStateOf(profile.categories.asCsv()) }
    var visible by remember(profile) { mutableStateOf(profile.visible) }
    var message by remember { mutableStateOf("") }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(store.readBackup().toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open backup destination")
                textToast(context, "Backup exported successfully")
            } catch (_: Exception) { textToast(context, "Could not export backup") }
        }
    }
    val loadBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Cannot read selected file")
                onRestore(store.restoreBackup(content))
                textToast(context, "Backup restored. Profile and saved jobs updated.")
            } catch (_: Exception) { textToast(context, "Invalid OpportunityTT backup; existing information kept.") }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        V3Heading("My profile", "Private preferences for better job matches.")
        V3Notice("You can browse jobs without a profile. Everything below stays on your phone; employers cannot search it online.")
        V3Field("Your name", name, { name = it })
        V3Field("Preferred town or region", region, { region = it })
        V3Field("Skills (separate with commas)", skills, { skills = it })
        V3Field("Qualifications (separate with commas)", quals, { quals = it })
        V3Field("Permits / licences (separate with commas)", licences, { licences = it })
        V3Field("Years of experience", experience, { experience = it }, numeric = true)
        V3Field("Preferred industries (separate with commas)", cats, { cats = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Opt in to local employer preview", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Only on this same phone; no online sharing.", color = Subtle, fontSize = 12.sp)
            }
            Switch(checked = visible, onCheckedChange = { visible = it })
        }
        if (message.isNotBlank()) V3Notice(message)
        Button(
            onClick = {
                val count = experience.toDoubleOrNull()
                message = when {
                    name.isBlank() -> "Please enter a name to save your profile."
                    count == null || count < 0 || count > 80 -> "Experience must be between 0 and 80 years."
                    else -> {
                        onSave(profile.copy(name = name.trim(), region = region.trim(),
                            skills = commaList(skills), qualifications = commaList(quals),
                            licences = commaList(licences), years = count,
                            categories = commaList(cats), visible = visible))
                        "Your profile has been saved on this device."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("Save profile") }
        HorizontalDivider()
        Text("Back up your information", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Test builds can require an uninstall when Android signing certificates change. Export a JSON backup first to protect your profile, saved jobs and tracking history.",
            color = Subtle, fontSize = 12.sp)
        OutlinedButton(onClick = { saveBackup.launch("OpportunityTT-backup.json") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Export backup")
        }
        OutlinedButton(onClick = { loadBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("Restore backup")
        }
        HorizontalDivider()
        Text("For employers", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Create local job drafts. Online publishing and real applicant accounts are not available yet.",
            color = Subtle, fontSize = 12.sp)
        OutlinedButton(onClick = onHire, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Default.Business, null); Spacer(Modifier.width(8.dp)); Text("Switch job-seeker / employer view")
        }
        Text("OpportunityTT 0.3.0 • Offline newspaper edition 17 October 2026", color = Subtle, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun V3EmployerWorkspace(
    drafts: List<Job>, onNew: () -> Unit, onOpen: (Job) -> Unit, onDelete: (Job) -> Unit
) {
    var pending by remember { mutableStateOf<Job?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            V3Heading("Employer workspace", "Prepare a job advertisement on this phone.")
            Spacer(Modifier.height(10.dp))
            V3Notice("LOCAL ONLY: These drafts are not published, and real candidates on other devices cannot see them. There is no live applicant database.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Create local job draft")
            }
        }
        item { Text(drafts.size.toString() + " local draft(s)", fontWeight = FontWeight.Bold) }
        if (drafts.isEmpty()) item {
            V3Notice("No employer drafts yet. Use Create local job draft to prepare an advertisement.")
        }
        items(drafts.reversed(), key = { it.id }) { job ->
            V3JobCard(job, false, onOpen = { onOpen(job) }, onToggle = { pending = job })
            TextButton(onClick = { pending = job }) { Text("Delete this draft") }
        }
    }
    pending?.let { job ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Delete local draft?") },
            text = { Text("The draft will be deleted from this phone. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { onDelete(job); pending = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}
@Composable
private fun V3PostDraft(onCancel: () -> Unit, onSave: (Job) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var employer by rememberSaveable { mutableStateOf("") }
    var town by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Not stated") }
    var skills by rememberSaveable { mutableStateOf("") }
    var hard by rememberSaveable { mutableStateOf("") }
    var qual by rememberSaveable { mutableStateOf("") }
    var licence by rememberSaveable { mutableStateOf("") }
    var years by rememberSaveable { mutableStateOf("0") }
    var pay by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        V3Notice("This creates a private draft only. It cannot be seen by job seekers on another phone.")
        V3Field("Job title *", title, { title = it })
        V3Field("Employer / business *", employer, { employer = it })
        V3Field("Town / location *", town, { town = it })
        V3Field("Category", category, { category = it })
        Text("Working arrangement", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        V3Chips(listOf("Not stated", "Full-time", "Part-time", "Contract", "Temporary"), type, { type = it })
        V3Field("Preferred skills (comma separated)", skills, { skills = it })
        V3Field("Mandatory skills (comma separated)", hard, { hard = it })
        V3Field("Mandatory qualification, if any", qual, { qual = it })
        V3Field("Required driving permit / licence, if any", licence, { licence = it })
        V3Field("Preferred experience in years", years, { years = it }, numeric = true)
        V3Field("Salary / pay, if known", pay, { pay = it })
        V3Field("Phone, WhatsApp, email or contact method", contact, { contact = it })
        V3Field("Description", description, { description = it }, lines = 4)
        if (error.isNotBlank()) V3Notice(error)
        Button(
            onClick = {
                val y = years.toDoubleOrNull()
                error = when {
                    title.isBlank() || employer.isBlank() || town.isBlank() -> "Enter a title, employer and location."
                    y == null || y < 0 || y > 80 -> "Experience years must be between 0 and 80."
                    else -> {
                        onSave(Job("draft-" + UUID.randomUUID(), title.trim(), employer.trim(),
                            town.trim(), type, category.ifBlank { "Other" },
                            commaList(skills), commaList(hard), qual.trim(), licence.trim(), y,
                            pay.trim(), description.trim(), false, contact.trim(), 0, "", false))
                        ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("Save private draft") }
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

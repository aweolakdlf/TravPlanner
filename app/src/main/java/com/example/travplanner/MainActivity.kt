package com.example.travplanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TravelPlan(
    val name: String,
    val schedule: String,
    val peopleCount: String,
    val photoUri: String? = null
)

val temporaryPlanList = mutableStateListOf<TravelPlan>()
val registeredUsers = mutableStateMapOf<String, String>()
var loggedInUser by mutableStateOf<String?>(null)

fun copyUriToInternalStorage(context: Context, uri: Uri): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val fileName = "travel_photo_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, fileName)
        val outputStream = FileOutputStream(file)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        sharedPreferences.all.forEach { (key, value) ->
            if (value is String) registeredUsers[key] = value
        }
        if (registeredUsers.isEmpty()) {
            registeredUsers["admin"] = "1234"
            sharedPreferences.edit().putString("admin", "1234").apply()
        }
        if (temporaryPlanList.isEmpty()) {
            temporaryPlanList.add(TravelPlan("제주도 여름 휴가", "2026.07.10 - 2026.07.15", "2명", null))
            temporaryPlanList.add(TravelPlan("부산 바캉스", "2026.08.01 - 2026.08.03", "4명", null))
        }

        setContent { AppMainScreen() }
    }
}

@Composable
fun AppMainScreen() {
    var currentRoute by remember { mutableStateOf("PlanList") }
    var editingPlan by remember { mutableStateOf<TravelPlan?>(null) }

    Scaffold(
        bottomBar = {
            if (currentRoute in listOf("PlanList", "Nearby", "Profile")) {
                NavigationBar {
                    NavigationBarItem(icon = { Icon(Icons.Default.List, null) }, label = { Text("여행 일정 조회") }, selected = currentRoute == "PlanList", onClick = { currentRoute = "PlanList" })
                    NavigationBarItem(icon = { Icon(Icons.Default.Place, null) }, label = { Text("주변 둘러보기") }, selected = currentRoute == "Nearby", onClick = { currentRoute = "Nearby" })
                    NavigationBarItem(icon = { Icon(Icons.Default.Person, null) }, label = { Text("내 정보") }, selected = currentRoute == "Profile", onClick = { currentRoute = "Profile" })
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentRoute) {
                "PlanList" -> TravelPlanListScreen(onNavigateToRegister = { currentRoute = "Register" }, onNavigateToEdit = { plan -> editingPlan = plan; currentRoute = "Edit" })
                "Register" -> RegisterPlanScreen(onNavigateToList = { currentRoute = "PlanList" })
                "Edit" -> EditPlanScreen(planToEdit = editingPlan, onNavigateToList = { currentRoute = "PlanList" })
                "Nearby" -> NearbyLocationScreen()
                "Profile" -> MemberStatusScreen()
            }
        }
    }
}

@Composable
fun TravelPlanListScreen(onNavigateToRegister: () -> Unit, onNavigateToEdit: (TravelPlan) -> Unit) {
    val context = LocalContext.current
    var selectedPlans by remember { mutableStateOf(setOf<TravelPlan>()) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    val sortedPlans = temporaryPlanList.sortedBy { it.schedule }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(text = "내 여행 일정", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(sortedPlans) { plan ->
                val isSelected = selectedPlans.contains(plan)
                TravelCard(
                    title = plan.name, date = plan.schedule, peopleCount = plan.peopleCount, photoUri = plan.photoUri,
                    isDeleteMode = isDeleteMode, isEditMode = isEditMode, isChecked = isSelected,
                    onCheckedChange = { checked -> selectedPlans = if (checked) selectedPlans + plan else selectedPlans - plan },
                    onEditClick = { onNavigateToEdit(plan) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!isDeleteMode && !isEditMode) {
                Button(onClick = onNavigateToRegister, modifier = Modifier.weight(1f).height(55.dp)) { Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { if (temporaryPlanList.isEmpty()) Toast.makeText(context, "수정할 일정이 없습니다.", Toast.LENGTH_SHORT).show() else isEditMode = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), modifier = Modifier.weight(1f).height(55.dp)
                ) { Icon(Icons.Default.Edit, contentDescription = "수정", modifier = Modifier.size(24.dp)) }
                Button(
                    onClick = { if (temporaryPlanList.isEmpty()) Toast.makeText(context, "삭제할 일정이 없습니다.", Toast.LENGTH_SHORT).show() else isDeleteMode = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.weight(1f).height(55.dp)
                ) { Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            } else if (isEditMode) {
                Button(onClick = { isEditMode = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), modifier = Modifier.weight(1f).height(55.dp)) { Text("취소", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Button(onClick = { }, enabled = false, modifier = Modifier.weight(2f).height(55.dp)) { Text("수정할 일정을 클릭하세요", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            } else if (isDeleteMode) {
                Button(onClick = { isDeleteMode = false; selectedPlans = emptySet() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), modifier = Modifier.weight(1f).height(55.dp)) { Text("취소", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        if (selectedPlans.isNotEmpty()) {
                            val deleteCount = selectedPlans.size
                            temporaryPlanList.removeAll(selectedPlans)
                            selectedPlans = emptySet()
                            isDeleteMode = false
                            Toast.makeText(context, "${deleteCount}개의 일정이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        } else { Toast.makeText(context, "삭제할 일정을 선택해주세요.", Toast.LENGTH_SHORT).show() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.weight(1f).height(55.dp)
                ) { Text("삭제 확인", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun TravelCard(title: String, date: String, peopleCount: String, photoUri: String?, isDeleteMode: Boolean, isEditMode: Boolean, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit, onEditClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().then(if (isEditMode) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier).clickable { if (isDeleteMode) onCheckedChange(!isChecked) else if (isEditMode) onEditClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isDeleteMode) { Checkbox(checked = isChecked, onCheckedChange = onCheckedChange) }
            Column(modifier = Modifier.weight(1f).padding(start = if (isDeleteMode) 0.dp else 8.dp)) {
                Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "$date  |  $peopleCount", fontSize = 14.sp)
            }
            if (photoUri != null) {
                Spacer(modifier = Modifier.width(16.dp))
                AsyncImage(model = Uri.parse(photoUri), contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterPlanScreen(onNavigateToList: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("") }
    var peopleCount by remember { mutableStateOf("4명") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val savedUri = copyUriToInternalStorage(context, uri)
                withContext(Dispatchers.Main) { selectedImageUri = savedUri }
            }
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    var peopleExpanded by remember { mutableStateOf(false) }
    val peopleOptions = (1..99).map { "${it}명" }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "여행 일정 등록", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        LabeledInputField(label = "일정 이름", value = name, onValueChange = { name = it })

        Column {
            Text(text = "일정", fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                value = schedule.ifEmpty { "달력 아이콘을 눌러 선택하세요" }, onValueChange = {}, readOnly = true,
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(imageVector = Icons.Default.DateRange, contentDescription = "달력 열기") } }
            )
        }

        Column {
            Text(text = "인원", fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
            ExposedDropdownMenuBox(expanded = peopleExpanded, onExpandedChange = { peopleExpanded = !peopleExpanded }) {
                OutlinedTextField(
                    value = peopleCount, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = peopleExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(expanded = peopleExpanded, onDismissRequest = { peopleExpanded = false }, modifier = Modifier.heightIn(max = 250.dp)) {
                    peopleOptions.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { peopleCount = option; peopleExpanded = false }) }
                }
            }
        }

        Text(text = "사진 추가", fontSize = 20.sp, modifier = Modifier.align(Alignment.Start).padding(top = 8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Color.Gray, RoundedCornerShape(8.dp)).clickable {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageUri != null) { AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            else { Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(48.dp).alpha(0.5f), tint = Color.Gray) }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onNavigateToList,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                modifier = Modifier.weight(1f).height(55.dp)
            ) {
                Text("취소", fontSize = 18.sp)
            }

            Button(
                onClick = {
                    if (name.isEmpty() || schedule.isEmpty()) {
                        Toast.makeText(context, "일정 이름과 날짜를 확인해주세요.", Toast.LENGTH_SHORT).show()
                    } else {
                        temporaryPlanList.add(TravelPlan(name, schedule, peopleCount, selectedImageUri?.toString()))
                        Toast.makeText(context, "일정이 등록되었습니다!", Toast.LENGTH_SHORT).show()
                        onNavigateToList()
                    }
                },
                modifier = Modifier.weight(1f).height(55.dp)
            ) {
                Text("확인", fontSize = 18.sp)
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val startMillis = dateRangePickerState.selectedStartDateMillis
                    val endMillis = dateRangePickerState.selectedEndDateMillis
                    if (startMillis != null && endMillis != null) {
                        val formatter = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
                        schedule = "${formatter.format(Date(startMillis))} - ${formatter.format(Date(endMillis))}"
                        showDatePicker = false
                    } else { Toast.makeText(context, "시작일과 종료일을 모두 선택해주세요.", Toast.LENGTH_SHORT).show() }
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("취소") } }
        ) { DateRangePicker(state = dateRangePickerState, modifier = Modifier.weight(1f)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlanScreen(planToEdit: TravelPlan?, onNavigateToList: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(planToEdit?.name ?: "") }
    var schedule by remember { mutableStateOf(planToEdit?.schedule ?: "") }
    var peopleCount by remember { mutableStateOf(planToEdit?.peopleCount ?: "4명") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(if (planToEdit?.photoUri != null) Uri.parse(planToEdit.photoUri) else null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val savedUri = copyUriToInternalStorage(context, uri)
                withContext(Dispatchers.Main) { selectedImageUri = savedUri }
            }
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val formatter = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
    var initialStartMillis: Long? = null
    var initialEndMillis: Long? = null
    try {
        if (schedule.contains(" - ")) {
            val parts = schedule.split(" - ")
            initialStartMillis = formatter.parse(parts[0])?.time
            initialEndMillis = formatter.parse(parts[1])?.time
        }
    } catch(e: Exception) {}

    val dateRangePickerState = rememberDateRangePickerState(initialSelectedStartDateMillis = initialStartMillis, initialSelectedEndDateMillis = initialEndMillis)
    var peopleExpanded by remember { mutableStateOf(false) }
    val peopleOptions = (1..99).map { "${it}명" }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "여행 일정 수정", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        LabeledInputField(label = "일정 이름", value = name, onValueChange = { name = it })

        Column {
            Text(text = "일정", fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                value = schedule.ifEmpty { "달력 아이콘을 눌러 선택하세요" }, onValueChange = {}, readOnly = true,
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(imageVector = Icons.Default.DateRange, contentDescription = "달력 열기") } }
            )
        }

        Column {
            Text(text = "인원", fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
            ExposedDropdownMenuBox(expanded = peopleExpanded, onExpandedChange = { peopleExpanded = !peopleExpanded }) {
                OutlinedTextField(
                    value = peopleCount, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = peopleExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(expanded = peopleExpanded, onDismissRequest = { peopleExpanded = false }, modifier = Modifier.heightIn(max = 250.dp)) {
                    peopleOptions.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { peopleCount = option; peopleExpanded = false }) }
                }
            }
        }

        Text(text = "사진 추가", fontSize = 20.sp, modifier = Modifier.align(Alignment.Start).padding(top = 8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Color.Gray, RoundedCornerShape(8.dp)).clickable {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageUri != null) { AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            else { Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(48.dp).alpha(0.5f), tint = Color.Gray) }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                if (name.isEmpty() || schedule.isEmpty()) { Toast.makeText(context, "일정 이름과 날짜를 확인해주세요.", Toast.LENGTH_SHORT).show() }
                else if (planToEdit != null) {
                    val index = temporaryPlanList.indexOf(planToEdit)
                    if (index != -1) {
                        temporaryPlanList[index] = TravelPlan(name, schedule, peopleCount, selectedImageUri?.toString())
                        Toast.makeText(context, "일정이 성공적으로 수정되었습니다!", Toast.LENGTH_SHORT).show()
                    }
                    onNavigateToList()
                }
            },
            modifier = Modifier.fillMaxWidth().height(55.dp)
        ) { Text("수정 완료", fontSize = 18.sp) }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val startMillis = dateRangePickerState.selectedStartDateMillis
                    val endMillis = dateRangePickerState.selectedEndDateMillis
                    if (startMillis != null && endMillis != null) {
                        schedule = "${formatter.format(Date(startMillis))} - ${formatter.format(Date(endMillis))}"
                        showDatePicker = false
                    } else { Toast.makeText(context, "시작일과 종료일을 모두 선택해주세요.", Toast.LENGTH_SHORT).show() }
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("취소") } }
        ) { DateRangePicker(state = dateRangePickerState, modifier = Modifier.weight(1f)) }
    }
}

@Composable
fun LabeledInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(text = label, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
        OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun MemberStatusScreen() {
    val context = LocalContext.current
    var authMode by remember { mutableStateOf("LOGIN") }

    var inputId by remember { mutableStateOf("") }
    var inputPw by remember { mutableStateOf("") }
    var inputPwConfirm by remember { mutableStateOf("") }

    val sharedPreferences = remember { context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }
    val planPrefs = remember { context.getSharedPreferences("TravelPlansPrefs", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        if (loggedInUser != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "${loggedInUser}님, 환영합니다!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        val jsonString = gson.toJson(temporaryPlanList.toList())
                        planPrefs.edit().putString("plans_$loggedInUser", jsonString).apply()
                        Toast.makeText(context, "${loggedInUser}님의 일정이 안전하게 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("여행 일정 백업하기", fontSize = 16.sp) }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val savedJson = planPrefs.getString("plans_$loggedInUser", null)
                        if (savedJson != null) {
                            val type = object : TypeToken<List<TravelPlan>>() {}.type
                            val savedPlans: List<TravelPlan> = gson.fromJson(savedJson, type)
                            temporaryPlanList.clear()
                            temporaryPlanList.addAll(savedPlans)
                            Toast.makeText(context, "${loggedInUser}님의 일정을 불러왔습니다.", Toast.LENGTH_SHORT).show()
                        } else { Toast.makeText(context, "저장된 일정이 없습니다.", Toast.LENGTH_SHORT).show() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("저장된 여행 일정 불러오기", fontSize = 16.sp) }

                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = {
                        loggedInUser = null
                        temporaryPlanList.clear()
                        Toast.makeText(context, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("로그아웃", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        } else {
            if (authMode == "LOGIN") {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = "로그인", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = inputId, onValueChange = { inputId = it }, label = { Text("아이디") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = inputPw, onValueChange = { inputPw = it }, label = { Text("비밀번호") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (inputId.isEmpty() || inputPw.isEmpty()) { Toast.makeText(context, "아이디와 비밀번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show() }
                            else if (registeredUsers[inputId] == inputPw) {
                                loggedInUser = inputId
                                Toast.makeText(context, "로그인 성공!", Toast.LENGTH_SHORT).show()
                                inputId = ""; inputPw = ""
                            } else { Toast.makeText(context, "아이디 또는 비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show() }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("로그인", fontSize = 16.sp) }
                    TextButton(onClick = { authMode = "REGISTER"; inputId = ""; inputPw = "" }) { Text("아직 계정이 없으신가요? 회원가입") }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = "회원가입", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = inputId, onValueChange = { inputId = it }, label = { Text("새 아이디") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = inputPw, onValueChange = { inputPw = it }, label = { Text("비밀번호") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = inputPwConfirm, onValueChange = { inputPwConfirm = it }, label = { Text("비밀번호 확인") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (inputId.isEmpty() || inputPw.isEmpty() || inputPwConfirm.isEmpty()) { Toast.makeText(context, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show() }
                            else if (registeredUsers.containsKey(inputId)) { Toast.makeText(context, "이미 사용 중인 아이디입니다.", Toast.LENGTH_SHORT).show() }
                            else if (inputPw != inputPwConfirm) { Toast.makeText(context, "비밀번호 확인이 일치하지 않습니다.", Toast.LENGTH_SHORT).show() }
                            else {
                                registeredUsers[inputId] = inputPw
                                sharedPreferences.edit().putString(inputId, inputPw).apply()
                                Toast.makeText(context, "회원가입 완료! 로그인해 주세요.", Toast.LENGTH_SHORT).show()
                                authMode = "LOGIN"; inputPwConfirm = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("회원가입 완료", fontSize = 16.sp) }
                    TextButton(onClick = { authMode = "LOGIN"; inputId = ""; inputPw = ""; inputPwConfirm = "" }) { Text("이미 계정이 있으신가요? 로그인") }
                }
            }
        }
    }
}

@Composable
fun NearbyLocationScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var currentLocationText by remember { mutableStateOf("위치 탐색 중...") }
    var currentLatLng by remember { mutableStateOf(LatLng(35.826, 128.739)) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(currentLatLng, 15f) }

    val fetchLocation = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            hasLocationPermission = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val newLatLng = LatLng(location.latitude, location.longitude)
                    currentLatLng = newLatLng
                    coroutineScope.launch { cameraPositionState.animate(update = CameraUpdateFactory.newLatLngZoom(newLatLng, 15f), durationMs = 1000) }
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val geocoder = Geocoder(context, Locale.KOREA)
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val addr = addresses[0]
                                val addressText = "${addr.adminArea ?: ""} ${addr.locality ?: ""} ${addr.thoroughfare ?: ""}".trim()
                                withContext(Dispatchers.Main) { currentLocationText = addressText.ifEmpty { "위치 확인 완료" } }
                            }
                        } catch (e: Exception) { withContext(Dispatchers.Main) { currentLocationText = "주소 변환 실패" } }
                    }
                } else {
                    Toast.makeText(context, "기기의 GPS 신호를 세팅해주세요.", Toast.LENGTH_SHORT).show()
                    currentLocationText = "위치 찾기 실패"
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) fetchLocation() else currentLocationText = "권한 거부됨"
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocation()
        else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocation()
                else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
        ) {
            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = currentLocationText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(mapType = MapType.SATELLITE, isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false)
            )
        }
    }
}
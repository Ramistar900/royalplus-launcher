package com.royalplus.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private object RoyalCalendarPalette {
    val Gold = Color(0xFFD4AF37)
    val Blue = Color(0xFF2678D8)
    val Purple = Color(0xFF873FAD)
    val Turquoise = Color(0xFF20C5C2)
    val DeepGreen = Color(0xFF075B43)
    val Black = Color(0xFF08090C)
    val Red = Color(0xFFD8323E)
    val White = Color(0xFFFFFFFF)
    val OatCrystal = Color(0xFFD8CDB8)
    val Glass = OatCrystal.copy(alpha = 0.10f)
    val GlassBorder = OatCrystal.copy(alpha = 0.34f)
}

@Composable
internal fun GregorianCalendarScreen(language: String) {
    val context = LocalContext.current
    val locale = remember(language) {
        when (language) {
            "en" -> Locale.ENGLISH
            "tr" -> Locale("tr", "TR")
            else -> Locale("ar")
        }
    }
    var visibleMonth by remember(locale) {
        mutableStateOf(Calendar.getInstance(locale).apply { set(Calendar.DAY_OF_MONTH, 1) })
    }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val reminders = remember(context) {
        mutableStateListOf<GregorianCalendarReminder>().apply {
            addAll(GregorianCalendarReminderScheduler.load(context))
        }
    }
    var editingReminder by remember { mutableStateOf<GregorianCalendarReminder?>(null) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember(context) {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    val today = Calendar.getInstance(locale)
    val monthTitle = remember(visibleMonth.timeInMillis, locale) {
        SimpleDateFormat("MMMM yyyy", locale).format(visibleMonth.time)
    }
    val selectedDate = Calendar.getInstance(locale).apply {
        timeInMillis = if (selectedDateMillis == 0L) visibleMonth.timeInMillis else selectedDateMillis
    }
    val selectedDateText = SimpleDateFormat(
        if (language == "ar") "EEEE، d MMMM yyyy" else "EEEE, d MMMM yyyy",
        locale
    ).format(selectedDate.time)
    val selectedYear = selectedDate.get(Calendar.YEAR)
    val selectedMonth = selectedDate.get(Calendar.MONTH)
    val selectedDay = selectedDate.get(Calendar.DAY_OF_MONTH)
    val selectedReminders = reminders.filter {
        it.occursOn(selectedYear, selectedMonth, selectedDay)
    }.sortedWith(compareBy<GregorianCalendarReminder> { it.hour }.thenBy { it.minute })
    val monthCalendar = (visibleMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val firstDayOfWeek = monthCalendar.firstDayOfWeek
    val firstWeekOffset = (monthCalendar.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
    val dayCount = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells: List<Int?> = List(firstWeekOffset) { null } +
        (1..dayCount).map { it } + List(42 - firstWeekOffset - dayCount) { null }
    val weekdayNames = DateFormatSymbols(locale).shortWeekdays
    val weekdays = (0..6).map { offset -> weekdayNames[((firstDayOfWeek - 1 + offset) % 7) + 1] }
    val numberFormat = NumberFormat.getIntegerInstance(locale)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                    .background(RoyalCalendarPalette.Glass)
                    .border(1.dp, RoyalCalendarPalette.Gold.copy(alpha = 0.68f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.CalendarToday,
                    contentDescription = null,
                    tint = RoyalCalendarPalette.Gold,
                    modifier = Modifier.size(25.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (language) {
                        "en" -> "Gregorian calendar"
                        "tr" -> "Miladi takvim"
                        else -> "التقويم الميلادي"
                    },
                    color = RoyalCalendarPalette.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when (language) {
                        "en" -> "Calendar, occasions and reminders"
                        "tr" -> "Takvim, etkinlikler ve hatırlatıcılar"
                        else -> "تقويم شهري ومناسبات وتنبيهات"
                    },
                    color = RoyalCalendarPalette.OatCrystal.copy(alpha = 0.82f),
                    fontSize = 12.sp
                )
            }
            Text(
                when (language) {
                    "en" -> "TODAY"
                    "tr" -> "BUGÜN"
                    else -> "اليوم"
                },
                color = RoyalCalendarPalette.Gold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(RoyalCalendarPalette.Gold.copy(alpha = 0.12f))
                    .clickable {
                        visibleMonth = Calendar.getInstance(locale).apply { set(Calendar.DAY_OF_MONTH, 1) }
                        selectedDateMillis = System.currentTimeMillis()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .focusable()
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = Color(0xD910151D),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, RoyalCalendarPalette.GlassBorder)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CalendarMonthButton(
                        Icons.Rounded.ChevronLeft,
                        if (language == "en") "Previous month" else if (language == "tr") "Önceki ay" else "الشهر السابق"
                    ) {
                        visibleMonth = (visibleMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                        selectedDateMillis = visibleMonth.timeInMillis
                    }
                    Text(
                        monthTitle,
                        color = RoyalCalendarPalette.Gold,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    CalendarMonthButton(
                        Icons.Rounded.ChevronRight,
                        if (language == "en") "Next month" else if (language == "tr") "Sonraki ay" else "الشهر التالي"
                    ) {
                        visibleMonth = (visibleMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                        selectedDateMillis = visibleMonth.timeInMillis
                    }
                }

                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        weekdays.forEach { dayName ->
                            Box(
                                modifier = Modifier.weight(1f).height(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(RoyalCalendarPalette.Glass),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    dayName,
                                    color = RoyalCalendarPalette.OatCrystal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        cells.chunked(7).forEach { week ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                week.forEach { day ->
                                    var dayFocused by remember(day, visibleMonth.timeInMillis) { mutableStateOf(false) }
                                    val dateReminders = if (day == null) emptyList() else reminders.filter {
                                        it.occursOn(visibleMonth.get(Calendar.YEAR), visibleMonth.get(Calendar.MONTH), day)
                                    }
                                    val isToday = day != null &&
                                        visibleMonth.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                        visibleMonth.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                                        day == today.get(Calendar.DAY_OF_MONTH)
                                    val isSelected = day != null &&
                                        selectedYear == visibleMonth.get(Calendar.YEAR) &&
                                        selectedMonth == visibleMonth.get(Calendar.MONTH) &&
                                        selectedDay == day
                                    val markerColor = when {
                                        dateReminders.any { it.repeat == CalendarReminderRepeat.YEARLY } -> RoyalCalendarPalette.Purple
                                        dateReminders.any { it.repeat == CalendarReminderRepeat.MONTHLY } -> RoyalCalendarPalette.DeepGreen
                                        dateReminders.any { it.leadMinutes == 0 } -> RoyalCalendarPalette.Red
                                        dateReminders.isNotEmpty() -> RoyalCalendarPalette.Turquoise
                                        else -> RoyalCalendarPalette.OatCrystal
                                    }
                                    Box(
                                        modifier = Modifier.weight(1f).height(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isToday -> RoyalCalendarPalette.Gold
                                                    dayFocused -> RoyalCalendarPalette.Blue.copy(alpha = 0.30f)
                                                    isSelected -> RoyalCalendarPalette.Turquoise.copy(alpha = 0.15f)
                                                    else -> Color(0xFF151D27)
                                                }
                                            )
                                            .then(
                                                when {
                                                    dayFocused -> Modifier.border(1.5.dp, RoyalCalendarPalette.OatCrystal, RoundedCornerShape(10.dp))
                                                    isSelected && !isToday -> Modifier.border(
                                                        1.dp,
                                                        RoyalCalendarPalette.Turquoise,
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                    else -> Modifier
                                                }
                                            )
                                            .then(
                                                if (day != null) Modifier.clickable {
                                                    selectedDateMillis = (visibleMonth.clone() as Calendar).apply {
                                                        set(Calendar.DAY_OF_MONTH, day)
                                                    }.timeInMillis
                                                }.onFocusChanged { dayFocused = it.isFocused }.focusable() else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (day != null) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    numberFormat.format(day),
                                                    color = if (isToday) RoyalCalendarPalette.Black else RoyalCalendarPalette.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                                if (dateReminders.isNotEmpty()) {
                                                    Box(
                                                        modifier = Modifier.padding(top = 2.dp)
                                                            .width((8 + dateReminders.size.coerceAtMost(3) * 3).dp)
                                                            .height(3.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isToday) RoyalCalendarPalette.Black else markerColor)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = RoyalCalendarPalette.Glass,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoyalCalendarPalette.GlassBorder)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    when (language) {
                                        "en" -> "Selected date"
                                        "tr" -> "Seçilen tarih"
                                        else -> "التاريخ المحدد"
                                    },
                                    color = RoyalCalendarPalette.OatCrystal.copy(alpha = 0.75f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    selectedDateText,
                                    color = RoyalCalendarPalette.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Button(
                                onClick = {
                                    editingReminder = null
                                    showReminderDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoyalCalendarPalette.Gold,
                                    contentColor = RoyalCalendarPalette.Black
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    when (language) {
                                        "en" -> "+ Add occasion"
                                        "tr" -> "+ Etkinlik ekle"
                                        else -> "+ إضافة مناسبة"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (selectedReminders.isEmpty()) {
                            Text(
                                when (language) {
                                    "en" -> "No occasions saved for this date yet"
                                    "tr" -> "Bu tarih için henüz etkinlik yok"
                                    else -> "لا توجد مناسبات محفوظة في هذا اليوم"
                                },
                                color = RoyalCalendarPalette.OatCrystal.copy(alpha = 0.72f),
                                fontSize = 12.sp
                            )
                        } else {
                            selectedReminders.forEach { reminder ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(RoyalCalendarPalette.Black.copy(alpha = 0.28f))
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(7.dp).clip(CircleShape)
                                            .background(
                                                when (reminder.repeat) {
                                                    CalendarReminderRepeat.YEARLY -> RoyalCalendarPalette.Purple
                                                    CalendarReminderRepeat.MONTHLY -> RoyalCalendarPalette.DeepGreen
                                                    CalendarReminderRepeat.NONE -> RoyalCalendarPalette.Turquoise
                                                }
                                            )
                                    )
                                    Column(modifier = Modifier.weight(1f).padding(start = 9.dp)) {
                                        Text(
                                            reminder.title,
                                            color = RoyalCalendarPalette.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2
                                        )
                                        val clock = String.format(Locale.ROOT, "%02d:%02d", reminder.hour, reminder.minute)
                                        Text(
                                            clock + "  •  " + when (reminder.leadMinutes) {
                                                0 -> if (language == "en") "At event time" else if (language == "tr") "Etkinlik saatinde" else "عند الموعد"
                                                10 -> if (language == "en") "10 min before" else if (language == "tr") "10 dk önce" else "قبل 10 دقائق"
                                                60 -> if (language == "en") "1 hour before" else if (language == "tr") "1 saat önce" else "قبل ساعة"
                                                1440 -> if (language == "en") "1 day before" else if (language == "tr") "1 gün önce" else "قبل يوم"
                                                else -> if (language == "en") "Reminder set" else if (language == "tr") "Hatırlatıcı ayarlı" else "تم ضبط التنبيه"
                                            } + if (reminder.repeat != CalendarReminderRepeat.NONE) {
                                                "  •  " + when (reminder.repeat) {
                                                    CalendarReminderRepeat.MONTHLY -> if (language == "en") "Monthly" else if (language == "tr") "Aylık" else "شهرياً"
                                                    CalendarReminderRepeat.YEARLY -> if (language == "en") "Yearly" else if (language == "tr") "Yıllık" else "سنوياً"
                                                    CalendarReminderRepeat.NONE -> ""
                                                }
                                            } else "",
                                            color = RoyalCalendarPalette.OatCrystal.copy(alpha = 0.76f),
                                            fontSize = 10.sp,
                                            maxLines = 3
                                        )
                                    }
                                    TextButton(onClick = {
                                        editingReminder = reminder
                                        showReminderDialog = true
                                    }) {
                                        Text(
                                            when (language) { "en" -> "Edit"; "tr" -> "Düzenle"; else -> "تعديل" },
                                            color = RoyalCalendarPalette.Gold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    TextButton(onClick = {
                                        GregorianCalendarReminderScheduler.delete(context, reminder.id)
                                        reminders.removeAll { it.id == reminder.id }
                                    }) {
                                        Text(
                                            when (language) { "en" -> "Delete"; "tr" -> "Sil"; else -> "حذف" },
                                            color = RoyalCalendarPalette.Red,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when (language) {
                                        "en" -> "Allow notifications so reminders can appear"
                                        "tr" -> "Hatırlatıcılar için bildirim izni verin"
                                        else -> "اسمح بالإشعارات حتى تظهر التنبيهات"
                                    },
                                    modifier = Modifier.weight(1f),
                                    color = RoyalCalendarPalette.OatCrystal,
                                    fontSize = 10.sp
                                )
                                TextButton(onClick = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }) {
                                    Text(
                                        when (language) { "en" -> "Allow"; "tr" -> "İzin ver"; else -> "سماح" },
                                        color = RoyalCalendarPalette.Blue,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReminderDialog) {
        val source = editingReminder
        CalendarReminderDialog(
            language = language,
            initialReminder = source,
            selectedYear = if (source == null) selectedYear else source.year,
            selectedMonth = if (source == null) selectedMonth else source.month,
            selectedDay = if (source == null) selectedDay else source.day,
            onDismiss = {
                showReminderDialog = false
                editingReminder = null
            },
            onSave = { reminder ->
                GregorianCalendarReminderScheduler.upsert(context, reminder)
                reminders.removeAll { it.id == reminder.id }
                reminders.add(reminder)
                showReminderDialog = false
                editingReminder = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
    }
}


private fun GregorianCalendarReminder.occursOn(year: Int, month: Int, day: Int): Boolean {
    val requestedMonthIndex = year * 12 + month
    val originalMonthIndex = this.year * 12 + this.month
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val displayedDay = this.day.coerceAtMost(calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
    if (day != displayedDay) return false
    return when (repeat) {
        CalendarReminderRepeat.NONE -> year == this.year && month == this.month && day == this.day
        CalendarReminderRepeat.MONTHLY -> requestedMonthIndex >= originalMonthIndex
        CalendarReminderRepeat.YEARLY -> year >= this.year && month == this.month
    }
}

@Composable
private fun CalendarReminderDialog(
    language: String,
    initialReminder: GregorianCalendarReminder?,
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int,
    onDismiss: () -> Unit,
    onSave: (GregorianCalendarReminder) -> Unit
) {
    var title by remember(initialReminder?.id) { mutableStateOf(initialReminder?.title.orEmpty()) }
    var description by remember(initialReminder?.id) { mutableStateOf(initialReminder?.description.orEmpty()) }
    var timeText by remember(initialReminder?.id) {
        mutableStateOf(
            if (initialReminder == null) "18:00"
            else String.format(Locale.ROOT, "%02d:%02d", initialReminder.hour, initialReminder.minute)
        )
    }
    var leadMinutes by remember(initialReminder?.id) { mutableStateOf(initialReminder?.leadMinutes ?: 60) }
    var repeat by remember(initialReminder?.id) {
        mutableStateOf(initialReminder?.repeat ?: CalendarReminderRepeat.NONE)
    }
    var invalidTime by remember { mutableStateOf(false) }
    val dateCalendar = Calendar.getInstance().apply {
        set(selectedYear, selectedMonth, selectedDay)
    }
    val dateText = SimpleDateFormat(
        if (language == "ar") "EEEE، d MMMM yyyy" else "EEEE, d MMMM yyyy",
        when (language) { "en" -> Locale.ENGLISH; "tr" -> Locale("tr", "TR"); else -> Locale("ar") }
    ).format(dateCalendar.time)
    val timeParts = timeText.trim().split(":")
    val hour = timeParts.getOrNull(0)?.toIntOrNull()
    val minute = timeParts.getOrNull(1)?.toIntOrNull()
    val validTime = timeParts.size == 2 &&
        hour != null && hour in 0..23 &&
        minute != null && minute in 0..59

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF171D27),
        titleContentColor = RoyalCalendarPalette.White,
        textContentColor = RoyalCalendarPalette.OatCrystal,
        title = {
            Text(
                when {
                    initialReminder != null && language == "en" -> "Edit occasion"
                    initialReminder != null && language == "tr" -> "Etkinliği düzenle"
                    initialReminder != null -> "تعديل المناسبة"
                    language == "en" -> "Add occasion"
                    language == "tr" -> "Etkinlik ekle"
                    else -> "إضافة مناسبة"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(dateText, color = RoyalCalendarPalette.Gold, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(when (language) { "en" -> "Occasion name"; "tr" -> "Etkinlik adı"; else -> "اسم المناسبة" })
                    },
                    colors = calendarTextFieldColors()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    label = {
                        Text(when (language) { "en" -> "Description (optional)"; "tr" -> "Açıklama (isteğe bağlı)"; else -> "وصف اختياري" })
                    },
                    colors = calendarTextFieldColors()
                )
                OutlinedTextField(
                    value = timeText,
                    onValueChange = {
                        timeText = it.filter { character -> character.isDigit() || character == ':' }.take(5)
                        invalidTime = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = {
                        Text(when (language) { "en" -> "Time (24-hour, HH:mm)"; "tr" -> "Saat (24 saat, SS:dd)"; else -> "وقت المناسبة (24 ساعة)" })
                    },
                    placeholder = { Text("18:30", color = RoyalCalendarPalette.OatCrystal.copy(alpha = 0.55f)) },
                    isError = invalidTime,
                    colors = calendarTextFieldColors()
                )
                if (invalidTime) {
                    Text(
                        when (language) { "en" -> "Enter a valid time such as 18:30"; "tr" -> "18:30 gibi geçerli bir saat girin"; else -> "أدخل وقتاً صحيحاً مثل 18:30" },
                        color = RoyalCalendarPalette.Red,
                        fontSize = 11.sp
                    )
                }
                Text(
                    when (language) { "en" -> "Remind me"; "tr" -> "Hatırlat"; else -> "وقت التنبيه" },
                    color = RoyalCalendarPalette.OatCrystal,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0, 10, 60, 1440).forEach { value ->
                        TextButton(
                            onClick = { leadMinutes = value },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = if (leadMinutes == value) RoyalCalendarPalette.Gold else RoyalCalendarPalette.White
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                when (value) {
                                    0 -> if (language == "en") "At time" else if (language == "tr") "Saatinde" else "عند الموعد"
                                    10 -> if (language == "en") "10 min" else if (language == "tr") "10 dk" else "10 دقائق"
                                    60 -> if (language == "en") "1 hour" else if (language == "tr") "1 saat" else "ساعة"
                                    else -> if (language == "en") "1 day" else if (language == "tr") "1 gün" else "يوم"
                                },
                                fontSize = 10.sp,
                                fontWeight = if (leadMinutes == value) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Text(
                    when (language) { "en" -> "Repeat"; "tr" -> "Tekrar"; else -> "تكرار المناسبة" },
                    color = RoyalCalendarPalette.OatCrystal,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        CalendarReminderRepeat.NONE,
                        CalendarReminderRepeat.MONTHLY,
                        CalendarReminderRepeat.YEARLY
                    ).forEach { option ->
                        TextButton(
                            onClick = { repeat = option },
                            modifier = Modifier.weight(1f),
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = if (repeat == option) RoyalCalendarPalette.Gold else RoyalCalendarPalette.White
                            )
                        ) {
                            Text(
                                when (option) {
                                    CalendarReminderRepeat.NONE -> if (language == "en") "Never" else if (language == "tr") "Yok" else "بدون"
                                    CalendarReminderRepeat.MONTHLY -> if (language == "en") "Monthly" else if (language == "tr") "Aylık" else "شهري"
                                    CalendarReminderRepeat.YEARLY -> if (language == "en") "Yearly" else if (language == "tr") "Yıllık" else "سنوي"
                                },
                                fontSize = 10.sp,
                                fontWeight = if (repeat == option) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Text(
                    when (language) {
                        "en" -> "Reminders are saved on this device. Android may delay alerts when exact-alarm access is unavailable."
                        "tr" -> "Hatırlatıcılar bu cihazda saklanır. Kesin alarm izni yoksa Android bildirimi geciktirebilir."
                        else -> "تُحفظ المناسبات على هذا الجهاز. قد يتأخر التنبيه قليلاً إذا لم يسمح النظام بالمنبه الدقيق."
                    },
                    color = RoyalCalendarPalette.OatCrystal.copy(alpha = 0.68f),
                    fontSize = 10.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    if (!validTime || hour == null || minute == null) {
                        invalidTime = true
                    } else {
                        onSave(
                            GregorianCalendarReminder(
                                id = initialReminder?.id ?: java.util.UUID.randomUUID().toString(),
                                title = title.trim(),
                                description = description.trim(),
                                year = selectedYear,
                                month = selectedMonth,
                                day = selectedDay,
                                hour = hour,
                                minute = minute,
                                leadMinutes = leadMinutes,
                                repeat = repeat
                            )
                        )
                    }
                }
            ) {
                Text(
                    when (language) { "en" -> "Save"; "tr" -> "Kaydet"; else -> "حفظ" },
                    color = RoyalCalendarPalette.Gold,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    when (language) { "en" -> "Cancel"; "tr" -> "İptal"; else -> "إلغاء" },
                    color = RoyalCalendarPalette.OatCrystal
                )
            }
        }
    )
}

@Composable
private fun calendarTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = RoyalCalendarPalette.White,
    unfocusedTextColor = RoyalCalendarPalette.White,
    focusedBorderColor = RoyalCalendarPalette.Gold,
    unfocusedBorderColor = RoyalCalendarPalette.GlassBorder,
    focusedLabelColor = RoyalCalendarPalette.Gold,
    unfocusedLabelColor = RoyalCalendarPalette.OatCrystal,
    cursorColor = RoyalCalendarPalette.Turquoise
)

@Composable
private fun CalendarMonthButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
            .background(if (focused) RoyalCalendarPalette.Blue.copy(alpha = 0.28f) else RoyalCalendarPalette.Glass)
            .border(
                1.dp,
                if (focused) RoyalCalendarPalette.Gold else RoyalCalendarPalette.GlassBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = RoyalCalendarPalette.Gold, modifier = Modifier.size(23.dp))
    }
}

package com.royalplus.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun GregorianCalendarScreen(language: String) {
    val locale = remember(language) {
        when (language) {
            "en" -> Locale.ENGLISH
            "tr" -> Locale("tr", "TR")
            else -> Locale("ar")
        }
    }
    var visibleMonth by remember {
        mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) })
    }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val today = Calendar.getInstance()
    val monthTitle = remember(visibleMonth.timeInMillis, locale) {
        SimpleDateFormat("MMMM yyyy", locale).format(visibleMonth.time)
    }
    val selectedDate = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
    val selectedDateText = SimpleDateFormat("EEEE، d MMMM yyyy", locale).format(selectedDate.time)
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF17252A))
                    .border(1.dp, Color(0xFFB99248).copy(alpha = 0.65f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = Color(0xFFE0BD72), modifier = Modifier.size(25.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(when (language) { "en" -> "Gregorian calendar"; "tr" -> "Miladi takvim"; else -> "التقويم الميلادي" }, color = Color(0xFFF4EAD5), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(when (language) { "en" -> "Monthly calendar"; "tr" -> "Aylık takvim"; else -> "عرض شهري منظم" }, color = Color(0xFF9DA8AC), fontSize = 12.sp)
            }
            Text(
                when (language) { "en" -> "TODAY"; "tr" -> "BUGÜN"; else -> "اليوم" },
                color = Color(0xFFE0BD72), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2418))
                    .clickable {
                        visibleMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
                        selectedDateMillis = System.currentTimeMillis()
                    }.padding(horizontal = 13.dp, vertical = 9.dp).focusable()
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = Color(0xD90B1117),
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB99248).copy(alpha = 0.32f))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    CalendarMonthButton(Icons.Rounded.ChevronLeft, if (language == "en") "Previous month" else if (language == "tr") "Önceki ay" else "الشهر السابق") {
                        visibleMonth = (visibleMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                        selectedDateMillis = 0L
                    }
                    Text(monthTitle, color = Color(0xFFE0BD72), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    CalendarMonthButton(Icons.Rounded.ChevronRight, if (language == "en") "Next month" else if (language == "tr") "Sonraki ay" else "الشهر التالي") {
                        visibleMonth = (visibleMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                        selectedDateMillis = 0L
                    }
                }

                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        weekdays.forEach { dayName ->
                            Box(modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF17252A)), contentAlignment = Alignment.Center) {
                                Text(dayName, color = Color(0xFFDFC27F), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
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
                                    val isToday = day != null && visibleMonth.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                        visibleMonth.get(Calendar.MONTH) == today.get(Calendar.MONTH) && day == today.get(Calendar.DAY_OF_MONTH)
                                    val isSelected = day != null && selectedDateMillis != 0L &&
                                        selectedDate.get(Calendar.YEAR) == visibleMonth.get(Calendar.YEAR) &&
                                        selectedDate.get(Calendar.MONTH) == visibleMonth.get(Calendar.MONTH) &&
                                        selectedDate.get(Calendar.DAY_OF_MONTH) == day
                                    Box(
                                        modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp))
                                            .background(when { isToday -> Color(0xFFB99248); isSelected -> Color(0xFF183B40); else -> Color(0xFF111A21) })
                                            .then(if (isSelected && !isToday) Modifier.border(1.dp, Color(0xFF4EAAA1), RoundedCornerShape(10.dp)) else Modifier)
                                            .then(if (day != null) Modifier.clickable {
                                                selectedDateMillis = (visibleMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }.timeInMillis
                                            }.focusable() else Modifier),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (day != null) Text(numberFormat.format(day), color = if (isToday) Color(0xFF111111) else Color(0xFFF0EEE8), fontSize = 16.sp, fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF17252A)).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(when (language) { "en" -> "Selected date"; "tr" -> "Seçilen tarih"; else -> "التاريخ المحدد" }, color = Color(0xFF9DA8AC), fontSize = 11.sp)
                        Text(if (selectedDateMillis == 0L) monthTitle else selectedDateText, color = Color(0xFFE7D6AE), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(when (language) { "en" -> "Gregorian"; "tr" -> "Miladi"; else -> "ميلادي" }, color = Color(0xFF4DB6AC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFF3A3020) else Color(0xFF17252A))
            .border(1.dp, if (focused) Color(0xFFE0BD72) else Color(0xFF51616A), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).onFocusChanged { focused = it.isFocused }.focusable(),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color(0xFFE0BD72), modifier = Modifier.size(23.dp))
    }
}

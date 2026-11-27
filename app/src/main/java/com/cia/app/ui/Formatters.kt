package com.cia.app.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun money(value: Long): String = NumberFormat.getNumberInstance(Locale("fa", "IR")).format(value)
fun dateTime(value: Long): String = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("fa", "IR")).format(Date(value))

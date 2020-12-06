package com.si.streetlight

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream
import java.io.OutputStream
import java.util.*


data class TimeSlot (val startHour: Int, val startMinute: Int, val endHour: Int, val endMinute: Int)


@ExperimentalUnsignedTypes
class MainActivity : AppCompatActivity() {
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream

    private lateinit var timeSlotStartTextView: TextView
    private lateinit var timeSlotEndTextView: TextView

    private lateinit var luminositySeekBar: SeekBar
    private lateinit var aliveTimeTextView: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        timeSlotStartTextView = findViewById(R.id.time_slot_start)
        timeSlotEndTextView = findViewById(R.id.time_slot_end)

        luminositySeekBar = findViewById(R.id.luminosity_seekbar)
        aliveTimeTextView = findViewById(R.id.alive_time_text_view)

        val modeRadioGroup: RadioGroup = findViewById(R.id.mode_radio_group)
        modeRadioGroup.setOnCheckedChangeListener { _: RadioGroup, id: Int ->
            val radioButton: View = findViewById(id)
            val idx = modeRadioGroup.indexOfChild(radioButton)
            updateMode(idx)
        }

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!bluetoothAdapter.isEnabled) {
            val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetooth, 0)
        }

        val devices = bluetoothAdapter.bondedDevices
        val hc05 = devices.find { device -> device.name == "HC-05" } ?: return

        val uuid = hc05.uuids[0].uuid

        val socket = hc05.createInsecureRfcommSocketToServiceRecord(uuid)

        socket.connect()

        inputStream = socket.inputStream
        outputStream = socket.outputStream

        updateGMTTime(inputStream, outputStream)
    }

    private fun showMessage(message: String) {
        Toast.makeText(applicationContext, message, LENGTH_SHORT).show()
    }

    private fun updateTimeSlot(
        inputStream: InputStream,
        outputStream: OutputStream,
        timeSlot: TimeSlot
    ) {
        val message = ubyteArrayOf(
            't'.toInt().toUByte(),
            timeSlot.startHour.toUByte(),
            timeSlot.startMinute.toUByte(),
            timeSlot.endHour.toUByte(),
            timeSlot.endMinute.toUByte()
        ).toByteArray()

        val success = sendCommand(inputStream, outputStream, message)

        showMessage(if (success) "Time Slot set" else "Error setting time slot")
    }

    private fun updateGMTTime(inputStream: InputStream, outputStream: OutputStream) {
        val calendar = Calendar.getInstance()

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH).toUByte()
        val day = calendar.get(Calendar.DAY_OF_MONTH).toUByte()

        val hour = calendar.get(Calendar.HOUR_OF_DAY).toUByte()
        val minute = calendar.get(Calendar.MINUTE).toUByte()
        val second = calendar.get(Calendar.SECOND).toUByte()

        val yearH = year.shr(8).and(0xff).toUByte()
        val yearL = year.and(0xff).toUByte()

        val message = ubyteArrayOf(
            'g'.toInt().toUByte(),
            second,
            minute,
            hour,
            day,
            month,
            yearH,
            yearL
        ).toByteArray()

        val success = sendCommand(inputStream, outputStream, message)

        showMessage(if (success) "GMT Time set" else "Error setting GMT time")
    }

    private fun updateLuminosity(luminosity: Int) {
        val message = ubyteArrayOf('l'.toInt().toUByte(), luminosity.toUByte()).toByteArray()

        val success = sendCommand(inputStream, outputStream, message)

        showMessage(if (success) "Luminosity set" else "Error setting luminosity threshold")
    }

    private fun updateAliveTime(aliveTime: Int) {
        val message = ubyteArrayOf('a'.toInt().toUByte(), aliveTime.toUByte()).toByteArray()

        val success = sendCommand(inputStream, outputStream, message)

        showMessage(if (success) "Alive time set" else "Error setting alive time")
    }

    private fun updateMode(mode: Int) {
        val message = ubyteArrayOf('m'.toInt().toUByte(), mode.toUByte()).toByteArray()

        val success = sendCommand(inputStream, outputStream, message)

        showMessage(if (success) "Mode set" else "Error setting mode")
    }

    private fun sendCommand(
        inputStream: InputStream,
        outputStream: OutputStream,
        message: ByteArray
    ): Boolean {
        outputStream.write(message)

        while (inputStream.available() == 0) {
            Log.d("StreetLight", "Waiting response")
        }

        val response = byteArrayOf(0)
        inputStream.read(response)

        val ack = response[0].toChar()
        return ack == 'A'
    }

    fun onTimeSlotUpdate(view: android.view.View) {
        val timeStartStr = timeSlotStartTextView.text
        val timeEndStr = timeSlotEndTextView.text

        val startParts = timeStartStr.split(":")
        val endParts = timeEndStr.split(":")
        val timeslot = TimeSlot(
            startParts[0].toInt(), startParts[1].toInt(),
            endParts[0].toInt(), endParts[1].toInt()
        )

        updateTimeSlot(inputStream, outputStream, timeslot)
    }

    fun onLuminosityUpdate(view: android.view.View) {
        val luminosity = luminositySeekBar.progress

        updateLuminosity(luminosity)
    }

    fun onAliveTimeUpdate(view: android.view.View) {
        val aliveTime = aliveTimeTextView.text.toString().toInt()

        if (aliveTime > 240) {
            showMessage("Invalid alive time, must be less than 240")
            return;
        }

        updateAliveTime(aliveTime)
    }
}
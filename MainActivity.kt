package com.example.gujaratireminder

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var items = mutableListOf<Reminder>()
    private lateinit var adapter: ItemAdapter
    private val loc = Locale("gu", "IN")
    private val fullFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", loc)
    private val dateFmt = SimpleDateFormat("dd MMM yyyy", loc)
    private val timeFmt = SimpleDateFormat("hh:mm a", loc)

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) doExport(uri)
        }
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) confirmImport(uri)
        }
    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val list = findViewById<ListView>(R.id.list)
        list.emptyView = findViewById(R.id.empty)
        adapter = ItemAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> showEdit(items[pos]) }
        findViewById<Button>(R.id.btnAdd).setOnClickListener { showEdit(null) }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        askExactOnce()
    }

    private fun askExactOnce() {
        val p = Store.prefs(this)
        if (Scheduler.canExact(this) || p.getBoolean("asked_exact", false)) return
        p.edit().putBoolean("asked_exact", true).apply()
        AlertDialog.Builder(this)
            .setTitle(R.string.exact_title)
            .setMessage(R.string.exact_msg)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                if (Build.VERSION.SDK_INT >= 31) {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:$packageName"))
                    )
                }
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun refresh() {
        items = Store.load(this)
        items.sortBy { it.time }
        adapter.notifyDataSetChanged()
    }

    // ---------- મેનુ ----------
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_theme -> showTheme()
            R.id.menu_export -> exportLauncher.launch("gujarati-reminder-backup.json")
            R.id.menu_import -> importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        return true
    }

    private fun showTheme() {
        val cur = Store.prefs(this).getInt("theme", 0)
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_theme)
            .setSingleChoiceItems(R.array.theme_options, cur) { d, which ->
                Store.prefs(this).edit().putInt("theme", which).apply()
                AppCompatDelegate.setDefaultNightMode(App.modeFor(which))
                d.dismiss()
            }
            .show()
    }

    // ---------- બેકઅપ ----------
    private fun doExport(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(Store.export(this).toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, R.string.export_ok, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.import_fail, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmImport(uri: Uri) {
        AlertDialog.Builder(this)
            .setMessage(R.string.import_confirm)
            .setPositiveButton(R.string.yes) { _, _ -> doImport(uri) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun doImport(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }
        val parsed = if (text == null) null else Store.parseBackup(text)
        if (parsed == null) {
            Toast.makeText(this, R.string.import_fail, Toast.LENGTH_LONG).show()
            return
        }
        Store.load(this).forEach { Scheduler.cancel(this, it.id) }
        parsed.forEach { if (!it.done) { Scheduler.rollForward(it); Scheduler.schedule(this, it) } }
        Store.save(this, parsed)
        refresh()
        Toast.makeText(this, getString(R.string.import_ok, parsed.size), Toast.LENGTH_SHORT).show()
    }

    // ---------- ઉમેરો / ફેરફાર ----------
    private fun showEdit(ex: Reminder?) {
        val v = layoutInflater.inflate(R.layout.dialog_edit, null)
        val etTitle = v.findViewById<EditText>(R.id.etTitle)
        val etNote = v.findViewById<EditText>(R.id.etNote)
        val bDate = v.findViewById<Button>(R.id.btnDate)
        val bTime = v.findViewById<Button>(R.id.btnTime)
        val sp = v.findViewById<Spinner>(R.id.spRepeat)

        sp.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.repeat_options)
        )
        val cal = Calendar.getInstance().apply {
            timeInMillis = ex?.time ?: (System.currentTimeMillis() + 3_600_000L)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        fun upd() {
            bDate.text = dateFmt.format(cal.time)
            bTime.text = timeFmt.format(cal.time)
        }
        if (ex != null) {
            etTitle.setText(ex.title)
            etNote.setText(ex.note)
            sp.setSelection(ex.repeat)
        }
        upd()

        bDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, m); cal.set(Calendar.DAY_OF_MONTH, d)
                upd()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        bTime.setOnClickListener {
            TimePickerDialog(this, { _, h, mi ->
                cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, mi)
                upd()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (ex == null) R.string.add else R.string.edit_title)
            .setView(v)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = etTitle.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(this, R.string.err_title, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val rep = sp.selectedItemPosition
            if (rep == 0 && cal.timeInMillis <= System.currentTimeMillis()) {
                Toast.makeText(this, R.string.err_past, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val all = Store.load(this)
            val r = if (ex == null) {
                Reminder(System.currentTimeMillis(), title, "", cal.timeInMillis, rep).also { all.add(it) }
            } else {
                all.find { it.id == ex.id } ?: Reminder(ex.id, title, "", cal.timeInMillis, rep).also { all.add(it) }
            }
            Scheduler.cancel(this, r.id)
            r.title = title
            r.note = etNote.text.toString().trim()
            r.time = cal.timeInMillis
            r.repeat = rep
            r.done = false
            Scheduler.rollForward(r)
            Scheduler.schedule(this, r)
            Store.save(this, all)
            refresh()
            dlg.dismiss()
        }
    }

    private fun confirmDelete(r: Reminder) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                Scheduler.cancel(this, r.id)
                val all = Store.load(this)
                all.removeAll { it.id == r.id }
                Store.save(this, all)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    inner class ItemAdapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_reminder, parent, false)
            val r = items[position]
            v.findViewById<TextView>(R.id.tvTitle).text = r.title
            val note = v.findViewById<TextView>(R.id.tvNote)
            note.text = r.note
            note.visibility = if (r.note.isBlank()) View.GONE else View.VISIBLE
            val rep = resources.getStringArray(R.array.repeat_options)[r.repeat]
            val done = if (r.done) " • " + getString(R.string.done_label) else ""
            v.findViewById<TextView>(R.id.tvTime).text = fullFmt.format(Date(r.time)) + " • " + rep + done
            v.findViewById<Button>(R.id.btnDel).setOnClickListener { confirmDelete(r) }
            return v
        }
    }
}

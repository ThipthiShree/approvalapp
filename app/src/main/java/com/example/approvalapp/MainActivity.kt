package com.example.approvalapp

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.print.PrintManager
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.firebase.database.*
import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale


class MainActivity : AppCompatActivity() {
    // ================= LIVE LOCATION =================
    private var allQuotations: List<Quotation>   = emptyList()
    private var allSalesOrders: List<SalesOrder> = emptyList()
    private var approvedList: List<Map<String, String>> = emptyList()
    private var rejectedList: List<Map<String, String>> = emptyList()

    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private val db = FirebaseDatabase
        .getInstance("https://approvalapp-a8007-default-rtdb.asia-southeast1.firebasedatabase.app")
        .reference
    private var pendingQuotationCount = 0
    private var pendingSalesCount     = 0
    private var dashboardExpanded     = false
    private var requestExpanded       = false
    private var profileMenuOpen       = false
    private var quotationsLoaded   = false
    private var salesOrdersLoaded  = false

    private fun checkAndLoadHistory() {
        if (quotationsLoaded && salesOrdersLoaded) loadApprovalHistory()
    }

    // ================= NAVIGATION DRAWER =================
    private fun setupDrawer() {
        drawerLayout = findViewById(R.id.drawerLayout)

        findViewById<LinearLayout>(R.id.hamburgerButton).setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        findViewById<LinearLayout>(R.id.navDashboard).setOnClickListener {
            dashboardExpanded = !dashboardExpanded
            val subMenu = findViewById<LinearLayout>(R.id.dashboardSubMenu)
            val chevron = findViewById<ImageView>(R.id.dashboardChevron)
            subMenu.visibility = if (dashboardExpanded) View.VISIBLE else View.GONE
            rotateChevron(chevron, if (dashboardExpanded) 180f else 0f)
        }

        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            findViewById<View>(R.id.homeLayout).visibility      = View.VISIBLE
            findViewById<View>(R.id.approvalContent).visibility = View.GONE
            findViewById<TextView>(R.id.topBarTitle).text       = "Home"
        }

        findViewById<LinearLayout>(R.id.navRequest).setOnClickListener {
            requestExpanded = !requestExpanded
            val subMenu = findViewById<LinearLayout>(R.id.navApprovalSubMenu)
            val chevron = findViewById<ImageView>(R.id.requestChevron)
            subMenu.visibility = if (requestExpanded) View.VISIBLE else View.GONE
            rotateChevron(chevron, if (requestExpanded) 180f else 0f)
        }

        findViewById<LinearLayout>(R.id.navApproval).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            findViewById<View>(R.id.homeLayout).visibility      = View.GONE
            findViewById<View>(R.id.approvalContent).visibility = View.VISIBLE
            findViewById<TextView>(R.id.topBarTitle).text       = "Request Approval"
        }

        setupProfileMenu()
    }

    // ================= PROFILE MENU =================
    private fun setupProfileMenu() {
        val prefs    = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val username = prefs.getString("username", "User") ?: "User"
        val initial  = username.firstOrNull()?.uppercaseChar()?.toString() ?: "U"

        findViewById<TextView>(R.id.tvProfileInitial)?.text  = initial
        findViewById<TextView>(R.id.tvDropdownInitial)?.text = initial
        findViewById<TextView>(R.id.tvMenuUsername)?.text    = username

        val profileIcon   = findViewById<View>(R.id.profileIconContainer)
        val dropdown      = findViewById<View>(R.id.profileDropdown)
        val overlay       = findViewById<View>(R.id.overlayDismiss)
        val menuSettings  = findViewById<View>(R.id.menuItemSettings)
        val menuPrint     = findViewById<View>(R.id.menuItemPrint)
        val menuChangePwd = findViewById<View>(R.id.menuItemChangePassword)
        val menuLogout    = findViewById<View>(R.id.menuItemLogout)

        profileIcon?.setOnClickListener {
            if (profileMenuOpen) hideProfileMenu() else showProfileMenu()
        }
        overlay?.setOnClickListener { hideProfileMenu() }

        menuSettings?.setOnClickListener {
            hideProfileMenu()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        menuPrint?.setOnClickListener {
            hideProfileMenu()
            val viewToPrint = if (findViewById<View>(R.id.approvalContent).visibility == View.VISIBLE)
                findViewById<View>(R.id.approvalContent)
            else
                findViewById<View>(R.id.homeLayout)
            val jobName = "${getString(R.string.app_name)} Document"
            val adapter = PrintDocumentAdapterHelper(this, jobName, viewToPrint)
            adapter.captureView()
            val pm = getSystemService(PRINT_SERVICE) as PrintManager
            pm.print(jobName, adapter, null)
        }

        menuChangePwd?.setOnClickListener {
            hideProfileMenu()
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        menuLogout?.setOnClickListener {
            hideProfileMenu()

            val dialogView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF0D1B3E.toInt())
                setPadding(dpToPx(28), dpToPx(28), dpToPx(28), dpToPx(24))
            }

            val iconWrapper = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(64), dpToPx(64)).also {
                    it.gravity = Gravity.CENTER_HORIZONTAL
                    it.bottomMargin = dpToPx(16)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0x33E05C5C.toInt())
                }
            }
            val iconText = TextView(this).apply {
                text = "⚠"
                textSize = 28f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            iconWrapper.addView(iconText)

            val title = TextView(this).apply {
                text = "Ready to leave?"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dpToPx(10) }
            }

            val message = TextView(this).apply {
                text = "You'll be signed out of your account.\nAny unsaved changes will be lost."
                textSize = 13f
                setTextColor(0xFF8AB4D4.toInt())
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dpToPx(28) }
            }

            val btnLogout = Button(this).apply {
                text = "Yes, log me out"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dpToPx(8).toFloat()
                    setColor(0xFFE05C5C.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)
                ).also { it.bottomMargin = dpToPx(10) }
            }

            val btnCancel = Button(this).apply {
                text = "Stay signed in"
                textSize = 13f
                setTextColor(0xFF8AB4D4.toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dpToPx(8).toFloat()
                    setColor(0xFF1A2A4A.toInt())
                    setStroke(1, 0xFF2A3A5A.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44)
                )
            }

            dialogView.addView(iconWrapper)
            dialogView.addView(title)
            dialogView.addView(message)
            dialogView.addView(btnLogout)
            dialogView.addView(btnCancel)

            val dialog = android.app.Dialog(this).apply {
                requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                setContentView(dialogView)
                setCancelable(true)
                window?.apply {
                    setBackgroundDrawableResource(android.R.color.transparent)
                    setLayout(
                        (resources.displayMetrics.widthPixels * 0.82).toInt(),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setGravity(Gravity.CENTER)
                }
            }

            btnLogout.setOnClickListener {
                dialog.dismiss()
                val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                prefs.edit().clear().apply()
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                val intent = Intent(this, LoginPage::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            btnCancel.setOnClickListener { dialog.dismiss() }
            dialog.show()
        }
    }

    private fun showProfileMenu() {
        profileMenuOpen = true
        val dropdown = findViewById<View>(R.id.profileDropdown) ?: return
        val overlay  = findViewById<View>(R.id.overlayDismiss)  ?: return
        dropdown.visibility   = View.VISIBLE
        overlay.visibility    = View.VISIBLE
        dropdown.alpha        = 0f
        dropdown.translationY = -16f
        dropdown.animate().alpha(1f).translationY(0f).setDuration(180).start()
    }

    private fun hideProfileMenu() {
        profileMenuOpen = false
        val dropdown = findViewById<View>(R.id.profileDropdown) ?: return
        val overlay  = findViewById<View>(R.id.overlayDismiss)  ?: return
        dropdown.animate().alpha(0f).translationY(-16f).setDuration(140)
            .withEndAction {
                dropdown.visibility = View.GONE
                overlay.visibility  = View.GONE
            }.start()
    }

    // ================= CHEVRON ROTATE =================
    private fun rotateChevron(view: ImageView, angle: Float) {
        view.animate().rotation(angle).setDuration(200).start()
    }

    // ================= LIFECYCLE =================
    // ================= LIFECYCLE =================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isDark = getSharedPreferences("AppSettings", MODE_PRIVATE)
            .getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else        AppCompatDelegate.MODE_NIGHT_NO
        )
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)

        findViewById<View>(R.id.homeLayout).visibility      = View.VISIBLE
        findViewById<View>(R.id.approvalContent).visibility = View.GONE

        setupDrawer()          // ← only once
        setupSearch()
        setupChevrons()
        loadQuotations()
        loadSalesOrders()

        startRingAnimations()
        fetchLocation() // ← only here
        startDateUpdater()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    // ================= CHEVRONS =================
    private fun setupChevrons() {
        val quotationTableContainer = findViewById<HorizontalScrollView>(R.id.quotationTableContainer)
        val quotationChevron        = findViewById<ImageButton>(R.id.quotationChevron)
        val salesTableContainer     = findViewById<HorizontalScrollView>(R.id.salesTableContainer)
        val salesChevron            = findViewById<ImageButton>(R.id.salesChevron)

        quotationChevron.setOnClickListener {
            if (quotationTableContainer.visibility == View.GONE) {
                quotationTableContainer.visibility = View.VISIBLE
                quotationChevron.setImageResource(android.R.drawable.arrow_up_float)
            } else {
                quotationTableContainer.visibility = View.GONE
                quotationChevron.setImageResource(android.R.drawable.arrow_down_float)
            }
        }

        salesChevron.setOnClickListener {
            if (salesTableContainer.visibility == View.GONE) {
                salesTableContainer.visibility = View.VISIBLE
                salesChevron.setImageResource(android.R.drawable.arrow_up_float)
            } else {
                salesTableContainer.visibility = View.GONE
                salesChevron.setImageResource(android.R.drawable.arrow_down_float)
            }
        }
    }

    // ================= LOAD QUOTATIONS =================
    private fun loadQuotations() {

        val tableLayout = findViewById<LinearLayout>(R.id.quotationInnerTable)

        lifecycleScope.launch {
            try {
                allQuotations = RetrofitClient.api.getQuotations()   // ← store into allQuotations
                val quotations = allQuotations

                if (tableLayout.childCount > 1)
                    tableLayout.removeViews(1, tableLayout.childCount - 1)

                var serial = 1
                for (q in quotations) {
                    if (q.actions.lowercase() != "pending") continue
                    val rowBg = if (serial % 2 == 0) R.color.table_row_even else R.color.table_row_odd
                    val row   = buildQuotationRow(
                        serial, q.reqno, q.quoteowner, q.accname,
                        q.opportunity, q.payterm, q.currency,
                        q.total.toString(), q.actionby, q.reqno, rowBg, q
                    )
                    val divider = View(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                        setBackgroundResource(R.color.divider)
                    }
                    tableLayout.addView(row)
                    tableLayout.addView(divider)
                    serial++
                }

                pendingQuotationCount = serial - 1
                findViewById<TextView>(R.id.quotationCount).text = pendingQuotationCount.toString()
                updateTicker(pendingQuotationCount, pendingSalesCount)
                quotationsLoaded = true
                checkAndLoadHistory()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ================= LOAD SALES ORDERS =================
    private fun loadSalesOrders() {

        val tableLayout = findViewById<LinearLayout>(R.id.salesInnerTable)

        lifecycleScope.launch {
            try {
                allSalesOrders = RetrofitClient.api.getSalesOrders()
                val salesOrders = allSalesOrders

                if (tableLayout.childCount > 1)
                    tableLayout.removeViews(1, tableLayout.childCount - 1)

                var serial = 1
                for (s in salesOrders) {
                    if (s.actions.lowercase() != "pending") continue
                    val rowBg = if (serial % 2 == 0) R.color.table_row_even else R.color.table_row_odd
                    val row   = buildSalesRow(
                        serial, s.requestno, s.quoteowner, s.quotename,
                        s.paymentterms, s.total.toString(), s.assignedto,
                        s.actionby, s.requestno, rowBg, s
                    )
                    val divider = View(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                        setBackgroundResource(R.color.divider)
                    }
                    tableLayout.addView(row)
                    tableLayout.addView(divider)
                    serial++
                }

                pendingSalesCount = serial - 1
                findViewById<TextView>(R.id.salesCount).text = pendingSalesCount.toString()
                updateTicker(pendingQuotationCount, pendingSalesCount)
                salesOrdersLoaded = true
                checkAndLoadHistory()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ================= UPDATE TICKER =================
    private fun updateTicker(quotationCount: Int, salesCount: Int) {
        val ticker = findViewById<TextView>(R.id.tickerText)
        val single = "  ●  Quotations Pending: $quotationCount   ·   Sales Orders Pending: $salesCount                                                                                               "
        ticker.text = single + single + single
        ticker.animate().cancel()
        ticker.translationX = 0f
        ticker.post {
            val paint = Paint().apply {
                textSize = ticker.textSize
                typeface = ticker.typeface
            }
            val singleWidth = paint.measureText(single)
            ObjectAnimator.ofFloat(ticker, "translationX", 0f, -singleWidth).apply {
                duration     = 20000
                repeatCount  = ObjectAnimator.INFINITE
                repeatMode   = ObjectAnimator.RESTART
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    // ================= BUILD QUOTATION ROW =================
    private fun buildQuotationRow(
        serial: Int, requestNo: String, quoteOwner: String, accountName: String,
        opportunity: String, paymentTerm: String, currency: String, total: String,
        actionBy: String, firebaseKey: String, rowBgColor: Int, quotation: Quotation
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(rowBgColor)
            tag = "quot_$firebaseKey"
        }

        fun cell(text: String, widthDp: Int, isSerial: Boolean = false) = TextView(this).apply {
            this.text = text
            width     = dpToPx(widthDp)
            setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10))
            setTextColor(if (isSerial) 0xFFC0182E.toInt() else 0xFFFF4444.toInt())
            textSize = 12f
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.rem)
            if (isSerial) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(cell("%02d".format(serial), 60, true))
        row.addView(cell(requestNo, 130))
        row.addView(cell(quoteOwner, 130))
        row.addView(cell(accountName, 140))
        row.addView(cell(opportunity, 160))
        row.addView(cell(paymentTerm, 150))
        row.addView(cell(currency, 110))
        row.addView(cell(total, 110))
        row.addView(cell(actionBy, 120))

        val actionCell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(120), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            gravity = Gravity.CENTER
        }

        fun actionBtn(label: String, bgRes: Int? = null, bgColor: Int? = null) = Button(this).apply {
            text = label
            bgRes?.let { setBackgroundResource(it) }
            bgColor?.let { setBackgroundColor(it) }
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(34)
            ).apply { bottomMargin = dpToPx(5) }
        }

        val viewBtn    = actionBtn("👁  View",   bgColor = 0xFF1565C0.toInt())
        val approveBtn = actionBtn("✔  Approve", bgRes   = R.drawable.approve_btn)
        val rejectBtn  = actionBtn("✖  Reject",  bgRes   = R.drawable.reject_btn).also {
            it.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(34)
            )
        }

        viewBtn.setOnClickListener    { showQuotationDialog(quotation) }
        approveBtn.setOnClickListener { updateStatus("quotations", firebaseKey, requestNo, "Approved") }
        rejectBtn.setOnClickListener  { updateStatus("quotations", firebaseKey, requestNo, "Rejected") }

        actionCell.addView(viewBtn)
        actionCell.addView(approveBtn)
        actionCell.addView(rejectBtn)
        row.addView(actionCell)
        return row
    }

    // ================= BUILD SALES ROW =================
    private fun buildSalesRow(
        serial: Int, requestNo: String, quoteOwner: String, quoteName: String,
        paymentTerms: String, total: String, assignedTo: String,
        actionBy: String, firebaseKey: String, rowBgColor: Int, salesOrder: SalesOrder
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(rowBgColor)
            tag = "sale_$firebaseKey"
        }

        fun cell(text: String, widthDp: Int, isSerial: Boolean = false) = TextView(this).apply {
            this.text = text
            width     = dpToPx(widthDp)
            setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10))
            setTextColor(if (isSerial) 0xFFC0182E.toInt() else 0xFFFF4444.toInt())
            textSize = 12f
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.rem)
            if (isSerial) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(cell("%02d".format(serial), 60, true))
        row.addView(cell(requestNo, 130))
        row.addView(cell(quoteOwner, 130))
        row.addView(cell(quoteName, 150))
        row.addView(cell(paymentTerms, 170))
        row.addView(cell(total, 110))
        row.addView(cell(assignedTo, 130))
        row.addView(cell(actionBy, 120))

        val actionCell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(120), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            gravity = Gravity.CENTER
        }

        fun actionBtn(label: String, bgRes: Int? = null, bgColor: Int? = null) = Button(this).apply {
            text = label
            bgRes?.let { setBackgroundResource(it) }
            bgColor?.let { setBackgroundColor(it) }
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(34)
            ).apply { bottomMargin = dpToPx(5) }
        }

        val viewBtn    = actionBtn("👁  View",   bgColor = 0xFF1565C0.toInt())
        val approveBtn = actionBtn("✔  Approve", bgRes   = R.drawable.approve_btn)
        val rejectBtn  = actionBtn("✖  Reject",  bgRes   = R.drawable.reject_btn).also {
            it.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(34)
            )
        }

        viewBtn.setOnClickListener    { showSalesDialog(salesOrder) }
        approveBtn.setOnClickListener { updateStatus("salesOrders", firebaseKey, requestNo, "Approved") }
        rejectBtn.setOnClickListener  { updateStatus("salesOrders", firebaseKey, requestNo, "Rejected") }

        actionCell.addView(viewBtn)
        actionCell.addView(approveBtn)
        actionCell.addView(rejectBtn)
        row.addView(actionCell)
        return row
    }

    // ================= SHOW QUOTATION DETAIL DIALOG =================
    private fun showQuotationDialog(q: Quotation) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1A2035.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        fun sectionHeader(title: String) = TextView(this).apply {
            text = title; textSize = 13f; setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF3B1F6E.toInt())
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fun detailRow(label: String, value: String) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            addView(TextView(this@MainActivity).apply { text = label; textSize = 12f; setTextColor(0xFF8AB4D4.toInt()); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            addView(TextView(this@MainActivity).apply { text = value; textSize = 12f; setTextColor(0xFFFFFFFF.toInt()); setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        }
        fun divider() = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(0xFF2A3A5A.toInt())
        }

        container.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(0xFF0D2B5E.toInt())
            gravity = Gravity.CENTER_VERTICAL; setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(TextView(this@MainActivity).apply { text = "Quotation Details"; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt()); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            addView(Button(this@MainActivity).apply { text = "✕ Close"; textSize = 11f; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFFD92B4C.toInt()); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(34)); setOnClickListener { dialog.dismiss() } })
        })

        container.addView(sectionHeader("Quotation Details"))
        container.addView(detailRow("Request No",    q.reqno));       container.addView(divider())
        container.addView(detailRow("Customer Name", q.accname));     container.addView(divider())
        container.addView(detailRow("Quote Owner",   q.quoteowner));  container.addView(divider())
        container.addView(detailRow("Opportunity",   q.opportunity)); container.addView(divider())
        container.addView(detailRow("Currency",      q.currency));    container.addView(divider())
        container.addView(detailRow("Action By",     q.actionby))

        container.addView(sectionHeader("Payment Terms"))
        container.addView(detailRow("Payment Term", q.payterm))

        container.addView(sectionHeader("Quotation Items"))
        container.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(0xFF0D2B5E.toInt())
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            fun hdr(t: String, w: Int? = null) = TextView(this@MainActivity).apply { text = t; textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF8AB4D4.toInt()); layoutParams = if (w != null) LinearLayout.LayoutParams(dpToPx(w), LinearLayout.LayoutParams.WRAP_CONTENT) else LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            addView(hdr("Product")); addView(hdr("Qty", 60)); addView(hdr("Unit Price", 90))
        })
        container.addView(divider())

        if (q.items.isEmpty()) {
            container.addView(TextView(this).apply { text = "No items found"; textSize = 12f; setTextColor(0xFF8AB4D4.toInt()); setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12)) })
        } else {
            for (item in q.items) {
                container.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    addView(TextView(this@MainActivity).apply { text = item.product; textSize = 12f; setTextColor(0xFFFFFFFF.toInt()); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                    addView(TextView(this@MainActivity).apply { text = item.qty.toString(); textSize = 12f; setTextColor(0xFFFFFFFF.toInt()); layoutParams = LinearLayout.LayoutParams(dpToPx(60), LinearLayout.LayoutParams.WRAP_CONTENT) })
                    addView(TextView(this@MainActivity).apply { text = "%.2f".format(item.unitprice); textSize = 12f; setTextColor(0xFFFFFFFF.toInt()); layoutParams = LinearLayout.LayoutParams(dpToPx(90), LinearLayout.LayoutParams.WRAP_CONTENT) })
                })
                container.addView(divider())
            }
        }

        container.addView(sectionHeader("Summary"))
        container.addView(detailRow("Total Buying Price",  "${q.currency} ${"%.2f".format(q.buyingprice)}"));  container.addView(divider())
        container.addView(detailRow("Total Selling Price", "${q.currency} ${"%.2f".format(q.sellingprice)}")); container.addView(divider())
        container.addView(detailRow("Margin Rate",         "${"%.1f".format(q.marginrate)}%"))
        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(16)) })

        scrollView.addView(container)
        dialog.setContentView(scrollView)
        dialog.window?.apply {
            setLayout((resources.displayMetrics.widthPixels * 0.93).toInt(), (resources.displayMetrics.heightPixels * 0.80).toInt())
            setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
    }

    // ================= SHOW SALES ORDER DETAIL DIALOG =================
    private fun showSalesDialog(s: SalesOrder) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1A2035.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        fun sectionHeader(title: String) = TextView(this).apply {
            text = title; textSize = 13f; setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF3B1F6E.toInt())
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fun detailRow(label: String, value: String) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            addView(TextView(this@MainActivity).apply { text = label; textSize = 12f; setTextColor(0xFF8AB4D4.toInt()); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            addView(TextView(this@MainActivity).apply { text = value; textSize = 12f; setTextColor(0xFFFFFFFF.toInt()); setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        }
        fun divider() = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(0xFF2A3A5A.toInt())
        }

        container.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(0xFF0D2B5E.toInt())
            gravity = Gravity.CENTER_VERTICAL; setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(TextView(this@MainActivity).apply { text = "Sales Order Details"; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt()); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            addView(Button(this@MainActivity).apply { text = "✕ Close"; textSize = 11f; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFFD92B4C.toInt()); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(34)); setOnClickListener { dialog.dismiss() } })
        })

        container.addView(sectionHeader("Sales Order Details"))
        container.addView(detailRow("Request No",  s.requestno));  container.addView(divider())
        container.addView(detailRow("Quote Name",  s.quotename));  container.addView(divider())
        container.addView(detailRow("Quote Owner", s.quoteowner)); container.addView(divider())
        container.addView(detailRow("Assigned To", s.assignedto)); container.addView(divider())
        container.addView(detailRow("Action By",   s.actionby))

        container.addView(sectionHeader("Payment Terms"))
        container.addView(detailRow("Payment Terms", s.paymentterms))

        container.addView(sectionHeader("Order Items"))
        container.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(0xFF0D2B5E.toInt())
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            fun hdr(t: String, w: Int? = null) = TextView(this@MainActivity).apply { text = t; textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF8AB4D4.toInt()); layoutParams = if (w != null) LinearLayout.LayoutParams(dpToPx(w), LinearLayout.LayoutParams.WRAP_CONTENT) else LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            addView(hdr("Product")); addView(hdr("Qty", 60)); addView(hdr("Unit Price", 90))
        })
        container.addView(divider())

        if (s.items.isEmpty()) {
            container.addView(TextView(this).apply { text = "No items found"; textSize = 12f; setTextColor(0xFF8AB4D4.toInt()); setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12)) })
        } else {
            for (item in s.items) {
                container.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    addView(TextView(this@MainActivity).apply { text = item.product; textSize = 12f; setTextColor(0xFFFFFFFF.toInt()); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                    addView(TextView(this@MainActivity).apply { text = item.qty.toString(); textSize = 12f; setTextColor(0xFFFFFFFF.toInt()); layoutParams = LinearLayout.LayoutParams(dpToPx(60), LinearLayout.LayoutParams.WRAP_CONTENT) })
                    addView(TextView(this@MainActivity).apply { text = "%.2f".format(item.unitprice); textSize = 12f; setTextColor(0xFFFFFFFF.toInt()); layoutParams = LinearLayout.LayoutParams(dpToPx(90), LinearLayout.LayoutParams.WRAP_CONTENT) })
                })
                container.addView(divider())
            }
        }

        container.addView(sectionHeader("Summary"))
        container.addView(detailRow("Total Buying Price",  "${"%.2f".format(s.buyingprice)}"));  container.addView(divider())
        container.addView(detailRow("Total Selling Price", "${"%.2f".format(s.sellingprice)}")); container.addView(divider())
        container.addView(detailRow("Margin Rate",         "${"%.1f".format(s.marginrate)}%"))
        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(16)) })

        scrollView.addView(container)
        dialog.setContentView(scrollView)
        dialog.window?.apply {
            setLayout((resources.displayMetrics.widthPixels * 0.93).toInt(), (resources.displayMetrics.heightPixels * 0.80).toInt())
            setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
    }

    // ================= UPDATE STATUS IN FIREBASE =================
    private fun updateStatus(node: String, firebaseKey: String, requestNo: String, status: String) {
        val sdf  = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        val time = sdf.format(java.util.Date())

        db.child(node).child(firebaseKey).updateChildren(mapOf(
            "status" to status.lowercase(), "actionTime" to time
        ))

        val recordNode = if (status == "Approved") "approvals" else "rejections"
        db.child(recordNode).push().setValue(mapOf(
            "requestNo" to requestNo,
            "type"      to if (node == "quotations") "Quotation" else "SalesOrder",
            "status"    to status,
            "timestamp" to time
        )).addOnSuccessListener {
            Toast.makeText(this, "$requestNo → $status ✔", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        val isQuotation = (node == "quotations")
        val tableLayout = findViewById<LinearLayout>(if (isQuotation) R.id.quotationInnerTable else R.id.salesInnerTable)
        val tagPrefix   = if (isQuotation) "quot_" else "sale_"

        for (i in 0 until tableLayout.childCount) {
            val child = tableLayout.getChildAt(i)
            if (child?.tag == "$tagPrefix$firebaseKey") {
                if (i + 1 < tableLayout.childCount) tableLayout.removeViewAt(i + 1)
                tableLayout.removeViewAt(i)
                break
            }
        }

        if (isQuotation) {
            pendingQuotationCount = (pendingQuotationCount - 1).coerceAtLeast(0)
            findViewById<TextView>(R.id.quotationCount).text = pendingQuotationCount.toString()
        } else {
            pendingSalesCount = (pendingSalesCount - 1).coerceAtLeast(0)
            findViewById<TextView>(R.id.salesCount).text = pendingSalesCount.toString()
        }
        updateTicker(pendingQuotationCount, pendingSalesCount)

        lifecycleScope.launch {
            try {
                if (node == "quotations") RetrofitClient.api.deleteQuotation(firebaseKey)
                else RetrofitClient.api.deleteSalesOrder(firebaseKey)
            } catch (_: Exception) {}
        }
    }

    // ================= DP HELPER =================
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // ================= RINGS =================
    private fun startRingAnimations() {
        fun spin(id: Int, dur: Long, reverse: Boolean = false) {
            findViewById<ImageView>(id)?.let {
                ObjectAnimator.ofFloat(it, "rotation",
                    if (reverse) 360f else 0f,
                    if (reverse) 0f   else 360f
                ).apply {
                    duration     = dur
                    repeatCount  = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    start()
                }
            }
        }
        spin(R.id.rotatingRingOuter, 20000)
        spin(R.id.rotatingRingInner, 12000, reverse = true)
    }

    private fun fetchLocation() {
        val tvLocation = findViewById<TextView>(R.id.tvLocation)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        fusedClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location ->
            if (location != null) {
                try {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    val city = addresses
                        ?.firstOrNull()
                        ?.locality
                        ?: addresses?.firstOrNull()?.subAdminArea
                        ?: "Unknown"
                    tvLocation.text = city
                } catch (e: Exception) {
                    tvLocation.text = "Unavailable"
                }
            } else {
                tvLocation.text = "Unavailable"
            }
        }.addOnFailureListener {
            tvLocation.text = "Unavailable"
        }
    }
    // ================= LIVE DATE =================
    private fun startDateUpdater() {
        val tvDate = findViewById<TextView>(R.id.tvTodayDate)
        val handler = android.os.Handler(mainLooper)
        val formatter = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val runnable = object : Runnable {
            override fun run() {
                tvDate.text = formatter.format(java.util.Date())
                // Re-runs at the next midnight so the date flips exactly on time
                val now = java.util.Calendar.getInstance()
                val next = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                handler.postDelayed(this, next.timeInMillis - now.timeInMillis)
            }
        }
        handler.post(runnable)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocation()
        } else {
            findViewById<TextView>(R.id.tvLocation)?.text = "No permission"
        }
    }
    // ================= SEARCH =================
    private fun setupSearch() {
        val typeSpinner   = findViewById<Spinner>(R.id.searchType)
        val statusSpinner = findViewById<Spinner>(R.id.searchStatus)
        val searchInput   = findViewById<EditText>(R.id.searchRequestNo)

        // Spinner data
        val types    = listOf("All", "Quotation", "Sales Order")
        val statuses = listOf("Pending", "Approved", "Rejected")

        typeSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, types).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        statusSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, statuses).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val listener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) = applySearch()
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        typeSpinner.onItemSelectedListener   = listener
        statusSpinner.onItemSelectedListener = listener

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = applySearch()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun applySearch() {
        val query  = findViewById<EditText>(R.id.searchRequestNo).text.toString().trim().lowercase()
        val type   = findViewById<Spinner>(R.id.searchType).selectedItem?.toString() ?: "All"
        val status = findViewById<Spinner>(R.id.searchStatus).selectedItem?.toString() ?: "Pending"

        val qTable       = findViewById<LinearLayout>(R.id.quotationInnerTable)
        val sTable       = findViewById<LinearLayout>(R.id.salesInnerTable)
        val historySection = findViewById<FrameLayout>(R.id.historySection)
        val historyTable   = findViewById<LinearLayout>(R.id.historyInnerTable)

        if (status == "Pending") {
            // show original tables, hide history
            findViewById<FrameLayout>(R.id.historySection).visibility = View.GONE
            findViewById<View>(R.id.quotationTableContainer.let { R.id.quotationTableContainer })

            val showQ = type == "All" || type == "Quotation"
            val filteredQ = if (showQ) allQuotations.filter {
                it.actions.lowercase() == "pending" &&
                        (query.isEmpty() || it.reqno.lowercase().contains(query))
            } else emptyList()

            if (qTable.childCount > 1) qTable.removeViews(1, qTable.childCount - 1)
            filteredQ.forEachIndexed { index, q ->
                val rowBg = if (index % 2 == 0) R.color.table_row_even else R.color.table_row_odd
                qTable.addView(buildQuotationRow(index + 1, q.reqno, q.quoteowner, q.accname,
                    q.opportunity, q.payterm, q.currency, q.total.toString(),
                    q.actionby, q.reqno, rowBg, q))
                qTable.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundResource(R.color.divider)
                })
            }
            pendingQuotationCount = filteredQ.size
            findViewById<TextView>(R.id.quotationCount).text = filteredQ.size.toString()

            val showS = type == "All" || type == "Sales Order"
            val filteredS = if (showS) allSalesOrders.filter {
                it.actions.lowercase() == "pending" &&
                        (query.isEmpty() || it.requestno.lowercase().contains(query))
            } else emptyList()

            if (sTable.childCount > 1) sTable.removeViews(1, sTable.childCount - 1)
            filteredS.forEachIndexed { index, s ->
                val rowBg = if (index % 2 == 0) R.color.table_row_even else R.color.table_row_odd
                sTable.addView(buildSalesRow(index + 1, s.requestno, s.quoteowner, s.quotename,
                    s.paymentterms, s.total.toString(), s.assignedto,
                    s.actionby, s.requestno, rowBg, s))
                sTable.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundResource(R.color.divider)
                })
            }
            pendingSalesCount = filteredS.size
            findViewById<TextView>(R.id.salesCount).text = filteredS.size.toString()

        } else {
            // hide original tables, show history table
            findViewById<HorizontalScrollView>(R.id.quotationTableContainer).visibility = View.GONE
            findViewById<HorizontalScrollView>(R.id.salesTableContainer).visibility     = View.GONE
            historySection.visibility = View.VISIBLE

            val sourceList = if (status == "Approved") approvedList else rejectedList

            // keep only header row (index 0), remove old data rows
            if (historyTable.childCount > 1) historyTable.removeViews(1, historyTable.childCount - 1)

            val typeFilteredQ = if (type == "All" || type == "Quotation")
                sourceList.filter { it["type"] == "Quotation" } else emptyList()
            val typeFilteredS = if (type == "All" || type == "Sales Order")
                sourceList.filter { it["type"] == "SalesOrder" } else emptyList()

            val combined = (typeFilteredQ + typeFilteredS).filter {
                query.isEmpty() || it["requestNo"]!!.lowercase().contains(query)
            }

            combined.forEachIndexed { index, record ->
                val reqNo     = record["requestNo"]!!
                val recType   = record["type"]!!
                val recStatus = record["status"]!!
                val timestamp = record["timestamp"]!!
                val rowBg     = if (index % 2 == 0) R.color.table_row_even else R.color.table_row_odd

                val matchedQ = allQuotations.find  { it.reqno     == reqNo }
                val matchedS = allSalesOrders.find { it.requestno == reqNo }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundResource(rowBg)
                }

                fun cell(text: String, widthDp: Int, color: Int = 0xFFFF4444.toInt()) =
                    TextView(this).apply {
                        this.text = text
                        width = dpToPx(widthDp)
                        setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10))
                        setTextColor(color)
                        textSize = 12f
                        typeface = ResourcesCompat.getFont(this@MainActivity, R.font.rem)
                        gravity = Gravity.CENTER_VERTICAL
                    }

                val serialColor = 0xFFC0182E.toInt()
                row.addView(cell("%02d".format(index + 1), 60, serialColor).also {
                    it.setTypeface(it.typeface, Typeface.BOLD)
                })
                row.addView(cell(reqNo, 150))
                row.addView(cell(if (recType == "Quotation") "Quotation" else "Sales Order", 100))

                if (matchedQ != null) {
                    row.addView(cell(matchedQ.quoteowner, 130))
                    row.addView(cell(matchedQ.accname,    150))
                    row.addView(cell(matchedQ.currency,   110))
                    row.addView(cell(matchedQ.total.toString(), 110))
                    row.addView(cell(matchedQ.actionby,   120))
                } else if (matchedS != null) {
                    row.addView(cell(matchedS.quoteowner, 130))
                    row.addView(cell(matchedS.quotename,  150))
                    row.addView(cell("—",                 110))
                    row.addView(cell(matchedS.total.toString(), 110))
                    row.addView(cell(matchedS.actionby,   120))
                } else {
                    // Firebase-only fallback if API data not in memory
                    row.addView(cell("—", 130))
                    row.addView(cell("—", 150))
                    row.addView(cell("—", 110))
                    row.addView(cell("—", 110))
                    row.addView(cell("—", 120))
                }

                // Status badge
                val statusColor = if (recStatus == "Approved") 0xFF4CAF50.toInt() else 0xFFE05C5C.toInt()
                row.addView(TextView(this).apply {
                    text = recStatus
                    width = dpToPx(120)
                    setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10))
                    setTextColor(statusColor)
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER_VERTICAL
                })
                row.addView(cell(timestamp, 180, 0xFF8AB4D4.toInt()))

                historyTable.addView(row)
                historyTable.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundResource(R.color.divider)
                })
            }

            findViewById<TextView>(R.id.historyCount).text = combined.size.toString()
            findViewById<TextView>(R.id.historyTitle).text = "$status History"
        }

        // restore table visibility when switching back to Pending
        if (status == "Pending") {
            findViewById<HorizontalScrollView>(R.id.quotationTableContainer).visibility = View.GONE
            findViewById<HorizontalScrollView>(R.id.salesTableContainer).visibility     = View.GONE
        }

        updateTicker(pendingQuotationCount, pendingSalesCount)
    }
    // ================= LOAD APPROVALS / REJECTIONS FROM FIREBASE =================
    private fun loadApprovalHistory() {
        db.child("approvals").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                approvedList = snapshot.children.mapNotNull { child ->
                    val requestNo = child.child("requestNo").getValue(String::class.java) ?: return@mapNotNull null
                    val type      = child.child("type").getValue(String::class.java) ?: ""
                    val timestamp = child.child("timestamp").getValue(String::class.java) ?: ""
                    mapOf("requestNo" to requestNo, "type" to type,
                        "status" to "Approved", "timestamp" to timestamp)
                }
                if (allQuotations.isNotEmpty() || allSalesOrders.isNotEmpty()) applySearch()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        db.child("rejections").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                rejectedList = snapshot.children.mapNotNull { child ->
                    val requestNo = child.child("requestNo").getValue(String::class.java) ?: return@mapNotNull null
                    val type      = child.child("type").getValue(String::class.java) ?: ""
                    val timestamp = child.child("timestamp").getValue(String::class.java) ?: ""
                    mapOf("requestNo" to requestNo, "type" to type,
                        "status" to "Rejected", "timestamp" to timestamp)
                }
                if (allQuotations.isNotEmpty() || allSalesOrders.isNotEmpty()) applySearch()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    // ================= BUILD HISTORY ROW =================
    private fun buildHistoryRow(
        serial: Int, requestNo: String, status: String,
        timestamp: String, rowBgColor: Int
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(rowBgColor)
        }

        fun cell(text: String, widthDp: Int, color: Int = 0xFFFFFFFF.toInt()) =
            TextView(this).apply {
                this.text = text
                width = dpToPx(widthDp)
                setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10))
                setTextColor(color)
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
            }

        val statusColor = if (status == "Approved") 0xFF4CAF50.toInt() else 0xFFE05C5C.toInt()

        row.addView(cell("%02d".format(serial), 60, 0xFFC0182E.toInt()))
        row.addView(cell(requestNo,  200))
        row.addView(cell(status,     130, statusColor))
        row.addView(cell(timestamp,  200))
        return row
    }
    // ================= BUILD QUOTATION HISTORY ROW =================
    private fun buildQuotationHistoryRow(
        serial: Int, q: Quotation, status: String, timestamp: String, rowBgColor: Int
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(rowBgColor)
        }

        fun cell(text: String, widthDp: Int, isSerial: Boolean = false) =
            TextView(this).apply {
                this.text = text
                width = dpToPx(widthDp)
                setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10))
                setTextColor(if (isSerial) 0xFFC0182E.toInt() else 0xFFFF4444.toInt())
                textSize = 12f
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.rem)
                if (isSerial) setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
            }

        row.addView(cell("%02d".format(serial), 60, true))
        row.addView(cell(q.reqno,       130))
        row.addView(cell(q.quoteowner,  130))
        row.addView(cell(q.accname,     140))
        row.addView(cell(q.opportunity, 160))
        row.addView(cell(q.payterm,     150))
        row.addView(cell(q.currency,    110))
        row.addView(cell(q.total.toString(), 110))
        row.addView(cell(q.actionby,    120))

        // Status badge instead of action buttons
        val statusCell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(120), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            gravity = Gravity.CENTER
        }
        statusCell.addView(TextView(this).apply {
            text = status
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(4).toFloat()
                setColor(if (status == "Approved") 0xFF2E7D32.toInt() else 0xFFB71C1C.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(4) }
        })
        statusCell.addView(TextView(this).apply {
            text = timestamp
            textSize = 9f
            setTextColor(0xFF8AB4D4.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        row.addView(statusCell)
        return row
    }
    // ================= BUILD SALES HISTORY ROW =================
    private fun buildSalesHistoryRow(
        serial: Int, s: SalesOrder, status: String, timestamp: String, rowBgColor: Int
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(rowBgColor)
        }

        fun cell(text: String, widthDp: Int, isSerial: Boolean = false) =
            TextView(this).apply {
                this.text = text
                width = dpToPx(widthDp)
                setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10))
                setTextColor(if (isSerial) 0xFFC0182E.toInt() else 0xFFFF4444.toInt())
                textSize = 12f
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.rem)
                if (isSerial) setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
            }

        row.addView(cell("%02d".format(serial), 60, true))
        row.addView(cell(s.requestno,    130))
        row.addView(cell(s.quoteowner,   130))
        row.addView(cell(s.quotename,    150))
        row.addView(cell(s.paymentterms, 170))
        row.addView(cell(s.total.toString(), 110))
        row.addView(cell(s.assignedto,   130))
        row.addView(cell(s.actionby,     120))

        // Status badge instead of action buttons
        val statusCell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(120), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            gravity = Gravity.CENTER
        }
        statusCell.addView(TextView(this).apply {
            text = status
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(4).toFloat()
                setColor(if (status == "Approved") 0xFF2E7D32.toInt() else 0xFFB71C1C.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(4) }
        })
        statusCell.addView(TextView(this).apply {
            text = timestamp
            textSize = 9f
            setTextColor(0xFF8AB4D4.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        row.addView(statusCell)
        return row
    }
}
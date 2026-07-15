package com.blacktube.app.ai

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R

class PromptLibraryActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: PromptLibraryAdapter
    private lateinit var searchEdit: EditText
    private lateinit var chipGroupFilter: LinearLayout
    private lateinit var tvActivePrompt: TextView
    private lateinit var btnClearActive: View
    private lateinit var fabNew: View

    private var allPrompts: List<BuiltInPrompt> = emptyList()
    private var currentFilter: PromptCategory? = null
    private var selectedFilterButton: View? = null

    companion object {
        const val RESULT_PROMPT_SELECTED = "result_prompt_id"

        fun createIntent(context: Context): Intent =
            Intent(context, PromptLibraryActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prompt_library)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_prompt_library)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.prompt_library_title)

        recycler = findViewById(R.id.rv_prompts)
        searchEdit = findViewById(R.id.et_prompt_search)
        chipGroupFilter = findViewById(R.id.chip_group_categories)
        tvActivePrompt = findViewById(R.id.tv_active_prompt_name)
        btnClearActive = findViewById(R.id.btn_clear_active_prompt)
        fabNew = findViewById(R.id.fab_new_prompt)

        setupCategoryChips()
        setupRecycler()
        setupSearch()
        setupActiveBanner()
        setupFab()

        refresh()
    }

    private fun setupCategoryChips() {
        val allChip = createCategoryButton("All", null)
        allChip.isSelected = true
        selectedFilterButton = allChip
        chipGroupFilter.addView(allChip)

        PromptCategory.values().forEach { cat ->
            chipGroupFilter.addView(createCategoryButton("${cat.emoji} ${cat.displayName}", cat))
        }
    }

    private fun createCategoryButton(label: String, category: PromptCategory?): AppCompatButton {
        return AppCompatButton(this).apply {
            text = label
            tag = category
            id = View.generateViewId()
            minWidth = 0
            minHeight = 0
            isAllCaps = false
            setTextColor(resolveThemeColor(android.R.attr.textColorPrimary))
            setBackgroundResource(R.drawable.bg_ai_chip)
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                selectedFilterButton?.isSelected = false
                isSelected = true
                selectedFilterButton = this
                currentFilter = tag as? PromptCategory
                applyFilter()
            }
        }
    }

    private fun resolveThemeColor(attr: Int): Int {
        val colors = theme.obtainStyledAttributes(intArrayOf(attr))
        return try {
            colors.getColor(0, 0)
        } finally {
            colors.recycle()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setupRecycler() {
        adapter = PromptLibraryAdapter(
            onUse = { prompt ->
                PromptLibrary.setActivePrompt(this, prompt.id)
                val data = Intent().putExtra(RESULT_PROMPT_SELECTED, prompt.id)
                setResult(Activity.RESULT_OK, data)
                Toast.makeText(this, getString(R.string.prompt_activated, prompt.title), Toast.LENGTH_SHORT).show()
                refreshActiveBanner()
            },
            onFavorite = { prompt ->
                PromptLibrary.toggleFavorite(this, prompt.id)
                refresh()
            },
            onDuplicate = { prompt ->
                val copy = PromptLibrary.duplicateAsUserPrompt(this, prompt)
                PromptEditorDialog.show(this, copy) {
                    PromptLibrary.saveUserPrompt(this, it)
                    refresh()
                }
            },
            onEdit = { prompt ->
                PromptEditorDialog.show(this, prompt) {
                    PromptLibrary.saveUserPrompt(this, it)
                    refresh()
                }
            },
            onDelete = { prompt ->
                PromptLibrary.deleteUserPrompt(this, prompt.id)
                Toast.makeText(this, getString(R.string.prompt_deleted), Toast.LENGTH_SHORT).show()
                refresh()
            },
            isFavorite = { PromptLibrary.isFavorite(this, it.id) },
            isActive = { PromptLibrary.getActivePromptId(this) == it.id }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
    }

    private fun setupSearch() {
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilter() }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupActiveBanner() {
        refreshActiveBanner()
        btnClearActive.setOnClickListener {
            PromptLibrary.clearActivePrompt(this)
            setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_PROMPT_SELECTED, ""))
            refreshActiveBanner()
            adapter.notifyDataSetChanged()
            Toast.makeText(this, getString(R.string.prompt_library_default_restored), Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshActiveBanner() {
        val active = PromptLibrary.getActivePrompt(this)
        if (active != null) {
            tvActivePrompt.text = getString(R.string.prompt_library_active, active.title)
            btnClearActive.visibility = View.VISIBLE
        } else {
            tvActivePrompt.text = getString(R.string.prompt_library_default)
            btnClearActive.visibility = View.GONE
        }
    }

    private fun setupFab() {
        fabNew.setOnClickListener {
            val blank = PromptLibrary.newUserPrompt()
            PromptEditorDialog.show(this, blank) {
                PromptLibrary.saveUserPrompt(this, it)
                refresh()
            }
        }
    }

    private fun refresh() {
        allPrompts = PromptLibrary.getAllForDisplay(this)
        applyFilter()
        refreshActiveBanner()
    }

    private fun applyFilter() {
        val query = searchEdit.text.toString().trim().lowercase()
        var filtered = allPrompts
        if (currentFilter != null) filtered = filtered.filter { it.category == currentFilter }
        if (query.isNotEmpty()) filtered = filtered.filter {
            it.title.lowercase().contains(query) ||
                    it.description.lowercase().contains(query) ||
                    it.category.displayName.lowercase().contains(query)
        }
        adapter.submitList(filtered, PromptLibrary.getFavoriteIds(this), PromptLibrary.getActivePromptId(this))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

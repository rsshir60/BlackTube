package com.blacktube.app.ai

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.schabi.newpipe.R

object PromptEditorDialog {

    /**
     * Show an editor bottom sheet for creating or modifying a user prompt.
     * [onSave] called with the updated prompt when user taps Save.
     */
    fun show(context: Context, prompt: BuiltInPrompt, onSave: (BuiltInPrompt) -> Unit) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_prompt_editor, null)
        dialog.setContentView(view)

        val etTitle = view.findViewById<EditText>(R.id.et_prompt_editor_title)
        val etDesc = view.findViewById<EditText>(R.id.et_prompt_editor_desc)
        val etBody = view.findViewById<EditText>(R.id.et_prompt_editor_body)
        val spinnerCategory = view.findViewById<AutoCompleteTextView>(R.id.spinner_prompt_category)
        val btnSave = view.findViewById<Button>(R.id.btn_editor_save)
        val btnCancel = view.findViewById<Button>(R.id.btn_editor_cancel)

        // Pre-fill
        etTitle.setText(prompt.title)
        etDesc.setText(prompt.description)
        etBody.setText(prompt.promptText)

        // Category dropdown
        val categories = PromptCategory.values()
        val catNames = categories.map { "${it.emoji} ${it.displayName}" }
        val catAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, catNames)
        spinnerCategory.setAdapter(catAdapter)
        val currentIdx = categories.indexOfFirst { it == prompt.category }.coerceAtLeast(0)
        spinnerCategory.setText(catNames[currentIdx], false)

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val desc = etDesc.text.toString().trim()
            val body = etBody.text.toString().trim()

            if (title.isEmpty()) {
                etTitle.error = "Title required"
                return@setOnClickListener
            }
            if (body.isEmpty()) {
                etBody.error = "Prompt text required"
                return@setOnClickListener
            }

            // Resolve chosen category
            val selectedText = spinnerCategory.text.toString()
            val selectedCategory = categories.firstOrNull { "${it.emoji} ${it.displayName}" == selectedText }
                ?: prompt.category

            val updated = prompt.copy(
                title = title,
                description = desc,
                promptText = body,
                category = selectedCategory,
                isBuiltIn = false
            )
            onSave(updated)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}

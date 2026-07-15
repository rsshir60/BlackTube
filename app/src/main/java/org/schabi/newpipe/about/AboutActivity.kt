package org.schabi.newpipe.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ThemeHelper

class AboutActivity : AppCompatActivity() {

    companion object {
        @JvmStatic
        fun createIntent(context: Context): Intent = Intent(context, AboutActivity::class.java)

        private const val INSTAGRAM_URL = "https://www.instagram.com/rsshir60"
        private const val GITHUB_URL = "https://github.com/rsshir60"
        private const val LINKEDIN_URL =
            "https://www.linkedin.com/in/ranjeet-shirsath-45b97324b?utm_source=share_via&utm_content=profile&utm_medium=member_android"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_blacktube)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val tvVersion = findViewById<TextView>(R.id.tv_version)
        tvVersion.text = "Version " + org.schabi.newpipe.BuildConfig.VERSION_NAME

        findViewById<TextView>(R.id.about_instagram).setOnClickListener { openUrl(INSTAGRAM_URL) }
        findViewById<TextView>(R.id.about_github).setOnClickListener { openUrl(GITHUB_URL) }
        findViewById<TextView>(R.id.about_linkedin).setOnClickListener { openUrl(LINKEDIN_URL) }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

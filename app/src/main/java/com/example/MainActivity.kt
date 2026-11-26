package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.AyeshaAppRoot
import com.example.ui.AyeshaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AyeshaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AyeshaAppRoot(viewModel = viewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.liveSessionManager.notifyAppForegrounded()
        viewModel.refreshPermissions(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.liveSessionManager.notifyAppBackgrounded()
    }
}

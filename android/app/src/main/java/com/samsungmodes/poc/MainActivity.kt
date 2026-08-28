package com.samsungmodes.poc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.samsungmodes.poc.ui.MainViewModel
import com.samsungmodes.poc.ui.SamsungModesScreen
import com.samsungmodes.poc.ui.theme.SamsungModesTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SamsungModesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SamsungModesScreen(viewModel = viewModel)
                }
            }
        }
    }
}

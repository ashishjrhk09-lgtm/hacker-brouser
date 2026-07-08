package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.data.BrowserDatabase
import com.example.data.BrowserRepository
import com.example.ui.BrowserMainScreen
import com.example.ui.BrowserViewModel
import com.example.ui.BrowserViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Instantiate database components
        val database = BrowserDatabase.getDatabase(applicationContext)
        val repository = BrowserRepository(database.browserDao())
        
        // Instantiate the ViewModel using the factory
        val viewModel: BrowserViewModel by viewModels {
            BrowserViewModelFactory(application, repository)
        }

        setContent {
            MyApplicationTheme {
                BrowserMainScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

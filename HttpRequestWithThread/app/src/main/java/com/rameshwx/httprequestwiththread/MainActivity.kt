package com.rameshwx.httprequestwiththread

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.rameshwx.httprequestwiththread.ui.OrderScreen
import com.rameshwx.httprequestwiththread.ui.theme.GroceryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GroceryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    OrderScreen()
                }
            }
        }
    }
}

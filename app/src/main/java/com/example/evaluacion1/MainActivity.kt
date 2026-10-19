package com.example.evaluacion1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.evaluacion1.ui.navigation.AppNavHost
import com.example.evaluacion1.ui.theme.Evaluacion1Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Evaluacion1Theme {
                AppNavHost()
            }
        }
    }
}

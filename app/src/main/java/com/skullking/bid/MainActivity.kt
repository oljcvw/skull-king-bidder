package com.skullking.bid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.skullking.bid.ui.BidScreen
import com.skullking.bid.ui.SkullKingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkullKingTheme {
                BidScreen()
            }
        }
    }
}

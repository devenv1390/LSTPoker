package com.aniapp.pokerdemo.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aniapp.pokerdemo.data.Card

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardRow(cards: List<Card?>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        cards.forEach {
            CardView(it)
        }
    }
}

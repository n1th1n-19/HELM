package dev.helm.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.helm.app.ui.theme.HelmBorder
import dev.helm.app.ui.theme.HelmCard

/** Standard HELM card: dark background with subtle border, 16dp radius. */
@Composable
fun HelmCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.border(1.dp, HelmBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HelmCard),
        content = { content() },
    )
}

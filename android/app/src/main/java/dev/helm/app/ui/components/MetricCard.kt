package dev.helm.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.helm.app.ui.theme.*

@Composable
fun MetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    unit: String = "",
) {
    HelmCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Accent bar at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(accent, RoundedCornerShape(1.dp))
            )
            Text(
                text = label,
                color = HelmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = value,
                    color = HelmTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        color = HelmTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}

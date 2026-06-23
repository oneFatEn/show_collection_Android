package com.jishi.collection.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jishi.collection.ui.theme.JiShiColors

@Composable
fun MessageBar(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(JiShiColors.SearchSurface, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(message, color = JiShiColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text, color = JiShiColors.TextTertiary, fontSize = 14.sp)
    }
}

@Composable
fun QuietIconButton(
    symbol: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
    ) {
        Text(
            text = symbol,
            color = JiShiColors.TextTertiary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
fun TopTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, color = JiShiColors.TextSecondary, fontWeight = FontWeight.Normal)
    }
}

@Composable
fun QuietSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索我的收藏...",
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = {
            Text(
                text = placeholder,
                color = JiShiColors.TextTertiary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
            )
        },
        leadingIcon = {
            Text("⌕", color = JiShiColors.TextTertiary, fontSize = 24.sp)
        },
        singleLine = true,
        textStyle = TextStyle(
            color = JiShiColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
        ),
        shape = RoundedCornerShape(10.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = JiShiColors.SearchSurface,
            unfocusedContainerColor = JiShiColors.SearchSurface,
            disabledContainerColor = JiShiColors.SearchSurface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
fun QuietPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingSymbol: String? = null,
) {
    OutlinedButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = JiShiColors.TextTertiary),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        if (leadingSymbol != null) {
            Text(leadingSymbol, color = JiShiColors.TextTertiary, fontSize = 13.sp)
        }
        Text(text, fontSize = 12.sp)
    }
}

@Composable
fun SectionLabel(
    text: String,
    action: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = JiShiColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

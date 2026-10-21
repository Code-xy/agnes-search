package com.seagull.agnesearch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagull.agnesearch.ui.theme.brand

@Composable
fun FieldLabel(text: String, hint: String = "") {
    val br = brand
    Column(modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) {
        Text(text, color = br.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (hint.isNotEmpty()) {
            Text(hint, color = br.textSecondary, fontSize = 11.sp)
        }
    }
}

/** 统一风格的单行输入框（自绘，避免 material3 TextField 的版本差异）。 */
@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    password: Boolean = false,
    number: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    trailing: @Composable (() -> Unit)? = null,
) {
    val br = brand
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(br.card, shape)
            .border(1.dp, br.cardBorder, shape)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f).padding(vertical = 11.dp)) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, color = br.textSecondary, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else maxLines,
                textStyle = TextStyle(color = br.textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(br.accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when {
                        password -> KeyboardType.Password
                        number -> KeyboardType.Number
                        else -> KeyboardType.Text
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.width(34.dp)) {
                Icon(Icons.Filled.Clear, contentDescription = "清空", tint = br.textSecondary)
            }
        }
        trailing?.invoke()
    }
}

/** 可选中的一行设置项。 */
@Composable
fun SwitchRow(label: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val br = brand
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = br.textPrimary, fontSize = 14.sp)
            if (desc.isNotEmpty()) Text(desc, color = br.textSecondary, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(26.dp)
                .background(if (checked) br.accent else br.divider, RoundedCornerShape(13.dp))
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(20.dp)
                    .background(br.card, RoundedCornerShape(10.dp)),
            )
        }
    }
}

/** 带数值显示的滑杆行。 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    display: String,
    onChange: (Float) -> Unit,
) {
    val br = brand
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = br.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(display, color = br.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
    }
}

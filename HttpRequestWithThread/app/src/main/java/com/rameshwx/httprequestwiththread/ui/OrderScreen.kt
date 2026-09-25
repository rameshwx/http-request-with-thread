package com.rameshwx.httprequestwiththread.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rameshwx.httprequestwiththread.data.model.DeliveryRider
import com.rameshwx.httprequestwiththread.data.model.GroceryItem
import java.text.NumberFormat
import java.util.Locale

private val Ink = Color(0xFF1D3027)
private val Muted = Color(0xFF68776E)
private val GreenTint = Color(0xFFE5F1E9)
private val Border = Color(0xFFE5E9E4)

@Composable
fun OrderScreen(viewModel: OrderViewModel = hiltViewModel()) {
    val state by viewModel.state.observeAsState(OrderUiState())
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F7F2))
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = topInset + 14.dp, bottom = bottomInset + 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Header(state.activeStep, state.isBusy)

        if (state.isLoading && state.catalog.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Preparing your shop", fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("Reading catalog, then checking available riders", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        state.errorMessage?.let { message ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFEB)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.orderOutcomeUnknown) "Order status needs checking" else "We hit a snag", color = Color(0xFF8B3027), fontWeight = FontWeight.Bold)
                    Text(message, color = Color(0xFF673C36), style = MaterialTheme.typography.bodyMedium)
                    if (!state.isLoading && !state.ridersLoaded) {
                        TextButton(onClick = viewModel::retryInitialLoad) { Text("Try loading again") }
                    }
                }
            }
        }

        SectionHeading("01", "Choose your groceries", "Pick one item for this order")
        if (state.catalog.isEmpty() && !state.isLoading) {
            EmptyCard("The grocery catalog is empty right now.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.catalog.forEach { item ->
                    ProductCard(
                        item = item,
                        image = state.productImages[item.id]
                            ?.takeIf { loaded -> loaded.imageUrl == item.imageUrl }
                            ?.bitmap,
                        selected = item.id == state.selectedItemId,
                        enabled = !state.isBusy,
                        onClick = { viewModel.selectItem(item) }
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Border)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Quantity", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("1–100 items", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    QuantityButton("−", enabled = state.quantity > 1 && !state.isBusy) { viewModel.setQuantity(state.quantity - 1) }
                    Text(state.quantity.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                    QuantityButton("+", enabled = state.quantity < 100 && !state.isBusy) { viewModel.setQuantity(state.quantity + 1) }
                }
            }
        }

        SectionHeading("02", "Delivery team", "Available riders in the network")
        if (state.riders.isEmpty()) {
            EmptyCard(if (state.ridersLoaded) "No riders are available at the moment." else "Rider availability appears after the catalog loads.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                state.riders.take(4).forEach { RiderCard(it) }
                if (state.riders.size > 4) Text("+ ${state.riders.size - 4} more riders available", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text("Riders are shown for information. The delivery API assigns a rider automatically.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }

        SectionHeading("03", "Your quote", "A fresh quote is required before checkout")
        state.quote?.let { quote ->
            Card(colors = CardDefaults.cardColors(containerColor = GreenTint), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("QUOTE READY", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${quote.quantity} × ${quote.itemName}", color = Ink)
                        Text(formatAmount(quote.totalCents), color = Ink, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = Color(0xFFCADBCF))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(formatAmount(quote.totalCents), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    }
                    Text(
                        quote.expiresAt?.let { "Quote expires at $it · single use" } ?: "Based on API price_cents · single use, expires in 10 minutes",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } ?: Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Text("Your total will appear here after you request a quote.", Modifier.padding(17.dp), color = Muted)
        }
        Button(
            onClick = viewModel::getQuote,
            enabled = state.canGetQuote,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (state.isBusy && state.activeStep.contains("quote", ignoreCase = true)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(10.dp))
            }
            Text(if (state.quote == null) "Get quote" else "Refresh quote", fontWeight = FontWeight.SemiBold)
        }

        SectionHeading("04", "Delivery details", "Tell us where to bring your order")
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Border)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.customerName,
                    onValueChange = viewModel::setCustomerName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Customer name") },
                    singleLine = true,
                    isError = state.customerNameError != null,
                    supportingText = {
                        Text(state.customerNameError ?: "Enter 2–100 characters. ${state.customerName.length}/100")
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                OutlinedTextField(
                    value = state.deliveryAddress,
                    onValueChange = viewModel::setDeliveryAddress,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Delivery address") },
                    minLines = 2,
                    isError = state.deliveryAddressError != null,
                    supportingText = {
                        Text(state.deliveryAddressError ?: "Enter 8–300 characters after trimming. One word is fine. ${state.deliveryAddress.length}/300")
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                OutlinedTextField(
                    value = state.customerNote,
                    onValueChange = viewModel::setCustomerNote,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Delivery note (optional)") },
                    minLines = 2,
                    isError = state.customerNoteError != null,
                    supportingText = {
                        Text(state.customerNoteError ?: "${state.customerNote.length}/300")
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
        }

        if (state.validationAttempted && !state.canPlaceOrder && state.quote != null) {
            Text(
                "Please correct the highlighted fields before the order is sent.",
                color = Color(0xFF8B3027),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = viewModel::placeOrder,
            enabled = state.canAttemptPlaceOrder,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF173F2E))
        ) {
            if (state.isBusy && state.activeStep.contains("order", ignoreCase = true)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(10.dp))
            }
            Text(if (state.orderOutcomeUnknown) "Check order status before retrying" else "Place order", fontWeight = FontWeight.SemiBold)
        }

        val placedOrder = state.order
        if (placedOrder != null) {
            Card(colors = CardDefaults.cardColors(containerColor = GreenTint), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Order confirmed ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Reference: ${placedOrder.orderId}", color = Ink)
                    Text("Status: ${placedOrder.status}", color = Muted)
                }
            }
        }

        Text("Four API calls · one after another · raw Java threads", Modifier.align(Alignment.CenterHorizontally), color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Header(step: String, busy: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("GROVE & GATHER", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(4.dp))
            Text("Good food, brought home.", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("A neighborhood grocery run, made simple.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Surface(color = GreenTint, shape = CircleShape) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { Text("🧺", fontSize = 27.sp) }
        }
    }
    Surface(color = Color.White, shape = RoundedCornerShape(100.dp), border = BorderStroke(1.dp, Border)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp)
            else Box(Modifier.size(8.dp).background(Color(0xFF3C9C69), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(step, color = Muted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Text("THREAD", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionHeading(number: String, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = GreenTint, shape = CircleShape) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProductCard(item: GroceryItem, image: android.graphics.Bitmap?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) GreenTint else Color.White),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Border)
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFF2F4EC), shape = RoundedCornerShape(13.dp)) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    if (image != null) {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("🥕", fontSize = 25.sp)
                    }
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = Ink, fontWeight = FontWeight.SemiBold)
                Text(item.description, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatAmount(item.priceCents), color = Ink, fontWeight = FontWeight.Bold)
                Text(if (selected) "SELECTED" else "EACH", color = if (selected) MaterialTheme.colorScheme.primary else Muted, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun QuantityButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(36.dp).clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) GreenTint else Color(0xFFF0F1EE),
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, color = if (enabled) MaterialTheme.colorScheme.primary else Muted, fontWeight = FontWeight.Bold, fontSize = 21.sp) }
    }
}

@Composable
private fun RiderCard(rider: DeliveryRider) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Border)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = GreenTint, shape = CircleShape) { Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text("🚲", fontSize = 19.sp) } }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rider.displayName, color = Ink, fontWeight = FontWeight.SemiBold)
                Text("${rider.vehicleType} · ${rider.serviceArea}", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text("AVAILABLE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Text(message, Modifier.fillMaxWidth().padding(16.dp), color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatAmount(cents: Int): String = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-LK")).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}.format(cents / 100.0)

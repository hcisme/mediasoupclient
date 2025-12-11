package io.github.hcisme.mediasoupclient.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * 大众化 通用的 dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dialog(
    modifier: Modifier = Modifier,
    visible: Boolean = false,
    properties: DialogProperties = DialogProperties(),
    boxHorizontalPadding: Dp = 16.dp,
    actionButtonHeight: Dp = 48.dp,
    confirmButtonText: String? = null,
    cancelButtonText: String? = null,
    onConfirm: (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    if (visible) {
        BasicAlertDialog(
            modifier = modifier,
            properties = properties,
            onDismissRequest = {
                onDismissRequest?.invoke()
            }
        ) {
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = boxHorizontalPadding)
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            space = 8.dp,
                            alignment = Alignment.CenterHorizontally
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        icon?.invoke()
                        title?.invoke()
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = boxHorizontalPadding, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                        content = content
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (cancelButtonText != null || confirmButtonText != null) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.6.dp)
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(actionButtonHeight)
                        ) {
                            if (cancelButtonText != null) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable {
                                            onDismissRequest?.invoke()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(cancelButtonText)
                                }
                            }

                            if (cancelButtonText != null && confirmButtonText != null) {
                                Spacer(
                                    modifier = Modifier
                                        .width(0.6.dp)
                                        .fillMaxHeight()
                                        .background(
                                            MaterialTheme.colorScheme.onBackground.copy(
                                                alpha = 0.4f
                                            )
                                        )
                                )
                            }

                            if (confirmButtonText != null) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable {
                                            onConfirm?.invoke()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(confirmButtonText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 原生风格的 dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeDialog(
    modifier: Modifier = Modifier,
    visible: Boolean = false,
    properties: DialogProperties = DialogProperties(),
    confirmButtonText: String? = null,
    cancelButtonText: String? = null,
    onConfirm: (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (visible) {
        BasicAlertDialog(
            modifier = modifier,
            properties = properties,
            onDismissRequest = {
                onDismissRequest?.invoke()
            }
        ) {
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.small,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 8.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        icon?.invoke()
                        title?.invoke()

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    content()

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (cancelButtonText != null) {
                            TextButton(
                                onClick = {
                                    onDismissRequest?.invoke()
                                },
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(cancelButtonText)
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        if (confirmButtonText != null) {
                            TextButton(
                                onClick = {
                                    onConfirm?.invoke()
                                },
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(confirmButtonText)
                            }
                        }
                    }
                }
            }
        }
    }
}

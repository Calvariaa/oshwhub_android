package io.github.Calvariaa.oshwhub.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.Calvariaa.oshwhub.data.model.Comment
import io.github.Calvariaa.oshwhub.util.Html

/** 评论输入框（工程 / 文章 / 帖子共用） */
@Composable
fun CommentComposer(
    sending: Boolean,
    onSend: (String, () -> Unit) -> Unit,
    placeholder: String = "友善评论，理性交流",
) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { onSend(text) { text = "" } },
            enabled = !sending && text.isNotBlank(),
            shape = RoundedCornerShape(50),
        ) {
            if (sending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Text("发送")
        }
    }
}

/** 单条评论 + 楼中楼回复 */
@Composable
fun CommentItem(comment: Comment, onUser: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable { onUser(comment.creator.uuid) }) {
                Avatar(comment.creator.avatar, size = 28)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    comment.creator.nickname.ifBlank { comment.creator.username },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    comment.created_at,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            Html.toPlain(comment.content),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 36.dp),
        )
        if (comment.replies.isNotEmpty()) {
            Column(Modifier.padding(start = 36.dp, top = 6.dp)) {
                comment.replies.forEach { reply ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Avatar(reply.creator.avatar, size = 22)
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                reply.creator.nickname.ifBlank { reply.creator.username },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(Html.toPlain(reply.content), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

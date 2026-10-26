package bot.afiliados.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import bot.afiliados.domain.EstadoFila
import bot.afiliados.domain.SituacaoPreco

private val Claro = lightColorScheme(primary = Color(0xFF1F5FD1), secondary = Color(0xFF00796B), tertiary = Color(0xFFB45309))
private val Escuro = darkColorScheme(primary = Color(0xFF9DBBFF), secondary = Color(0xFF6FD3C3), tertiary = Color(0xFFF5B66B))

@Composable
fun TemaAfiliados(conteudo: @Composable () -> Unit) {
    val escuro = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val cores: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (escuro) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        escuro -> Escuro
        else -> Claro
    }
    MaterialTheme(colorScheme = cores, content = conteudo)
}

val espaco = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

@Composable
fun Secao(titulo: String, modifier: Modifier = Modifier, acao: (@Composable () -> Unit)? = null, conteudo: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                acao?.invoke()
            }
            conteudo()
        }
    }
}

@Composable
fun EstadoVazio(titulo: String, texto: String, modifier: Modifier = Modifier, acao: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.outline)
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        acao?.invoke()
    }
}

@Composable
fun Aviso(texto: String, erro: Boolean = false) {
    val c = if (erro) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val t = if (erro) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = c)) {
        Text(texto, Modifier.padding(12.dp), color = t, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Selo(texto: String, cor: Color) {
    AssistChip(onClick = {}, label = { Text(texto, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(labelColor = cor), modifier = Modifier.height(28.dp))
}

@Composable
fun corPreco(s: SituacaoPreco): Color = when (s) {
    SituacaoPreco.VERIFICADO, SituacaoPreco.VERIFICADO_MANUAL -> MaterialTheme.colorScheme.secondary
    SituacaoPreco.PRODUTO_INDISPONIVEL, SituacaoPreco.LINK_INVALIDO -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}

@Composable
fun corFila(e: EstadoFila): Color = when (e) {
    EstadoFila.PUBLICADO_CONFIRMADO, EstadoFila.PUBLICADO_INFORMADO -> MaterialTheme.colorScheme.secondary
    EstadoFila.FALHA, EstadoFila.ENVIO_NAO_CONFIRMADO -> MaterialTheme.colorScheme.error
    EstadoFila.CANCELADO -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun Confirmar(titulo: String, texto: String, confirmar: String = "Confirmar", destrutivo: Boolean = false, aoConfirmar: () -> Unit, aoFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(onClick = { aoConfirmar(); aoFechar() }) {
                Text(confirmar, color = if (destrutivo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Voltar") } },
    )
}

@Composable
fun Campo(valor: String, aoMudar: (String) -> Unit, rotulo: String, modifier: Modifier = Modifier, ajuda: String? = null, numero: Boolean = false, linhas: Int = 1, erro: String? = null, segredo: Boolean = false) {
    OutlinedTextField(
        value = valor, onValueChange = aoMudar, label = { Text(rotulo) }, modifier = modifier.fillMaxWidth(),
        singleLine = linhas == 1, minLines = linhas, isError = erro != null,
        supportingText = (erro ?: ajuda)?.let { { Text(it) } },
        keyboardOptions = if (numero) KeyboardOptions(keyboardType = KeyboardType.Decimal) else if (segredo) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        visualTransformation = if (segredo) PasswordVisualTransformation() else VisualTransformation.None,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Escolha(rotulo: String, opcoes: List<T>, selecionado: T?, nome: (T) -> String, aoEscolher: (T) -> Unit, modifier: Modifier = Modifier) {
    var aberto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = aberto, onExpandedChange = { aberto = it }, modifier = modifier) {
        OutlinedTextField(
            value = selecionado?.let(nome) ?: "", onValueChange = {}, readOnly = true, label = { Text(rotulo) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(aberto) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            opcoes.forEach { o -> DropdownMenuItem(text = { Text(nome(o)) }, onClick = { aoEscolher(o); aberto = false }) }
        }
    }
}

@Composable
fun Rodape() = Spacer(Modifier.height(96.dp))

fun Context.avisar(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

fun Context.copiar(rotulo: String, texto: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(rotulo, texto))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) avisar("Copiado")
}

fun Context.abrirLink(url: String) {
    if (!url.startsWith("https://")) return avisar("Link não é HTTPS")
    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: ActivityNotFoundException) { avisar("Nenhum app abre este link") }
}

/** Abre o compartilhamento do Android. Abrir NÃO significa que foi enviado. */
fun Context.compartilhar(texto: String, direto: String? = null) {
    val base = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
    if (direto != null) {
        try {
            startActivity(Intent(base).setPackage(direto))
            return
        } catch (_: ActivityNotFoundException) { /* cai no seletor */ }
    }
    startActivity(Intent.createChooser(base, "Publicar oferta"))
}

package bot.afiliados.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bot.afiliados.R

/*
 * Mesmo visual do app BattleHub ("Visual 1 · Minimalista"), só o estilo:
 * fundo quase preto, cartões cinza com linha fina, dourado champanhe só em
 * destaque (regra 60-30-10) e letra única Figtree, títulos em 700.
 */
object Cores {
    // Tema Preto
    val fundo = Color(0xFF0A0A0B)
    val fundo2 = Color(0xFF0D0D0F)
    val cartao = Color(0xFF111113)
    val cartao2 = Color(0xFF161619)
    val cartao3 = Color(0xFF1C1C20)
    val linha = Color(0xFF1E1E22)
    val linha2 = Color(0xFF2A2A30)
    val texto = Color(0xFFECECEE)
    val texto2 = Color(0xFFB9B9C0)
    val cinza = Color(0xFF8A8A93)
    val apagado = Color(0xFF5E5E66)
    val dourado = Color(0xFFC8AC78)
    val douradoClaro = Color(0xFFDCC596)
    val douradoFundo = Color(0xFF6B5A3A)
    val tinta = Color(0xFF141210)
    val verde = Color(0xFF34D399)
    val vermelho = Color(0xFFFB7185)

    // Tema Branco
    val bFundo = Color(0xFFF6F4EF)
    val bCartao = Color(0xFFFFFFFF)
    val bCartao2 = Color(0xFFF4F2EC)
    val bCartao3 = Color(0xFFEBE8E0)
    val bLinha = Color(0xFFE4E0D7)
    val bLinha2 = Color(0xFFD6D1C6)
    val bTexto = Color(0xFF17171A)
    val bTexto2 = Color(0xFF3D3B36)
    val bCinza = Color(0xFF6F6C65)
    val bApagado = Color(0xFFA29E95)
    val bDourado = Color(0xFF9A7527)
    val bDouradoFundo = Color(0xFFC9A24D)
    val bTinta = Color(0xFF17130B)
    val bVerde = Color(0xFF0F9F6E)
    val bVermelho = Color(0xFFE11D48)
}

private val Preto = darkColorScheme(
    primary = Cores.dourado, onPrimary = Cores.tinta,
    primaryContainer = Cores.dourado, onPrimaryContainer = Cores.tinta,
    secondary = Cores.verde, onSecondary = Cores.fundo,
    secondaryContainer = Cores.cartao2, onSecondaryContainer = Cores.texto,
    tertiary = Cores.douradoClaro, onTertiary = Cores.tinta,
    tertiaryContainer = Color(0xFF1F1A12), onTertiaryContainer = Cores.douradoClaro,
    error = Cores.vermelho, onError = Cores.fundo,
    errorContainer = Color(0xFF2A1219), onErrorContainer = Color(0xFFFDA4AF),
    background = Cores.fundo, onBackground = Cores.texto,
    surface = Cores.fundo, onSurface = Cores.texto,
    surfaceVariant = Cores.cartao2, onSurfaceVariant = Cores.cinza,
    surfaceContainerLowest = Cores.fundo2, surfaceContainerLow = Cores.cartao,
    surfaceContainer = Cores.cartao2, surfaceContainerHigh = Cores.cartao3, surfaceContainerHighest = Cores.cartao3,
    surfaceBright = Cores.cartao3, surfaceDim = Cores.fundo,
    inverseSurface = Cores.texto, inverseOnSurface = Cores.fundo, inversePrimary = Cores.douradoFundo,
    outline = Cores.linha2, outlineVariant = Cores.linha, scrim = Color.Black,
)

private val Branco = lightColorScheme(
    primary = Cores.bDourado, onPrimary = Color.White,
    primaryContainer = Cores.bDouradoFundo, onPrimaryContainer = Cores.bTinta,
    secondary = Cores.bVerde, onSecondary = Color.White,
    secondaryContainer = Cores.bCartao2, onSecondaryContainer = Cores.bTexto,
    tertiary = Color(0xFF7D5F1F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5EBD3), onTertiaryContainer = Color(0xFF5C4614),
    error = Cores.bVermelho, onError = Color.White,
    errorContainer = Color(0xFFFDE4E9), onErrorContainer = Color(0xFF9F1239),
    background = Cores.bFundo, onBackground = Cores.bTexto,
    surface = Cores.bFundo, onSurface = Cores.bTexto,
    surfaceVariant = Cores.bCartao2, onSurfaceVariant = Cores.bCinza,
    surfaceContainerLowest = Cores.bCartao, surfaceContainerLow = Cores.bCartao,
    surfaceContainer = Cores.bCartao2, surfaceContainerHigh = Cores.bCartao, surfaceContainerHighest = Cores.bCartao3,
    surfaceBright = Cores.bCartao, surfaceDim = Cores.bCartao3,
    inverseSurface = Cores.bTexto, inverseOnSurface = Cores.bFundo, inversePrimary = Cores.dourado,
    outline = Cores.bLinha2, outlineVariant = Cores.bLinha, scrim = Color.Black,
)

val Figtree = FontFamily(
    Font(R.font.figtree_400, FontWeight.Normal),
    Font(R.font.figtree_500, FontWeight.Medium),
    Font(R.font.figtree_600, FontWeight.SemiBold),
    Font(R.font.figtree_700, FontWeight.Bold),
)

private fun TextStyle.f(peso: FontWeight? = null) = copy(fontFamily = Figtree, fontWeight = peso ?: fontWeight)

private val Letras = Typography().run {
    copy(
        displayLarge = displayLarge.f(FontWeight.Bold), displayMedium = displayMedium.f(FontWeight.Bold), displaySmall = displaySmall.f(FontWeight.Bold),
        headlineLarge = headlineLarge.f(FontWeight.Bold), headlineMedium = headlineMedium.f(FontWeight.Bold), headlineSmall = headlineSmall.f(FontWeight.Bold),
        titleLarge = titleLarge.f(FontWeight.Bold), titleMedium = titleMedium.f(FontWeight.Bold), titleSmall = titleSmall.f(FontWeight.SemiBold),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(FontWeight.SemiBold), labelMedium = labelMedium.f(FontWeight.SemiBold), labelSmall = labelSmall.f(FontWeight.Medium),
    )
}

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(20.dp),
)

/** "sistema", "preto" ou "branco" (igual a Perfil → Aparência da BattleHub). */
@Composable
fun temaEscuro(escolha: String) = when (escolha) {
    "preto" -> true
    "branco" -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun TemaAfiliados(escolha: String = "sistema", conteudo: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (temaEscuro(escolha)) Preto else Branco, typography = Letras, shapes = Formas, content = conteudo)
}

/** Cartão padrão: cinza escuro, linha fina, sem sombra nem degradê. */
@Composable
fun Cartao(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    content: @Composable ColumnScope.() -> Unit,
) = OutlinedCard(modifier, shape = MaterialTheme.shapes.medium, colors = colors, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), content = content)

/** Filtro ligado fica claro sobre o fundo escuro, como as abas da BattleHub. */
@Composable
fun Chip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) = FilterChip(
    selected = selected, onClick = onClick, label = label, modifier = modifier,
    colors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent, labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.onSurface, selectedLabelColor = MaterialTheme.colorScheme.surface,
    ),
    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = MaterialTheme.colorScheme.outlineVariant, selectedBorderColor = Color.Transparent),
)

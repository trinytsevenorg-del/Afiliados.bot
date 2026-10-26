package bot.afiliados.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import bot.afiliados.AfiliadosApp
import bot.afiliados.MainActivity

private data class Aba(val rota: String, val nome: String, val icone: ImageVector)

private val abas = listOf(
    Aba("inicio", "Início", Icons.Outlined.Home),
    Aba("produtos", "Produtos", Icons.Outlined.Inventory2),
    Aba("nichos", "Nichos", Icons.Outlined.Category),
    Aba("fila", "Fila", Icons.Outlined.Schedule),
    Aba("ajustes", "Ajustes", Icons.Outlined.Settings),
)

object Rotas {
    fun novoProduto(link: String? = null) = "novo-produto" + (link?.let { "?link=" + Uri.encode(it) } ?: "")
    fun produto(id: Long) = "produto/$id"
    fun editar(id: Long) = "produto/$id/editar"
    fun oferta(produtoId: Long? = null) = "oferta" + (produtoId?.let { "?produto=$it" } ?: "")
    fun item(id: Long) = "fila/$id"
}

fun NavHostController.irPara(rota: String) = navigate(rota) { launchSingleTop = true }

@Composable
fun AppRaiz(app: AfiliadosApp, atividade: MainActivity, desbloqueado: Boolean, aoDesbloquear: () -> Unit) {
    if (!desbloqueado) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Lock, null)
            Text("App bloqueado", Modifier.padding(16.dp))
            Button(onClick = aoDesbloquear) { Text("Desbloquear") }
        }
        return
    }
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rotaAtual = entrada?.destination?.route

    val link by atividade.linkRecebido
    LaunchedEffect(link) {
        link?.let { nav.irPara(Rotas.novoProduto(it)); atividade.linkRecebido.value = null }
    }
    val print by atividade.printRecebido
    LaunchedEffect(print) {
        if (print) { nav.irPara(Rotas.novoProduto()); atividade.printRecebido.value = false }
    }
    val item by atividade.itemAberto
    LaunchedEffect(item) {
        item?.let { nav.irPara(Rotas.item(it)); atividade.itemAberto.value = null }
    }

    Scaffold(
        bottomBar = {
            if (abas.any { it.rota == rotaAtual }) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                        abas.forEach { a ->
                            NavigationBarItem(
                                selected = rotaAtual == a.rota,
                                onClick = { nav.navigate(a.rota) { popUpTo("inicio") { saveState = true }; launchSingleTop = true; restoreState = true } },
                                icon = { Icon(a.icone, null) },
                                label = { Text(a.nome) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(bottom = pad.calculateBottomPadding())) {
            NavHost(nav, startDestination = "inicio") {
                composable("inicio") { InicioTela(app, nav) }
                composable("produtos") { ProdutosTela(app, nav) }
                composable("nichos") { NichosTela(app, nav) }
                composable("fila") { FilaTela(app, nav) }
                composable("ajustes") { AjustesTela(app, nav) }
                composable("comissoes") { ComissoesTela(app, nav) }
                composable("lote") { LoteTela(app, nav) }
                composable("novo-produto?link={link}", arguments = listOf(navArgument("link") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                    ProdutoFormTela(app, nav, produtoId = null, linkInicial = it.arguments?.getString("link"))
                }
                composable("produto/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    ProdutoDetalheTela(app, nav, it.arguments!!.getLong("id"))
                }
                composable("produto/{id}/editar", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    ProdutoFormTela(app, nav, produtoId = it.arguments!!.getLong("id"), linkInicial = null)
                }
                composable("oferta?produto={produto}", arguments = listOf(navArgument("produto") { type = NavType.LongType; defaultValue = -1L })) {
                    OfertaTela(app, nav, it.arguments!!.getLong("produto").takeIf { id -> id > 0 })
                }
                composable("fila/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    ItemFilaTela(app, nav, it.arguments!!.getLong("id"))
                }
            }
        }
    }
}

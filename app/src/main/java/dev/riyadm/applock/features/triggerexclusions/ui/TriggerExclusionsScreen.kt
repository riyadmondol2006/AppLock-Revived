package dev.riyadm.applock.features.triggerexclusions.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.riyadm.applock.features.applist.domain.AppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerExclusionsScreen(
    navController: NavController,
    viewModel: TriggerExclusionsViewModel = viewModel()
) {
    val context = LocalContext.current

    val allApps by viewModel.allApps.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val excludedApps by viewModel.excludedApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val manualPackageName by viewModel.manualPackageName.collectAsState()

    val showManualAddDialog = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadApps(context)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SearchTopBar(
                title = "Trigger Exclusions",
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onBack = { navController.navigateUp() },
                onAdd = { showManualAddDialog.value = true }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "Select apps that will NOT trigger locks when switching to locked apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                val excludedNotInList = excludedApps.filter { pkg ->
                    allApps.none { it.packageName == pkg }
                }

                if (excludedNotInList.isNotEmpty()) {
                    item {
                        Text(
                            text = "Manually Added Packages",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(excludedNotInList) { packageName ->
                        ManualPackageItem(
                            packageName = packageName,
                            onToggle = { viewModel.toggleAppExclusion(context, packageName) }
                        )
                    }
                    item {
                        Text(
                            text = "Installed Apps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                }

                items(filteredApps) { app ->
                    AppExclusionItem(
                        app = app,
                        isExcluded = excludedApps.contains(app.packageName),
                        onToggle = { viewModel.toggleAppExclusion(context, app.packageName) }
                    )
                }
            }
        }
    }

    if (showManualAddDialog.value) {
        AlertDialog(
            onDismissRequest = { showManualAddDialog.value = false },
            title = { Text("Add Package Manually") },
            text = {
                Column {
                    Text(
                        text = "Enter the package name of the app you want to exclude:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = manualPackageName,
                        onValueChange = viewModel::updateManualPackageName,
                        label = { Text("Package Name") },
                        placeholder = { Text("com.example.app") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addManualPackage(context, manualPackageName)
                        showManualAddDialog.value = false
                    },
                    enabled = manualPackageName.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showManualAddDialog.value = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "Add package manually")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search apps or package names...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp)
                )
            }
        }
    }
}

@Composable
private fun AppExclusionItem(app: AppInfo, isExcluded: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = isExcluded, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun ManualPackageItem(packageName: String, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Text("📦", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Manually added package",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = true, onCheckedChange = { onToggle() })
        }
    }
}

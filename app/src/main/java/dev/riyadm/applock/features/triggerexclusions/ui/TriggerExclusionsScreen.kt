package dev.riyadm.applock.features.triggerexclusions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.riyadm.applock.R
import dev.riyadm.applock.core.ui.MorphingIconBadge
import dev.riyadm.applock.features.applist.ui.PackageToggleScreen

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TriggerExclusionsScreen(
    navController: NavController,
    viewModel: TriggerExclusionsViewModel = viewModel()
) {
    val checked by viewModel.checked.collectAsStateWithLifecycle()

    PackageToggleScreen(
        title = stringResource(R.string.trigger_exclusions_title),
        subtitle = pluralStringResource(R.plurals.trigger_exclusions_count, checked.size, checked.size),
        viewModel = viewModel,
        checkedSectionTitle = stringResource(R.string.trigger_exclusions_section_excluded),
        checkedThumbIcon = Icons.Rounded.Block,
        addPackageDescription = stringResource(R.string.trigger_exclusions_add_desc),
        onBack = { navController.navigateUp() },
        onToggle = viewModel::setExcluded,
        onAddPackage = viewModel::addManualPackage,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MorphingIconBadge(
                    polygon = if (checked.isEmpty()) MaterialShapes.Cookie7Sided else MaterialShapes.Clover4Leaf,
                    icon = Icons.Rounded.Keyboard,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    size = 56.dp
                )
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.trigger_exclusions_hero_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.trigger_exclusions_hero_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

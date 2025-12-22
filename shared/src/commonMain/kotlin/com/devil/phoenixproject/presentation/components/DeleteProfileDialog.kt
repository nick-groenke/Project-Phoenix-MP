package com.devil.phoenixproject.presentation.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.devil.phoenixproject.data.repository.UserProfile
import com.devil.phoenixproject.data.repository.UserProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Confirmation dialog for deleting a user profile.
 */
@Composable
fun DeleteProfileDialog(
    profile: UserProfile,
    profileRepository: UserProfileRepository,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Profile") },
        text = {
            Text("Are you sure you want to delete \"${profile.name}\"? This cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        profileRepository.deleteProfile(profile.id)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

package dev.helm.app.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.helm.app.data.repository.HelmRepository
import dev.helm.app.data.websocket.ConnectionState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    repository: HelmRepository,
) : ViewModel() {
    val connectionState: StateFlow<ConnectionState> = repository.connectionState
}

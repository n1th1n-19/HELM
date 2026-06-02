package dev.helm.app.ui.overview

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.helm.app.data.model.HelmState
import dev.helm.app.data.repository.HelmRepository
import dev.helm.app.data.websocket.ConnectionState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val repository: HelmRepository,
) : ViewModel() {
    val state: StateFlow<HelmState> = repository.state
    val connectionState: StateFlow<ConnectionState> = repository.connectionState
}

package dev.helm.app.ui.media

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.helm.app.data.model.HelmState
import dev.helm.app.data.repository.HelmRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MediaViewModel @Inject constructor(
    repository: HelmRepository,
) : ViewModel() {
    val state: StateFlow<HelmState> = repository.state
}

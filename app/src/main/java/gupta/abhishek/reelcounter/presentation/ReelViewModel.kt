package gupta.abhishek.reelcounter.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gupta.abhishek.reelcounter.data.local.AppClock
import gupta.abhishek.reelcounter.data.local.ReelEntry
import gupta.abhishek.reelcounter.domain.domain.ReelRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReelUiState(
    val todayCount: Int = 0,
    val totalCount: Int = 0,
    val daysSkipped: Int = 0, // how many test days were skipped with "Start new day"
    val reels: List<ReelEntry> = emptyList(),
)

@HiltViewModel
class ReelViewModel @Inject constructor(
    private val repository: ReelRepository,
) : ViewModel() {

    val uiState: StateFlow<ReelUiState> =
        combine(
            repository.todayCount, repository.totalCount, repository.reels, AppClock.offsetMs,
        ) { today, total, reels, offset ->
            ReelUiState(
                todayCount = today,
                totalCount = total,
                daysSkipped = (offset / AppClock.DAY_MS).toInt(),
                reels = reels,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReelUiState(),
        )

    fun startNewDay() = repository.startNewDay()

    fun clearAll() {
        viewModelScope.launch { repository.clear() }
    }
}
package com.metrolist.music.ui.screens.wrapped

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.R
import kotlinx.coroutines.launch

@Composable
fun WrappedScreen(
    navController: NavController,
    viewModel: WrappedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { viewModel.releaseAudio() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.pauseAudio()
                Lifecycle.Event.ON_START -> viewModel.resumeAudio()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!state.isDataReady && !state.isLoading) {
            viewModel.prepare(
                fromTimeStamp = WrappedViewModel.HALF_YEAR_START,
                toTimeStamp = WrappedViewModel.HALF_YEAR_END,
            )
        }
    }

    when {
        state.isLoading || !state.isDataReady -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.wrapped_loading))
            }
        }

        state.error != null -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.wrapped_error))
                Spacer(Modifier.height(8.dp))
                Text(state.error!!)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text(stringResource(R.string.wrapped_back))
                }
            }
        }

        state.isAudioLoading && !state.isAudioReady -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.wrapped_audio_loading),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = {
                        if (state.audioTotalTracks > 0) {
                            state.audioLoadingProgress.toFloat() / state.audioTotalTracks.toFloat()
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.wrapped_audio_progress,
                        state.audioLoadingProgress,
                        state.audioTotalTracks,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { viewModel.skipAudio() }) {
                    Text(stringResource(R.string.wrapped_audio_skip))
                }
            }
        }

        state.audioErrorMessage != null && !state.isAudioReady && !state.isAudioLoading -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.wrapped_audio_error))
                Spacer(Modifier.height(8.dp))
                Text(state.audioErrorMessage!!)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { viewModel.retryAudio() }) {
                        Text(stringResource(R.string.wrapped_audio_retry))
                    }
                    TextButton(onClick = { viewModel.skipAudio() }) {
                        Text(stringResource(R.string.wrapped_audio_skip))
                    }
                }
            }
        }

        state.isDataReady && state.isAudioReady -> {
            val pagerState = rememberPagerState(
                initialPage = 0,
                pageCount = { 19 },
            )

            LaunchedEffect(pagerState.currentPage) {
                viewModel.onPageChanged(pagerState.currentPage)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val goNext: () -> Unit = { scope.launch { pagerState.animateScrollToPage(page + 1) }; Unit }
                    val goPrev: () -> Unit = { scope.launch { pagerState.animateScrollToPage(page - 1) }; Unit }
                    val goFinish: () -> Unit = { navController.popBackStack() }

                    when (page) {
                        0 -> WrappedPage1Intro(state, goNext, {}, goFinish)
                        1 -> WrappedPage2GuessMinutes(state, goNext, goPrev, goFinish)
                        2 -> WrappedPage3MinutesReveal(state, goNext, goPrev, goFinish)
                        3 -> WrappedPage4ShareMinutes(state, goNext, goPrev, goFinish)
                        4 -> WrappedPage5TotalSongs(state, goNext, goPrev, goFinish)
                        5 -> WrappedPage6GuessTopSong(state, goNext, goPrev, goFinish)
                        6 -> WrappedPage7TopSongs(state, goNext, goPrev, goFinish)
                        7 -> WrappedPage8ShareTopTracks(state, goNext, goPrev, goFinish)
                        8 -> WrappedPage9GuessTopArtist(state, goNext, goPrev, goFinish)
                        9 -> WrappedPage10TopArtistReveal(state, goNext, goPrev, goFinish)
                        10 -> WrappedPage11TopArtists(state, goNext, goPrev, goFinish)
                        11 -> WrappedPage12ShareTopArtists(state, goNext, goPrev, goFinish)
                        12 -> WrappedPage13GuessTopAlbum(state, goNext, goPrev, goFinish)
                        13 -> WrappedPage14TopAlbumReveal(state, goNext, goPrev, goFinish)
                        14 -> WrappedPage15TopAlbums(state, goNext, goPrev, goFinish)
                        15 -> WrappedPage16FunStat(state, goNext, goPrev, goFinish)
                        16 -> WrappedPage17Playlist(state, goNext, goPrev, goFinish)
                        17 -> WrappedPage18Conclusion(state, goNext, goPrev, goFinish)
                        18 -> WrappedPage19Credits(state, {}, goPrev, goFinish)
                    }
                }

                TextButton(
                    onClick = { viewModel.toggleMute() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Text(
                        text = if (state.isMuted) stringResource(R.string.wrapped_unmute)
                        else stringResource(R.string.wrapped_mute),
                    )
                }
            }
        }
    }
}

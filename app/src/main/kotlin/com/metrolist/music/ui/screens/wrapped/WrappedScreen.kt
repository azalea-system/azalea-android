package com.metrolist.music.ui.screens.wrapped

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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

        state.isDataReady -> {
            val pagerState = rememberPagerState(
                initialPage = 0,
                pageCount = { 19 },
            )

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
        }
    }
}

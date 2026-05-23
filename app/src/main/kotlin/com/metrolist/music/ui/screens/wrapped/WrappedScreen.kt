package com.metrolist.music.ui.screens.wrapped

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.R

@Composable
fun WrappedScreen(
    navController: NavController,
    viewModel: WrappedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!state.isDataReady && !state.isLoading) {
            viewModel.prepare(
                fromTimeStamp = WrappedViewModel.HALF_YEAR_START,
                toTimeStamp = WrappedViewModel.HALF_YEAR_END,
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.wrapped_loading))
            }

            state.error != null -> {
                Text(stringResource(R.string.wrapped_error))
                Spacer(Modifier.height(8.dp))
                Text(state.error!!)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text(stringResource(R.string.wrapped_back))
                }
            }

            state.isDataReady -> {
                Text(
                    text = stringResource(R.string.wrapped_half_year_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.wrapped_minutes_listened, state.totalMinutes),
                )
                Text(
                    text = stringResource(R.string.wrapped_unique_songs, state.uniqueSongCount),
                )
                Text(
                    text = stringResource(R.string.wrapped_unique_artists, state.uniqueArtistCount),
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text(stringResource(R.string.wrapped_back))
                }
            }
        }
    }
}

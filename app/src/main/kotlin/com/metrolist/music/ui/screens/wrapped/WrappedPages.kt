package com.metrolist.music.ui.screens.wrapped

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val TOTAL_PAGES = 19

@Composable
fun WrappedPageLayout(
    pageIndex: Int,
    onNext: () -> Unit = {},
    onPrev: () -> Unit = {},
    onFinish: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val isFirst = pageIndex == 0
    val isLast = pageIndex == TOTAL_PAGES - 1

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isFirst) {
                TextButton(onClick = onPrev) {
                    Text("< Back")
                }
            } else {
                Spacer(Modifier.width(64.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(TOTAL_PAGES) { i ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (i == pageIndex) 24.dp else 8.dp,
                                height = 8.dp,
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i == pageIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    )
                }
            }

            if (!isLast) {
                TextButton(onClick = onNext) {
                    Text("Next >")
                }
            } else {
                Button(onClick = onFinish) {
                    Text("Finish")
                }
            }
        }
    }
}

@Composable
fun WrappedPage1Intro(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 0, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Your Half-Year in Music",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "January — May 2026",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage2GuessMinutes(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 1, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Guess Your Minutes",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "How many minutes do you think you listened?",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage3MinutesReveal(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 2, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Minutes Listened",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "You listened for ${state.totalMinutes} minutes",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage4ShareMinutes(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 3, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Share Your Minutes",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${state.totalMinutes} minutes — share this stat!",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage5TotalSongs(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 4, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Total Songs",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "You listened to ${state.uniqueSongCount} unique songs",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage6GuessTopSong(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 5, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Guess Your #1 Song",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Can you guess which song you listened to most?",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage7TopSongs(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 6, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Your Top 5 Songs",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            state.topSongs.take(5).forEachIndexed { index, song ->
                Text(
                    text = "#${index + 1}  ${song.title}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
fun WrappedPage8ShareTopTracks(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 7, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Share Your Top Tracks",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Show off your top 5 songs!",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage9GuessTopArtist(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 8, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Guess Your #1 Artist",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Can you pick your most-listened artist?",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage10TopArtistReveal(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 9, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Your #1 Artist",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.topArtists.firstOrNull()?.artist?.name ?: "—",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage11TopArtists(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 10, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Your Top 5 Artists",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            state.topArtists.forEachIndexed { index, artist ->
                Text(
                    text = "#${index + 1}  ${artist.artist.name}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
fun WrappedPage12ShareTopArtists(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 11, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Share Your Top Artists",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Show off your top 5 artists!",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage13GuessTopAlbum(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 12, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Guess Your #1 Album",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Can you guess your most-played album?",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage14TopAlbumReveal(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 13, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Your #1 Album",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.topAlbums.firstOrNull()?.album?.title ?: "—",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage15TopAlbums(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 14, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Your Top 5 Albums",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            state.topAlbums.forEachIndexed { index, album ->
                Text(
                    text = "#${index + 1}  ${album.album.title}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
fun WrappedPage16FunStat(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 15, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Fun Stat",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Something interesting about your listening habits",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage17Playlist(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 16, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Your Wrapped Playlist",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "A playlist of your top songs this half-year",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage18Conclusion(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 17, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Thanks for listening!",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "We hope you enjoyed your half-year in review",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WrappedPage19Credits(
    state: WrappedState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    WrappedPageLayout(pageIndex = 18, onNext = onNext, onPrev = onPrev, onFinish = onFinish) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Made with love",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "by the Metrolist team",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "See you next half-year!",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

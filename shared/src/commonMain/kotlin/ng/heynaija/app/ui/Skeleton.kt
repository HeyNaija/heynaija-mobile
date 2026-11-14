package ng.heynaija.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import ng.heynaija.app.posts.ListGutter
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii

/**
 * Creates an animated linear shimmer brush that sweeps diagonally across skeleton blocks.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    val colors = HeyNaija.colors
    val base = if (colors.dark) colors.panel else colors.line.copy(alpha = 0.65f)
    val highlight = if (colors.dark) colors.lineStrong else colors.surface

    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translateAnim - 400f, translateAnim - 400f),
        end = Offset(translateAnim, translateAnim),
    )
}

/**
 * A placeholder block styled with the animated shimmer brush.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = Radii.chip,
    brush: Brush = rememberShimmerBrush(),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush),
    )
}

/**
 * Skeletal placeholder matching the dimensions and structure of [PostCard].
 */
@Composable
fun PostCardSkeleton(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    focused: Boolean = false,
    brush: Brush = rememberShimmerBrush(),
) {
    Card(modifier) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp)) {
            // Header: Avatar + Author name & handle
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(
                    modifier = Modifier.size(if (compact) 32.dp else 40.dp),
                    shape = CircleShape,
                    brush = brush,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.38f).height(14.dp),
                        shape = Radii.chip,
                        brush = brush,
                    )
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.24f).height(10.dp),
                        shape = Radii.chip,
                        brush = brush,
                    )
                }
            }
            Spacer(Modifier.height(if (compact) 10.dp else 12.dp))
            // Body: Text lines
            SkeletonBlock(
                modifier = Modifier.fillMaxWidth().height(14.dp),
                shape = Radii.chip,
                brush = brush,
            )
            Spacer(Modifier.height(6.dp))
            SkeletonBlock(
                modifier = Modifier.fillMaxWidth(if (focused) 0.95f else 0.82f).height(14.dp),
                shape = Radii.chip,
                brush = brush,
            )
            if (focused) {
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(
                    modifier = Modifier.fillMaxWidth(0.6f).height(14.dp),
                    shape = Radii.chip,
                    brush = brush,
                )
            }
            // Actions row
            if (!compact) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    repeat(4) {
                        SkeletonBlock(
                            modifier = Modifier.width(36.dp).height(16.dp),
                            shape = Radii.chip,
                            brush = brush,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skeletal list of posts with shimmer animation.
 */
@Composable
fun PostListSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 4,
    contentPadding: PaddingValues = PaddingValues(ListGutter),
    brush: Brush = rememberShimmerBrush(),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListGutter),
    ) {
        items(count) {
            PostCardSkeleton(brush = brush)
        }
    }
}

/**
 * Skeletal placeholder for a thread screen: focused post + connector + replies.
 */
@Composable
fun ThreadSkeleton(
    modifier: Modifier = Modifier,
    brush: Brush = rememberShimmerBrush(),
) {
    val colors = HeyNaija.colors
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ListGutter, end = ListGutter, top = ListGutter, bottom = 48.dp),
    ) {
        item {
            PostCardSkeleton(focused = true, brush = brush)
        }
        item {
            Box(Modifier.fillMaxWidth().height(ListGutter)) {
                Box(
                    Modifier
                        .padding(start = 33.dp)
                        .width(2.dp)
                        .height(ListGutter)
                        .background(colors.lineStrong),
                )
            }
        }
        items(3) {
            Column(Modifier.padding(bottom = ListGutter)) {
                PostCardSkeleton(compact = true, brush = brush)
            }
        }
    }
}

/**
 * Skeletal row matching [NotificationRow].
 */
@Composable
fun NotificationRowSkeleton(
    modifier: Modifier = Modifier,
    brush: Brush = rememberShimmerBrush(),
) {
    Card(modifier) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SkeletonBlock(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                brush = brush,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                SkeletonBlock(
                    modifier = Modifier.fillMaxWidth(0.65f).height(14.dp),
                    shape = Radii.chip,
                    brush = brush,
                )
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(
                    modifier = Modifier.fillMaxWidth(0.28f).height(10.dp),
                    shape = Radii.chip,
                    brush = brush,
                )
            }
        }
    }
}

/**
 * Skeletal list of notifications with shimmer animation.
 */
@Composable
fun NotificationsSkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 6,
    brush: Brush = rememberShimmerBrush(),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(ListGutter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count) {
            NotificationRowSkeleton(brush = brush)
        }
    }
}

/**
 * Skeletal placeholder matching [CommunityCard].
 */
@Composable
fun CommunityCardSkeleton(
    modifier: Modifier = Modifier,
    brush: Brush = rememberShimmerBrush(),
) {
    val colors = HeyNaija.colors
    Card(modifier) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(
                    modifier = Modifier.size(48.dp),
                    shape = Radii.control,
                    brush = brush,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.5f).height(16.dp),
                        shape = Radii.chip,
                        brush = brush,
                    )
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.25f).height(12.dp),
                        shape = Radii.chip,
                        brush = brush,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SkeletonBlock(
                modifier = Modifier.fillMaxWidth().height(14.dp),
                shape = Radii.chip,
                brush = brush,
            )
            Spacer(Modifier.height(4.dp))
            SkeletonBlock(
                modifier = Modifier.fillMaxWidth(0.8f).height(14.dp),
                shape = Radii.chip,
                brush = brush,
            )
            HorizontalDivider(Modifier.padding(top = 14.dp), color = colors.line)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(
                    modifier = Modifier.width(120.dp).height(16.dp),
                    shape = Radii.chip,
                    brush = brush,
                )
                SkeletonBlock(
                    modifier = Modifier.width(64.dp).height(28.dp),
                    shape = Radii.control,
                    brush = brush,
                )
            }
        }
    }
}

/**
 * Skeletal list of communities with shimmer animation.
 */
@Composable
fun CommunitiesSkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 4,
    brush: Brush = rememberShimmerBrush(),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(ListGutter),
        verticalArrangement = Arrangement.spacedBy(ListGutter),
    ) {
        items(count) {
            CommunityCardSkeleton(brush = brush)
        }
    }
}

/**
 * Skeletal placeholder for user profile (header + posts).
 */
@Composable
fun ProfileSkeleton(
    modifier: Modifier = Modifier,
    brush: Brush = rememberShimmerBrush(),
) {
    val colors = HeyNaija.colors
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ListGutter, end = ListGutter, top = ListGutter, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(ListGutter),
    ) {
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkeletonBlock(
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            brush = brush,
                        )
                        SkeletonBlock(
                            modifier = Modifier.width(90.dp).height(32.dp),
                            shape = Radii.control,
                            brush = brush,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.45f).height(20.dp),
                        shape = Radii.chip,
                        brush = brush,
                    )
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.28f).height(12.dp),
                        shape = Radii.chip,
                        brush = brush,
                    )
                    Spacer(Modifier.height(12.dp))
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.9f).height(14.dp),
                        shape = Radii.chip,
                        brush = brush,
                    )
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.65f).height(14.dp),
                        shape = Radii.chip,
                        brush = brush,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(3) {
                            SkeletonBlock(
                                modifier = Modifier.width(60.dp).height(16.dp),
                                shape = Radii.chip,
                                brush = brush,
                            )
                        }
                    }
                }
            }
        }
        item {
            // Filter tabs skeleton
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    SkeletonBlock(
                        modifier = Modifier.width(68.dp).height(32.dp),
                        shape = Radii.control,
                        brush = brush,
                    )
                }
            }
        }
        items(2) {
            PostCardSkeleton(brush = brush)
        }
    }
}

/**
 * Skeletal placeholder for community detail (header card + posts).
 */
@Composable
fun CommunityDetailSkeleton(
    modifier: Modifier = Modifier,
    brush: Brush = rememberShimmerBrush(),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ListGutter, end = ListGutter, top = ListGutter, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(ListGutter),
    ) {
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SkeletonBlock(Modifier.size(48.dp), shape = Radii.control, brush = brush)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                SkeletonBlock(Modifier.width(120.dp).height(16.dp), shape = Radii.chip, brush = brush)
                                Spacer(Modifier.height(4.dp))
                                SkeletonBlock(Modifier.width(60.dp).height(12.dp), shape = Radii.chip, brush = brush)
                            }
                        }
                        SkeletonBlock(Modifier.width(72.dp).height(32.dp), shape = Radii.control, brush = brush)
                    }
                    Spacer(Modifier.height(12.dp))
                    SkeletonBlock(Modifier.fillMaxWidth().height(14.dp), shape = Radii.chip, brush = brush)
                }
            }
        }
        items(2) {
            PostCardSkeleton(brush = brush)
        }
    }
}

/**
 * A subtle elastic bounce bar that animates at the top of a feed while dragging down.
 */
@Composable
fun ElasticPullBar(
    pullFraction: Float,
    modifier: Modifier = Modifier,
) {
    if (pullFraction <= 0.02f) return
    val clamped = pullFraction.coerceIn(0f, 1.5f)
    val colors = HeyNaija.colors
    val width = (36 + (clamped * 48)).dp
    val height = (4 + (clamped * 2)).dp
    val alpha = (clamped * 1.4f).coerceIn(0.2f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (clamped * 6).dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(Radii.chip)
                .background(colors.accent.copy(alpha = alpha)),
        )
    }
}



package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.returnyoutubedislike.ReturnYouTubeDislikeInfo;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ThirdPartyApiHelper {
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX = 299;
    private static final int YOUTUBE_SERVICE_ID = 0;
    private static final String TEMP_SEGMENT_UUID = "TEMP";

    private static final Map<String, SponsorBlockSegment[]> SPONSOR_BLOCK_CACHE =
            new ConcurrentHashMap<>();
    private static final Map<String, ReturnYouTubeDislikeInfo> RYD_CACHE =
            new ConcurrentHashMap<>();

    private ThirdPartyApiHelper() {
    }

    public static boolean isYoutubeStream(@Nullable final StreamInfo info) {
        return info != null && info.getServiceId() == YOUTUBE_SERVICE_ID
                && !isBlank(info.getId());
    }

    @Nullable
    public static ReturnYouTubeDislikeInfo getCachedRydInfo(@Nullable final StreamInfo info) {
        if (!isYoutubeStream(info)) {
            return null;
        }
        return RYD_CACHE.get(info.getId());
    }

    @NonNull
    public static ReturnYouTubeDislikeInfo fetchRydInfo(
            @NonNull final Context context,
            @NonNull final StreamInfo info
    ) throws Exception {
        if (!isYoutubeStream(info)) {
            throw new IllegalArgumentException("ReturnYouTubeDislike supports YouTube streams only");
        }

        final String videoId = info.getId();
        final ReturnYouTubeDislikeInfo cached = RYD_CACHE.get(videoId);
        if (cached != null) {
            return cached;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String apiUrl = normalizeBaseUrl(prefs.getString(
                context.getString(R.string.return_youtube_dislike_api_url_key),
                context.getString(R.string.return_youtube_dislike_default_api_url)));
        final String url = apiUrl + "/votes?videoId=" + urlEncode(videoId);
        final Response response = NewPipe.getDownloader().get(url);
        if (response.responseCode() < HTTP_SUCCESS_MIN
                || response.responseCode() > HTTP_SUCCESS_MAX) {
            throw new IllegalStateException("ReturnYouTubeDislike request failed: "
                    + response.responseCode());
        }

        final JSONObject json = new JSONObject(response.responseBody());
        final ReturnYouTubeDislikeInfo infoResult = new ReturnYouTubeDislikeInfo();
        infoResult.viewCount = json.optLong("viewCount", -1);
        infoResult.likes = json.optLong("likes", -1);
        infoResult.dislikes = json.optLong("dislikes", -1);
        RYD_CACHE.put(videoId, infoResult);
        return infoResult;
    }

    @NonNull
    public static SponsorBlockSegment[] getCachedSponsorBlockSegments(
            @Nullable final StreamInfo info
    ) {
        if (!isYoutubeStream(info)) {
            return new SponsorBlockSegment[0];
        }
        return SPONSOR_BLOCK_CACHE.getOrDefault(info.getId(), new SponsorBlockSegment[0]);
    }

    @NonNull
    public static SponsorBlockSegment[] fetchSponsorBlockSegments(
            @NonNull final Context context,
            @NonNull final StreamInfo info
    ) throws Exception {
        if (!isYoutubeStream(info)) {
            return new SponsorBlockSegment[0];
        }

        final String videoId = info.getId();
        final SponsorBlockSegment[] cached = SPONSOR_BLOCK_CACHE.get(videoId);
        if (cached != null) {
            return cached;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final List<String> categories = getEnabledSponsorBlockCategories(context, prefs);
        if (categories.isEmpty()) {
            final SponsorBlockSegment[] empty = new SponsorBlockSegment[0];
            SPONSOR_BLOCK_CACHE.put(videoId, empty);
            return empty;
        }

        final String apiUrl = normalizeBaseUrl(prefs.getString(
                context.getString(R.string.sponsor_block_api_url_key),
                context.getString(R.string.sponsor_block_default_api_url)));
        final String categoryJson = new JSONArray(categories).toString();
        final String url = apiUrl + "/skipSegments?videoID=" + urlEncode(videoId)
                + "&categories=" + urlEncode(categoryJson);
        final Response response = NewPipe.getDownloader().get(url);
        if (response.responseCode() == HTTP_NOT_FOUND) {
            final SponsorBlockSegment[] empty = new SponsorBlockSegment[0];
            SPONSOR_BLOCK_CACHE.put(videoId, empty);
            return empty;
        }
        if (response.responseCode() < HTTP_SUCCESS_MIN
                || response.responseCode() > HTTP_SUCCESS_MAX) {
            throw new IllegalStateException("SponsorBlock request failed: "
                    + response.responseCode());
        }

        final JSONArray json = new JSONArray(response.responseBody());
        final List<SponsorBlockSegment> parsed = new ArrayList<>();
        for (int i = 0; i < json.length(); i++) {
            final JSONObject obj = json.getJSONObject(i);
            final SponsorBlockCategory category = parseCategory(obj.optString("category", ""));
            if (category == null) {
                continue;
            }
            final JSONArray segment = obj.optJSONArray("segment");
            if (segment == null || segment.length() < 2) {
                continue;
            }

            final float startMs = (float) (segment.optDouble(0, 0.0) * 1000.0);
            final float endMs = (float) (segment.optDouble(1, 0.0) * 1000.0);
            if (endMs <= startMs) {
                continue;
            }

            final SponsorBlockAction action = parseAction(obj.optString("actionType", ""), category);
            final String uuid = obj.optString("UUID", obj.optString("uuid", ""));
            parsed.add(new SponsorBlockSegment(uuid, startMs, endMs, category, action));
        }

        parsed.sort(Comparator.comparingDouble(SponsorBlockSegment::getSegmentStart));
        final SponsorBlockSegment[] result = parsed.toArray(new SponsorBlockSegment[0]);
        SPONSOR_BLOCK_CACHE.put(videoId, result);
        return result;
    }

    public static void replacePendingSponsorBlockSegment(
            @NonNull final StreamInfo info,
            @NonNull final SponsorBlockSegment segment
    ) {
        if (!isYoutubeStream(info)) {
            return;
        }
        final List<SponsorBlockSegment> updated = new ArrayList<>();
        for (final SponsorBlockSegment existing : getCachedSponsorBlockSegments(info)) {
            if (!TEMP_SEGMENT_UUID.equals(existing.uuid)) {
                updated.add(existing);
            }
        }
        updated.add(segment);
        updated.sort(Comparator.comparingDouble(SponsorBlockSegment::getSegmentStart));
        SPONSOR_BLOCK_CACHE.put(info.getId(), updated.toArray(new SponsorBlockSegment[0]));
    }

    public static void clearPendingSponsorBlockSegment(@NonNull final StreamInfo info) {
        if (!isYoutubeStream(info)) {
            return;
        }
        final List<SponsorBlockSegment> updated = new ArrayList<>();
        for (final SponsorBlockSegment existing : getCachedSponsorBlockSegments(info)) {
            if (!TEMP_SEGMENT_UUID.equals(existing.uuid)) {
                updated.add(existing);
            }
        }
        SPONSOR_BLOCK_CACHE.put(info.getId(), updated.toArray(new SponsorBlockSegment[0]));
    }

    private static List<String> getEnabledSponsorBlockCategories(
            @NonNull final Context context,
            @NonNull final SharedPreferences prefs
    ) {
        final List<String> categories = new ArrayList<>();
        addCategoryIfEnabled(categories, prefs,
                context.getString(R.string.sponsor_block_category_sponsor_key), "sponsor");
        addCategoryIfEnabled(categories, prefs,
                context.getString(R.string.sponsor_block_category_intro_key), "intro");
        addCategoryIfEnabled(categories, prefs,
                context.getString(R.string.sponsor_block_category_outro_key), "outro");
        addCategoryIfEnabled(categories, prefs,
                context.getString(R.string.sponsor_block_category_interaction_key), "interaction");
        addCategoryIfEnabled(categories, prefs,
                context.getString(R.string.sponsor_block_category_highlight_key), "poi_highlight");
        addCategoryIfEnabled(categories, prefs,
                context.getString(R.string.sponsor_block_category_self_promo_key), "selfpromo");
        addCategoryIfEnabled(categories, prefs,
                context.getString(R.string.sponsor_block_category_non_music_key), "music_offtopic");
        addCategoryIfEnabled(categories, prefs,
                context.getString(R.string.sponsor_block_category_preview_key), "preview");
        addCategoryIfEnabled(categories, prefs,
                context.getString(R.string.sponsor_block_category_filler_key), "filler");
        return categories;
    }

    private static void addCategoryIfEnabled(
            @NonNull final List<String> categories,
            @NonNull final SharedPreferences prefs,
            @NonNull final String key,
            @NonNull final String value
    ) {
        if (prefs.getBoolean(key, false)) {
            categories.add(value);
        }
    }

    @Nullable
    private static SponsorBlockCategory parseCategory(@NonNull final String value) {
        return switch (value) {
            case "sponsor" -> SponsorBlockCategory.SPONSOR;
            case "intro" -> SponsorBlockCategory.INTRO;
            case "outro" -> SponsorBlockCategory.OUTRO;
            case "interaction" -> SponsorBlockCategory.INTERACTION;
            case "selfpromo" -> SponsorBlockCategory.SELF_PROMO;
            case "music_offtopic" -> SponsorBlockCategory.NON_MUSIC;
            case "preview" -> SponsorBlockCategory.PREVIEW;
            case "filler" -> SponsorBlockCategory.FILLER;
            case "poi_highlight" -> SponsorBlockCategory.HIGHLIGHT;
            default -> null;
        };
    }

    private static SponsorBlockAction parseAction(
            @NonNull final String value,
            @NonNull final SponsorBlockCategory category
    ) {
        if (category == SponsorBlockCategory.HIGHLIGHT || "poi".equals(value)) {
            return SponsorBlockAction.POI;
        }
        return switch (value) {
            case "mute" -> SponsorBlockAction.MUTE;
            case "highlight" -> SponsorBlockAction.HIGHLIGHT;
            default -> SponsorBlockAction.SKIP;
        };
    }

    @NonNull
    private static String normalizeBaseUrl(@Nullable final String value) {
        String url = isBlank(value) ? "" : value.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    @NonNull
    private static String urlEncode(@NonNull final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(@Nullable final String value) {
        return value == null || value.trim().isEmpty();
    }
}

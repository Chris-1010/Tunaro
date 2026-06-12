package com.ca.tunaro.utils;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.Map;

/**
 * Pretty-prints JSON with syntax colouring for display on a dark background.
 */
public class JsonHighlighter {

    private static final int COLOR_KEY = 0xFF79B8FF;
    private static final int COLOR_STRING = 0xFF85E89D;
    private static final int COLOR_NUMBER = 0xFFB392F0;
    private static final int COLOR_KEYWORD = 0xFFF97583; // true / false / null
    private static final int COLOR_PUNCTUATION = 0xFFBBBBBB;

    private static final String INDENT = "  ";

    // Above this size the thousands of colour spans make TextView layout slow
    // enough to ANR, so fall back to plain text
    private static final int MAX_HIGHLIGHT_LENGTH = 30_000;

    private static final Gson gson = new Gson();

    public static CharSequence highlight(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (json.length() > MAX_HIGHLIGHT_LENGTH) {
            return new GsonBuilder().setPrettyPrinting().create().toJson(root);
        }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendElement(sb, root, 0);
        return sb;
    }

    private static void appendElement(SpannableStringBuilder sb, JsonElement element, int depth) {
        if (element.isJsonNull()) {
            appendColored(sb, "null", COLOR_KEYWORD);
        } else if (element.isJsonPrimitive()) {
            appendPrimitive(sb, element.getAsJsonPrimitive());
        } else if (element.isJsonArray()) {
            appendArray(sb, element.getAsJsonArray(), depth);
        } else {
            appendObject(sb, element.getAsJsonObject(), depth);
        }
    }

    private static void appendPrimitive(SpannableStringBuilder sb, JsonPrimitive primitive) {
        if (primitive.isString()) {
            appendColored(sb, gson.toJson(primitive), COLOR_STRING);
        } else if (primitive.isBoolean()) {
            appendColored(sb, primitive.getAsString(), COLOR_KEYWORD);
        } else {
            appendColored(sb, primitive.getAsString(), COLOR_NUMBER);
        }
    }

    private static void appendArray(SpannableStringBuilder sb, JsonArray array, int depth) {
        if (array.size() == 0) {
            appendColored(sb, "[]", COLOR_PUNCTUATION);
            return;
        }
        appendColored(sb, "[\n", COLOR_PUNCTUATION);
        int i = 0;
        for (JsonElement item : array) {
            indent(sb, depth + 1);
            appendElement(sb, item, depth + 1);
            if (++i < array.size()) appendColored(sb, ",", COLOR_PUNCTUATION);
            sb.append('\n');
        }
        indent(sb, depth);
        appendColored(sb, "]", COLOR_PUNCTUATION);
    }

    private static void appendObject(SpannableStringBuilder sb, JsonObject object, int depth) {
        if (object.size() == 0) {
            appendColored(sb, "{}", COLOR_PUNCTUATION);
            return;
        }
        appendColored(sb, "{\n", COLOR_PUNCTUATION);
        int i = 0;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            indent(sb, depth + 1);
            appendColored(sb, gson.toJson(new JsonPrimitive(entry.getKey())), COLOR_KEY);
            appendColored(sb, ": ", COLOR_PUNCTUATION);
            appendElement(sb, entry.getValue(), depth + 1);
            if (++i < object.size()) appendColored(sb, ",", COLOR_PUNCTUATION);
            sb.append('\n');
        }
        indent(sb, depth);
        appendColored(sb, "}", COLOR_PUNCTUATION);
    }

    private static void indent(SpannableStringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) sb.append(INDENT);
    }

    private static void appendColored(SpannableStringBuilder sb, String text, int color) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
